package com.mihomo.gateway.service

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.mihomo.gateway.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GatewayTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val running = AppContainer.gateway.state.value.running
            if (running) {
                AppContainer.gateway.stop()
                AppContainer.settings.update { it.copy(desiredRunning = false) }
            } else {
                AppContainer.settings.update { it.copy(desiredRunning = true) }
                AppContainer.gateway.start()
            }
            withContext(Dispatchers.Main) { updateTile() }
        }
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        tile.state = if (AppContainer.gateway.state.value.running) {
            Tile.STATE_ACTIVE
        } else {
            Tile.STATE_INACTIVE
        }
        tile.label = "Mihomo 网关"
        tile.icon = Icon.createWithResource(this, android.R.drawable.stat_sys_upload_done)
        tile.updateTile()
    }
}
