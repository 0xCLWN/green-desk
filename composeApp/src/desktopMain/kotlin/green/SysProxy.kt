package green

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun setSysProxy(enable: Boolean, socksPort: Int = 10808, httpPort: Int = 10809) =
    withContext(Dispatchers.IO) {
        when {
            isMac -> setMacProxy(enable, socksPort, httpPort)
            isWindows -> setWindowsProxy(enable, httpPort)
        }
    }

private fun setMacProxy(enable: Boolean, socksPort: Int, httpPort: Int) {
    val services = runCatching {
        Runtime.getRuntime()
            .exec(arrayOf("networksetup", "-listallnetworkservices"))
            .inputStream.bufferedReader().readLines()
            .drop(1)
            .filter { !it.startsWith("*") && it.isNotBlank() }
    }.getOrDefault(listOf("Wi-Fi", "Ethernet"))

    for (svc in services) {
        if (enable) {
            exec("networksetup", "-setsocksfirewallproxy", svc, "127.0.0.1", "$socksPort")
            exec("networksetup", "-setsocksfirewallproxystate", svc, "on")
            exec("networksetup", "-setwebproxy", svc, "127.0.0.1", "$httpPort")
            exec("networksetup", "-setwebproxystate", svc, "on")
            exec("networksetup", "-setsecurewebproxy", svc, "127.0.0.1", "$httpPort")
            exec("networksetup", "-setsecurewebproxystate", svc, "on")
        } else {
            exec("networksetup", "-setsocksfirewallproxystate", svc, "off")
            exec("networksetup", "-setwebproxystate", svc, "off")
            exec("networksetup", "-setsecurewebproxystate", svc, "off")
        }
    }
}

private fun setWindowsProxy(enable: Boolean, httpPort: Int) {
    val key = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings"
    if (enable) {
        exec("reg", "add", key, "/v", "ProxyEnable", "/t", "REG_DWORD", "/d", "1", "/f")
        exec("reg", "add", key, "/v", "ProxyServer", "/t", "REG_SZ", "/d", "127.0.0.1:$httpPort", "/f")
    } else {
        exec("reg", "add", key, "/v", "ProxyEnable", "/t", "REG_DWORD", "/d", "0", "/f")
    }
}

private fun exec(vararg cmd: String) {
    runCatching { Runtime.getRuntime().exec(cmd).waitFor() }
}
