import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File as JFile
import java.net.URLClassLoader
import java.util.Properties
import javax.imageio.ImageIO

plugins {
    kotlin("jvm")
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * THE single source of truth for this app's version.
 *
 * It stamps three things that must never disagree: the MSI's `packageVersion`
 * (what Windows Installer compares to decide "is this an upgrade?"), the
 * generated `banglu-typer-version.txt` resource the running app reads to know
 * what it is, and the `version` field of the update manifest the release
 * workflow publishes. An update that does not increment this is invisible to
 * both Windows Installer and the in-app updater, so BUMP IT on every shippable
 * change (same discipline as android-keyboard's versionCode/versionName).
 */
val bangluTyperVersion = "1.0.15"

/**
 * THE edition switch. `-PbangluStore=true` builds the Microsoft Store variant.
 *
 * It selects one of two extra source directories — `src/msi/kotlin` (default,
 * downloaded-from-the-website build: in-app updater + `HKCU\…\Run` start-on-
 * login) or `src/store/kotlin` (MSIX build: neither) — and the matching extra
 * test directory. The rationale, and what each edition may and may not
 * contain, lives in `src/main/kotlin/com/banglu/winime/EditionPorts.kt`.
 *
 * This is a source-set swap rather than a runtime flag on purpose: the Store
 * package must not merely refrain from using the JDK web client, it must not
 * CONTAIN it. `verifyStoreEdition` below is the gate that keeps that true.
 */
val bangluStoreEdition = project.findProperty("bangluStore") == "true"
val editionName = if (bangluStoreEdition) "store" else "msi"

kotlin.sourceSets.named("main") { kotlin.srcDir("src/$editionName/kotlin") }
kotlin.sourceSets.named("test") { kotlin.srcDir("src/${editionName}Test/kotlin") }

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(libs.kotlinx.coroutines.core)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    implementation("org.xerial:sqlite-jdbc:3.45.1.0")
    implementation(libs.kotlinx.serialization.json)
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")
    testImplementation(kotlin("test"))
}

/**
 * The MSI ships no HTTP stack it does not use.
 *
 * `:shared`'s jvmMain declares `ktor-client-okhttp` transitively for other
 * consumers; nothing in `shared/src` imports `io.ktor` or `okhttp` (verified by
 * grep), and this module's only network code is [java.net.http.HttpClient] from
 * the JDK. Dragging ktor + okhttp + okio (13 jars, ~3.1 MB) into an app whose
 * whole promise is "your typing never leaves your machine" would put three
 * general-purpose HTTP clients on the classpath that no code path can reach —
 * a claim nobody can audit by reading the code.
 *
 * Scoped to THIS project's configurations only: `:shared` and every other
 * consumer keep their dependencies untouched.
 */
configurations.configureEach {
    exclude(group = "io.ktor")
    exclude(group = "com.squareup.okhttp3")
    exclude(group = "com.squareup.okio")
}

/**
 * Writes [bangluTyperVersion] into a classpath resource so the running app can
 * read its own version without a hardcoded Kotlin constant that would silently
 * drift from `packageVersion`. `jpackage.app-version` (set in
 * `app/BangluTyper.cfg` by jpackage) is the authoritative answer for an
 * INSTALLED app; this resource is what makes a `./gradlew :windows-ime:run` dev
 * session — and the smoke workflow's app image — able to answer too.
 */
val generatedVersionDir = layout.buildDirectory.dir("generated/bangluVersion")
val generateVersionResource by tasks.registering {
    val outDir = generatedVersionDir
    val version = bangluTyperVersion
    inputs.property("version", version)
    outputs.dir(outDir)
    doLast {
        val file = outDir.get().file("banglu-typer-version.txt").asFile
        file.parentFile.mkdirs()
        file.writeText(version)
    }
}
sourceSets.named("main") { resources.srcDir(generatedVersionDir) }
tasks.named("processResources") { dependsOn(generateVersionResource) }

compose.desktop {
    application {
        mainClass = "com.banglu.winime.MainKt"
        // Packaging needs a full JDK with jpackage (the IDE JBR lacks it).
        // Same fallback chain as desktop-app/build.gradle.kts (S128 lesson):
        // explicit BANGLU_JDK, then the known dev JDK if it exists on THIS
        // machine, then the running JVM — a bare developer path breaks
        // clean-clone builds.
        javaHome = System.getenv("BANGLU_JDK")
            ?: "/Users/mdshahabulalam/.gradle/jdks/eclipse_adoptium-17-aarch64-os_x.2/jdk-17.0.17+10/Contents/Home"
                .takeIf { JFile(it).exists() }
            ?: System.getProperty("java.home")
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi)
            // jpackage builds a MINIMAL runtime; Compose's default module set
            // omits these. jdeps-verified requirements of our jars — without
            // java.sql the installed app dies at first convert (JDBC store).
            // java.net.http carries HttpClient, the JDK's own HTTP stack and
            // the ONLY networking this app has (update/Updater.kt). Without it
            // the installed app dies with NoClassDefFoundError the first time
            // it checks for an update — a jpackage runtime contains exactly the
            // modules named here.
            modules(
                "java.sql", "java.instrument", "java.management",
                "jdk.unsupported", "java.net.http",
            )
            packageName = "BangluTyper"
            packageVersion = bangluTyperVersion
            description = "Type Bangla anywhere on Windows"
            vendor = "Banglu"
            licenseFile.set(rootProject.layout.projectDirectory.file("LICENSE"))
            // resources/common/dictionary.sqlite is bundled into the MSI
            // (CI copies it; gitignored — 143MB). LICENSES.md in the same
            // dir IS committed — see verifyPackagedDictionary below.
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))
            windows {
                iconFile.set(project.layout.projectDirectory.file("icons/banglu.ico"))
                menu = true; shortcut = true
                // ── THE UPGRADE CODE — NEVER CHANGE THIS VALUE ──────────────
                // Windows Installer identifies a *product family* by its
                // UpgradeCode. jpackage generates a RANDOM one per build when
                // none is given, so every MSI we shipped was a different
                // product: installing 1.0.1 next to 1.0.0 left both in Add/
                // Remove Programs, the new one could not replace files the
                // running old one held open, and Windows Installer's answer to
                // a locked file is to schedule the replacement for next boot —
                // which is exactly the "restart your PC" prompt users hit.
                // With a stable UpgradeCode the FindRelatedProducts/
                // RemoveExistingProducts sequence runs instead: the old version
                // is uninstalled and the new one installed in one transaction,
                // in place. Change this value and every already-installed copy
                // becomes un-upgradable forever.
                upgradeUuid = "32c380f5-14c1-4cf8-b3fb-cca107852703"
                // Per-user install (%LOCALAPPDATA%), not Program Files. Two
                // reasons, both about the update story: an in-place upgrade of
                // a per-machine install needs elevation, so every silent update
                // would raise a UAC prompt; and files under Program Files held
                // open by the running app are precisely what pushes Windows
                // Installer into reboot-scheduling. ONE-TIME COST: an existing
                // per-machine 1.0.0 is a different product (no UpgradeCode at
                // all) and is not replaced — it must be uninstalled by hand
                // once. From 1.0.1 onward upgrades are in place and silent.
                perUserInstall = true
                // -PbangluConsole=true builds a console launcher. A GUI-subsystem
                // jpackage app reports every startup failure as the same opaque
                // "Failed to launch JVM" box with the real cause discarded; the
                // console build prints it. Ships false — users must never see a
                // terminal window behind the tray app.
                console = project.findProperty("bangluConsole") == "true"
            }
        }
    }
}

