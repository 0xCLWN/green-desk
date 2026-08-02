package green

import green.model.AppState
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
        )
    )
    val state: StateFlow<AppState> = _state.asStateFlow()

    init {
        val current = _state.value
        keyStore.save(current.keys.filter { !it.isBaked })
        if (current.activeKeyId == null) {
            _state.update { it.copy(activeKeyId = it.keys.firstOrNull()?.id) }
        }
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
        if (xray.isRunning) scope.launch { stopProxy() }
        else scope.launch { startProxy() }
    }

    fun toggleSysProxy() {
        scope.launch {
            val enable = !_state.value.sysProxyEnabled
            setSysProxy(enable = enable)
            _state.update { it.copy(sysProxyEnabled = enable) }
            settingsStore.save(AppSettings(sysProxyEnabled = enable))
        }
    }

    private suspend fun startProxy() {
        val key = _state.value.activeKey ?: return
        _state.update { it.copy(error = null) }
        xray.start(key).fold(
            onSuccess = {
                _state.update { it.copy(running = true) }
                if (_state.value.sysProxyEnabled) setSysProxy(enable = true)
            },
            onFailure = { e ->
                _state.update { it.copy(error = e.message) }
            },
        )
    }

    private suspend fun stopProxy() {
        if (_state.value.sysProxyEnabled) setSysProxy(enable = false)
        xray.stop()
        _state.update { it.copy(running = false) }
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
