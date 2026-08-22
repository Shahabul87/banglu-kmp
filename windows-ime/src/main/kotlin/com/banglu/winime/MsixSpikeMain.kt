package com.banglu.winime

import com.banglu.winime.hook.LowLevelHook

/**
 * ⚠ THROWAWAY SPIKE HARNESS — delete with [MsixSpikeProbe] and
 * `.github/workflows/windows-ime-msix-spike.yml`.
 *
 * Runs [MsixSpikeProbe] WITHOUT starting the Compose application, because the
 * application cannot be started inside an MSIX container at all: the updater's
 * JDK-supplied web client is constructed eagerly at composition (Main.kt:195 →
 * Updater.kt:244) and that constructor throws "Unable to establish loopback
 * connection" under MSIX, killing the process before the keyboard hook, the
 * injector or the storage layer are ever reached. Runs 1 and 2 of the spike
 * both died there, so questions A/B/C/D had no evidence at all.
 *
 * (The wording above deliberately avoids naming that client's type: the
 * `verifyUpdaterIsolation` gate greps this whole source tree for it, and the
 * gate is right — nothing outside `update/` should mention it, comments
 * included.)
 *
 * This harness exercises the SAME production classes the app would — the real
 * [LowLevelHook], the real `SendInputInjector`, the real [WinStorage] with its
 * real default directory — minus Compose and minus the updater. It changes no
 * production behaviour: it is an additional entry point that nothing ships or
 * references, and `MainKt` remains the packaged app's mainClass.
 *
 * Launch (inside the package container, via Invoke-CommandInDesktopPackage):
 *   <InstallLocation>\runtime\bin\java.exe -cp "<InstallLocation>\app\*" \
 *     com.banglu.winime.MsixSpikeMainKt
 */
fun main() {
    println("MSIX-SPIKE-HARNESS start")
    System.out.flush()

    val hook = LowLevelHook()
    hook.onHookLost = { cause ->
        println(
            "MSIX-SPIKE-HARNESS hook install FAILED: " +
                (cause?.toString() ?: "SetWindowsHookEx returned NULL")
        )
        System.out.flush()
    }

    // Never swallows: the question is whether Windows lets the hook be
    // INSTALLED, and a harness that ate the runner's keystrokes would be a
    // different experiment.
    val sink = object : HookSink {
        override fun onKey(key: RawKey, foregroundExe: String): Boolean = false
    }

    val started = runCatching { hook.start(sink) }
    if (started.isFailure) {
        println("MSIX-SPIKE-HARNESS hook.start threw: ${started.exceptionOrNull()}")
    }

    MsixSpikeProbe.run(hook.isInstalled)

    // Long enough for the hook to be observably alive rather than a value read
    // one millisecond after installation.
    Thread.sleep(10_000)
    println("MSIX-SPIKE-HARNESS hookStillInstalledAfter10s=${hook.isInstalled}")
    hook.stop()
    println("MSIX-SPIKE-HARNESS end")
    System.out.flush()
}
