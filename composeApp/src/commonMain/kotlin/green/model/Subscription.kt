package green.model

import kotlinx.serialization.Serializable

@Serializable
data class Subscription(
    val id: String,
    val url: String,
    val name: String,
    val lastUpdated: Long? = null,
    val lastError: String? = null,
)
