package green

import green.model.AppState
import green.model.UpdateInfo
import green.model.VlessKey
import green.model.activeKey
import green.model.isBaked
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

class AppViewModel(
    private val keyStore: KeyStore,
    private val settingsStore: SettingsStore,
    private val xray: XrayProcess,
    private val scope: CoroutineScope,
) {
    private val settings = settingsStore.load()
    private val _state = MutableStateFlow(
        AppState(
            keys = mergeKeys(keyStore.load(), loadBakedKeys()),
            sysProxyEnabled = settings.sysProxyEnabled,
            socksPort = settings.socksPort,
            httpPort = settings.httpPort,
        )
    )
    val state: StateFlow<AppState> = _state.asStateFlow()

    init {
        val current = _state.value
        keyStore.save(current.keys.filter { !it.isBaked })
        if (current.activeKeyId == null) {
            _state.update { it.copy(activeKeyId = it.keys.firstOrNull()?.id) }
        }
        scope.launch { checkUpdate() }
    }

    fun addKey(uri: String, name: String) {
        val key = VlessKey(id = UUID.randomUUID().toString(), name = name, uri = uri, addedAt = System.currentTimeMillis())
        _state.update { s ->
            val keys = s.keys + key
            keyStore.save(keys)
            s.copy(keys = keys, activeKeyId = s.activeKeyId ?: key.id)
        }
    }

    fun removeKey(id: String) {
        if (_state.value.keys.find { it.id == id }?.isBaked == true) return
        if (_state.value.activeKeyId == id && xray.isRunning) {
            scope.launch { stopProxy() }
        }
        _state.update { s ->
            val keys = s.keys.filter { it.id != id }
            keyStore.save(keys.filter { !it.isBaked })
            s.copy(
                keys = keys,
                activeKeyId = if (s.activeKeyId == id) keys.firstOrNull()?.id else s.activeKeyId,
            )
        }
    }

    fun activateKey(id: String) {
        val wasRunning = xray.isRunning
        if (wasRunning) scope.launch {
            stopProxy()
            _state.update { it.copy(activeKeyId = id) }
            startProxy()
        } else {
            _state.update { it.copy(activeKeyId = id) }
        }
    }

    fun toggleProxy() {
        if (_state.value.running) scope.launch { stopProxy() }
        else scope.launch { startProxy() }
    }

    fun toggleSysProxy() {
        val s = _state.value
        val enable = !s.sysProxyEnabled
        _state.update { it.copy(sysProxyEnabled = enable) }
        settingsStore.save(settings.copy(sysProxyEnabled = enable))
        scope.launch { setSysProxy(enable = enable, socksPort = s.socksPort, httpPort = s.httpPort) }
    }

    fun updatePorts(socksPort: Int, httpPort: Int) {
        _state.update { it.copy(socksPort = socksPort, httpPort = httpPort) }
        settingsStore.save(settings.copy(socksPort = socksPort, httpPort = httpPort))
    }

    private suspend fun startProxy() {
        val key = _state.value.activeKey ?: return
        val s = _state.value
        _state.update { it.copy(error = null) }
        xray.start(key, s.socksPort, s.httpPort).fold(
            onSuccess = {
                _state.update { it.copy(running = true) }
                if (_state.value.sysProxyEnabled) setSysProxy(enable = true, socksPort = s.socksPort, httpPort = s.httpPort)
            },
            onFailure = { e ->
                _state.update { it.copy(error = e.message) }
            },
        )
    }

    private suspend fun stopProxy() {
        val s = _state.value
        if (s.sysProxyEnabled) setSysProxy(enable = false, socksPort = s.socksPort, httpPort = s.httpPort)
        xray.stop()
        _state.update { it.copy(running = false) }
    }

    fun installUpdate(info: UpdateInfo) {
        scope.launch {
            _state.update { it.copy(updateProgress = 0f) }
            runCatching {
                val file = downloadUpdate(info) { progress ->
                    _state.update { it.copy(updateProgress = progress) }
                }
                openFile(file)
            }
            _state.update { it.copy(updateProgress = null) }
        }
    }

    fun dismissUpdate() {
        _state.update { it.copy(availableUpdate = null) }
    }

    private suspend fun checkUpdate() {
        val current = settings
        val info = checkForUpdate(current) ?: return
        val newSettings = current.copy(
            updateCheckedAt = System.currentTimeMillis(),
            updateTag = info.tag,
            updateUrl = info.downloadUrl,
        )
        settingsStore.save(newSettings)
        if (isNewer(info.tag)) _state.update { it.copy(availableUpdate = info) }
    }

    fun onExit() {
        if (xray.isRunning) {
            runBlocking { stopProxy() }
        }
    }
}

private fun mergeKeys(stored: List<VlessKey>, baked: List<VlessKey>): List<VlessKey> {
    val storedIds = stored.map { it.id }.toSet()
    return baked.filter { it.id !in storedIds } + stored
}