kotlin { jvmToolchain(17) }
tasks.withType<Test> {
    useJUnitPlatform()
    maxHeapSize = "4g"   // full validator + bigrams, same as :shared jvmTest
}

// S128 pattern (see desktop-app/build.gradle.kts): packaging shipped
// whatever dictionary.sqlite sat in resources/common — a stale version
// passes every test (tests read the repo-root db) and then gets REJECTED
// by the runtime version gate inside the installed app, silently degrading
// it to seed mode. This task makes the version match a PACKAGING
// dependency: it reads the db's metadata version with the project's own
// sqlite-jdbc and compares it to DictionaryVersion.REQUIRED parsed from
// the shared source of truth.
val verifyPackagedDictionary by tasks.registering {
    val dbFile = project.layout.projectDirectory.file("resources/common/dictionary.sqlite").asFile
    val versionSource = rootProject.layout.projectDirectory
        .file("shared/src/commonMain/kotlin/com/banglu/engine/DictionaryVersion.kt").asFile
    inputs.file(versionSource)
    outputs.upToDateWhen { false }
    doLast {
        require(dbFile.exists()) {
            "resources/common/dictionary.sqlite is missing — copy the current repo-root dictionary.sqlite before packaging"
        }
        val required = Regex("""REQUIRED\s*=\s*"([^"]+)"""")
            .find(versionSource.readText())?.groupValues?.get(1)
            ?: error("Could not parse DictionaryVersion.REQUIRED")
        val jars = configurations.getByName("runtimeClasspath").files
            .filter { it.name.contains("sqlite-jdbc") || it.name.contains("slf4j") }
        require(jars.any { it.name.contains("sqlite-jdbc") }) { "sqlite-jdbc not on runtimeClasspath" }
        val loader = URLClassLoader(jars.map { it.toURI().toURL() }.toTypedArray())
        val driver = Class.forName("org.sqlite.JDBC", true, loader)
            .getDeclaredConstructor().newInstance() as java.sql.Driver
        val conn = driver.connect("jdbc:sqlite:${dbFile.absolutePath}", Properties())
        val actual = conn.use { c ->
            c.createStatement().use { st ->
                st.executeQuery("SELECT value FROM metadata WHERE key='version'").use { rs ->
                    if (rs.next()) rs.getString(1) else "missing"
                }
            }
        }
        require(actual == required) {
            "Packaged dictionary version $actual != engine REQUIRED $required — " +
                "the installed app would reject it at runtime and fall back to seed mode. " +
                "cp <repo-root>/dictionary.sqlite windows-ime/resources/common/dictionary.sqlite"
        }
        logger.lifecycle("packaged dictionary version OK: $actual")
        // The resources dir's dictionary.sqlite is gitignored (143MB), but
        // LICENSES.md there IS committed (dataset notices + font OFL) — no
        // copy-on-package needed, unlike desktop-app which regenerates its
        // notices file from a different source of truth at every build.
    }
}

// Every packaging/distribution task must refuse a stale dictionary.
tasks.matching { it.name.startsWith("package") || it.name.startsWith("createDistributable") }
    .configureEach { dependsOn(verifyPackagedDictionary) }

/**
 * Every Kotlin file that this build actually compiles into the app: the shared
 * `main` tree plus the one edition tree selected above. Both isolation gates
 * walk this — a law that only held for `src/main/kotlin` would have a hole the
 * size of an edition source set.
 */
val compiledSourceRoots: List<JFile> = listOf(
    project.layout.projectDirectory.dir("src/main/kotlin").asFile,
    project.layout.projectDirectory.dir("src/$editionName/kotlin").asFile,
)

fun compiledKotlinFiles(): List<Pair<JFile, JFile>> =
    compiledSourceRoots.flatMap { root ->
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.map { root to it }
    }

val verifyHookIsolation by tasks.registering {
    compiledSourceRoots.forEach { inputs.dir(it) }
    doLast {
        val offenders = compiledKotlinFiles()
            // invariantSeparatorsPath, not `path`: on a Windows runner the
            // separator is a backslash, so a literal "/hook/" test never
            // matched and the gate reported the hook's OWN files as the
            // violation — the first time it ran on windows-latest it failed
            // the Store build for having a hook layer at all.
            .filterNot { (_, f) -> f.invariantSeparatorsPath.contains("/hook/") }
            .filter { (_, f) -> f.readText().contains("import com.sun.jna") }
            .map { (root, f) -> f.relativeTo(root.parentFile.parentFile).invariantSeparatorsPath }
        require(offenders.isEmpty()) {
            "JNA imports outside hook/ (spec isolation law): $offenders"
        }
    }
}
tasks.named("check") { dependsOn(verifyHookIsolation) }

/**
 * The privacy boundary, enforced the same way the JNA one is.
 *
 * This module's rule used to be "no network capability anywhere". The in-app
 * updater is a deliberate, narrow exception, and "narrow" has to be a property
 * of the source tree rather than a promise in a README:
 *
 *  1. Only `update/` may name an HTTP client. Every other file — the hook, the
 *     controller, the composer, storage, the UI — stays structurally unable to
 *     open a socket, so nothing derived from a keystroke has anywhere to go.
 *  2. `update/` may not reach the engine, the composer, the controller or
 *     storage. The one file that CAN talk to the network cannot see a single
 *     character the user typed.
 *
 * Together those two greps are the actual guarantee behind the README's
 * privacy claim.
 */
val verifyUpdaterIsolation by tasks.registering {
    compiledSourceRoots.forEach { inputs.dir(it) }
    doLast {
        val networkTokens = listOf("java.net.http", "HttpClient", "URLConnection", "java.net.Socket")
        val typingTokens = listOf(
            "com.banglu.engine", "com.banglu.winime.composer", "SmartEngine",
            "WinStorage", "Controller", "Composer",
        )
        val files = compiledKotlinFiles()
        val networkOutside = files
            .filterNot { (_, f) -> f.invariantSeparatorsPath.contains("/update/") }
            .filter { (_, f) -> networkTokens.any { f.readText().contains(it) } }
            .map { (root, f) -> f.relativeTo(root.parentFile.parentFile).path }
        require(networkOutside.isEmpty()) {
            "HTTP client references outside update/ (privacy boundary): $networkOutside"
        }
        val typingInside = files
            .filter { (_, f) -> f.invariantSeparatorsPath.contains("/update/") }
            .filter { (_, f) -> typingTokens.any { f.readText().contains(it) } }
            .map { (root, f) -> f.relativeTo(root.parentFile.parentFile).path }
        require(typingInside.isEmpty()) {
            "update/ must not reach the engine/composer/controller/storage: $typingInside"
        }
    }
}
tasks.named("check") { dependsOn(verifyUpdaterIsolation) }

/**
 * The Store edition's own law: the package contains no networking and no
 * Run-key writer AT ALL.
 *
 * `verifyUpdaterIsolation` above proves the network is confined to `update/`.
 * That is the right law for the MSI, and it is not enough for the Store, where
 * the requirement is absence rather than confinement — an MSIX container
 * refuses the loopback socket pair the JDK web client opens in its
 * CONSTRUCTOR, so a Store build that so much as constructs one dies at
 * start-up, and a Store app that writes `HKCU\…\Run` writes a path that no
 * longer exists after the next update. Both were found the expensive way; this
 * grep is what stops either from creeping back in through a shared file.
 *
 * No-op for the MSI edition, where both things are legitimate.
 */
val verifyStoreEdition by tasks.registering {
    compiledSourceRoots.forEach { inputs.dir(it) }
    onlyIf { bangluStoreEdition }
    doLast {
        val forbidden = mapOf(
            "java.net.http" to "the JDK web client cannot be constructed inside an MSIX container",
            "HttpClient" to "the JDK web client cannot be constructed inside an MSIX container",
            "URLConnection" to "a Store build must ship no network stack",
            "java.net.Socket" to "a Store build must ship no network stack",
            "CurrentVersion\\\\Run" to "a packaged app's start-on-login is a manifest StartupTask, not a Run key",
            "UpdateService" to "the updater must be absent from the Store build, not merely unused",
        )
        val offenders = compiledKotlinFiles().flatMap { (root, f) ->
            val text = f.readText()
            forbidden.entries.filter { text.contains(it.key) }
                .map { "${f.relativeTo(root.parentFile.parentFile).path}: '${it.key}' — ${it.value}" }
        }
        require(offenders.isEmpty()) {
            "Store edition contains code it must never run:\n  " + offenders.joinToString("\n  ")
        }
        logger.lifecycle("store edition OK: no network stack, no Run-key writer, no updater")
    }
}
tasks.named("check") { dependsOn(verifyStoreEdition) }

/**
 * The MSI is the WEBSITE edition's package, and the website edition is the one
 * with the updater. Building an MSI from a Store-flavoured source tree would
 * produce an installer that can never tell its users a fix exists — silently,
 * and only discoverable months later.
 */
tasks.matching { it.name.startsWith("packageMsi") || it.name.startsWith("packageReleaseMsi") }
    .configureEach {
        doFirst {
            require(!bangluStoreEdition) {
                "packageMsi builds the website edition; drop -PbangluStore=true " +
                    "(the Store edition ships as an MSIX — see windows-ime/msix/)"
            }
        }
    }

// ── MSIX (Microsoft Store) packaging inputs ─────────────────────────────────
//
// jpackage builds the app IMAGE; MakeAppx turns that image plus a manifest and
// a set of PNG tiles into the .msix. The two tasks below produce the manifest
// and the tiles. They are plain JVM work with no Windows dependency, so they
// run (and can be reviewed) on this repo's Mac dev machine — only MakeAppx
// itself needs the Windows runner.

val msixOutDir = layout.buildDirectory.dir("msix")

/**
 * Generates `AppxManifest.xml` from `windows-ime/store-identity.md`.
 *
 * The identity values are PARSED from that file rather than repeated here, so
 * there is exactly one place they can be wrong. Partner Center rejects an
 * upload whose Identity/Name, Publisher or PublisherDisplayName differ from the
 * ones it assigned — by even a character — and a second hand-maintained copy of
 * three opaque strings is a copy that eventually drifts.
 *
 * The package version is `bangluTyperVersion` plus a fourth component of `0`:
 * MSIX versions are four-part and the Store RESERVES the revision component,
 * rejecting any package whose fourth number is non-zero.
 */
val generateAppxManifest by tasks.registering {
    group = "banglu"
    description = "Write build/msix/AppxManifest.xml from store-identity.md + bangluTyperVersion"
    val identityDoc = project.layout.projectDirectory.file("store-identity.md").asFile
    val outFile = msixOutDir.map { it.file("AppxManifest.xml") }
    val version = bangluTyperVersion
    inputs.file(identityDoc)
    inputs.property("version", version)
    outputs.file(outFile)
    doLast {
        fun field(name: String): String =
            Regex("""\|\s*`?\Q$name\E`?\s*\|\s*`?([^`|]+?)`?\s*\|""")
                .find(identityDoc.readText())?.groupValues?.get(1)?.trim()
                ?: error("store-identity.md has no row for '$name' — the Store manifest cannot be generated")

        val identityName = field("Package/Identity/Name")
        val publisher = field("Package/Identity/Publisher")
        val publisherDisplayName = field("Package/Properties/PublisherDisplayName")
        require(publisher.startsWith("CN=")) {
            "Publisher must be the full X.500 string Partner Center assigned, got '$publisher'"
        }
        val target = outFile.get().asFile
        target.parentFile.mkdirs()
        target.writeText(appxManifest(identityName, publisher, publisherDisplayName, "$version.0"))
        logger.lifecycle("wrote ${target.absolutePath} (Identity/Name=$identityName version=$version.0)")
    }
}

/**
 * The MSIX visual assets, scaled from `icons/banglu.ico`.
 *
 * jpackage takes a `.ico`; MSIX takes PNG tiles at several fixed sizes, and
 * `Square150x150Logo`, `Square44x44Logo` and `StoreLogo` are mandatory — a
 * package missing any of them fails certification. Rather than invent new
 * artwork, this extracts the 256x256 PNG that already lives inside the existing
 * `.ico` (every entry in it is PNG-encoded) and downsamples it.
 */
val generateMsixAssets by tasks.registering {
    group = "banglu"
    description = "Scale windows-ime/icons/banglu.ico into build/msix/Assets/*.png for MSIX"
    val icoFile = project.layout.projectDirectory.file("icons/banglu.ico").asFile
    val outDir = msixOutDir.map { it.dir("Assets") }
    inputs.file(icoFile)
    outputs.dir(outDir)
    doLast {
        val source = largestIcoImage(icoFile)
        val dir = outDir.get().asFile
        dir.mkdirs()
        // Square sizes: the three mandatory logos, the two optional tile sizes,
        // and the target-size variants Windows picks from for the taskbar, the
        // Start list and Alt-Tab. Names are the plain (scale-100) forms, which
        // is what a package with no scale-qualified assets is expected to have.
        val squares = mapOf(
            "StoreLogo.png" to 50,
            "Square44x44Logo.png" to 44,
            "Square44x44Logo.targetsize-16.png" to 16,
            "Square44x44Logo.targetsize-24.png" to 24,
            "Square44x44Logo.targetsize-32.png" to 32,
            "Square44x44Logo.targetsize-48.png" to 48,
            "Square44x44Logo.targetsize-256.png" to 256,
            "Square71x71Logo.png" to 71,
            "Square150x150Logo.png" to 150,
            "Square310x310Logo.png" to 310,
        )
        squares.forEach { (name, size) ->
            ImageIO.write(scaleSquare(source, size), "png", JFile(dir, name))
        }
        // The wide tile is not square: the glyph is centred at tile height on a
        // transparent field rather than stretched, which is what a stretched
        // logo looks like at 310x150 (bad).
        ImageIO.write(wideTile(source, 310, 150), "png", JFile(dir, "Wide310x150Logo.png"))
        logger.lifecycle("wrote ${squares.size + 1} MSIX assets to ${dir.absolutePath}")
    }
}

/**
 * Reads the largest image out of a Vista-era `.ico` whose entries are PNGs.
 *
 * ImageIO cannot read `.ico`, but every entry in `icons/banglu.ico` is a
 * complete PNG file, so the directory is walked by hand and the payload handed
 * to ImageIO as-is. A `.ico` built from BMP/DIB entries instead would fail
 * here, loudly, rather than silently producing blank tiles.
 */
fun largestIcoImage(ico: JFile): BufferedImage {
    val bytes = ico.readBytes()
    fun u16(at: Int) = (bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8)
    fun u32(at: Int) = u16(at).toLong() or (u16(at + 2).toLong() shl 16)
    require(u16(0) == 0 && u16(2) == 1) { "${ico.name} is not an ICO file" }
    val count = u16(4)
    require(count > 0) { "${ico.name} contains no images" }
    var best = -1
    var bestSize = -1
    for (i in 0 until count) {
        val entry = 6 + i * 16
        val width = (bytes[entry].toInt() and 0xFF).let { if (it == 0) 256 else it }
        if (width > bestSize) { bestSize = width; best = entry }
    }
    val size = u32(best + 8).toInt()
    val offset = u32(best + 12).toInt()
    val payload = bytes.copyOfRange(offset, offset + size)
    require(payload.size > 8 && payload[1] == 'P'.code.toByte() && payload[2] == 'N'.code.toByte()) {
        "${ico.name}'s largest entry (${bestSize}px) is not PNG-encoded — MSIX asset generation " +
            "needs a PNG-bearing .ico; re-export the icon or add a DIB decoder here"
    }
    return ImageIO.read(payload.inputStream())
        ?: error("could not decode the ${bestSize}px image inside ${ico.name}")
}

fun scaleSquare(source: BufferedImage, size: Int): BufferedImage {
    val out = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val g = out.createGraphics()
    try {
        g.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BICUBIC,
        )
        g.setRenderingHint(
            RenderingHints.KEY_RENDERING,
            RenderingHints.VALUE_RENDER_QUALITY,
        )
        g.drawImage(source, 0, 0, size, size, null)
    } finally {
        g.dispose()
    }
    return out
}

