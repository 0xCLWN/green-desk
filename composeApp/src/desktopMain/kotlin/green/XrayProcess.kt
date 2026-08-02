package green

import green.model.VlessKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.ServerSocket
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
                val api = freePort()
                val configFile = appDir.resolve("config.json")

                // redirectErrorStream so we never deadlock on a full stderr pipe.
                val key2json = ProcessBuilder(
                    binary.toString(), "key2json",
                    "--socks-port", "$socksPort",
                    "--http-port", "$httpPort",
                    "--api-port", "$api",
                    key.uri,
                ).directory(appDir.toFile()).redirectErrorStream(true).start()
                val output = key2json.inputStream.bufferedReader().readText()
                if (key2json.waitFor() != 0) error("key2json failed: $output")
                configFile.writeText(output)

                val logFile = appDir.resolve("xray.log").toFile()
                val proc = ProcessBuilder(binary.toString(), "run", "--headless", "-c", configFile.toString())
                    .directory(appDir.toFile())
                    .redirectErrorStream(true)
                    .also { it.environment()["XRAY_LOCATION_ASSET"] = appDir.toString() }
                    .start()
                process = proc

                // Drain output so the pipe never blocks.
                Thread {
                    logFile.outputStream().use { out -> proc.inputStream.copyTo(out) }
                }.apply { isDaemon = true; start() }

                // Suspending delay keeps the coroutine cancellable (unlike Thread.sleep).
                delay(500)
                if (!proc.isAlive) {
                    val tail = logFile.takeIf { it.exists() }
                        ?.readLines()?.takeLast(5)?.joinToString("\n")
                        ?: "(no log)"
                    error("xray exited (code ${proc.exitValue()})\n$tail")
                }

                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // @Synchronized so onExit() and an in-flight start() can't race on `process`.
    @Synchronized fun stop() {
        process?.let { p ->
            p.destroy()
            if (!p.waitFor(3, TimeUnit.SECONDS)) p.destroyForcibly()
        }
        process = null
    }

    private fun ensureBinary(): Path {
        val dest = appDir.resolve(xrayBinaryName())

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

private fun freePort(): Int = ServerSocket(0).use { it.localPort }
