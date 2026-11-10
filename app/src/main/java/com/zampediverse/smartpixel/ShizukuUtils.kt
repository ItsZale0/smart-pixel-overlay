package com.zampediverse.smartpixel

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

object ShizukuUtils {
    fun isAvailable(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    fun hasPermission(): Boolean = try {
        isAvailable() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    fun requestPermission(requestCode: Int = 1001) {
        try {
            if (isAvailable() && !hasPermission()) {
                if (!Shizuku.shouldShowRequestPermissionRationale()) {
                    Shizuku.requestPermission(requestCode)
                }
            }
        } catch (_: Throwable) {
        }
    }

    fun runCommand(command: String, timeoutSec: Long = 5): Boolean {
        return try {
            if (!hasPermission()) return false
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val remote = method.invoke(null, arrayOf("sh", "-c", command), null, null) ?: return false

            val waitFor = remote.javaClass.getMethod("waitFor")
            val destroy = try { remote.javaClass.getMethod("destroy") } catch (_: Throwable) { null }
            val exitValue = remote.javaClass.getMethod("exitValue")
            val getInputStream = try { remote.javaClass.getMethod("getInputStream") } catch (_: Throwable) { null }
            val getErrorStream = try { remote.javaClass.getMethod("getErrorStream") } catch (_: Throwable) { null }

            val deadline = System.currentTimeMillis() + timeoutSec * 1000
            var code: Int? = null
            while (System.currentTimeMillis() < deadline) {
                try {
                    code = (exitValue.invoke(remote) as Int)
                    break
                } catch (_: Throwable) {
                    Thread.sleep(50)
                }
            }
            if (code == null) {
                try { destroy?.invoke(remote) } catch (_: Throwable) {}
                return false
            }
            try {
                val ins = getInputStream?.invoke(remote) as? java.io.InputStream
                if (ins != null) BufferedReader(InputStreamReader(ins)).readText()
            } catch (_: Throwable) {}
            try {
                val ers = getErrorStream?.invoke(remote) as? java.io.InputStream
                if (ers != null) BufferedReader(InputStreamReader(ers)).readText()
            } catch (_: Throwable) {}
            try { remote.javaClass.getMethod("close").invoke(remote) } catch (_: Throwable) {}
            code == 0
        } catch (_: Throwable) {
            false
        }
    }

    fun grantOverlayPermission(packageName: String): Boolean {
        val cmds = listOf(
            "cmd appops set $packageName SYSTEM_ALERT_WINDOW allow",
            "appops set $packageName SYSTEM_ALERT_WINDOW allow",
            "pm grant $packageName android.permission.SYSTEM_ALERT_WINDOW"
        )
        return cmds.any { runCommand(it, timeoutSec = 6) }
    }
}