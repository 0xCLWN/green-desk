package com.green.android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// GEO UPDATER — delete this file to remove geo update support.
// Also remove the marked blocks in VpnViewModel, GreenVpnService, and MainActivity.
object GeoUpdater {
    const val DEFAULT_GEOIP_URL =
        "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/geoip.dat"
    const val DEFAULT_GEOSITE_URL =
        "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/geosite.dat"

    private const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

    fun isStale(filesDir: File): Boolean = listOf("geoip.dat", "geosite.dat").any { name ->
        val f = File(filesDir, name)
        !f.exists() || System.currentTimeMillis() - f.lastModified() > MAX_AGE_MS
    }

    suspend fun download(
        filesDir: File,
        geoipUrl: String = DEFAULT_GEOIP_URL,
        geositeUrl: String = DEFAULT_GEOSITE_URL,
    ) = withContext(Dispatchers.IO) {
        listOf("geoip.dat" to geoipUrl, "geosite.dat" to geositeUrl).forEach { (name, url) ->
            val tmp = File(filesDir, "$name.tmp")
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout = 60_000
                conn.inputStream.use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                }
                conn.disconnect()
                tmp.renameTo(File(filesDir, name))
            } finally {
                if (tmp.exists()) tmp.delete()
            }
        }
    }
}
