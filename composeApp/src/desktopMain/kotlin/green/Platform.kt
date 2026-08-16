package green

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.writeText

val isWindows get() = System.getProperty("os.name").startsWith("Windows")
val isMac get() = System.getProperty("os.name").startsWith("Mac")
val isArm get() = System.getProperty("os.arch").let { it == "aarch64" || it == "arm64" }

// Write text to <file>.bk then atomically rename to <file>.
// Guarantees the target is never partially written — crash safety.
fun Path.writeTextSafely(text: String) {
    val bk = resolveSibling("$fileName.bk")
    bk.writeText(text)
    bk.restrictToOwner()
    runCatching {
        Files.move(bk, this, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }.onFailure {
        // ATOMIC_MOVE unsupported on this filesystem (common on Windows) — fall back.
        Files.move(bk, this, StandardCopyOption.REPLACE_EXISTING)
    }
}

// Silently no-op on Windows (which uses ACLs inherited from %APPDATA% / ~/Library).
fun Path.restrictToOwner(executable: Boolean = false) {
    if (isWindows) return
    val perms = if (executable) "rwx------" else "rw-------"
    runCatching { Files.setPosixFilePermissions(this, PosixFilePermissions.fromString(perms)) }
}

fun appDataDir(): Path {
    val base = when {
        isWindows -> Path.of(System.getenv("APPDATA") ?: System.getProperty("user.home"), "green-desktop")
        isMac -> Path.of(System.getProperty("user.home"), "Library", "Application Support", "green-desktop")
        else -> Path.of(System.getProperty("user.home"), ".config", "green-desktop")
    }
    base.toFile().mkdirs()
    base.restrictToOwner(executable = true)
    return base
}

fun xrayResourceName(): String = when {
    isWindows -> "xray/xray-windows-amd64.exe"
    isMac && isArm -> "xray/xray-darwin-arm64"
    isMac -> "xray/xray-darwin-amd64"
    isArm -> "xray/xray-linux-arm64"
    else -> "xray/xray-linux-amd64"
}

fun xrayBinaryName(): String = if (isWindows) "xray.exe" else "xray"
