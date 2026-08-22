package com.banglu.winime.update

import com.banglu.winime.AppVersion
import com.banglu.winime.UpdateGateway
import com.banglu.winime.UpdateStatus
import com.banglu.winime.Version
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Duration

/**
 * বাংলু টাইপার's in-app updater — and the ONLY networked code in this module.
 *
 * ## Which builds contain it
 *
 * This file lives in `src/msi/kotlin`, the WEBSITE edition's source set, so it
 * is compiled into the MSI build and is physically absent from the Microsoft
 * Store build (`-PbangluStore=true`). The Store updates Store apps, an MSIX
 * container refuses the loopback socket pair the JDK web client's constructor
 * opens, and a package that must never open a socket should not ship a socket
 * library. See `EditionPorts.kt`.
 *
 * ## Why this file exists at all
 *
 * Every shipped fix used to reach the user as "download the MSI again and
 * reinstall it". This file replaces that with the web-app expectation: the app
 * notices a new version, offers it, fetches it, checks it, and hands it to
 * Windows Installer.
 *
 * ## The privacy contract, stated exactly
 *
 * - **What it contacts:** `github.com`, plus whatever `*.githubusercontent.com`
 *   host GitHub redirects release downloads to. Nothing else — every hop is
 *   checked against [Net.isAllowedHost] before a byte is sent, and redirects
 *   are followed BY HAND (`Redirect.NEVER`) precisely so that check cannot be
 *   bypassed by a redirect off-site.
 * - **What it sends:** an unauthenticated `GET` of a static file. No request
 *   body, no cookies, no headers of our own, no query string, no identifier,
 *   no version number, no machine or user attribute. [Net.safeUri] rejects any
 *   URL we author that carries a query, a fragment or user-info, so a
 *   parameter cannot be smuggled in through the published manifest either.
 * - **What it can see:** nothing the user typed. This file imports no engine,
 *   no typing state, no storage; `verifyUpdaterIsolation` in
 *   `build.gradle.kts` fails the build if that ever stops being true, and it
 *   equally fails the build if any file OUTSIDE this package names an HTTP
 *   client.
 * - **When it runs:** once at startup if "স্বয়ংক্রিয় আপডেট" is on, and
 *   whenever the user asks. Never on the typing path — every entry point here
 *   blocks and must be called from a background dispatcher.
 *
 * ## Failure posture
 *
 * Nothing in here may crash the app, block typing, or nag. Every failure — no
 * network, a 404, a truncated download, a checksum mismatch, an installer that
 * will not start — collapses to "no update", is reported as one line in the
 * control window, and is reported at all only when the user asked. An
 * automatic check that fails says nothing.
 */

/** Where the published manifest lives. See the workflow note in the README. */
private const val MANIFEST_URL =
    "https://github.com/Shahabul87/banglu-kmp/releases/download/windows-update/windows-update.json"

/** A manifest is a few hundred bytes; anything larger is not our file. */
private const val MAX_MANIFEST_BYTES = 64 * 1024

/** The MSI carries the 143MB dictionary. Generous, but not unbounded. */
private const val MAX_MSI_BYTES = 512L * 1024 * 1024

private const val MAX_REDIRECTS = 5

/**
 * The published update descriptor.
 *
 * Every field is required on purpose: a manifest missing any one of them is
 * treated as no manifest at all rather than half-applied. `notes` is the one
 * line of Bengali the control window shows beside the offer.
 */
@Serializable
data class UpdateManifest(
    val version: String,
    val url: String,
    val sha256: String,
    val notes: String,
)

/** The outcome of a check. There is deliberately no "error" case here — a
 *  failed check is indistinguishable from "nothing new" to the rest of the app. */
sealed interface UpdateDecision {
    object None : UpdateDecision
    data class Available(val manifest: UpdateManifest) : UpdateDecision
}

/** The host allowlist and the URL shapes this app will and will not fetch. */
internal object Net {
    val manifestUri: URI? get() = safeUri(MANIFEST_URL)

    /**
     * `github.com` and its asset CDN. Written as an exact match plus a
     * dot-anchored suffix so `evilgithubusercontent.com` and
     * `github.com.attacker.net` both fail.
     */
    fun isAllowedHost(host: String?): Boolean {
        val h = host?.lowercase() ?: return false
        return h == "github.com" || h.endsWith(".githubusercontent.com")
    }

