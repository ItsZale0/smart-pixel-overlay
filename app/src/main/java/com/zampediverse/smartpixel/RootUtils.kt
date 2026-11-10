package com.zampediverse.smartpixel

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

object RootUtils {
    fun runSu(command: String, timeoutSec: Long = 3): Boolean {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) {
                p.destroy()
                try { p.destroyForcibly() } catch (_: Exception) {}
                return false
            }
            p.exitValue() == 0
        } catch (_: Exception) {
            false
        }
    }

    fun grantOverlayPermission(packageName: String): Boolean {
        val cmds = listOf(
            "cmd appops set $packageName SYSTEM_ALERT_WINDOW allow",
            "appops set $packageName SYSTEM_ALERT_WINDOW allow",
            "pm grant $packageName android.permission.SYSTEM_ALERT_WINDOW"
        )
        return cmds.any { runSu(it, timeoutSec = 4) }
    }

    fun hasSu(): Boolean {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            if (!p.waitFor(2, TimeUnit.SECONDS)) {
                p.destroy()
                try { p.destroyForcibly() } catch (_: Exception) {}
                return false
            }
            val out = BufferedReader(InputStreamReader(p.inputStream)).readText()
            p.exitValue() == 0 && out.contains("uid=0")
        } catch (_: Exception) {
            false
        }
    }
}
