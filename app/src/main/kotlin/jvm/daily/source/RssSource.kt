package jvm.daily.source

import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import jvm.daily.config.RssFeedConfig
import jvm.daily.model.Article
import jvm.daily.model.FeedIngestResult
import jvm.daily.model.FeedIngestStatus
import jvm.daily.model.SourceFetchOutcome
import kotlinx.datetime.Clock
import java.net.HttpURLConnection
import java.net.URI

class RssSource(
    private val feeds: List<RssFeedConfig>,
    private val clock: Clock = Clock.System,
) : Source {

    override val sourceType: String = "rss"

    override suspend fun fetch(): List<Article> {
        return fetchOutcomes().flatMap { it.articles }
    }

    override suspend fun fetchOutcomes(): List<SourceFetchOutcome> {
        return feeds.map { feedConfig -> fetchFeedWithRetry(feedConfig) }
    }

    private fun fetchFeedWithRetry(feedConfig: RssFeedConfig): SourceFetchOutcome {
        val errors = mutableListOf<String>()
        repeat(MAX_ATTEMPTS) { idx ->
            val attempt = idx + 1
            val result = fetchFeed(feedConfig, attempt)
            if (result.feed.status != FeedIngestStatus.FAILED) {
                return result
            }
            errors += result.feed.errors
        }
        return SourceFetchOutcome(
            feed = FeedIngestResult(
                sourceType = sourceType,
                sourceId = feedConfig.url,
                status = FeedIngestStatus.FAILED,
                fetchedCount = 0,
                errors = errors.ifEmpty { listOf("Failed after $MAX_ATTEMPTS attempts") },
            ),
            articles = emptyList(),
        )
    }

    private fun fetchFeed(feedConfig: RssFeedConfig, attempt: Int): SourceFetchOutcome {
        return try {
            val url = URI(feedConfig.url).toURL()
            val inputStream = if (url.protocol == "http" || url.protocol == "https") {
                val connection = url.openConnection() as HttpURLConnection
                connection.setRequestProperty("User-Agent", "JVM-Daily/1.0")
                connection.connectTimeout = CONNECTION_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.inputStream
            } else {
                url.openStream()
            }

            inputStream.use { stream ->
                val feed = SyndFeedInput().build(XmlReader(stream))
                var skippedEntries = 0
                val articles = feed.entries.mapNotNull { entry ->
                    val link = entry.link ?: run {
                        skippedEntries++
                        return@mapNotNull null
                    }
                    val title = entry.title ?: run {
                        skippedEntries++
                        return@mapNotNull null
                    }

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

                val status = if (skippedEntries > 0) FeedIngestStatus.PARTIAL_SUCCESS else FeedIngestStatus.SUCCESS
                val warnings = if (skippedEntries > 0) listOf("Skipped $skippedEntries malformed entries") else emptyList()

                SourceFetchOutcome(
                    feed = FeedIngestResult(
                        sourceType = sourceType,
                        sourceId = feedConfig.url,
                        status = status,
                        fetchedCount = articles.size,
                        errors = warnings,
                    ),
                    articles = articles,
                )
            }
        } catch (e: Exception) {
            println("[rss] Attempt $attempt/$MAX_ATTEMPTS failed for ${feedConfig.url}: ${e.message}")
            SourceFetchOutcome(
                feed = FeedIngestResult(
                    sourceType = sourceType,
                    sourceId = feedConfig.url,
                    status = FeedIngestStatus.FAILED,
                    fetchedCount = 0,
                    errors = listOf(e.message ?: "Unknown RSS fetch error"),
                ),
                articles = emptyList(),
            )
        }
    }

    companion object {
        private const val MAX_ATTEMPTS = 2
        private const val CONNECTION_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 15_000
    }
}