fun wideTile(source: BufferedImage, width: Int, height: Int): BufferedImage {
    val out = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val g = out.createGraphics()
    try {
        g.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BICUBIC,
        )
        val glyph = (height * 0.72).toInt()
        g.drawImage(source, (width - glyph) / 2, (height - glyph) / 2, glyph, glyph, null)
    } finally {
        g.dispose()
    }
    return out
}

/**
 * The manifest, verbatim.
 *
 * Every choice in it is load-bearing:
 *  - `runFullTrust` is a RESTRICTED capability and is what a Win32 desktop app
 *    in the Store declares. It needs a written justification on Partner
 *    Center's Submission options page, reviewed by a human.
 *  - `EntryPoint="Windows.FullTrustApplication"` is what makes `Executable` a
 *    plain .exe rather than a UWP activation target.
 *  - `windows.startupTask` REPLACES the MSI's `HKCU\…\Run` entry. `Enabled`
 *    is the initial state; from then on the user owns it in Settings → Apps →
 *    Startup, and there is no in-app toggle in this edition precisely so that
 *    the app cannot claim a state Windows disagrees with.
 *  - `MinVersion` 10.0.17763.0 is Windows 10 1809, the oldest release that
 *    supports the `uap5` startup-task extension used here.
 *  - `Properties/DisplayName` must match the app name reserved in Partner
 *    Center; `uap:VisualElements/DisplayName` is free text and carries the
 *    Bengali name users actually look for.
 */
