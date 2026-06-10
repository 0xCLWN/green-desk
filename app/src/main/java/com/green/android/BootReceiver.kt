package com.green.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.green.android.data.AppDatabase
import go.Seq
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import libgreen.Libgreen
import java.net.ServerSocket
import java.util.UUID

class BootReceiver : BroadcastReceiver() {

    @OptIn(DelicateCoroutinesApi::class)
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(Prefs.AUTO_CONNECT, false)) return
        val selectedId = prefs.getInt(Prefs.LAST_SELECTED, -1).takeIf { it != -1 } ?: return

        // VPN permission must already be granted — if not, we can't show a dialog at boot
        if (VpnService.prepare(context) != null) return

        val pending = goAsync()
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val config = AppDatabase.get(context).configDao().getById(selectedId) ?: return@launch

                Seq.setContext(context.applicationContext)
                Libgreen.setAssetPath(context.filesDir.absolutePath)

                val rawJson = when {
                    config.vlessLink != null -> Libgreen.vlessKeyToXrayJson(config.vlessLink)
                    config.configJson != null -> config.configJson
                    else -> return@launch
                }
                val dnsServer = when {
                    config.vlessLink != null -> Libgreen.vlessKeyDnsServer(config.vlessLink)
                    config.configJson != null -> dnsServerFromJson(config.configJson)
                    else -> "1.1.1.1"
                }
                val port = ServerSocket(0).use { it.localPort }
                val user = UUID.randomUUID().toString().replace("-", "").take(16)
                val pass = UUID.randomUUID().toString().replace("-", "").take(16)
                val configJson = patchXrayConfig(rawJson, port, user, pass)
                val notify = prefs.getBoolean(Prefs.NOTIFY, true)
                val allowedApps = prefs.getStringSet(Prefs.ALLOWED_APPS, emptySet()) ?: emptySet()

                VpnState.status.value = VpnStatus.CONNECTING
                context.startForegroundService(
                    Intent(context, GreenVpnService::class.java).apply {
                        action = GreenVpnService.ACTION_START
                        putExtra(GreenVpnService.EXTRA_CONFIG_JSON, configJson)
                        putExtra(GreenVpnService.EXTRA_DNS_SERVER, dnsServer)
                        putExtra(GreenVpnService.EXTRA_SOCKS_PORT, port)
                        putExtra(GreenVpnService.EXTRA_SOCKS_USER, user)
                        putExtra(GreenVpnService.EXTRA_SOCKS_PASS, pass)
                        putExtra(GreenVpnService.EXTRA_NOTIFY, notify)
                        putStringArrayListExtra(GreenVpnService.EXTRA_ALLOWED_APPS, ArrayList(allowedApps))
                    }
                )
            } finally {
                pending.finish()
            }
        }
    }
}