    /**
     * For URLs WE author — the manifest URL and the download URL inside it.
     * Strictest form: https, allowed host, and no query, fragment or user-info,
     * which is what makes "we send nothing about the user" checkable rather
     * than merely asserted.
     */
    fun safeUri(raw: String): URI? {
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        if (!uri.isAbsolute) return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (uri.userInfo != null) return null
        if (uri.rawQuery != null || uri.rawFragment != null) return null
        if (!isAllowedHost(uri.host)) return null
        return uri
    }

    /**
     * For a hop WE were redirected to. Same host allowlist and same https
     * requirement, but a query IS permitted: GitHub answers a release-asset
     * download with a redirect to a signed CDN URL, and that signature lives in
     * the query string. We never add one; we only decline to break on
     * GitHub's.
     */
    fun redirectUri(from: URI, location: String): URI? {
        val resolved = runCatching { from.resolve(location) }.getOrNull() ?: return null
        if (!resolved.isAbsolute) return null
        if (!resolved.scheme.equals("https", ignoreCase = true)) return null
        if (resolved.userInfo != null) return null
        if (!isAllowedHost(resolved.host)) return null
        return resolved
    }
}

/** SHA-256, and the rule that an unverified file does not survive. */
object Checksum {
    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return hex(digest.digest())
    }

    /**
     * True only if [file] hashes to [expected]. On ANY other outcome — wrong
     * hash, unreadable file, malformed expectation — the file is DELETED
     * before returning false. A downloaded MSI that failed its checksum is not
     * a diagnostic artefact; it is an installer we must make sure nobody can
     * double-click later.
     */
    fun verifyOrDelete(file: File, expected: String): Boolean {
        val ok = expected.matches(Regex("[0-9a-fA-F]{64}")) &&
            runCatching { sha256(file) }.getOrNull()?.equals(expected, ignoreCase = true) == true
        if (!ok) file.delete()
        return ok
    }
}

/**
 * The JDK's own HTTP client, redirect-following disabled so every hop passes
 * [Net.redirectUri]. No interceptors, no cookie handler, no authenticator, no
 * proxy configuration and no headers beyond the ones the JDK itself must send.
 */
