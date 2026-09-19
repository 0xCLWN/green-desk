package green.model

data class UpdateInfo(
    val tag: String,
    val downloadUrl: String,
    val sizeBytes: Long,
) {
    val sizeLabel: String get() = when {
        sizeBytes <= 0 -> ""
        sizeBytes < 1024 * 1024 -> "${"%.1f".format(sizeBytes / 1024.0)} KB"
        else -> "${"%.1f".format(sizeBytes / 1024.0 / 1024.0)} MB"
    }
}
