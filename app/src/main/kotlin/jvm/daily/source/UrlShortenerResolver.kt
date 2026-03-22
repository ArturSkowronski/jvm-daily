package jvm.daily.source

import java.net.HttpURLConnection
import java.net.URI

/**
 * Resolves shortened URLs to their final destination for known URL shortener domains.
 * Non-shortener URLs are returned unchanged without any HTTP call.
 */
class UrlShortenerResolver(
    private val knownShorteners: Set<String> = DEFAULT_SHORTENERS,
    private val httpHead: (String) -> String? = ::resolveViaHead,
) {

    fun resolve(url: String): String {
        val host = runCatching { URI(url).host }.getOrNull() ?: return url
        if (host !in knownShorteners) return url
        return httpHead(url) ?: url
    }

    companion object {
        val DEFAULT_SHORTENERS: Set<String> = setOf(
            "jb.gg",
            "t.co",
            "bit.ly",
            "buff.ly",
            "ow.ly",
            "tinyurl.com",
        )

        private fun resolveViaHead(url: String): String? {
            return try {
                val connection = URI(url).toURL().openConnection() as HttpURLConnection
                connection.requestMethod = "HEAD"
                connection.instanceFollowRedirects = true
                connection.connectTimeout = 5_000
                connection.readTimeout = 5_000
                connection.connect()
                val finalUrl = connection.url.toString()
                connection.disconnect()
                finalUrl.takeIf { it != url }
            } catch (_: Exception) {
                null
            }
        }
    }
}
