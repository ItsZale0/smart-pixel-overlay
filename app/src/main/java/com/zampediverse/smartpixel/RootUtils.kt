package com.zampediverse.smartpixel

import java.io.BufferedReader
import java.io.InputStreamReader

object RootUtils {
    fun runSu(command: String): Boolean {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val code = p.waitFor()
            code == 0
        } catch (_: Exception) {
            false
        }
    }

    fun grantOverlayPermission(packageName: String): Boolean {
        // Works on many devices with root; on some needs different commands
        val cmds = listOf(
            "cmd appops set $packageName SYSTEM_ALERT_WINDOW allow",
            "appops set $packageName SYSTEM_ALERT_WINDOW allow",
            "pm grant $packageName android.permission.SYSTEM_ALERT_WINDOW"
        )
        return cmds.any { runSu(it) }
    }

    fun hasSu(): Boolean {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val out = BufferedReader(InputStreamReader(p.inputStream)).readText()
            p.waitFor() == 0 && out.contains("uid=0")
        } catch (_: Exception) {
            false
        }
    }
}
