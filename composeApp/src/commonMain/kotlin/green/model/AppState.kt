package green.model

data class AppState(
    val keys: List<VlessKey> = emptyList(),
    val activeKeyId: String? = null,
    val running: Boolean = false,
    val error: String? = null,
    val upBytes: Long = 0L,
    val downBytes: Long = 0L,
)

val AppState.activeKey: VlessKey?
    get() = keys.find { it.id == activeKeyId }
