package com.zampediverse.smartpixel

import android.content.Context
import android.provider.Settings

object NativeSmartPixel {
    data class NativeKeys(
        val namespace: String, // "system" or "secure"
        val enableKey: String,
        val powerSaveKey: String? = null,
        val patternKey: String? = null,
        val shiftKey: String? = null,
        val levelKey: String? = null
    )

    private val candidates = listOf(
        NativeKeys("system", "smart_pixels_enable", "smart_pixels_on_power_save", "smart_pixels_pattern", "smart_pixels_shift_timeout"),
        NativeKeys("system", "smart_pixels_on", "smart_pixels_on_power_save", "smart_pixels_pattern", "smart_pixels_shift_timeout"),
        NativeKeys("secure", "smart_pixels_enable", "smart_pixels_on_power_save", "smart_pixels_pattern", "smart_pixels_shift_timeout"),
        NativeKeys("system", "smart_pixels", null, null, null),
        NativeKeys("system", "smart_pixel_enable", null, null, null)
    )

    fun detect(context: Context): NativeKeys? {
        for (c in candidates) {
            val v = getSetting(context, c.namespace, c.enableKey)
            if (v != null) return c
        }
        return null
    }

    private fun getSetting(context: Context, ns: String, key: String): String? {
        return try {
            when (ns) {
                "secure" -> Settings.Secure.getString(context.contentResolver, key)
                "global" -> Settings.Global.getString(context.contentResolver, key)
                else -> Settings.System.getString(context.contentResolver, key)
            }
        } catch (_: Throwable) { null }
    }

    fun isEnabled(context: Context, keys: NativeKeys): Boolean {
        val v = getSetting(context, keys.namespace, keys.enableKey) ?: return false
        return v == "1" || v.equals("true", true)
    }

    fun setEnabled(context: Context, keys: NativeKeys, enabled: Boolean, useRoot: Boolean, useShizuku: Boolean): Boolean {
        val v = if (enabled) "1" else "0"
        return putSetting(context, keys.namespace, keys.enableKey, v, useRoot, useShizuku)
    }

    fun setPowerSave(context: Context, keys: NativeKeys, enabled: Boolean, useRoot: Boolean, useShizuku: Boolean): Boolean {
        val k = keys.powerSaveKey ?: return true
        return putSetting(context, keys.namespace, k, if (enabled) "1" else "0", useRoot, useShizuku)
    }

    fun setPattern(context: Context, keys: NativeKeys, pattern: Int, useRoot: Boolean, useShizuku: Boolean): Boolean {
        val k = keys.patternKey ?: return true
        return putSetting(context, keys.namespace, k, pattern.toString(), useRoot, useShizuku)
    }

    fun setShift(context: Context, keys: NativeKeys, seconds: Int, useRoot: Boolean, useShizuku: Boolean): Boolean {
        val k = keys.shiftKey ?: return true
        return putSetting(context, keys.namespace, k, seconds.toString(), useRoot, useShizuku)
    }

    private fun putSetting(context: Context, ns: String, key: String, value: String, useRoot: Boolean, useShizuku: Boolean): Boolean {
        // Try direct write (may work in some cases) then privileged
        try {
            val ok = when (ns) {
                "secure" -> Settings.Secure.putString(context.contentResolver, key, value)
                "global" -> Settings.Global.putString(context.contentResolver, key, value)
                else -> Settings.System.putString(context.contentResolver, key, value)
            }
            if (ok) return true
        } catch (_: Throwable) {}
        val cmd = "settings put $ns $key $value"
        if (useShizuku && ShizukuUtils.runCommand(cmd)) return true
        if (useRoot && RootUtils.runSu(cmd)) return true
        return false
    }
}