package green

import java.nio.file.Path

val isWindows get() = System.getProperty("os.name").startsWith("Windows")
val isMac get() = System.getProperty("os.name").startsWith("Mac")
val isArm get() = System.getProperty("os.arch").let { it == "aarch64" || it == "arm64" }

fun appDataDir(): Path {
    val base = when {
        isWindows -> Path.of(System.getenv("APPDATA") ?: System.getProperty("user.home"), "green-desktop")
        isMac -> Path.of(System.getProperty("user.home"), "Library", "Application Support", "green-desktop")
        else -> Path.of(System.getProperty("user.home"), ".config", "green-desktop")
    }
    base.toFile().mkdirs()
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
