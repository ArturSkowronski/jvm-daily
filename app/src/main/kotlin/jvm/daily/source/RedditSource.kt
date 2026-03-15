package jvm.daily.source

import jvm.daily.config.RedditSourceConfig
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
 * Reddit source — fetches posts + full comment threads from subreddits.
 *
 * Uses Reddit's public JSON API (no auth required):
 *   GET https://www.reddit.com/r/{subreddit}/new.json?limit=25
 *   GET https://www.reddit.com/r/{subreddit}/comments/{id}.json
 *
 * Each post becomes an Article with:
 *   - content: post selftext + formatted comment thread
 *   - comments: structured discussion with authors and scores
 *   - author: post author
 */
class RedditSource(
    private val configs: List<RedditSourceConfig>,
    private val clock: Clock = Clock.System,
) : Source {

    override val sourceType: String = "reddit"

    override suspend fun fetch(): List<Article> {
        return fetchOutcomes().flatMap { it.articles }
    }

    override suspend fun fetchOutcomes(): List<SourceFetchOutcome> {
        return configs.map { config -> fetchSubreddit(config) }
    }

    private fun fetchSubreddit(config: RedditSourceConfig): SourceFetchOutcome {
        val subreddit = config.subreddit
        return try {
            val listingJson = httpGet("https://www.reddit.com/r/$subreddit/new.json?limit=${config.limit}")
            val listing = Json.parseToJsonElement(listingJson)
            val posts = listing.jsonObject["data"]?.jsonObject?.get("children")?.jsonArray ?: emptyList()

            val articles = posts.mapNotNull { child ->
                try {
                    parsePost(child.jsonObject["data"]!!.jsonObject, subreddit)
                } catch (e: Exception) {
                    null // skip malformed posts
                }
            }

            SourceFetchOutcome(
                feed = FeedIngestResult(
                    sourceType = sourceType,
                    sourceId = "r/$subreddit",
                    status = FeedIngestStatus.SUCCESS,
                    fetchedCount = articles.size,
                ),
                articles = articles,
            )
        } catch (e: Exception) {
            SourceFetchOutcome(
                feed = FeedIngestResult(
                    sourceType = sourceType,
                    sourceId = "r/$subreddit",
                    status = FeedIngestStatus.FAILED,
                    fetchedCount = 0,
                    errors = listOf("${e.javaClass.simpleName}: ${e.message}"),
                ),
                articles = emptyList(),
            )
        }
    }

    private fun parsePost(post: JsonObject, subreddit: String): Article {
        val postId = post["id"]!!.jsonPrimitive.content
        val title = post["title"]!!.jsonPrimitive.content
        val selftext = post["selftext"]?.jsonPrimitive?.contentOrNull ?: ""
        val author = post["author"]?.jsonPrimitive?.contentOrNull ?: "[deleted]"
        val permalink = post["permalink"]!!.jsonPrimitive.content
        val url = "https://www.reddit.com$permalink"
        val score = post["score"]?.jsonPrimitive?.intOrNull ?: 0
        val numComments = post["num_comments"]?.jsonPrimitive?.intOrNull ?: 0
        val createdUtc = post["created_utc"]?.jsonPrimitive?.doubleOrNull

        // Fetch comments for this post
        val comments = fetchComments(subreddit, postId)

        val content = buildString {
            if (selftext.isNotBlank()) {
                appendLine(selftext)
                appendLine()
            }
            appendLine("Score: $score | Comments: $numComments")
            appendLine()
            if (comments.isNotEmpty()) {
                appendLine("--- Discussion ---")
                for (comment in comments) {
                    appendLine(comment)
                }
            }
        }

        val commentsFormatted = buildString {
            for (comment in comments) {
                appendLine(comment)
            }
        }.trimEnd()

        val canonicalId = CanonicalArticleId.from(sourceType, "r/$subreddit/$postId", title, url)

        return Article(
            id = canonicalId,
            title = title,
            content = content,
            sourceType = sourceType,
            sourceId = "r/$subreddit/$postId",
            url = url,
            author = author,
            comments = commentsFormatted.ifEmpty { null },
            ingestedAt = clock.now(),
        )
    }

    private fun fetchComments(subreddit: String, postId: String): List<String> {
        return try {
            val json = httpGet("https://www.reddit.com/r/$subreddit/comments/$postId.json?limit=50&depth=3")
            val elements = Json.parseToJsonElement(json).jsonArray
            if (elements.size < 2) return emptyList()

            val commentListing = elements[1].jsonObject["data"]?.jsonObject?.get("children")?.jsonArray
                ?: return emptyList()

            commentListing.mapNotNull { child ->
                parseComment(child.jsonObject, depth = 0)
            }.flatten()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseComment(thing: JsonObject, depth: Int): List<String>? {
        if (thing["kind"]?.jsonPrimitive?.contentOrNull != "t1") return null
        val data = thing["data"]?.jsonObject ?: return null

        val author = data["author"]?.jsonPrimitive?.contentOrNull ?: "[deleted]"
        val body = data["body"]?.jsonPrimitive?.contentOrNull ?: return null
        val score = data["score"]?.jsonPrimitive?.intOrNull ?: 0

        val indent = "  ".repeat(depth)
        val lines = mutableListOf("$indent[$author | $score pts] $body")

        // Parse replies recursively
        val replies = data["replies"]
        if (replies is JsonObject) {
            val replyChildren = replies.jsonObject["data"]?.jsonObject?.get("children")?.jsonArray
            replyChildren?.forEach { child ->
                parseComment(child.jsonObject, depth + 1)?.let { lines.addAll(it) }
            }
        }

        return lines
    }

    private fun httpGet(url: String): String {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.instanceFollowRedirects = true

        if (connection.responseCode !in 200..299) {
            error("HTTP ${connection.responseCode} for $url")
        }

        return connection.inputStream.use { it.bufferedReader().readText() }
    }

    companion object {
        private const val USER_AGENT = "JVM-Daily/1.0 (by /u/jvm-daily-bot)"
        private const val TIMEOUT_MS = 15_000
    }
}
