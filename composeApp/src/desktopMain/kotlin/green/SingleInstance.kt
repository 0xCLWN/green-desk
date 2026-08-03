package green

import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

object SingleInstance {
    private const val PORT = 57312
    private const val MAGIC = "green-desktop-focus"

    /**
     * Returns true if this is the first instance.
     * If another instance is already running, sends it a focus signal and returns false —
     * the caller should exit immediately.
     */
    fun tryAcquire(onFocusRequest: () -> Unit): Boolean {
        // Try to reach an existing instance.
        try {
            Socket("127.0.0.1", PORT).use { socket ->
                socket.outputStream.bufferedWriter().apply { write(MAGIC); newLine(); flush() }
            }
            return false
        } catch (_: IOException) {
            // Nothing listening — we are the first instance.
        }

        val server = try {
            ServerSocket(PORT, 1, InetAddress.getLoopbackAddress())
        } catch (_: IOException) {
            // Race: another instance just bound the port. Let this one proceed rather than block.
            return true
        }

        Thread {
            try {
                while (true) {
                    val client = server.accept()
                    try {
                        if (client.inputStream.bufferedReader().readLine() == MAGIC) onFocusRequest()
                    } finally {
                        client.close()
                    }
                }
            } catch (_: IOException) { /* server closed on shutdown */ }
        }.apply {
            isDaemon = true
            name = "single-instance-listener"
            start()
        }

        Runtime.getRuntime().addShutdownHook(Thread { runCatching { server.close() } })
        return true
    }
}
