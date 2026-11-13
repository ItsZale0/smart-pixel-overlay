package com.zampediverse.smartpixel

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object UpdateUtils {
    private const val REPO_API = "https://api.github.com/repos/ItsZale0/smart-pixel-overlay/releases/latest"
    private const val PREF_LAST_CHECK = "update_last_check"
    private const val CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L // 12h

    data class ReleaseInfo(val tag: String, val versionName: String, val apkUrl: String?, val htmlUrl: String)

    fun shouldAutoCheck(context: Context): Boolean {
        val sp = Prefs.sp(context)
        val last = sp.getLong(PREF_LAST_CHECK, 0L)
        return System.currentTimeMillis() - last > CHECK_INTERVAL_MS
    }

    fun markChecked(context: Context) {
        Prefs.sp(context).edit().putLong(PREF_LAST_CHECK, System.currentTimeMillis()).apply()
    }

    fun fetchLatestRelease(): ReleaseInfo? {
        return try {
            val conn = (URL(REPO_API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "SmartPixel-Updater")
            }
            conn.inputStream.use { ins ->
                val text = BufferedReader(InputStreamReader(ins)).readText()
                val json = JSONObject(text)
                val tag = json.optString("tag_name", "")
                val html = json.optString("html_url", "https://github.com/ItsZale0/smart-pixel-overlay/releases")
                val ver = tag.removePrefix("v").trim()
                var apkUrl: String? = null
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val a = assets.getJSONObject(i)
                        val name = a.optString("name", "")
                        val url = a.optString("browser_download_url", "")
                        if (name.endsWith(".apk") && name.contains("release", ignoreCase = true)) {
                            apkUrl = url
                            break
                        }
                    }
                    if (apkUrl == null) {
                        for (i in 0 until assets.length()) {
                            val a = assets.getJSONObject(i)
                            val name = a.optString("name", "")
                            val url = a.optString("browser_download_url", "")
                            if (name.endsWith(".apk")) { apkUrl = url; break }
                        }
                    }
                }
                ReleaseInfo(tag = tag, versionName = ver, apkUrl = apkUrl, htmlUrl = html)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun isNewer(remote: String, current: String): Boolean {
        fun parse(v: String): List<Int> = v.removePrefix("v").trim().split('.').mapNotNull { it.toIntOrNull() }
        val r = parse(remote)
        val c = parse(current)
        val n = maxOf(r.size, c.size)
        for (i in 0 until n) {
            val rv = r.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (rv != cv) return rv > cv
        }
        return false
    }

    fun downloadAndInstall(context: Context, apkUrl: String, version: String) {
        try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val fileName = "SmartPixel-$version-release.apk"
            val req = DownloadManager.Request(Uri.parse(apkUrl))
                .setTitle(context.getString(R.string.update_downloading_title))
                .setDescription(fileName)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setMimeType("application/vnd.android.package-archive")
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "updates/$fileName")

            val downloadId = dm.enqueue(req)
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id != downloadId) return
                    try { ctx.unregisterReceiver(this) } catch (_: Exception) {}
                    val uri = dm.getUriForDownloadedFile(downloadId)
                    if (uri != null) {
                        installApk(ctx, uri)
                    } else {
                        // fallback: try direct file path
                        val f = File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "updates/$fileName")
                        if (f.exists()) installApkFile(ctx, f)
                        else Toast.makeText(ctx, ctx.getString(R.string.update_download_failed), Toast.LENGTH_LONG).show()
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
            }
            Toast.makeText(context, context.getString(R.string.update_download_started), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            // fallback: open releases page
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ItsZale0/smart-pixel-overlay/releases/latest")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {
                Toast.makeText(context, context.getString(R.string.update_failed_open), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun installApk(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try { context.startActivity(intent) } catch (_: Exception) {
            Toast.makeText(context, context.getString(R.string.update_install_failed), Toast.LENGTH_LONG).show()
        }
    }

    private fun installApkFile(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            installApk(context, uri)
        } catch (_: Exception) {
            Toast.makeText(context, context.getString(R.string.update_install_failed), Toast.LENGTH_LONG).show()
        }
    }
}
