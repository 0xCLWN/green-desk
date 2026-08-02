package green

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

@Serializable
data class AppSettings(
    val sysProxyEnabled: Boolean = false,
    val updateCheckedAt: Long = 0,
    val updateTag: String = "",
    val updateUrl: String = "",
)

class SettingsStore(private val dir: Path) {
    private val file = dir.resolve("settings.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun load(): AppSettings = runCatching {
        json.decodeFromString<AppSettings>(file.readText())
    }.getOrDefault(AppSettings())

    fun save(settings: AppSettings) {
        file.writeText(json.encodeToString(AppSettings.serializer(), settings))
    }
}
