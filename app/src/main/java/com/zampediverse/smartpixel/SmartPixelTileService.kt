package com.zampediverse.smartpixel

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class SmartPixelTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        val ctx = applicationContext
        if (!Prefs.isEnabled(ctx)) {
            if (!Settings.canDrawOverlays(ctx)) {
                val i = Intent(ctx, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("request_overlay", true)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startActivityAndCollapse(android.app.PendingIntent.getActivity(
                        ctx, 10, i,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                    ))
                } else {
                    @Suppress("DEPRECATION")
                    startActivityAndCollapse(i)
                }
                return
            }
            Prefs.setEnabled(ctx, true)
            SmartPixelService.start(ctx)
        } else {
            Prefs.setEnabled(ctx, false)
            SmartPixelService.stop(ctx)
        }
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val enabled = Prefs.isEnabled(applicationContext)
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (enabled) "${Prefs.intensity(applicationContext)}%" else null
        }
        tile.updateTile()
    }
}
