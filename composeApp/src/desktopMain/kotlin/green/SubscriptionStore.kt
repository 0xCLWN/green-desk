package green

import green.model.Subscription
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.readText

class SubscriptionStore(private val dir: Path) {
    private val file = dir.resolve("subscriptions.json")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun load(): List<Subscription> = runCatching {
        json.decodeFromString(ListSerializer(Subscription.serializer()), file.readText())
    }.getOrDefault(emptyList())

    fun save(subscriptions: List<Subscription>) {
        file.writeTextSafely(json.encodeToString(ListSerializer(Subscription.serializer()), subscriptions))
    }
}
