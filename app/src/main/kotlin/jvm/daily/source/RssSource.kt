package jvm.daily.source

import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import jvm.daily.config.RssFeedConfig
import jvm.daily.model.Article
import kotlinx.datetime.Clock
import java.net.HttpURLConnection
import java.net.URI

class RssSource(
    private val feeds: List<RssFeedConfig>,
    private val clock: Clock = Clock.System,
) : Source {

    override val sourceType: String = "rss"

    override suspend fun fetch(): List<Article> {
        return feeds.flatMap { feedConfig -> fetchFeed(feedConfig) }
    }

    private fun fetchFeed(feedConfig: RssFeedConfig): List<Article> {
        return try {
            val url = URI(feedConfig.url).toURL()
            val inputStream = if (url.protocol == "http" || url.protocol == "https") {
                val connection = url.openConnection() as HttpURLConnection
                connection.setRequestProperty("User-Agent", "JVM-Daily/1.0")
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                connection.inputStream
            } else {
                url.openStream()
            }

            val input = SyndFeedInput()
            val feed = input.build(XmlReader(inputStream))

            feed.entries.mapNotNull { entry ->
                val link = entry.link ?: return@mapNotNull null
                val title = entry.title ?: return@mapNotNull null

                val content = entry.description?.value
                    ?: entry.contents.firstOrNull()?.value
                    ?: ""

                Article(
                    id = "rss:${entry.uri ?: link}",
                    title = title,
                    content = content,
                    sourceType = sourceType,
                    sourceId = feedConfig.url,
                    url = link,
                    author = entry.author?.takeIf { it.isNotBlank() },
                    comments = entry.comments,
                    ingestedAt = clock.now(),
                )
            }
        } catch (e: Exception) {
            println("[rss] Failed to fetch feed ${feedConfig.url}: ${e.message}")
            emptyList()
        }
    }
}
