package com.banglu.keyboard

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import java.io.File

object BangluProcessGuards {
    fun requireUiProcess(context: Context, componentName: String) {
        val appContext = context.applicationContext
        val packageName = appContext.packageName
        val processName = currentProcessName(appContext)
        check(processName.isNotBlank()) {
            "Unable to identify current process for $componentName"
        }
        require(processName != packageName) {
            "$componentName must not run inside the Banglu IME process"
        }
    }

    fun currentProcessName(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Application.getProcessName().orEmpty()
        }
        val pid = android.os.Process.myPid()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val processName = activityManager
            ?.runningAppProcesses
            ?.firstOrNull { it.pid == pid }
            ?.processName
            .orEmpty()
        if (processName.isNotBlank()) return processName

        return runCatching {
            File("/proc/self/cmdline")
                .readText()
                .substringBefore('\u0000')
                .trim()
        }.getOrDefault("")
    }
}
