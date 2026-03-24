package com.subtitlescreen

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var btnToggle: Button
    private var overlayActive = false

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            startOverlay()
        } else {
            Toast.makeText(this, R.string.overlay_permission_required, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnToggle = findViewById(R.id.btnToggle)
        overlayActive = OverlayService.isRunning
        updateButton()

        btnToggle.setOnClickListener {
            if (overlayActive) {
                stopOverlay()
            } else {
                if (Settings.canDrawOverlays(this)) {
                    startOverlay()
                } else {
                    requestOverlayPermission()
                }
            }
        }

        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        overlayActive = OverlayService.isRunning
        updateButton()
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlayPermissionLauncher.launch(intent)
    }

    private fun startOverlay() {
        startForegroundService(Intent(this, OverlayService::class.java))
        overlayActive = true
        updateButton()
    }

    private fun stopOverlay() {
        stopService(Intent(this, OverlayService::class.java))
        overlayActive = false
        updateButton()
    }

    private fun updateButton() {
        btnToggle.setText(if (overlayActive) R.string.hide_overlay else R.string.show_overlay)
    }
}
