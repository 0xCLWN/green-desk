package green.model

data class AppState(
    val keys: List<VlessKey> = emptyList(),
    val activeKeyId: String? = null,
    val running: Boolean = false,
    val sysProxyEnabled: Boolean = false,
    val socksPort: Int = 10808,
    val httpPort: Int = 10809,
    val error: String? = null,
    val availableUpdate: UpdateInfo? = null,
    val updateProgress: Float? = null,
    val updateError: String? = null,
    val checkingUpdate: Boolean = false,
    val connecting: Boolean = false,
)

val AppState.activeKey: VlessKey?
    get() = keys.find { it.id == activeKeyId }
