package green

import green.model.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

const val GITHUB_REPO = "0xCLWN/green-desktop"
private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L

suspend fun checkForUpdate(settings: AppSettings): UpdateInfo? = withContext(Dispatchers.IO) {
    if (settings.updateCheckedAt > 0 &&
        System.currentTimeMillis() - settings.updateCheckedAt < CACHE_TTL_MS
    ) {
        return@withContext cachedUpdate(settings)
    }

    runCatching {
        val url = URL("https://api.github.com/repos/$GITHUB_REPO/releases/latest")
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000

        val json = conn.inputStream.use { Json.parseToJsonElement(it.bufferedReader().readText()).jsonObject }
        val tag = json["tag_name"]?.jsonPrimitive?.content ?: return@runCatching null
        val assets = json["assets"]?.jsonArray ?: return@runCatching null

        val suffix = platformAssetSuffix()
        val asset = assets.firstOrNull {
            it.jsonObject["name"]?.jsonPrimitive?.content?.endsWith(suffix) == true
        }?.jsonObject

        val downloadUrl = asset?.get("browser_download_url")?.jsonPrimitive?.content ?: ""
        val size = asset?.get("size")?.jsonPrimitive?.long ?: 0L

        UpdateInfo(tag, downloadUrl, size)
    }.getOrNull()
}

private fun cachedUpdate(settings: AppSettings): UpdateInfo? {
    val tag = settings.updateTag.ifBlank { return null }
    val url = settings.updateUrl.ifBlank { return null }
    if (!isNewer(tag)) return null
    return UpdateInfo(tag, url, 0L)
}

suspend fun downloadUpdate(info: UpdateInfo, onProgress: (Float) -> Unit): File =
    withContext(Dispatchers.IO) {
        val dest = File(System.getProperty("java.io.tmpdir"), "Green${platformAssetSuffix()}")
        val conn = URL(info.downloadUrl).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 120_000
        val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
        var received = 0L
        conn.inputStream.use { input ->
            dest.outputStream().use { output ->
                val buf = ByteArray(8192)
                var n: Int
                while (input.read(buf).also { n = it } >= 0) {
                    output.write(buf, 0, n)
                    received += n
                    if (total > 0) onProgress(received.toFloat() / total)
                }
            }
        }
        dest
    }

fun openFile(file: File) {
    when {
        isMac -> ProcessBuilder("open", file.absolutePath).start()
        isWindows -> ProcessBuilder("cmd", "/c", "start", "", file.absolutePath).start()
        else -> ProcessBuilder("xdg-open", file.absolutePath).start()
    }
}

private fun platformAssetSuffix(): String = when {
    isMac -> "-arm64.dmg"
    isWindows -> "-windows.msi"
    else -> "-linux.AppImage"
}

fun isNewer(tag: String): Boolean = compareVersions(tag.trimStart('v'), APP_VERSION) > 0

private fun compareVersions(a: String, b: String): Int {
    val pa = a.split(".").map { it.toIntOrNull() ?: 0 }
    val pb = b.split(".").map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(pa.size, pb.size)) {
        val diff = (pa.getOrElse(i) { 0 }) - (pb.getOrElse(i) { 0 })
        if (diff != 0) return diff
    }
    return 0
}
