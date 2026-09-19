package green

import green.model.VlessKey

fun loadBakedKeys(): List<VlessKey> {
    val text = runCatching {
        Thread.currentThread().contextClassLoader
            .getResourceAsStream("baked_keys.txt")
            ?.bufferedReader()?.readText()
    }.getOrNull() ?: return emptyList()

    return text.lines()
        .map { it.trim() }
        .filter { it.startsWith("vless://") }
        .map { uri ->
            val name = uri.substringAfterLast("#", "").ifBlank { "Key" }
            VlessKey(
                id = "baked:${uri.hashCode().toUInt()}",
                name = name,
                uri = uri,
            )
        }
}
