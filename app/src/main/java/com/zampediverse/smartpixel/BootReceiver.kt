package com.zampediverse.smartpixel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&
            action != "com.htc.intent.action.QUICKBOOT_POWERON"
        ) return

        val effective = Prefs.isEnabled(context) || run {
            if (!Prefs.batterySaver(context)) false
            else {
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                pm.isPowerSaveMode
            }
        }
        if (!effective) return

        // Native ROM mode: try to reapply native setting (no overlay permission needed)
        val nativeKeys = NativeSmartPixel.detect(context)
        if (Prefs.nativeMode(context) && nativeKeys != null) {
            Thread {
                NativeSmartPixel.setEnabled(context, nativeKeys, true, useRoot = true, useShizuku = true)
                NativeSmartPixel.setPowerSave(context, nativeKeys, Prefs.batterySaver(context), true, true)
                NativeSmartPixel.setPattern(context, nativeKeys, Prefs.pattern(context), true, true)
                NativeSmartPixel.setShift(context, nativeKeys, Prefs.shiftSeconds(context), true, true)
            }.start()
            return
        }

        if (Settings.canDrawOverlays(context)) {
            SmartPixelService.start(context)
        }
    }
}
