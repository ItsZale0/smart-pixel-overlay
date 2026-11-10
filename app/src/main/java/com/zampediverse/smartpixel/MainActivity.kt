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
                        if (ok) maybeApply() else Toast.makeText(this, "Comando Shizuku fallito o permesso non concesso", Toast.LENGTH_LONG).show()
                    }
                } else {
                    pendingShizukuGrant = false
                    if (grantResult != PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(this, "Permesso Shizuku negato", Toast.LENGTH_SHORT).show()
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
        val intensity = Prefs.intensity(this)
        b.seekIntensity.progress = intensity
        b.textIntensity.text = "$intensity%"
        b.spinnerPattern.setSelection(Prefs.pattern(this).coerceIn(0, 5))
        val shiftSec = Prefs.shiftSeconds(this)
        val shiftIdx = shiftValues.indexOf(shiftSec).let { if (it >= 0) it else 0 }
        b.spinnerShift.setSelection(shiftIdx)
    }

    private fun bindListeners() {
        b.switchEnable.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                ensureOverlayPermission {
                    Prefs.setEnabled(this, true)
                    maybeApply()
                }
                if (!Settings.canDrawOverlays(this)) b.switchEnable.isChecked = false
            } else {
                Prefs.setEnabled(this, false)
                maybeApply()
            }
        }

        b.switchBatterySaver.setOnCheckedChangeListener { _, checked ->
            Prefs.setBatterySaver(this, checked)
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
                            Toast.makeText(this, "Permesso overlay concesso (root)", Toast.LENGTH_SHORT).show()
                            maybeApply()
                        } else {
                            Toast.makeText(this, "Impossibile concedere con root. Verifica root/su o usa Shizuku/ADB.", Toast.LENGTH_LONG).show()
                        }
                        updateStatus()
                    }
                }
            }
        }

        b.btnShizukuGrant.setOnClickListener {
            if (!ShizukuUtils.isAvailable()) {
                Toast.makeText(this, "Shizuku non disponibile o non in esecuzione", Toast.LENGTH_LONG).show()
                updateStatus()
                return@setOnClickListener
            }
            if (ShizukuUtils.hasPermission()) {
                runShizukuGrant { ok ->
                    if (ok) maybeApply() else Toast.makeText(this, "Comando Shizuku fallito", Toast.LENGTH_LONG).show()
                }
            } else {
                pendingShizukuGrant = true
                ShizukuUtils.requestPermission()
                Toast.makeText(this, "Richiesta permesso Shizuku inviata...", Toast.LENGTH_SHORT).show()
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
                                Toast.makeText(this, "Permesso concesso", Toast.LENGTH_SHORT).show()
                                onGranted()
                            } else {
                                Toast.makeText(this, "Root non disponibile o comando fallito", Toast.LENGTH_LONG).show()
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
        if (effectiveEnabled() && Settings.canDrawOverlays(this)) {
            SmartPixelService.start(this)
        } else if (!effectiveEnabled()) {
            SmartPixelService.stop(this)
        }
        updateStatus()
    }

    private fun getShizukuStatus(): String {
        val c = cachedShizuku
        if (c != null) return c
        val s = when {
            !ShizukuUtils.isAvailable() -> "no"
            ShizukuUtils.hasPermission() -> "ok"
            else -> "permesso richiesto"
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
                        cachedShizuku = "ok"
                        Toast.makeText(this, "Permesso overlay concesso (Shizuku)", Toast.LENGTH_SHORT).show()
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
        b.textStatus.text = "Overlay: ${if (overlay) "OK" else "NO"} | Root: ${when (rootCached) { true -> "rilevato"; false -> "no"; null -> "..." }} | Shizuku: $shizuku | Attivo: ${if (eff) "sì" else "no"}"
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
                        b.textStatus.text = "Overlay: ${if (ov) "OK" else "NO"} | Root: ${if (root) "rilevato" else "no"} | Shizuku: $sh | Attivo: ${if (e) "sì" else "no"}"
                    }
                }
            }
        }
    }
}