fun appxManifest(
    identityName: String,
    publisher: String,
    publisherDisplayName: String,
    version: String,
): String = """<?xml version="1.0" encoding="utf-8"?>
<Package
  xmlns="http://schemas.microsoft.com/appx/manifest/foundation/windows10"
  xmlns:uap="http://schemas.microsoft.com/appx/manifest/uap/windows10"
  xmlns:uap5="http://schemas.microsoft.com/appx/manifest/uap/windows10/5"
  xmlns:rescap="http://schemas.microsoft.com/appx/manifest/foundation/windows10/restrictedcapabilities"
  IgnorableNamespaces="uap uap5 rescap">

  <Identity
    Name="$identityName"
    Publisher="$publisher"
    Version="$version"
    ProcessorArchitecture="x64" />

  <Properties>
    <DisplayName>$publisherDisplayName</DisplayName>
    <PublisherDisplayName>$publisherDisplayName</PublisherDisplayName>
    <Logo>Assets\StoreLogo.png</Logo>
  </Properties>

  <Dependencies>
    <TargetDeviceFamily Name="Windows.Desktop" MinVersion="10.0.17763.0" MaxVersionTested="10.0.26100.0" />
  </Dependencies>

  <Resources>
    <Resource Language="en-us" />
    <Resource Language="bn-bd" />
  </Resources>

  <Applications>
    <Application Id="BangluTyper" Executable="BangluTyper.exe" EntryPoint="Windows.FullTrustApplication">
      <uap:VisualElements
        DisplayName="বাংলু টাইপার"
        Description="Type Bangla anywhere on Windows"
        BackgroundColor="transparent"
        Square150x150Logo="Assets\Square150x150Logo.png"
        Square44x44Logo="Assets\Square44x44Logo.png">
        <uap:DefaultTile
          Wide310x150Logo="Assets\Wide310x150Logo.png"
          Square71x71Logo="Assets\Square71x71Logo.png"
          Square310x310Logo="Assets\Square310x310Logo.png" />
      </uap:VisualElements>
      <Extensions>
        <uap5:Extension Category="windows.startupTask" Executable="BangluTyper.exe" EntryPoint="Windows.FullTrustApplication">
          <uap5:StartupTask TaskId="BangluTyperStartup" Enabled="true" DisplayName="বাংলু টাইপার" />
        </uap5:Extension>
      </Extensions>
    </Application>
  </Applications>

  <Capabilities>
    <rescap:Capability Name="runFullTrust" />
  </Capabilities>
</Package>
"""

/**
 * Renders the control window on the developer's machine so its design can be
 * SEEN rather than guessed at. The app proper is Win32-only, but the window is
 * pure Compose with no JNA in its tree, so it renders anywhere.
 *
 *   ./gradlew :windows-ime:previewControlWindow
 *   ./gradlew :windows-ime:previewControlWindow -Dbanglu.preview.mode=update
 */
val previewControlWindow by tasks.registering(JavaExec::class) {
    group = "banglu"
    description = "Open the control window for design review (dev machine, no hook)"
    mainClass.set("com.banglu.winime.ui.DesignPreviewKt")
    classpath = sourceSets["test"].runtimeClasspath
    systemProperty("banglu.preview.mode", providers.systemProperty("banglu.preview.mode").getOrElse("bangla"))
}
