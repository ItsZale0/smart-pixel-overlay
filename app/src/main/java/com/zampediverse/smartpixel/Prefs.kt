package com.zampediverse.smartpixel

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val NAME = "smart_pixel_prefs"
    const val KEY_ENABLED = "enabled"
    const val KEY_BATTERY_SAVER = "battery_saver"
    const val KEY_INTENSITY = "intensity" // 0..100 percent off (density)
    const val KEY_BLACK_LEVEL = "black_level" // 0..100 opacity of masked pixels (100 = total black)
    const val KEY_PATTERN = "pattern" // index
    const val KEY_SHIFT = "shift" // seconds, 0 = off
    const val KEY_NATIVE_MODE = "native_mode"

    fun sp(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context) = sp(context).getBoolean(KEY_ENABLED, false)
    fun setEnabled(context: Context, v: Boolean) = sp(context).edit().putBoolean(KEY_ENABLED, v).apply()

    fun batterySaver(context: Context) = sp(context).getBoolean(KEY_BATTERY_SAVER, false)
    fun setBatterySaver(context: Context, v: Boolean) = sp(context).edit().putBoolean(KEY_BATTERY_SAVER, v).apply()

    fun intensity(context: Context) = sp(context).getInt(KEY_INTENSITY, 25).coerceIn(0, 100)
    fun setIntensity(context: Context, v: Int) = sp(context).edit().putInt(KEY_INTENSITY, v.coerceIn(0, 100)).apply()

    fun blackLevel(context: Context) = sp(context).getInt(KEY_BLACK_LEVEL, 100).coerceIn(0, 100)
    fun setBlackLevel(context: Context, v: Int) = sp(context).edit().putInt(KEY_BLACK_LEVEL, v.coerceIn(0, 100)).apply()

    fun pattern(context: Context) = sp(context).getInt(KEY_PATTERN, 0)
    fun setPattern(context: Context, v: Int) = sp(context).edit().putInt(KEY_PATTERN, v).apply()

    fun shiftSeconds(context: Context) = sp(context).getInt(KEY_SHIFT, 0)
    fun setShiftSeconds(context: Context, v: Int) = sp(context).edit().putInt(KEY_SHIFT, v).apply()

    fun nativeMode(context: Context) = sp(context).getBoolean(KEY_NATIVE_MODE, false)
    fun setNativeMode(context: Context, v: Boolean) = sp(context).edit().putBoolean(KEY_NATIVE_MODE, v).apply()
}
