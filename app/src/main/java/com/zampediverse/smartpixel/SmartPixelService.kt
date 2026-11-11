package com.zampediverse.smartpixel

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat

class SmartPixelService : Service() {
    companion object {
        const val ACTION_UPDATE = "com.zampediverse.smartpixel.UPDATE"
        const val ACTION_STOP = "com.zampediverse.smartpixel.STOP"
        const val CHANNEL_ID = "smart_pixel"
        const val NOTIF_ID = 1

        fun start(context: Context) {
            val i = Intent(context, SmartPixelService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun update(context: Context) {
            val i = Intent(context, SmartPixelService::class.java).setAction(ACTION_UPDATE)
            try { context.startService(i) } catch (_: Exception) {}
        }

        fun stop(context: Context) {
            val i = Intent(context, SmartPixelService::class.java).setAction(ACTION_STOP)
            try { context.startService(i) } catch (_: Exception) {}
        }
    }

    private var wm: WindowManager? = null
    private var view: SmartPixelView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var shiftIndex = 0
    private var lastDrawTime = 0L
    private val minDrawIntervalMs = 50L // throttle redraws to max 20fps
    private val shiftRunnable = object : Runnable {
        override fun run() {
            val secs = Prefs.shiftSeconds(this@SmartPixelService)
            if (secs > 0 && view != null) {
                shiftIndex = (shiftIndex + 1) % 16
                view?.shiftIndex = shiftIndex
                scheduleRedraw()
                handler.postDelayed(this, secs * 1000L)
            }
        }
    }

    private fun scheduleRedraw() {
        val now = System.currentTimeMillis()
        if (now - lastDrawTime >= minDrawIntervalMs) {
            lastDrawTime = now
            view?.invalidate()
        } else {
            handler.postAtTime({ view?.invalidate() }, lastDrawTime + minDrawIntervalMs)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { removeOverlay(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return START_NOT_STICKY }
            ACTION_UPDATE -> applyState()
            else -> { startForeground(NOTIF_ID, buildNotification()); applyState() }
        }
        return START_STICKY
    }

    private fun effectiveEnabled(): Boolean {
        if (Prefs.isEnabled(this)) return true
        if (Prefs.batterySaver(this)) {
            val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (pm.isPowerSaveMode) return true
        }
        return false
    }

    private fun applyState() {
        if (!effectiveEnabled()) {
            removeOverlay(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf(); return
        }
        showOverlay()
        view?.intensity = Prefs.intensity(this)
        view?.blackLevel = Prefs.blackLevel(this)
        view?.pattern = Prefs.pattern(this)
        handler.removeCallbacks(shiftRunnable)
        val secs = Prefs.shiftSeconds(this)
        if (secs > 0) handler.postDelayed(shiftRunnable, secs * 1000L) else {
            // No shift interval - disable shift functionality
            shiftIndex = 0
            view?.shiftIndex = 0
        }
    }

    private fun showOverlay() {
        if (view != null) return
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        val v = SmartPixelView(this)
        v.intensity = Prefs.intensity(this)
        v.blackLevel = Prefs.blackLevel(this)
        v.pattern = Prefs.pattern(this)
        v.isDrawing = true // Enable drawing
        try {
            wm?.addView(v, params)
            view = v
        } catch (_: Exception) {}
    }

    private fun removeOverlay() {
        handler.removeCallbacks(shiftRunnable)
        try { if (view != null) wm?.removeView(view) } catch (_: Exception) {}
        view = null
        // Disable drawing to save battery
        view?.isDrawing = false
    }

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(CHANNEL_ID, getString(R.string.service_channel), NotificationManager.IMPORTANCE_LOW)
            ch.setShowBadge(false)
            nm.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.service_running))
            .setSmallIcon(R.drawable.ic_tile)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }
}
