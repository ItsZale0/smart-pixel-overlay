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
        if (!Settings.canDrawOverlays(context)) return
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val effective = Prefs.isEnabled(context) || (Prefs.batterySaver(context) && pm.isPowerSaveMode)
        if (effective) {
            SmartPixelService.start(context)
        } else {
            SmartPixelService.stop(context)
        }
    }
}
