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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class AppViewModel(
    private val keyStore: KeyStore,
    private val settingsStore: SettingsStore,
    private val xray: XrayProcess,
    private val scope: CoroutineScope,
) {
    // @Volatile + @Synchronized saveSettings() keeps this consistent across threads.
    @Volatile private var settings = settingsStore.load()

    private val _state = MutableStateFlow(
        AppState(
            keys = mergeKeys(keyStore.load(), loadBakedKeys()),
            sysProxyEnabled = settings.sysProxyEnabled,
            socksPort = settings.socksPort,
            httpPort = settings.httpPort,
        )
    )
    val state: StateFlow<AppState> = _state.asStateFlow()

    // Serialises all stop/start sequences.
    private val proxyMutex = Mutex()

    // Serialises concurrent toggleSysProxy calls so networksetup calls don't interleave.
    private val sysProxyMutex = Mutex()

    init {
        val current = _state.value
        keyStore.save(current.keys.filter { !it.isBaked })
        if (current.activeKeyId == null) {
            _state.update { it.copy(activeKeyId = it.keys.firstOrNull()?.id) }
        }
        scope.launch {
            val snap = settings
            val info = checkForUpdate(snap) ?: return@launch
            saveSettings(snap.copy(
                updateCheckedAt = System.currentTimeMillis(),
                updateTag = info.tag,
                updateUrl = info.downloadUrl,
            ))
            if (isNewer(info.tag)) _state.update { it.copy(availableUpdate = info) }
        }
    }

    fun addKey(uri: String, name: String) {
        val key = VlessKey(id = UUID.randomUUID().toString(), name = name, uri = uri, addedAt = System.currentTimeMillis())
        _state.update { s -> s.copy(keys = s.keys + key, activeKeyId = s.activeKeyId ?: key.id) }
        // Save outside update lambda — avoids side-effects on CAS retry.
        keyStore.save(_state.value.keys)
    }

    fun removeKey(id: String) {
        if (_state.value.keys.find { it.id == id }?.isBaked == true) return
        scope.launch {
            if (_state.value.activeKeyId == id && _state.value.running) {
                proxyMutex.withLock { stopProxyLocked() }
            }
            _state.update { s ->
                s.copy(
                    keys = s.keys.filter { it.id != id },
                    activeKeyId = if (s.activeKeyId == id) s.keys.firstOrNull { it.id != id }?.id else s.activeKeyId,
                )
            }
            keyStore.save(_state.value.keys.filter { !it.isBaked })
        }
    }

    fun activateKey(id: String) {
        scope.launch {
            proxyMutex.withLock {
                val wasRunning = _state.value.running
                if (wasRunning) stopProxyLocked()
                _state.update { it.copy(activeKeyId = id) }
                if (wasRunning) startProxyLocked()
            }
        }
    }

    fun toggleProxy() {
        scope.launch {
            proxyMutex.withLock {
                if (_state.value.running) stopProxyLocked() else startProxyLocked()
            }
        }
    }

    fun toggleSysProxy() {
        val s = _state.value
        val enable = !s.sysProxyEnabled
        _state.update { it.copy(sysProxyEnabled = enable) }
        saveSettings(settings.copy(sysProxyEnabled = enable))
        scope.launch {
            sysProxyMutex.withLock {
                setSysProxy(enable = enable, socksPort = s.socksPort, httpPort = s.httpPort)
            }
        }
    }

    fun updatePorts(socksPort: Int, httpPort: Int) {
        _state.update { it.copy(socksPort = socksPort, httpPort = httpPort) }
        saveSettings(settings.copy(socksPort = socksPort, httpPort = httpPort))
    }

    @Synchronized private fun saveSettings(s: AppSettings) {
        settings = s
        settingsStore.save(s)
    }

    private suspend fun startProxyLocked() {
        val key = _state.value.activeKey ?: return
        val s = _state.value
        _state.update { it.copy(error = null, connecting = true) }
        xray.start(key, s.socksPort, s.httpPort).fold(
            onSuccess = {
                _state.update { it.copy(running = true, connecting = false) }
                if (_state.value.sysProxyEnabled) setSysProxy(enable = true, socksPort = s.socksPort, httpPort = s.httpPort)
            },
            onFailure = { e ->
                _state.update { it.copy(error = e.message, connecting = false) }
            },
        )
    }

    private suspend fun stopProxyLocked() {
        val s = _state.value
        if (s.sysProxyEnabled) setSysProxy(enable = false, socksPort = s.socksPort, httpPort = s.httpPort)
        xray.stop()
        _state.update { it.copy(running = false, connecting = false) }
    }

    fun installUpdate(info: UpdateInfo) {
        scope.launch {
            _state.update { it.copy(updateProgress = 0f) }
            runCatching {
                val file = downloadUpdate(info) { progress ->
                    _state.update { it.copy(updateProgress = progress) }
                }
                proxyMutex.withLock { if (_state.value.running) stopProxyLocked() }
                openFile(file)
            }
            _state.update { it.copy(updateProgress = null) }
        }
    }

    fun dismissUpdate() {
        _state.update { it.copy(availableUpdate = null) }
    }

    fun checkUpdate() {
        scope.launch {
            _state.update { it.copy(checkingUpdate = true) }
            val snap = settings
            val info = checkForUpdate(snap.copy(updateCheckedAt = 0))
            if (info != null) {
                saveSettings(snap.copy(
                    updateCheckedAt = System.currentTimeMillis(),
                    updateTag = info.tag,
                    updateUrl = info.downloadUrl,
                ))
                if (isNewer(info.tag)) _state.update { it.copy(availableUpdate = info) }
            }
            _state.update { it.copy(checkingUpdate = false) }
        }
    }

    fun renameKey(id: String, name: String) {
        if (name.isBlank()) return
        _state.update { s ->
            s.copy(keys = s.keys.map { if (it.id == id) it.copy(name = name) else it })
        }
        keyStore.save(_state.value.keys.filter { !it.isBaked })
    }

    fun editKey(id: String, name: String, uri: String) {
        _state.update { s ->
            s.copy(keys = s.keys.map { if (it.id == id) it.copy(name = name, uri = uri) else it })
        }
        keyStore.save(_state.value.keys.filter { !it.isBaked })
    }

    fun onExit() {
        scope.cancel()
        // Scope is cancelled — no new coroutines can start. Clean up synchronously.
        if (_state.value.running) runBlocking {
            val s = _state.value
            if (s.sysProxyEnabled) setSysProxy(enable = false, socksPort = s.socksPort, httpPort = s.httpPort)
            xray.stop() // @Synchronized — safe even if a cancelled coroutine is mid-stop
        }
    }
}

private fun mergeKeys(stored: List<VlessKey>, baked: List<VlessKey>): List<VlessKey> {
    val storedIds = stored.map { it.id }.toSet()
    return baked.filter { it.id !in storedIds } + stored
}
