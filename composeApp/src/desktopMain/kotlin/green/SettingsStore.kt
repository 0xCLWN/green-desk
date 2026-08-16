package green

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.readText

@Serializable
data class AppSettings(
    val sysProxyEnabled: Boolean = false,
    val socksPort: Int = 10808,
    val httpPort: Int = 10809,
    val updateCheckedAt: Long = 0,
    val updateTag: String = "",
    val updateUrl: String = "",
    val activeKeyId: String = "",
)

class SettingsStore(private val dir: Path) {
    private val file = dir.resolve("settings.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun load(): AppSettings = runCatching {
        json.decodeFromString<AppSettings>(file.readText())
    }.getOrDefault(AppSettings())

    fun save(settings: AppSettings) {
        file.writeTextSafely(json.encodeToString(AppSettings.serializer(), settings))
    }
}
