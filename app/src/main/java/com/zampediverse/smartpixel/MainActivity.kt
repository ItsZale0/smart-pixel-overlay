package com.zampediverse.smartpixel

import android.content.Intent
import android.net.Uri
import android.os.Build
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

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding
    private val shiftValues = intArrayOf(0, 15, 30, 60, 120, 300, 600)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

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
        updateStatus()
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
            val ok = RootUtils.grantOverlayPermission(packageName)
            if (ok) {
                Toast.makeText(this, "Permesso overlay concesso (root)", Toast.LENGTH_SHORT).show()
                maybeApply()
            } else {
                Toast.makeText(this, "Impossibile concedere con root. Verifica root/su o usa Shizuku/ADB.", Toast.LENGTH_LONG).show()
            }
            updateStatus()
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
                if (RootUtils.grantOverlayPermission(packageName)) {
                    Toast.makeText(this, "Permesso concesso", Toast.LENGTH_SHORT).show()
                    onGranted()
                } else {
                    Toast.makeText(this, "Root non disponibile o comando fallito", Toast.LENGTH_LONG).show()
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

    private fun updateStatus() {
        val overlay = Settings.canDrawOverlays(this)
        val root = RootUtils.hasSu()
        val eff = effectiveEnabled()
        b.textStatus.text = "Overlay: ${if (overlay) "OK" else "NO"} | Root: ${if (root) "rilevato" else "no"} | Attivo: ${if (eff) "sì" else "no"}"
    }
}
