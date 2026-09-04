package com.aivoice.flow.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.aivoice.flow.R
import com.aivoice.flow.Settings
import com.aivoice.flow.databinding.ActivityMainBinding
import com.aivoice.flow.service.DictationService
import com.aivoice.flow.service.FlowAccessibilityService
import com.aivoice.flow.whisper.ModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Setup screen. Walks the three permissions the dictation loop needs, unpacks
 * the bundled model, and starts/stops the floating button.
 *
 * The accessibility toggle cannot be granted programmatically by design, so
 * the best any app can do is deep-link into Settings and explain what to tap.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: Settings

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refresh()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        settings = Settings(this)

        binding.micAction.setOnClickListener { requestMicPermissions() }
        binding.overlayAction.setOnClickListener { openOverlaySettings() }
        binding.accessibilityAction.setOnClickListener { openAccessibilitySettings() }
        binding.toggleService.setOnClickListener { toggleService() }

        setUpLanguagePicker()
        installModel()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    // -- setup steps -------------------------------------------------------

    private fun setUpLanguagePicker() {
        val languages = Settings.Language.entries
        binding.languageSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            languages.map { it.label },
        )
        binding.languageSpinner.setSelection(
            languages.indexOf(Settings.Language.fromCode(settings.language)),
        )
        binding.languageSpinner.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long,
                ) {
                    settings.language = languages[position].code
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            }
    }

    /**
     * Copies the weights out of the APK on first launch. It is ~181 MiB, so it
     * runs off the main thread with a progress bar rather than silently
     * blocking the first tap on the mic.
     */
    private fun installModel() {
        if (ModelStore.isInstalled(this)) {
            refresh()
            return
        }
        binding.modelProgress.visibility = View.VISIBLE
        binding.modelProgress.isIndeterminate = false
        binding.modelStatus.text = getString(R.string.model_unpacking)

        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                ModelStore.install(applicationContext) { fraction ->
                    val percent = (fraction * 100).toInt()
                    runOnUiThread { binding.modelProgress.progress = percent }
                }
            }
            binding.modelProgress.visibility = View.GONE
            if (!ok) {
                Toast.makeText(this@MainActivity, R.string.toast_model_failed, Toast.LENGTH_LONG)
                    .show()
            }
            refresh()
        }
    }

    private fun requestMicPermissions() {
        val wanted = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            wanted += Manifest.permission.POST_NOTIFICATIONS
        }
        requestPermissions.launch(wanted.toTypedArray())
    }

    private fun openOverlaySettings() {
        startActivity(
            Intent(
                AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            ),
        )
    }

    private fun openAccessibilitySettings() {
        Toast.makeText(this, R.string.accessibility_hint, Toast.LENGTH_LONG).show()
        startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun toggleService() {
        if (DictationService.isRunning.value) {
            DictationService.stop(this)
        } else {
            if (!hasMicPermission() || !hasOverlayPermission()) {
                Toast.makeText(this, R.string.toast_finish_setup, Toast.LENGTH_SHORT).show()
                return
            }
            DictationService.start(this)
        }
        binding.root.postDelayed(::refresh, 400)
    }

    // -- state -------------------------------------------------------------

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasOverlayPermission(): Boolean = AndroidSettings.canDrawOverlays(this)

    private fun refresh() {
        val mic = hasMicPermission()
        val overlay = hasOverlayPermission()
        val a11y = FlowAccessibilityService.isEnabled(this)
        val model = ModelStore.isInstalled(this)

        bindStep(mic, binding.micStatus, binding.micAction)
        bindStep(overlay, binding.overlayStatus, binding.overlayAction)
        bindStep(a11y, binding.accessibilityStatus, binding.accessibilityAction)

        binding.modelStatus.text = getString(
            if (model) R.string.model_ready else R.string.model_missing,
        )

        val running = DictationService.isRunning.value
        binding.toggleService.setText(
            if (running) R.string.action_stop_bubble else R.string.action_start_bubble,
        )
        binding.toggleService.isEnabled = mic && overlay && model
        binding.hint.setText(
            when {
                !model -> R.string.hint_model
                !mic || !overlay -> R.string.hint_permissions
                !a11y -> R.string.hint_accessibility
                running -> R.string.hint_running
                else -> R.string.hint_ready
            },
        )
    }

    private fun bindStep(granted: Boolean, status: android.widget.TextView, action: View) {
        status.setText(if (granted) R.string.status_granted else R.string.status_needed)
        status.setTextColor(
            ContextCompat.getColor(
                this,
                if (granted) R.color.status_ok else R.color.status_pending,
            ),
        )
        action.isEnabled = !granted
    }
}
