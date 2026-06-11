package com.green.android

import org.json.JSONArray
import org.json.JSONObject

object Prefs {
    const val NAME           = "green"
    const val AUTO_CONNECT   = "auto_connect"
    const val NOTIFY         = "notify"
    const val ALLOWED_APPS   = "allowed_apps"
    const val LAST_SELECTED  = "last_selected_id"
    const val DEFAULTS_SEEDED = "defaults_seeded"
    const val GEO_ENABLED    = "geo_enabled"
    const val GEO_GEOIP_URL  = "geo_geoip_url"
    const val GEO_GEOSITE_URL = "geo_geosite_url"
    const val DISGUISE           = "disguise"
    const val UPDATE_LAST_CHECK        = "update_last_check"
    const val UPDATE_DISMISSED_TAG     = "update_dismissed_tag"
    const val UPDATE_CACHED_TAG        = "update_cached_tag"
    const val UPDATE_CACHED_URL        = "update_cached_url"
    const val UPDATE_CACHED_SIZE       = "update_cached_size"
    const val UPDATE_CACHED_DOWNLOAD   = "update_cached_download"
    const val SUGGESTED_APPS           = "suggested_apps"
}

private val countryCodeRegex = Regex("_([a-zA-Z]{2})$")

fun nameWithFlag(name: String): String {
    val code = countryCodeRegex.find(name)?.groupValues?.get(1)?.uppercase() ?: return name
    val flag = code.map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
    val base = name.dropLast(3).trimEnd()
    return "$flag ${base.ifBlank { code }}"
}

fun patchXrayConfig(json: String, port: Int, user: String, pass: String): String {
    val obj = JSONObject(json)
    val inbounds = obj.optJSONArray("inbounds") ?: return json
    for (i in 0 until inbounds.length()) {
        val inbound = inbounds.getJSONObject(i)
        if (inbound.optString("protocol") == "socks") {
            inbound.put("port", port)
            val settings = inbound.optJSONObject("settings") ?: JSONObject()
            settings.put("auth", "password")
            settings.put("accounts", JSONArray().put(JSONObject().put("user", user).put("pass", pass)))
            inbound.put("settings", settings)
        }
    }
    return obj.toString()
}

fun dnsServerFromJson(json: String): String {
    return try {
        val servers = JSONObject(json).optJSONObject("dns")?.optJSONArray("servers")
        if (servers != null) {
            for (i in 0 until servers.length()) {
                val addr = when (val s = servers.get(i)) {
                    is JSONObject -> s.optString("address", "")
                    is String -> s
                    else -> ""
                }
                if (addr.isNotBlank() && addr != "localhost" && !addr.startsWith("127.")) return addr
            }
        }
        "1.1.1.1"
    } catch (_: Exception) {
        "1.1.1.1"
    }
}
