package green

import green.model.VlessKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.ServerSocket
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.*

class XrayProcess(private val appDir: Path) {
    private var process: Process? = null
    val isRunning get() = process?.isAlive == true

    suspend fun start(key: VlessKey, socksPort: Int = 10808, httpPort: Int = 10809): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            stop()
            val binary = ensureBinary()
            val api = freePort()
            val configFile = appDir.resolve("config.json")
            val key2json = ProcessBuilder(
                binary.toString(), "key2json",
                "--socks-port", "$socksPort",
                "--http-port", "$httpPort",
                "--api-port", "$api",
                key.uri,
            ).directory(appDir.toFile()).start()
            val json = key2json.inputStream.bufferedReader().readText()
            if (key2json.waitFor() != 0) error("key2json failed: ${key2json.errorStream.bufferedReader().readText()}")
            configFile.writeText(json)

            process = ProcessBuilder(binary.toString(), "run", "-c", configFile.toString())
                .directory(appDir.toFile())
                .redirectErrorStream(true)
                .also { pb ->
                    pb.environment()["XRAY_LOCATION_ASSET"] = appDir.toString()
                }
                .start()
        }
    }

    fun stop() {
        process?.destroy()
        process = null
    }

    private fun ensureBinary(): Path {
        val dest = appDir.resolve(xrayBinaryName())
        if (dest.exists()) return dest

        val resourceName = xrayResourceName()
        val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(resourceName)
            ?: error("Bundled xray binary not found: $resourceName")

        stream.use { dest.outputStream().use { out -> it.copyTo(out) } }

        if (!isWindows) {
            dest.setPosixFilePermissions(
                dest.getPosixFilePermissions() + PosixFilePermission.OWNER_EXECUTE
            )
        }
        return dest
    }
}

private fun freePort(): Int {
    // Let the OS assign a free port in the ephemeral range, then immediately release it.
    // There's a brief TOCTOU window, but it's negligible for local proxy ports.
    return ServerSocket(0).use { it.localPort }
}