internal class Http(
    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(Duration.ofSeconds(15))
        .build(),
) {
    private fun request(uri: URI): HttpRequest =
        HttpRequest.newBuilder(uri).GET().timeout(Duration.ofMinutes(30)).build()

    /** A 200 response's body stream, or null for anything else at all. */
    private fun open(start: URI): HttpResponse<InputStream>? {
        var uri = start
        repeat(MAX_REDIRECTS) {
            val response = runCatching {
                client.send(request(uri), HttpResponse.BodyHandlers.ofInputStream())
            }.getOrNull() ?: return null
            val status = response.statusCode()
            if (status in 301..308) {
                runCatching { response.body().close() }
                val location = response.headers().firstValue("location").orElse(null) ?: return null
                uri = Net.redirectUri(uri, location) ?: return null
                return@repeat
            }
            if (status != 200) {
                runCatching { response.body().close() }
                return null
            }
            return response
        }
        return null
    }

    fun fetchText(uri: URI, maxBytes: Int): String? {
        val response = open(uri) ?: return null
        return runCatching {
            response.body().use { input ->
                val bytes = input.readNBytes(maxBytes + 1)
                if (bytes.size > maxBytes) null else bytes.toString(Charsets.UTF_8)
            }
        }.getOrNull()
    }

    /**
     * Streams [uri] into [target], hashing as it goes, reporting whole-percent
     * progress. Returns the SHA-256 of what was written, or null on any
     * failure; the caller deletes a partial file either way.
     */
    fun download(uri: URI, target: File, maxBytes: Long, onProgress: (Int) -> Unit): String? {
        val response = open(uri) ?: return null
        val total = response.headers().firstValueAsLong("content-length").orElse(-1L)
        val digest = MessageDigest.getInstance("SHA-256")
        target.parentFile?.mkdirs()
        var written = 0L
        var lastPercent = -1
        val ok = runCatching {
            response.body().use { input ->
                target.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        written += read
                        if (written > maxBytes) return@runCatching false
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                        if (total > 0) {
                            val percent = ((written * 100) / total).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }
            true
        }.getOrDefault(false)
        return if (ok) Checksum.hex(digest.digest()) else null
    }
}

/**
 * Parsing and comparison, with no I/O at all — the whole decision this feature
 * turns on, in one pure function, so every branch is testable without a socket.
 */
object UpdatePlan {
    private val json = Json { ignoreUnknownKeys = true }

    fun decide(manifestJson: String?, currentVersion: String?): UpdateDecision {
        if (manifestJson == null || currentVersion == null) return UpdateDecision.None
        val manifest = runCatching { json.decodeFromString<UpdateManifest>(manifestJson) }
            .getOrNull() ?: return UpdateDecision.None
        if (Version.parse(manifest.version) == null) return UpdateDecision.None
        if (!manifest.sha256.matches(Regex("[0-9a-fA-F]{64}"))) return UpdateDecision.None
        if (Net.safeUri(manifest.url) == null) return UpdateDecision.None
        if (!Version.isNewer(manifest.version, currentVersion)) return UpdateDecision.None
        return UpdateDecision.Available(manifest)
    }
}

/**
 * Hands the MSI to Windows Installer.
 *
 * `/qb` shows a progress bar and no questions. `REBOOT=ReallySuppress` is the
 * belt to `upgradeUuid`'s braces: the stable UpgradeCode is what lets the
 * installer replace the old product in place, and this property is what
 * guarantees that even if something IS still locked, Windows Installer reports
 * it rather than scheduling a restart and prompting for one.
 *
 * Windows-only by construction, exactly like `StartupRegistry` — the guard is
 * checked before [ProcessBuilder] is touched so this repo's Mac test suite can
 * never spawn anything.
 */
internal object Installer {
    fun isWindowsOs(osName: String?): Boolean =
        osName?.contains("windows", ignoreCase = true) == true

    fun launch(msi: File, osName: String? = System.getProperty("os.name")): Boolean {
        if (!isWindowsOs(osName)) return false
        if (!msi.isFile) return false
        return runCatching {
            ProcessBuilder("msiexec", "/i", msi.absolutePath, "/qb", "REBOOT=ReallySuppress")
                .start()
            true
        }.getOrDefault(false)
    }
}

private const val LINE_CHECKING = "আপডেট খোঁজা হচ্ছে…"
private const val LINE_UP_TO_DATE = "আপনি সর্বশেষ সংস্করণেই আছেন"
private const val LINE_CHECK_FAILED =
    "আপডেট দেখা গেল না — ইন্টারনেট সংযোগ পরীক্ষা করে আবার চেষ্টা করুন।"
private const val LINE_VERIFYING = "আপডেট যাচাই করা হচ্ছে…"
private const val LINE_DOWNLOAD_FAILED = "আপডেট নামানো যায়নি — পরে আবার চেষ্টা করুন।"
private const val LINE_CHECKSUM_FAILED =
    "আপডেট ফাইলটি ঠিকমতো আসেনি, তাই বাতিল করা হলো। পরে আবার চেষ্টা করুন।"
private const val LINE_INSTALLER_FAILED = "ইনস্টলার চালু করা যায়নি — পরে আবার চেষ্টা করুন।"
private const val LINE_INSTALLING =
    "আপডেট ইনস্টল হচ্ছে। বাংলু টাইপার এখন বন্ধ হবে — ইনস্টল শেষ হলে Start মেনু থেকে " +
        "আবার চালু করুন।"

/**
 * The update flow, start to finish. Every method BLOCKS: call from a
 * background dispatcher only, never from the AWT event thread and never from
 * anything on the typing path.
 *
 * [report] is called with the whole UI state each time it changes; the caller
 * is responsible for hopping to the UI thread (`Main.kt` does it with
 * `EventQueue.invokeLater`, like every other cross-thread write there).
 */
class UpdateService(
    private val downloadDir: File,
    private val currentVersion: String? = AppVersion.current,
    private val report: (UpdateStatus) -> Unit,
) : UpdateGateway {
    // Not injectable, deliberately: the seam a test would use to swap this out
    // is also the seam that would let a future caller hand this class a
    // different, unaudited transport. The decision logic every test cares
    // about lives in UpdatePlan/Version/Net/Checksum, which need no transport
    // at all.
    private val http = Http()

    @Volatile private var pending: UpdateManifest? = null
    @Volatile private var busy = false

    /**
     * [manual] is the whole difference between honest and naggy. A user who
     * asked gets an answer either way; an automatic startup check that found
     * nothing — or could not reach the network at all — says nothing, because
     * a fresh install on a train should not open with an error.
     */
    override fun check(manual: Boolean) {
        if (busy) return
        busy = true
        try {
            if (manual) report(UpdateStatus(line = LINE_CHECKING, busy = true))
            prune()
            val uri = Net.manifestUri
            val body = uri?.let { http.fetchText(it, MAX_MANIFEST_BYTES) }
            when (val decision = UpdatePlan.decide(body, currentVersion)) {
                is UpdateDecision.Available -> {
                    pending = decision.manifest
                    report(UpdateStatus(line = offerLine(decision.manifest), actionable = true))
                }
                UpdateDecision.None -> {
                    pending = null
                    val line = when {
                        !manual -> ""
                        body == null -> LINE_CHECK_FAILED
                        else -> "$LINE_UP_TO_DATE (v${currentVersion ?: "?"})"
                    }
                    report(UpdateStatus(line = line))
                }
            }
        } catch (t: Throwable) {
            // Belt and braces: nothing reaches the caller, so a hostile or
            // broken response can never take the keyboard down with it.
            System.err.println("Banglu update check failed: $t")
            pending = null
            report(UpdateStatus(line = if (manual) LINE_CHECK_FAILED else ""))
        } finally {
            busy = false
        }
    }

    /**
     * Downloads, verifies, launches the installer, and only then calls
     * [onInstallerStarted] — which the caller wires to quitting the app. The
     * app MUST exit promptly: the installer cannot replace files the running
     * app holds open, and a file it cannot replace is what makes Windows
     * Installer ask for a reboot.
     *
     * Every failure branch leaves the app running, the offer intact, and one
     * explanatory line in the control window.
     */
    override fun install(onInstallerStarted: () -> Unit) {
        val manifest = pending ?: return
        if (busy) return
        busy = true
        try {
            val uri = Net.safeUri(manifest.url)
            if (uri == null) {
                report(UpdateStatus(line = LINE_DOWNLOAD_FAILED, actionable = true))
                return
            }
            val target = File(downloadDir, "BangluTyper-${manifest.version}.msi")
            report(UpdateStatus(line = downloadLine(0), busy = true))
            val hashed = http.download(uri, target, MAX_MSI_BYTES) { percent ->
                report(UpdateStatus(line = downloadLine(percent), busy = true))
            }
            if (hashed == null) {
                target.delete()
                report(UpdateStatus(line = LINE_DOWNLOAD_FAILED, actionable = true))
                return
            }
            report(UpdateStatus(line = LINE_VERIFYING, busy = true))
            if (!Checksum.verifyOrDelete(target, manifest.sha256)) {
                report(UpdateStatus(line = LINE_CHECKSUM_FAILED, actionable = true))
                return
            }
            if (!Installer.launch(target)) {
                report(UpdateStatus(line = LINE_INSTALLER_FAILED, actionable = true))
                return
            }
            report(UpdateStatus(line = LINE_INSTALLING, busy = true))
            onInstallerStarted()
        } catch (t: Throwable) {
            System.err.println("Banglu update install failed: $t")
            report(UpdateStatus(line = LINE_DOWNLOAD_FAILED, actionable = true))
        } finally {
            busy = false
        }
    }

    private fun offerLine(manifest: UpdateManifest): String =
        "নতুন সংস্করণ ${manifest.version} এসেছে — ${manifest.notes}"

    private fun downloadLine(percent: Int): String = "আপডেট নামছে… $percent%"

    /**
     * Removes MSIs left behind by an earlier run. We exit as soon as the
     * installer starts, so nothing else ever gets the chance to clean up, and
     * a ~150MB file per update would otherwise accumulate forever in
     * `~/.banglu/updates`.
     */
    private fun prune() {
        runCatching {
            downloadDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".msi")) file.delete()
            }
        }
    }
}
