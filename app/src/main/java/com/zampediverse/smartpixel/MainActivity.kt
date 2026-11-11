package com.zampediverse.smartpixel

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.zampediverse.smartpixel.databinding.ActivityMainBinding
import rikka.shizuku.Shizuku
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding
    private val shiftValues = intArrayOf(0, 15, 30, 60, 120, 300, 600)
    @Volatile private var cachedHasSu: Boolean? = null
    @Volatile private var suCheckInFlight = false
    @Volatile private var cachedShizuku: String? = null // "available+granted", "available", "no"
    @Volatile private var pendingShizukuGrant = false

    private val shizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            runOnUiThread {
                cachedShizuku = if (grantResult == PackageManager.PERMISSION_GRANTED) "available+granted" else "available"
                if (grantResult == PackageManager.PERMISSION_GRANTED && pendingShizukuGrant) {
                    pendingShizukuGrant = false
                    runShizukuGrant { ok ->
                        if (ok) maybeApply() else Toast.makeText(this, R.string.toast_shizuku_cmd_failed, Toast.LENGTH_LONG).show()
                    }
                } else {
                    pendingShizukuGrant = false
                    if (grantResult != PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(this, R.string.toast_shizuku_denied, Toast.LENGTH_SHORT).show()
                    }
                }
                updateStatus()
            }
        }

    private val shizukuBinderReceivedListener = Shizuku.OnBinderReceivedListener {
        runOnUiThread { cachedShizuku = null; updateStatus() }
    }
    private val shizukuBinderDeadListener = Shizuku.OnBinderDeadListener {
        runOnUiThread { cachedShizuku = "no"; updateStatus() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        try { Shizuku.addBinderReceivedListenerSticky(shizukuBinderReceivedListener) } catch (_: Throwable) {}
        try { Shizuku.addBinderDeadListener(shizukuBinderDeadListener) } catch (_: Throwable) {}

        setupSpinners()
        loadState()
        bindListeners()

        if (intent?.getBooleanExtra("request_overlay", false) == true) {
            ensureOverlayPermission { maybeApply() }
        }
    }

    override fun onResume() {
        super.onResume()
        loadState()
        cachedShizuku = null
        updateStatus()
    }

    override fun onDestroy() {
        try { Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener) } catch (_: Throwable) {}
        try { Shizuku.removeBinderReceivedListener(shizukuBinderReceivedListener) } catch (_: Throwable) {}
        try { Shizuku.removeBinderDeadListener(shizukuBinderDeadListener) } catch (_: Throwable) {}
        super.onDestroy()
    }

    private fun setupSpinners() {
        val patterns = listOf(
            getString(R.string.pattern_0),
            getString(R.string.pattern_1),
            getString(R.string.pattern_2),
            getString(R.string.pattern_3),
            getString(R.string.pattern_4),
            getString(R.string.pattern_5)
        )
        b.spinnerPattern.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, patterns)

        val shifts = listOf(
            getString(R.string.shift_off),
            getString(R.string.shift_15s),
            getString(R.string.shift_30s),
            getString(R.string.shift_1m),
            getString(R.string.shift_2m),
            getString(R.string.shift_5m),
            getString(R.string.shift_10m)
        )
        b.spinnerShift.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, shifts)
    }

    private fun loadState() {
        b.switchEnable.isChecked = Prefs.isEnabled(this)
        b.switchBatterySaver.isChecked = Prefs.batterySaver(this)
        b.switchNativeMode.isChecked = Prefs.nativeMode(this)
        val native = NativeSmartPixel.detect(this)
        b.switchNativeMode.isEnabled = native != null
        val intensity = Prefs.intensity(this)
        b.seekIntensity.progress = intensity
        b.textIntensity.text = "$intensity%"
        val blackLevel = Prefs.blackLevel(this)
        b.seekBlackLevel.progress = blackLevel
        b.textBlackLevel.text = "$blackLevel%"
        b.spinnerPattern.setSelection(Prefs.pattern(this).coerceIn(0, 5))
        val shiftSec = Prefs.shiftSeconds(this)
        val shiftIdx = shiftValues.indexOf(shiftSec).let { if (it >= 0) it else 0 }
        b.spinnerShift.setSelection(shiftIdx)
    }

    private fun bindListeners() {
        b.switchEnable.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                if (Prefs.nativeMode(this) && NativeSmartPixel.detect(this) != null) {
                    Prefs.setEnabled(this, true)
                    maybeApply()
                } else {
                    ensureOverlayPermission {
                        Prefs.setEnabled(this, true)
                        maybeApply()
                    }
                    if (!Prefs.nativeMode(this) && !Settings.canDrawOverlays(this)) b.switchEnable.isChecked = false
                }
            } else {
                Prefs.setEnabled(this, false)
                maybeApply()
            }
        }

        b.switchBatterySaver.setOnCheckedChangeListener { _, checked ->
            Prefs.setBatterySaver(this, checked)
            maybeApply()
        }

        b.switchNativeMode.setOnCheckedChangeListener { _, checked ->
            Prefs.setNativeMode(this, checked)
            maybeApply()
        }

        b.seekIntensity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val p = progress.coerceIn(1, 100)
                b.textIntensity.text = "$p%"
                if (fromUser) {
                    Prefs.setIntensity(this@MainActivity, p)
                    SmartPixelService.update(this@MainActivity)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        b.seekBlackLevel.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val p = progress.coerceIn(0, 100)
                b.textBlackLevel.text = "$p%"
                if (fromUser) {
                    Prefs.setBlackLevel(this@MainActivity, p)
                    SmartPixelService.update(this@MainActivity)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        b.spinnerPattern.onItemSelectedListener = simpleSelect { pos ->
            Prefs.setPattern(this, pos)
            SmartPixelService.update(this)
        }
        b.spinnerShift.onItemSelectedListener = simpleSelect { pos ->
            Prefs.setShiftSeconds(this, shiftValues.getOrElse(pos) { 0 })
            SmartPixelService.update(this)
        }

        b.btnRootGrant.setOnClickListener {
            b.btnRootGrant.isEnabled = false
            thread {
                val ok = RootUtils.grantOverlayPermission(packageName)
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        b.btnRootGrant.isEnabled = true
                        if (ok) {
                            cachedHasSu = true
                            Toast.makeText(this, R.string.toast_root_granted, Toast.LENGTH_SHORT).show()
                            maybeApply()
                        } else {
                            Toast.makeText(this, R.string.toast_root_failed, Toast.LENGTH_LONG).show()
                        }
                        updateStatus()
                    }
                }
            }
        }

        b.btnShizukuGrant.setOnClickListener {
            if (!ShizukuUtils.isAvailable()) {
                Toast.makeText(this, R.string.toast_shizuku_unavailable, Toast.LENGTH_LONG).show()
                updateStatus()
                return@setOnClickListener
            }
            if (ShizukuUtils.hasPermission()) {
                runShizukuGrant { ok ->
                    if (ok) maybeApply() else Toast.makeText(this, R.string.toast_shizuku_cmd_failed, Toast.LENGTH_LONG).show()
                }
            } else {
                pendingShizukuGrant = true
                ShizukuUtils.requestPermission()
                Toast.makeText(this, R.string.toast_shizuku_requested, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun simpleSelect(onSelect: (Int) -> Unit) = object : AdapterView.OnItemSelectedListener {
        private var init = false
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            if (!init) { init = true; return } // skip initial callback
            onSelect(position)
        }
        override fun onNothingSelected(parent: AdapterView<*>?) {}
    }

    private fun ensureOverlayPermission(onGranted: () -> Unit) {
        if (Settings.canDrawOverlays(this)) { onGranted(); return }
        AlertDialog.Builder(this)
            .setTitle(R.string.overlay_permission_title)
            .setMessage(R.string.overlay_permission_msg)
            .setPositiveButton(R.string.grant) { _, _ ->
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivity(intent)
            }
            .setNeutralButton(R.string.root_grant) { _, _ ->
                thread {
                    val ok = RootUtils.grantOverlayPermission(packageName)
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) {
                            if (ok) {
                                cachedHasSu = true
                                Toast.makeText(this, R.string.toast_permission_granted, Toast.LENGTH_SHORT).show()
                                onGranted()
                            } else {
                                Toast.makeText(this, R.string.toast_root_unavailable, Toast.LENGTH_LONG).show()
                            }
                            updateStatus()
                        }
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun effectiveEnabled(): Boolean {
        if (Prefs.isEnabled(this)) return true
        if (Prefs.batterySaver(this)) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (pm.isPowerSaveMode) return true
        }
        return false
    }

    private fun maybeApply() {
        val nativeKeys = NativeSmartPixel.detect(this)
        val useNative = Prefs.nativeMode(this) && nativeKeys != null
        val enabled = effectiveEnabled()

        if (useNative && nativeKeys != null) {
            SmartPixelService.stop(this)
            thread {
                val ok = NativeSmartPixel.setEnabled(this, nativeKeys, enabled, useRoot = true, useShizuku = true)
                if (ok) {
                    NativeSmartPixel.setPowerSave(this, nativeKeys, Prefs.batterySaver(this), true, true)
                    NativeSmartPixel.setPattern(this, nativeKeys, Prefs.pattern(this), true, true)
                    NativeSmartPixel.setShift(this, nativeKeys, Prefs.shiftSeconds(this), true, true)
                }
                runOnUiThread { updateStatus() }
            }
        } else {
            if (enabled && Settings.canDrawOverlays(this)) {
                SmartPixelService.start(this)
            } else if (!enabled) {
                SmartPixelService.stop(this)
            }
        }
        updateStatus()
    }

    private fun getShizukuStatus(): String {
        val c = cachedShizuku
        if (c != null) return c
        val s = when {
            !ShizukuUtils.isAvailable() -> getString(R.string.status_no_short)
            ShizukuUtils.hasPermission() -> getString(R.string.status_ok)
            else -> getString(R.string.status_permission_required)
        }
        cachedShizuku = s
        return s
    }

    private fun runShizukuGrant(onDone: (Boolean) -> Unit) {
        b.btnShizukuGrant.isEnabled = false
        thread {
            val ok = ShizukuUtils.grantOverlayPermission(packageName)
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    b.btnShizukuGrant.isEnabled = true
                    if (ok) {
                        cachedShizuku = getString(R.string.status_ok)
                        Toast.makeText(this, R.string.toast_shizuku_granted, Toast.LENGTH_SHORT).show()
                    }
                    updateStatus()
                    onDone(ok)
                }
            }
        }
    }

    private fun updateStatus() {
        val overlay = Settings.canDrawOverlays(this)
        val eff = effectiveEnabled()
        val rootCached = cachedHasSu
        val shizuku = getShizukuStatus()
        val native = NativeSmartPixel.detect(this)
        val nativeStr = if (native != null) "${getString(R.string.status_yes)} (${native.enableKey})" else getString(R.string.status_no_short)
        b.textStatus.text = "${getString(R.string.status_overlay)}: ${if (overlay) getString(R.string.status_ok) else getString(R.string.status_no)} | " +
            "${getString(R.string.status_root)}: ${when (rootCached) { true -> getString(R.string.status_detected); false -> getString(R.string.status_no_short); null -> getString(R.string.status_checking) }} | " +
            "${getString(R.string.status_shizuku)}: $shizuku | " +
            "${getString(R.string.status_native_rom)}: $nativeStr | " +
            "${getString(R.string.status_active)}: ${if (eff) getString(R.string.status_yes) else getString(R.string.status_no_short)}"
        if (rootCached == null && !suCheckInFlight) {
            suCheckInFlight = true
            thread {
                val root = RootUtils.hasSu()
                runOnUiThread {
                    suCheckInFlight = false
                    if (!isFinishing && !isDestroyed) {
                        cachedHasSu = root
                        val ov = Settings.canDrawOverlays(this)
                        val e = effectiveEnabled()
                        val sh = getShizukuStatus()
                        val n = NativeSmartPixel.detect(this)
                        val ns = if (n != null) "${getString(R.string.status_yes)} (${n.enableKey})" else getString(R.string.status_no_short)
                        b.textStatus.text = "${getString(R.string.status_overlay)}: ${if (ov) getString(R.string.status_ok) else getString(R.string.status_no)} | " +
                            "${getString(R.string.status_root)}: ${if (root) getString(R.string.status_detected) else getString(R.string.status_no_short)} | " +
                            "${getString(R.string.status_shizuku)}: $sh | " +
                            "${getString(R.string.status_native_rom)}: $ns | " +
                            "${getString(R.string.status_active)}: ${if (e) getString(R.string.status_yes) else getString(R.string.status_no_short)}"
                    }
                }
            }
        }
    }
}
