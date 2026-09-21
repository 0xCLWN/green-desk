package green

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.util.Base64

private const val SUBSCRIPTION_TIMEOUT_MS = 10_000

suspend fun fetchSubscriptionLinks(url: String): List<String> = withContext(Dispatchers.IO) {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.connectTimeout = SUBSCRIPTION_TIMEOUT_MS
    conn.readTimeout = SUBSCRIPTION_TIMEOUT_MS
    conn.setRequestProperty("User-Agent", "green-desktop/$APP_VERSION")
    // Java's HttpURLConnection defaults to "Accept: text/html, image/gif, image/jpeg, */*; q=0.2"
    // when no Accept header is set. Subscription servers that sniff Accept for "text/html" to
    // decide whether to serve a human-readable info page (e.g. 3x-ui's maybeServeSubPage) treat
    // that default as a browser request and never return the actual link list — override it.
    conn.setRequestProperty("Accept", "*/*")
    val raw = try { conn.inputStream.bufferedReader().readText() } finally { conn.disconnect() }
    val text = decodeSubscriptionBase64(raw) ?: raw
    text.lineSequence().map { it.trim() }.filter { it.startsWith("vless://") }.toList()
}

// java.util.Base64 — JVM-portable equivalent of Android's android.util.Base64.
// Unlike Android's decoder, java.util.Base64 is strict: it throws on embedded newlines
// (many subscriptions wrap the blob at ~76 chars) and on missing padding (many subscription
// generators emit unpadded base64/base64url). Strip whitespace and re-pad before decoding
// so both those common real-world shapes still decode.
fun decodeSubscriptionBase64(s: String): String? {
    val cleaned = s.trim().filterNot { it.isWhitespace() }
    if (cleaned.isEmpty()) return null
    val padded = cleaned + "=".repeat((4 - cleaned.length % 4) % 4)
    for (decoder in listOf(Base64.getDecoder(), Base64.getUrlDecoder())) {
        val decoded = runCatching { String(decoder.decode(padded), Charsets.UTF_8) }.getOrNull()
        if (decoded != null && decoded.contains("://")) return decoded
    }
    return null
}

fun subscriptionLinkName(link: String): String {
    val fragment = link.substringAfterLast("#", "")
    val decoded = runCatching { URLDecoder.decode(fragment, "UTF-8") }.getOrDefault(fragment)
    return decoded.ifBlank { link.substringAfter("@").substringBefore("?") }
}

fun isPlausibleVlessLink(link: String): Boolean =
    link.startsWith("vless://") && runCatching { URI(link).host != null }.getOrDefault(false)
