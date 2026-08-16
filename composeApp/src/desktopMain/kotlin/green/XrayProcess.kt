package green

import green.model.VlessKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.util.concurrent.TimeUnit
import kotlin.io.path.*

class XrayProcess(private val appDir: Path) {
    @Volatile private var process: Process? = null
    val isRunning get() = process?.isAlive == true

    suspend fun start(key: VlessKey, socksPort: Int = 10808, httpPort: Int = 10809): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                stop()
                val binary = ensureBinary()
                val configFile = appDir.resolve("config.json")

                // Pass URI via stdin — keeps it off the process argument list (not visible in ps).
                // No --api-port: the gRPC API is unused and would expose server config to any local process.
                val key2json = ProcessBuilder(
                    binary.toString(), "key2json",
                    "--socks-port", "$socksPort",
                    "--http-port", "$httpPort",
                ).directory(appDir.toFile()).redirectErrorStream(true).start()
                key2json.outputStream.bufferedWriter().use { it.write(key.uri) }
                val output = key2json.inputStream.bufferedReader().readText()
                if (key2json.waitFor() != 0) error("key2json failed: $output")

                // Write config then restrict; delete once xray has loaded it.
                configFile.writeText(output)
                configFile.restrictToOwner()

                val logFile = appDir.resolve("xray.log")
                val proc = ProcessBuilder(binary.toString(), "run", "--headless", "-c", configFile.toString())
                    .directory(appDir.toFile())
                    .redirectErrorStream(true)
                    .also { it.environment()["XRAY_LOCATION_ASSET"] = appDir.toString() }
                    .start()
                process = proc

                // Drain output so the pipe never blocks; restrict log file to owner-only.
                Thread {
                    logFile.toFile().outputStream().also { logFile.restrictToOwner() }
                        .use { out -> proc.inputStream.copyTo(out) }
                }.apply { isDaemon = true; start() }

                delay(500)
                if (!proc.isAlive) {
                    val tail = logFile.takeIf { it.exists() }
                        ?.readLines()?.takeLast(5)?.joinToString("\n")
                        ?: "(no log)"
                    error("xray exited (code ${proc.exitValue()})\n$tail")
                }

                // xray has loaded the config — delete it so the endpoint doesn't sit on disk.
                runCatching { configFile.deleteIfExists() }

                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    @Synchronized fun stop() {
        process?.let { p ->
            p.destroy()
            if (!p.waitFor(3, TimeUnit.SECONDS)) p.destroyForcibly()
        }
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
