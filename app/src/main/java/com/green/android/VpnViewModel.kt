package com.green.android

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.util.Base64
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.green.android.BuildConfig
import com.green.android.data.AppDatabase
import com.green.android.data.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import libgreen.Libgreen
import org.json.JSONObject
import java.io.File
import java.net.ServerSocket
import java.util.UUID

enum class VpnStatus { DISCONNECTED, CONNECTING, CONNECTED }

data class UpdateInfo(val tag: String, val sizeLabel: String, val url: String)

object VpnState {
    val status = MutableStateFlow(VpnStatus.DISCONNECTED)
}

data class GeoState(
    val enabled: Boolean,
    val geoipUrl: String,
    val geositeUrl: String,
    val updating: Boolean = false,
    val filesVersion: Int = 0,
)

class VpnViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).configDao()
    private val subDao = AppDatabase.get(app).subscriptionDao()
    private val prefs: SharedPreferences =
        app.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)

    private val _autoConnect = MutableStateFlow(prefs.getBoolean(Prefs.AUTO_CONNECT, false))
    val autoConnect: StateFlow<Boolean> = _autoConnect.asStateFlow()
    fun setAutoConnect(v: Boolean) { _autoConnect.value = v; prefs.edit { putBoolean(Prefs.AUTO_CONNECT, v) } }

    private val _notify = MutableStateFlow(prefs.getBoolean(Prefs.NOTIFY, true))
    val notify: StateFlow<Boolean> = _notify.asStateFlow()
    fun setNotify(v: Boolean) { _notify.value = v; prefs.edit { putBoolean(Prefs.NOTIFY, v) } }

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    private val _disguise = MutableStateFlow(prefs.getString(Prefs.DISGUISE, "default") ?: "default")
    val disguise: StateFlow<String> = _disguise.asStateFlow()
    fun setDisguise(v: String) {
        _disguise.value = v
        prefs.edit { putString(Prefs.DISGUISE, v) }
        applyDisguise(getApplication(), v)
    }

    val subscriptions: StateFlow<List<com.green.android.data.Subscription>> = subDao.getAllFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { seedDefaultConfigs() }
        viewModelScope.launch(Dispatchers.IO) { runCatching { refreshAllSubscriptions() } }
        viewModelScope.launch { runCatching { checkForUpdates() } }
    }

    val configs: StateFlow<List<Config>> = dao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedId = MutableStateFlow<Int?>(
        prefs.getInt(Prefs.LAST_SELECTED, -1).takeIf { it != -1 }
    )
    val selectedId: StateFlow<Int?> = _selectedId.asStateFlow()

    private val _addError = MutableStateFlow<String?>(null)
    val addError: StateFlow<String?> = _addError.asStateFlow()

    private val _subscriptionImporting = MutableStateFlow(false)
    val subscriptionImporting: StateFlow<Boolean> = _subscriptionImporting.asStateFlow()

    val status: StateFlow<VpnStatus> = VpnState.status.asStateFlow()

    private val _allowedApps = MutableStateFlow<Set<String>>(
        prefs.getStringSet(Prefs.ALLOWED_APPS, emptySet()) ?: emptySet()
    )
    val allowedApps: StateFlow<Set<String>> = _allowedApps.asStateFlow()

    fun setAllowedApps(apps: Set<String>) {
        _allowedApps.value = apps
        prefs.edit { putStringSet(Prefs.ALLOWED_APPS, apps) }
    }

    private val _geo = MutableStateFlow(
        GeoState(
            enabled = prefs.getBoolean(Prefs.GEO_ENABLED, false),
            geoipUrl = prefs.getString(Prefs.GEO_GEOIP_URL, GeoUpdater.DEFAULT_GEOIP_URL) ?: GeoUpdater.DEFAULT_GEOIP_URL,
            geositeUrl = prefs.getString(Prefs.GEO_GEOSITE_URL, GeoUpdater.DEFAULT_GEOSITE_URL) ?: GeoUpdater.DEFAULT_GEOSITE_URL,
        )
    )
    val geo: StateFlow<GeoState> = _geo.asStateFlow()

    fun setGeoEnabled(v: Boolean) { _geo.update { it.copy(enabled = v) }; prefs.edit { putBoolean(Prefs.GEO_ENABLED, v) } }
    fun setGeoipUrl(v: String) { _geo.update { it.copy(geoipUrl = v) }; prefs.edit { putString(Prefs.GEO_GEOIP_URL, v) } }
    fun setGeositeUrl(v: String) { _geo.update { it.copy(geositeUrl = v) }; prefs.edit { putString(Prefs.GEO_GEOSITE_URL, v) } }

    private var geoUpdateJob: Job? = null

    fun skipGeoUpdate() { geoUpdateJob?.cancel() }

    fun updateGeoNow() {
        geoUpdateJob?.cancel()
        geoUpdateJob = viewModelScope.launch {
            _geo.update { it.copy(updating = true) }
            val filesDir = getApplication<Application>().filesDir
            runCatching { GeoUpdater.download(filesDir, _geo.value.geoipUrl, _geo.value.geositeUrl) }
            _geo.update { it.copy(updating = false, filesVersion = it.filesVersion + 1) }
        }
    }

    fun importGeoFile(context: Context, uri: Uri, name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    File(getApplication<Application>().filesDir, name).outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            _geo.update { it.copy(filesVersion = it.filesVersion + 1) }
        }
    }

    private var connectJob: Job? = null
    private var pendingConfigJson: String? = null
    private var pendingDnsServer: String = "1.1.1.1"
    private var pendingSocksPort: Int = 10808
    private var pendingSocksUser: String = ""
    private var pendingSocksPass: String = ""

    private suspend fun seedDefaultConfigs() {
        if (prefs.getBoolean(Prefs.DEFAULTS_SEEDED, false)) return

        // Apply default_config.json from assets if present
        runCatching {
            getApplication<Application>().assets.open("default_config.json")
                .bufferedReader().readText()
        }.onSuccess { raw ->
            runCatching {
                val obj = org.json.JSONObject(raw)

                obj.optString("subscription_url").takeIf { it.isNotBlank() }?.let { url ->
                    val name = runCatching { java.net.URL(url).host }.getOrElse { url.take(30) }
                    subDao.insert(com.green.android.data.Subscription(url = url, name = name))
                }

                obj.optJSONArray("vless_keys")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val link = arr.getString(i).trim()
                        if (link.startsWith("vless://")) runCatching {
                            Libgreen.validateVlessKey(link)
                            dao.insert(Config(name = linkName(link), vlessLink = link))
                        }
                    }
                }

                prefs.edit {
                    if (obj.has("auto_connect")) putBoolean(Prefs.AUTO_CONNECT, obj.getBoolean("auto_connect"))
                    if (obj.has("notify")) putBoolean(Prefs.NOTIFY, obj.getBoolean("notify"))
                    if (obj.has("geo_enabled")) putBoolean(Prefs.GEO_ENABLED, obj.getBoolean("geo_enabled"))
                    obj.optString("geo_geoip_url").takeIf { it.isNotBlank() }?.let { putString(Prefs.GEO_GEOIP_URL, it) }
                    obj.optString("geo_geosite_url").takeIf { it.isNotBlank() }?.let { putString(Prefs.GEO_GEOSITE_URL, it) }
                }

                // Sync StateFlows with newly written prefs
                _autoConnect.value = prefs.getBoolean(Prefs.AUTO_CONNECT, false)
                _notify.value = prefs.getBoolean(Prefs.NOTIFY, true)
                _geo.update { it.copy(
                    enabled = prefs.getBoolean(Prefs.GEO_ENABLED, false),
                    geoipUrl = prefs.getString(Prefs.GEO_GEOIP_URL, GeoUpdater.DEFAULT_GEOIP_URL) ?: GeoUpdater.DEFAULT_GEOIP_URL,
                    geositeUrl = prefs.getString(Prefs.GEO_GEOSITE_URL, GeoUpdater.DEFAULT_GEOSITE_URL) ?: GeoUpdater.DEFAULT_GEOSITE_URL,
                ) }

                obj.optString("disguise").takeIf { it.isNotBlank() }?.let { disguise ->
                    prefs.edit { putString(Prefs.DISGUISE, disguise) }
                    _disguise.value = disguise
                    applyDisguise(getApplication(), disguise)
                }
            }
        }

        prefs.edit { putBoolean(Prefs.DEFAULTS_SEEDED, true) }
    }

    private suspend fun checkForUpdates() {
        val repo = "0xCLWN/green"

        val now = System.currentTimeMillis()
        val lastCheck = prefs.getLong(Prefs.UPDATE_LAST_CHECK, 0L)
        val cachedTag = prefs.getString(Prefs.UPDATE_CACHED_TAG, null)
        val cachedUrl = prefs.getString(Prefs.UPDATE_CACHED_URL, null)
        val cachedSize = prefs.getString(Prefs.UPDATE_CACHED_SIZE, null)

        val tag: String
        val url: String
        val sizeLabel: String

        if (now - lastCheck < 24L * 3600 * 1000 && cachedTag != null && cachedUrl != null && cachedSize != null) {
            tag = cachedTag; url = cachedUrl; sizeLabel = cachedSize
        } else {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val conn = java.net.URL("https://api.github.com/repos/$repo/releases/latest")
                        .openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 10_000
                    conn.readTimeout = 10_000
                    conn.setRequestProperty("Accept", "application/vnd.github+json")
                    val json = try { conn.inputStream.bufferedReader().readText() } finally { conn.disconnect() }
                    val obj = JSONObject(json)
                    val t = obj.getString("tag_name")
                    val u = obj.getString("html_url")
                    val assets = obj.optJSONArray("assets")
                    var bytes = 0L
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val a = assets.getJSONObject(i)
                            if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                                bytes = a.optLong("size", 0L); break
                            }
                        }
                    }
                    val sl = if (bytes > 0) "%.1f MB".format(bytes / (1024.0 * 1024.0)) else ""
                    Triple(t, u, sl)
                }.getOrNull()
            } ?: return
            tag = result.first; url = result.second; sizeLabel = result.third
            prefs.edit {
                putLong(Prefs.UPDATE_LAST_CHECK, now)
                putString(Prefs.UPDATE_CACHED_TAG, tag)
                putString(Prefs.UPDATE_CACHED_URL, url)
                putString(Prefs.UPDATE_CACHED_SIZE, sizeLabel)
            }
        }

        val dismissed = prefs.getString(Prefs.UPDATE_DISMISSED_TAG, null)
        val current = BuildConfig.VERSION_NAME
        if (tag.trimStart('v') != current.trimStart('v') && tag != dismissed) {
            _updateInfo.value = UpdateInfo(tag = tag, sizeLabel = sizeLabel, url = url)
        }
    }

    fun dismissUpdate() {
        val tag = _updateInfo.value?.tag ?: return
        prefs.edit { putString(Prefs.UPDATE_DISMISSED_TAG, tag) }
        _updateInfo.value = null
    }

    private fun applyDisguise(context: Context, disguise: String) {
        val pm = context.packageManager
        val pkg = context.packageName
        // Class names are rooted in the namespace (com.green.android), not the applicationId,
        // so the two diverge in debug builds that add an applicationIdSuffix.
        val ns = MainActivity::class.java.packageName
        val aliases = mapOf(
            "default"    to ComponentName(pkg, "$ns.MainActivityDefault"),
            "alfa_bank"  to ComponentName(pkg, "$ns.MainActivityAlfaBank"),
            "calculator" to ComponentName(pkg, "$ns.MainActivityCalculator"),
        )
        val target = aliases[disguise] ?: aliases["default"]!!
        for ((_, component) in aliases) {
            val state = if (component == target)
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
        }
    }

    fun select(config: Config) {
        _selectedId.value = config.id
        prefs.edit { putInt(Prefs.LAST_SELECTED, config.id) }
    }

    fun addConfig(input: String) {
        viewModelScope.launch {
            _addError.value = null
            try {
                val trimmed = input.trim()
                if (trimmed.startsWith("vless://")) {
                    withContext(Dispatchers.Default) { Libgreen.validateVlessKey(trimmed) }
                    val name = trimmed.substringAfterLast("#", "").ifBlank {
                        trimmed.substringAfter("@").substringBefore("?")
                    }
                    dao.insert(Config(name = name, vlessLink = trimmed))
                } else {
                    val name = nameFromJsonConfig(trimmed)
                    dao.insert(Config(name = name, configJson = trimmed))
                }
            } catch (e: Exception) {
                _addError.value = e.message ?: "Invalid input"
            }
        }
    }

    fun addSubscription(url: String) {
        viewModelScope.launch {
            _addError.value = null
            _subscriptionImporting.value = true
            try {
                withContext(Dispatchers.IO) {
                    val name = runCatching { java.net.URL(url).host }.getOrElse { url.take(30) }
                    val subId = subDao.insert(com.green.android.data.Subscription(url = url, name = name)).toInt()
                    val links = fetchSubscriptionLinks(url)
                    if (links.isEmpty()) {
                        _addError.value =
                            "No VLESS servers found in subscription"; return@withContext
                    }
                    var added = 0
                    for (link in links) {
                        runCatching {
                            Libgreen.validateVlessKey(link)
                            dao.insert(
                                Config(
                                    name = linkName(link),
                                    vlessLink = link,
                                    subscriptionId = subId
                                )
                            )
                            added++
                        }
                    }
                    if (added == 0) _addError.value = "No valid VLESS servers found"
                }
            } catch (e: Exception) {
                _addError.value = e.message ?: "Failed to fetch subscription"
            } finally {
                _subscriptionImporting.value = false
            }
        }
    }

    fun deleteSubscription(sub: com.green.android.data.Subscription) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                dao.deleteBySubscriptionId(sub.id)
                subDao.delete(sub)
            }
            val selId = _selectedId.value ?: return@launch
            val stillExists = withContext(Dispatchers.IO) { dao.getById(selId) != null }
            if (!stillExists) _selectedId.value = null
        }
    }

    private suspend fun refreshAllSubscriptions() {
        val subs = subDao.getAll()
        for (sub in subs) runCatching { refreshSubscription(sub) }
        // Clear selection if its config was deleted during refresh
        if (_selectedId.value != null && dao.getById(_selectedId.value!!) == null) {
            _selectedId.value = null
        }
    }

    private suspend fun refreshSubscription(sub: com.green.android.data.Subscription) {
        val newLinks = fetchSubscriptionLinks(sub.url)
        val existing = dao.getBySubscriptionId(sub.id)
        val existingByBase = existing.associateBy { it.vlessLink?.substringBefore("#").orEmpty() }
        val newByBase = newLinks.associateBy { it.substringBefore("#") }

        for (config in existing) {
            if (config.vlessLink?.substringBefore("#") !in newByBase) dao.delete(config)
        }
        for (link in newLinks) {
            val base = link.substringBefore("#")
            val name = linkName(link)
            val existing = existingByBase[base]
            when {
                existing == null -> dao.insert(
                    Config(
                        name = name,
                        vlessLink = link,
                        subscriptionId = sub.id
                    )
                )

                existing.name != name -> dao.update(existing.copy(name = name, vlessLink = link))
            }
        }
    }

    private fun fetchSubscriptionLinks(url: String): List<String> {
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.setRequestProperty("User-Agent", "smol-vpn/1.0")
        val raw = try {
            conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
        val text = decodeBase64(raw) ?: raw
        return text.lineSequence().map { it.trim() }.filter { it.startsWith("vless://") }.toList()
    }

    private fun linkName(link: String) =
        java.net.URLDecoder.decode(link.substringAfterLast("#", ""), "UTF-8")
            .ifBlank { link.substringAfter("@").substringBefore("?") }

    private fun decodeBase64(s: String): String? {
        for (flags in listOf(Base64.DEFAULT, Base64.URL_SAFE)) {
            runCatching {
                val decoded = Base64.decode(s.trim(), flags).toString(Charsets.UTF_8)
                if (decoded.contains("://")) return decoded
            }
        }
        return null
    }

    private fun nameFromJsonConfig(json: String): String {
        return try {
            val obj = JSONObject(json)
            val outbounds = obj.optJSONArray("outbounds")
            if (outbounds != null && outbounds.length() > 0) {
                val settings = outbounds.getJSONObject(0).optJSONObject("settings")
                val flat = settings?.optString("address", "")
                if (!flat.isNullOrBlank()) return flat
                val vnext = settings?.optJSONArray("vnext")
                val addr = vnext?.getJSONObject(0)?.optString("address", "")
                if (!addr.isNullOrBlank()) return addr
            }
            "JSON config"
        } catch (_: Exception) {
            "JSON config"
        }
    }

    fun clearAddError() {
        _addError.value = null
    }

    fun updateConfig(config: Config) {
        viewModelScope.launch { dao.update(config) }
    }

    fun deleteConfig(config: Config) {
        viewModelScope.launch { dao.delete(config) }
        if (_selectedId.value == config.id) _selectedId.value = null
    }

    fun connect(context: Context, launcher: ActivityResultLauncher<Intent>) {
        connectJob?.cancel()
        val config = configs.value.find { it.id == _selectedId.value } ?: return
        connectJob = viewModelScope.launch {
            val geo = _geo.value
            val filesDir = getApplication<Application>().filesDir
            if (geo.enabled && GeoUpdater.isStale(filesDir)) {
                _geo.update { it.copy(updating = true) }
                geoUpdateJob = launch { runCatching { GeoUpdater.download(filesDir, geo.geoipUrl, geo.geositeUrl) } }
                geoUpdateJob?.join()
                _geo.update { it.copy(updating = false) }
            }

            try {
                // Generate the xray config fresh from the vless key each time —
                // this picks up any config schema improvements from app updates,
                // and also performs DNS pre-resolution before the TUN is established.
                data class Resolved(
                    val configJson: String,
                    val dnsServer: String,
                    val socksPort: Int,
                    val socksUser: String,
                    val socksPass: String,
                )

                val resolved = withContext(Dispatchers.IO) {
                    val rawJson = when {
                        config.vlessLink != null -> Libgreen.vlessKeyToXrayJson(config.vlessLink)
                        config.configJson != null -> config.configJson
                        else -> throw IllegalStateException("config has neither vlessLink nor configJson")
                    }
                    val dns = when {
                        config.vlessLink != null -> Libgreen.vlessKeyDnsServer(config.vlessLink)
                        config.configJson != null -> dnsServerFromJson(config.configJson)
                        else -> "1.1.1.1"
                    }
                    // Port 0 lets the OS pick a free port; prevents other apps from
                    // connecting to a hardcoded port even if they know our proxy is running.
                    val port = ServerSocket(0).use { it.localPort }
                    val user = UUID.randomUUID().toString().replace("-", "").take(16)
                    val pass = UUID.randomUUID().toString().replace("-", "").take(16)
                    Resolved(
                        configJson = patchXrayConfig(rawJson, port, user, pass),
                        dnsServer = dns,
                        socksPort = port,
                        socksUser = user,
                        socksPass = pass,
                    )
                }
                pendingConfigJson = resolved.configJson
                pendingDnsServer = resolved.dnsServer
                pendingSocksPort = resolved.socksPort
                pendingSocksUser = resolved.socksUser
                pendingSocksPass = resolved.socksPass
                val prepare = VpnService.prepare(context)
                if (prepare != null) launcher.launch(prepare)
                else startService(
                    context,
                    resolved.configJson,
                    resolved.dnsServer,
                    resolved.socksPort,
                    resolved.socksUser,
                    resolved.socksPass,
                    _notify.value,
                )
            } catch (e: Exception) {
                VpnState.status.value = VpnStatus.DISCONNECTED
            }
        }
    }

    fun onPermissionDenied() {
        connectJob?.cancel()
        connectJob = null
        VpnState.status.value = VpnStatus.DISCONNECTED
    }

    fun onPermissionGranted(context: Context) {
        startService(
            context,
            pendingConfigJson ?: return,
            pendingDnsServer,
            pendingSocksPort,
            pendingSocksUser,
            pendingSocksPass,
            _notify.value,
        )
    }

    fun disconnect(context: Context) {
        connectJob?.cancel()
        connectJob = null
        _geo.update { it.copy(updating = false) }
        context.startService(
            Intent(context, GreenVpnService::class.java).apply {
                action = GreenVpnService.ACTION_STOP
            }
        )
    }

    private fun startService(
        context: Context,
        configJson: String,
        dnsServer: String,
        socksPort: Int,
        socksUser: String,
        socksPass: String,
        notify: Boolean,
    ) {
        VpnState.status.value = VpnStatus.CONNECTING
        context.startForegroundService(
            Intent(context, GreenVpnService::class.java).apply {
                action = GreenVpnService.ACTION_START
                putExtra(GreenVpnService.EXTRA_CONFIG_JSON, configJson)
                putExtra(GreenVpnService.EXTRA_DNS_SERVER, dnsServer)
                putExtra(GreenVpnService.EXTRA_SOCKS_PORT, socksPort)
                putExtra(GreenVpnService.EXTRA_SOCKS_USER, socksUser)
                putExtra(GreenVpnService.EXTRA_SOCKS_PASS, socksPass)
                putExtra(GreenVpnService.EXTRA_NOTIFY, notify)
                putStringArrayListExtra(
                    GreenVpnService.EXTRA_ALLOWED_APPS,
                    ArrayList(_allowedApps.value)
                )
            }
        )
    }
}
