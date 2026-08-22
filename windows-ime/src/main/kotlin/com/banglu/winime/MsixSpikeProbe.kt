package com.banglu.winime

import com.banglu.winime.hook.SendInputInjector
import java.io.File

/**
 * ⚠ THROWAWAY SPIKE CODE — delete together with
 * `.github/workflows/windows-ime-msix-spike.yml`.
 *
 * It answers one question and nothing else: does বাংলু টাইপার still work when
 * Windows runs it as an MSIX-packaged (containerised) app, which is what the
 * Microsoft Store path would require. Every line here is a measurement, not a
 * feature.
 *
 * It is INERT unless the environment variable [ENV_FLAG] is exactly "1", so a
 * shipped MSI behaves precisely as it did before this file existed. Nothing in
 * the hook, the composer, the controller, the engine or the storage path was
 * changed for the spike; this probe only *observes* those existing components.
 *
 * It writes its findings to stdout AND to two files, because a packaged app
 * launched through `shell:AppsFolder` has no console to inherit — and because
 * WHERE those two files turn out to live is itself finding C (does MSIX
 * filesystem virtualisation redirect `%USERPROFILE%\.banglu` away from the
 * directory the desktop editor shares?).
 */
internal object MsixSpikeProbe {

    const val ENV_FLAG = "BANGLU_MSIX_SPIKE"
    const val PROPERTY_FLAG = "banglu.msix.spike"

    /**
     * Two flags because a packaged app cannot be given an environment: Windows
     * activates it through the AppX service, not from the shell that asked for
     * it, so the env var reaches an unpackaged dev run and nothing else. The
     * system property is set by editing the app image's own `.cfg` before
     * packaging, which is the only channel that survives MSIX activation.
     */
    fun enabled(): Boolean =
        System.getenv(ENV_FLAG) == "1" || System.getProperty(PROPERTY_FLAG) == "1"

    /** Called once, from the hook-start path, on a background thread. */
    fun run(hookInstalled: Boolean) {
        val lines = mutableListOf<String>()
        fun say(text: String) {
            lines += text
            println("MSIX-SPIKE $text")
            System.out.flush()
        }

        say("probe-start")

        // ── identity / launch context ────────────────────────────────────────
        // A process launched from an installed MSIX runs out of
        // C:\Program Files\WindowsApps\<PackageFullName>\ — the clearest
        // in-process signal of package identity available without adding a
        // Win32 call outside hook/ (the JNA isolation law).
        val exe = runCatching { ProcessHandle.current().info().command().orElse("(unknown)") }
            .getOrElse { "(unavailable: $it)" }
        say("exePath=$exe")
        say("packagedByPath=${exe.contains("WindowsApps", ignoreCase = true)}")
        say("userHome=${System.getProperty("user.home")}")
        say("env.USERPROFILE=${System.getenv("USERPROFILE")}")
        say("env.LOCALAPPDATA=${System.getenv("LOCALAPPDATA")}")
        say("java.io.tmpdir=${System.getProperty("java.io.tmpdir")}")
        say("resources.dir=${System.getProperty("compose.application.resources.dir")}")

        // ── A: the keyboard hook ─────────────────────────────────────────────
        say("hookInstalled=$hookInstalled")

        // ── D: JNA native extraction ─────────────────────────────────────────
        // Reaching this line at all means JNA's native layer loaded (the hook
        // path dereferences Kernel32/User32 before we get here), but name the
        // extracted artefact so a failure is legible rather than inferred.
        say("jna.tmpdir=${System.getProperty("jna.tmpdir")}")
        say("jna.boot.library.path=${System.getProperty("jna.boot.library.path")}")
        for (dir in listOfNotNull(
            System.getProperty("java.io.tmpdir"),
            System.getProperty("jna.tmpdir"),
            System.getenv("TEMP"),
        ).distinct()) {
            val found = runCatching {
                File(dir).walkTopDown().maxDepth(2)
                    .filter { it.isFile && it.name.startsWith("jna", ignoreCase = true) }
                    .map { "${it.absolutePath} (${it.length()}b)" }
                    .take(5).toList()
            }.getOrElse { listOf("(unreadable: $it)") }
            say("jnaFilesIn[$dir]=$found")
        }

        // ── B: SendInput ─────────────────────────────────────────────────────
        // The production injector, called unmodified. It throws with the exact
        // inserted-event count and GetLastError when the call is refused, so a
        // clean return is real evidence that SendInput was accepted by the OS.
        // What it CANNOT prove headlessly is that the characters arrived in
        // another application's edit control — there is no focused editor here.
        val sendInput = runCatching { SendInputInjector().injectText("\u0995") }
        say(
            "sendInput=" + sendInput.fold(
                onSuccess = { "ACCEPTED (all events inserted, no UIPI error)" },
                onFailure = { "REFUSED: $it" },
            )
        )

        // ── C: where do %USERPROFILE%\.banglu writes land? ────────────────────
        // Uses the SHIPPING storage class with its SHIPPING default directory,
        // so the path measured is the product's real one. The pair is a
        // plausible phonetic mapping, per the anti-poisoning law.
        val homeBanglu = File(System.getProperty("user.home"), ".banglu")
        val learned = File(homeBanglu, "learned.json")
        val storage = runCatching {
            kotlinx.coroutines.runBlocking {
                WinStorage().saveLearnedWord("jbo", "\u09AF\u09BE\u09AC\u09CB", 1)
            }
        }
        say("storageWrite=" + storage.fold({ "ok" }, { "FAILED: $it" }))
        say("learnedPath=${learned.absolutePath}")
        say("learnedCanonical=${runCatching { learned.canonicalPath }.getOrElse { "$it" }}")
        say("learnedExistsFromInside=${learned.exists()} size=${runCatching { learned.length() }.getOrElse { -1L }}")
        say("bangluDirListing=${runCatching { homeBanglu.list()?.toList() }.getOrElse { listOf("$it") }}")

        say("probe-end")

        // Two drops, deliberately: the outside-the-container hunt in the
        // workflow looks for BOTH, and which one it finds (and at which real
        // path) is the redirection answer.
        val payload = lines.joinToString(System.lineSeparator())
        for (target in listOf(
            File(homeBanglu, "msix-spike.log"),
            File(System.getProperty("java.io.tmpdir"), "banglu-msix-spike.log"),
        )) {
            runCatching {
                target.parentFile?.mkdirs()
                target.writeText(payload)
                println("MSIX-SPIKE wrote ${target.absolutePath}")
            }.onFailure { println("MSIX-SPIKE could not write ${target.absolutePath}: $it") }
        }
        System.out.flush()
    }
}
