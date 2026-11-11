package com.zampediverse.smartpixel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings

class PowerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != PowerManager.ACTION_POWER_SAVE_MODE_CHANGED) return
        if (!Prefs.batterySaver(context)) return

        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val effective = Prefs.isEnabled(context) || pm.isPowerSaveMode
        val nativeKeys = NativeSmartPixel.detect(context)

        if (Prefs.nativeMode(context) && nativeKeys != null) {
            Thread {
                NativeSmartPixel.setEnabled(context, nativeKeys, effective, useRoot = true, useShizuku = true)
                NativeSmartPixel.setPowerSave(context, nativeKeys, Prefs.batterySaver(context), true, true)
                NativeSmartPixel.setPattern(context, nativeKeys, Prefs.pattern(context), true, true)
                NativeSmartPixel.setShift(context, nativeKeys, Prefs.shiftSeconds(context), true, true)
            }.start()
            return
        }

        if (!Settings.canDrawOverlays(context)) return
        if (effective) {
            SmartPixelService.start(context)
        } else {
            SmartPixelService.stop(context)
        }
    }
}
