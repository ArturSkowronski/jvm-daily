package jvm.daily.source

import jvm.daily.config.BlueskyConfig
import jvm.daily.model.Article
import jvm.daily.model.CanonicalArticleId
import jvm.daily.model.FeedIngestResult
import jvm.daily.model.FeedIngestStatus
import jvm.daily.model.SourceFetchOutcome
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URI

/**
 * Bluesky source — follows JVM community leaders on Bluesky (AT Protocol).
 *
 * Uses the public Bluesky API (no auth required):
 *   GET https://public.api.bsky.app/xrpc/app.bsky.feed.getAuthorFeed?actor={handle}
 *
 * Many prominent Java/JVM community members have migrated from Twitter to Bluesky:
 * JDK developers, framework authors, conference speakers, etc.
 */
class BlueskySource(
    private val config: BlueskyConfig,
    private val clock: Clock = Clock.System,
) : Source {

    override val sourceType: String = "bluesky"

    override suspend fun fetch(): List<Article> = fetchOutcomes().flatMap { it.articles }

    override suspend fun fetchOutcomes(): List<SourceFetchOutcome> {
        return config.accounts.map { handle -> fetchAccount(handle) }
    }

    private fun fetchAccount(handle: String): SourceFetchOutcome {
        return try {
            val url = "https://public.api.bsky.app/xrpc/app.bsky.feed.getAuthorFeed?actor=$handle&limit=${config.limit}&filter=posts_no_replies"
            val json = httpGet(url)
            val feed = Json.parseToJsonElement(json).jsonObject["feed"]?.jsonArray ?: emptyList()

            val cutoff = clock.now().minus(kotlin.time.Duration.parse("${config.sinceDays}d"))

            val articles = feed.mapNotNull { item ->
                try {
                    val post = item.jsonObject["post"]!!.jsonObject
                    val record = post["record"]!!.jsonObject
                    val createdAt = record["\$type"]?.let {
                        record["createdAt"]?.jsonPrimitive?.contentOrNull
                    } ?: record["createdAt"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null

                    val postTime = Instant.parse(createdAt)
                    if (postTime < cutoff) return@mapNotNull null

                    parsePost(post, handle)
                } catch (_: Exception) { null }
            }

            SourceFetchOutcome(
                feed = FeedIngestResult(
                    sourceType = sourceType,
                    sourceId = handle,
                    status = FeedIngestStatus.SUCCESS,
                    fetchedCount = articles.size,
                ),
                articles = articles,
            )
        } catch (e: Exception) {
            SourceFetchOutcome(
                feed = FeedIngestResult(
                    sourceType = sourceType,
                    sourceId = handle,
                    status = FeedIngestStatus.FAILED,
                    fetchedCount = 0,
                    errors = listOf("${e.javaClass.simpleName}: ${e.message}"),
                ),
                articles = emptyList(),
            )
        }
    }

    private fun parsePost(post: JsonObject, handle: String): Article {
        val author = post["author"]!!.jsonObject
        val displayName = author["displayName"]?.jsonPrimitive?.contentOrNull ?: handle
        val authorHandle = author["handle"]?.jsonPrimitive?.contentOrNull ?: handle
        val record = post["record"]!!.jsonObject
        val text = record["text"]?.jsonPrimitive?.contentOrNull ?: ""
        val createdAt = record["createdAt"]?.jsonPrimitive?.contentOrNull ?: ""
        val uri = post["uri"]!!.jsonPrimitive.content
        val likeCount = post["likeCount"]?.jsonPrimitive?.intOrNull ?: 0
        val repostCount = post["repostCount"]?.jsonPrimitive?.intOrNull ?: 0
        val replyCount = post["replyCount"]?.jsonPrimitive?.intOrNull ?: 0

        // Extract embedded link if present
        val embed = post["embed"]?.jsonObject
        val externalUrl = embed?.get("external")?.jsonObject?.get("uri")?.jsonPrimitive?.contentOrNull
        val externalTitle = embed?.get("external")?.jsonObject?.get("title")?.jsonPrimitive?.contentOrNull

        // Build the Bluesky post URL
        val rkey = uri.substringAfterLast("/")
        val postUrl = "https://bsky.app/profile/$authorHandle/post/$rkey"

        val titleWithAuthor = "[$displayName] ${externalTitle ?: text}"

        val content = buildString {
            appendLine("$displayName (@$authorHandle) on Bluesky:")
            appendLine()
            appendLine(text)
            appendLine()
            appendLine("Likes: $likeCount | Reposts: $repostCount | Replies: $replyCount")
            if (externalUrl != null) {
                appendLine("Link: $externalUrl")
                if (externalTitle != null) appendLine("Title: $externalTitle")
            }
        }

        val canonicalId = CanonicalArticleId.from(sourceType, handle, titleWithAuthor, postUrl)

        return Article(
            id = canonicalId,
            title = titleWithAuthor.take(200),
            content = content,
            sourceType = sourceType,
            sourceId = "$handle/$rkey",
            url = externalUrl ?: postUrl,
            author = "$displayName (@$authorHandle)",
            ingestedAt = clock.now(),
        )
    }

    private fun httpGet(url: String): String {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", "JVM-Daily/1.0")
        connection.setRequestProperty("Accept", "application/json")
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        if (connection.responseCode !in 200..299) {
            error("HTTP ${connection.responseCode} for $url")
        }
        return connection.inputStream.use { it.bufferedReader().readText() }
    }
}
