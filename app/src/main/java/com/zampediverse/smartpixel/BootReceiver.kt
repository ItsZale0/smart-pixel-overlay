package com.zampediverse.smartpixel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED || intent?.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            if (Prefs.isEnabled(context) && Settings.canDrawOverlays(context)) {
                SmartPixelService.start(context)
            }
        }
    }
}
