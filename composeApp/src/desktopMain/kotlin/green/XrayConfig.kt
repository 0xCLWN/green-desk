package green

import green.model.VlessKey
import java.net.URI

fun buildXrayConfig(key: VlessKey, socksPort: Int = 10808, httpPort: Int = 10809, apiPort: Int = 8888): String {
    val uri = URI(key.uri)
    val uuid = uri.userInfo
    val host = uri.host
    val port = uri.port.takeIf { it > 0 } ?: 443
    val params = uri.query.orEmpty()
        .split("&")
        .mapNotNull { it.split("=", limit = 2).takeIf { p -> p.size == 2 }?.let { p -> p[0] to p[1] } }
        .toMap()

    val network = params["type"] ?: "tcp"
    val security = params["security"] ?: "none"
    val flow = params["flow"] ?: ""
    val sni = params["sni"] ?: params["host"] ?: host
    val fingerprint = params["fp"] ?: "chrome"
    val pbk = params["pbk"] ?: ""
    val sid = params["sid"] ?: ""
    val spx = java.net.URLDecoder.decode(params["spx"] ?: "", "UTF-8")
    val wsPath = java.net.URLDecoder.decode(params["path"] ?: "/", "UTF-8")
    val wsHost = params["host"] ?: sni

    val streamSettings = buildStreamSettings(network, security, sni, fingerprint, pbk, sid, spx, wsPath, wsHost)

    return """
{
  "log": { "loglevel": "warning" },
  "api": {
    "tag": "api",
    "services": ["HandlerService", "StatsService", "LoggerService"]
  },
  "stats": {},
  "policy": {
    "system": {
      "statsOutboundDownlink": true,
      "statsOutboundUplink": true
    }
  },
  "inbounds": [
    {
      "tag": "socks",
      "port": $socksPort,
      "listen": "127.0.0.1",
      "protocol": "socks",
      "settings": { "udp": true }
    },
    {
      "tag": "http",
      "port": $httpPort,
      "listen": "127.0.0.1",
      "protocol": "http"
    },
    {
      "tag": "api",
      "port": $apiPort,
      "listen": "127.0.0.1",
      "protocol": "dokodemo-door",
      "settings": { "address": "127.0.0.1" }
    }
  ],
  "outbounds": [
    {
      "tag": "proxy",
      "protocol": "vless",
      "settings": {
        "vnext": [{
          "address": "$host",
          "port": $port,
          "users": [{
            "id": "$uuid",
            "encryption": "none"${if (flow.isNotEmpty()) """,
            "flow": "$flow"""" else ""}
          }]
        }]
      },
      "streamSettings": $streamSettings
    },
    {
      "tag": "direct",
      "protocol": "freedom"
    }
  ],
  "routing": {
    "rules": [
      {
        "inboundTag": ["api"],
        "outboundTag": "api",
        "type": "field"
      },
      {
        "type": "field",
        "ip": ["geoip:private"],
        "outboundTag": "direct"
      }
    ]
  }
}
""".trimIndent()
}

private fun buildStreamSettings(
    network: String,
    security: String,
    sni: String,
    fingerprint: String,
    pbk: String,
    sid: String,
    spx: String,
    wsPath: String,
    wsHost: String,
): String {
    val networkSettings = when (network) {
        "ws" -> """
      "wsSettings": {
        "path": "$wsPath",
        "headers": { "Host": "$wsHost" }
      }"""
        "grpc" -> """
      "grpcSettings": { "serviceName": "$wsPath" }"""
        else -> ""
    }

    val securitySettings = when (security) {
        "reality" -> """
      "realitySettings": {
        "fingerprint": "$fingerprint",
        "serverName": "$sni",
        "publicKey": "$pbk",
        "shortId": "$sid",
        "spiderX": "$spx"
      }"""
        "tls" -> """
      "tlsSettings": {
        "serverName": "$sni",
        "fingerprint": "$fingerprint"
      }"""
        else -> ""
    }

    val parts = listOfNotNull(
        """"network": "$network"""",
        if (security != "none") """"security": "$security"""" else null,
        networkSettings.ifBlank { null },
        securitySettings.ifBlank { null },
    )

    return "{\n      ${parts.joinToString(",\n      ")}\n    }"
}
