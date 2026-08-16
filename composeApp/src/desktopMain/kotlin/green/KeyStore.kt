package green

import green.model.VlessKey
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

class KeyStore(private val dir: Path) {
    private val file = dir.resolve("keys.json")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun load(): List<VlessKey> = runCatching {
        json.decodeFromString(ListSerializer(VlessKey.serializer()), file.readText())
    }.getOrDefault(emptyList())

    fun save(keys: List<VlessKey>) {
        file.writeText(json.encodeToString(ListSerializer(VlessKey.serializer()), keys))
        file.restrictToOwner()
    }
}
