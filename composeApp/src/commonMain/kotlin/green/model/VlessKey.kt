package green.model

import kotlinx.serialization.Serializable

@Serializable
data class VlessKey(
    val id: String,
    val name: String,
    val uri: String,
    val addedAt: Long = 0L,
)

val VlessKey.isBaked get() = id.startsWith("baked:")
