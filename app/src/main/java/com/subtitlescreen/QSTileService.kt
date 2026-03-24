package com.subtitlescreen

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class QSTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()

        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(
                    PendingIntent.getActivity(
                        this, 0, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
            return
        }

        val wasRunning = OverlayService.isRunning
        if (wasRunning) {
            stopService(Intent(this, OverlayService::class.java))
        } else {
            startForegroundService(Intent(this, OverlayService::class.java))
        }
        setTileState(!wasRunning)
    }

    private fun updateTile() {
        setTileState(OverlayService.isRunning)
    }

    private fun setTileState(active: Boolean) {
        qsTile?.let { tile ->
            tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.label = getString(R.string.tile_label)
            tile.updateTile()
        }
    }
}
