package com.green.android

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.green.android.data.AppDatabase
import libgreen.Libgreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.ServerSocket
import java.util.UUID

class VpnTileService : TileService() {
    private var scope: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope = s
        s.launch {
            VpnState.status.collect { status ->
                val tile = qsTile ?: return@collect
                tile.state = when (status) {
                    VpnStatus.CONNECTED, VpnStatus.CONNECTING -> Tile.STATE_ACTIVE
                    VpnStatus.DISCONNECTED -> Tile.STATE_INACTIVE
                }
                tile.updateTile()
            }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        scope?.cancel()
        scope = null
    }

    override fun onClick() {
        super.onClick()
        when (VpnState.status.value) {
            VpnStatus.CONNECTED, VpnStatus.CONNECTING -> stopVpn()
            VpnStatus.DISCONNECTED -> startVpn()
        }
    }

    private fun stopVpn() {
        startService(Intent(this, GreenVpnService::class.java).apply {
            action = GreenVpnService.ACTION_STOP
        })
    }

    private fun startVpn() {
        val prepare = VpnService.prepare(this)
        if (prepare != null) {
            val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
            return
        }
        scope?.launch(Dispatchers.IO) {
            val prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
            val selectedId = prefs.getInt(Prefs.LAST_SELECTED, -1).takeIf { it != -1 } ?: return@launch
            val config = AppDatabase.get(this@VpnTileService).configDao().getById(selectedId) ?: return@launch
            val allowedApps = prefs.getStringSet(Prefs.ALLOWED_APPS, emptySet()) ?: emptySet()
            val notify = prefs.getBoolean(Prefs.NOTIFY, true)
            runCatching {
                val rawJson = when {
                    config.vlessLink != null -> Libgreen.vlessKeyToXrayJson(config.vlessLink)
                    config.configJson != null -> config.configJson
                    else -> return@runCatching
                }
                val dns = when {
                    config.vlessLink != null -> Libgreen.vlessKeyDnsServer(config.vlessLink)
                    config.configJson != null -> dnsServerFromJson(config.configJson)
                    else -> "1.1.1.1"
                }
                val port = ServerSocket(0).use { it.localPort }
                val user = UUID.randomUUID().toString().replace("-", "").take(16)
                val pass = UUID.randomUUID().toString().replace("-", "").take(16)
                val configJson = patchXrayConfig(rawJson, port, user, pass)
                withContext(Dispatchers.Main) {
                    startForegroundService(
                        Intent(this@VpnTileService, GreenVpnService::class.java).apply {
                            action = GreenVpnService.ACTION_START
                            putExtra(GreenVpnService.EXTRA_CONFIG_JSON, configJson)
                            putExtra(GreenVpnService.EXTRA_DNS_SERVER, dns)
                            putExtra(GreenVpnService.EXTRA_SOCKS_PORT, port)
                            putExtra(GreenVpnService.EXTRA_SOCKS_USER, user)
                            putExtra(GreenVpnService.EXTRA_SOCKS_PASS, pass)
                            putExtra(GreenVpnService.EXTRA_NOTIFY, notify)
                            putStringArrayListExtra(GreenVpnService.EXTRA_ALLOWED_APPS, ArrayList(allowedApps))
                        }
                    )
                }
            }
        }
    }
}
