package jvm.daily.model

object CanonicalArticleId {
    fun from(
        namespace: String,
        sourceId: String,
        title: String,
        url: String? = null,
        sourceNativeId: String? = null,
    ): String {
        val key = normalizeUrl(url)
            ?: normalizeToken(sourceNativeId)
            ?: normalizeToken(title)
            ?: normalizeToken(sourceId)
            ?: "unknown"
        return "${normalizeNamespace(namespace)}:$key"
    }

    private fun normalizeNamespace(raw: String): String =
        raw.trim().lowercase().replace(Regex("[^a-z0-9_\\-]"), "_")

    private fun normalizeUrl(raw: String?): String? {
        val trimmed = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val withoutFragment = trimmed.substringBefore('#')
        val normalizedSlash = withoutFragment.trimEnd('/')
        return normalizeToken(normalizedSlash)
    }

    private fun normalizeToken(raw: String?): String? {
        val cleaned = raw?.trim()?.lowercase()?.replace(Regex("\\s+"), " ") ?: return null
        return cleaned
            .replace(Regex("[^a-z0-9._:/\\- ]"), "")
            .replace(' ', '-')
            .takeIf { it.isNotBlank() }
    }
}
