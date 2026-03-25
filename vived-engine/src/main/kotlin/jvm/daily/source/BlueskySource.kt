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
import java.net.URLEncoder

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
    private val urlShortenerResolver: UrlShortenerResolver = UrlShortenerResolver(),
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

                    var article = parsePost(post, handle)
                    // If no external URL found, check if author replied to themselves with a link
                    if (article.url?.startsWith("https://bsky.app") == true) {
                        val atUri = post["uri"]!!.jsonPrimitive.content
                        val authorHandle = post["author"]!!.jsonObject["handle"]?.jsonPrimitive?.contentOrNull ?: handle
                        val displayName = post["author"]!!.jsonObject["displayName"]?.jsonPrimitive?.contentOrNull ?: handle
                        val threadLink = fetchThreadLink(atUri, authorHandle)
                        if (threadLink != null) {
                            val newTitle = threadLink.title?.let { "[$displayName] $it" }
                            article = article.copy(
                                url = threadLink.url,
                                title = (newTitle ?: article.title).take(200),
                            )
                        }
                    }
                    article
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

        // Extract embedded link if present (handles external card, recordWithMedia, and facet links)
        val embed = post["embed"]?.jsonObject
        val externalUrl = embed?.get("external")?.jsonObject?.get("uri")?.jsonPrimitive?.contentOrNull
            ?: embed?.get("media")?.jsonObject?.get("external")?.jsonObject?.get("uri")?.jsonPrimitive?.contentOrNull
            ?: record["facets"]?.jsonArray?.firstNotNullOfOrNull { facet ->
                facet.jsonObject["features"]?.jsonArray?.firstNotNullOfOrNull { feature ->
                    val f = feature.jsonObject
                    if (f["\$type"]?.jsonPrimitive?.contentOrNull == "app.bsky.richtext.facet#link")
                        f["uri"]?.jsonPrimitive?.contentOrNull
                    else null
                }
            }
        val embedDesc = embed?.get("external")?.jsonObject?.get("description")?.jsonPrimitive?.contentOrNull
            ?: embed?.get("media")?.jsonObject?.get("external")?.jsonObject?.get("description")?.jsonPrimitive?.contentOrNull
        val embedTitle = embed?.get("external")?.jsonObject?.get("title")?.jsonPrimitive?.contentOrNull
            ?: embed?.get("media")?.jsonObject?.get("external")?.jsonObject?.get("title")?.jsonPrimitive?.contentOrNull
        val resolvedExternalUrl = externalUrl?.let { urlShortenerResolver.resolve(it) }
        // For facet links (no embed card), fetch page meta to get real title + description
        val pageMeta = if (embedTitle == null && resolvedExternalUrl != null) fetchPageMeta(resolvedExternalUrl) else null
        val externalTitle = embedTitle ?: pageMeta?.title
        val externalDesc  = embedDesc  ?: pageMeta?.description

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
            if (resolvedExternalUrl != null) {
                appendLine("Link: $resolvedExternalUrl")
                if (externalTitle != null) appendLine("Title: $externalTitle")
                if (externalDesc != null) appendLine("Description: $externalDesc")
            }
        }

        val canonicalId = CanonicalArticleId.from(sourceType, handle, titleWithAuthor, postUrl)

        return Article(
            id = canonicalId,
            title = titleWithAuthor.take(200),
            content = content,
            sourceType = sourceType,
            sourceId = "$handle/$rkey",
            url = resolvedExternalUrl ?: postUrl,
            author = "$displayName (@$authorHandle)",
            ingestedAt = clock.now(),
        )
    }

    private data class PageMeta(val title: String?, val description: String?)

    /** Fetches HTML title + og/meta description from a page. Best-effort, returns nulls on error. */
    private fun fetchPageMeta(url: String): PageMeta {
        return try {
            val connection = URI(url).toURL().openConnection() as HttpURLConnection
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 JVM-Daily/1.0")
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.instanceFollowRedirects = true
            if (connection.responseCode !in 200..299) return PageMeta(null, null)
            val html = connection.inputStream.use { it.bufferedReader().readText() }

            val rawTitle = Regex("<title[^>]*>([^<]+)</title>", RegexOption.IGNORE_CASE)
                .find(html)?.groupValues?.get(1)?.trim()
            val title = rawTitle?.let { cleanPageTitle(it) }

            val description = Regex("""<meta\s+(?:name|property)=["'](?:description|og:description)["']\s+content=["']([^"']{10,})["']""", RegexOption.IGNORE_CASE)
                .find(html)?.groupValues?.get(1)?.trim()
                ?: Regex("""<meta\s+content=["']([^"']{10,})["']\s+(?:name|property)=["'](?:description|og:description)["']""", RegexOption.IGNORE_CASE)
                    .find(html)?.groupValues?.get(1)?.trim()

            PageMeta(title, description?.let { decodeHtmlEntities(it) }?.take(500))
        } catch (_: Exception) { PageMeta(null, null) }
    }

    private fun cleanPageTitle(raw: String): String? {
        val decoded = decodeHtmlEntities(raw.trim())
        var clean = decoded.split(" · ").first().trim()
        if (clean.length < 15) clean = decoded
        val pipeParts = clean.split(" | ")
        if (pipeParts.size > 1) clean = pipeParts.dropLast(1).joinToString(" | ").trim()
        return clean.takeIf { it.isNotBlank() }
    }

    private fun decodeHtmlEntities(s: String) = s
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'")

    private data class ThreadLink(val url: String, val title: String?)

    /** Fetches the post thread and returns a URL+title from a direct author self-reply, or null. */
    private fun fetchThreadLink(atUri: String, authorHandle: String): ThreadLink? {
        return try {
            val encoded = URLEncoder.encode(atUri, "UTF-8")
            val url = "https://public.api.bsky.app/xrpc/app.bsky.feed.getPostThread?uri=$encoded&depth=3"
            val json = httpGet(url)
            val thread = Json.parseToJsonElement(json).jsonObject["thread"]?.jsonObject ?: return null
            val replies = thread["replies"]?.jsonArray ?: return null

            for (reply in replies) {
                val replyPost = reply.jsonObject["post"]?.jsonObject ?: continue
                val replyHandle = replyPost["author"]?.jsonObject?.get("handle")?.jsonPrimitive?.contentOrNull ?: continue
                if (replyHandle != authorHandle) continue

                val embed = replyPost["embed"]?.jsonObject
                val externalUrl = embed?.get("external")?.jsonObject?.get("uri")?.jsonPrimitive?.contentOrNull
                    ?: embed?.get("media")?.jsonObject?.get("external")?.jsonObject?.get("uri")?.jsonPrimitive?.contentOrNull
                val externalTitle = embed?.get("external")?.jsonObject?.get("title")?.jsonPrimitive?.contentOrNull
                    ?: embed?.get("media")?.jsonObject?.get("external")?.jsonObject?.get("title")?.jsonPrimitive?.contentOrNull
                if (externalUrl != null) return ThreadLink(urlShortenerResolver.resolve(externalUrl), externalTitle)

                val record = replyPost["record"]?.jsonObject ?: continue
                val facetUrl = record["facets"]?.jsonArray?.firstNotNullOfOrNull { facet ->
                    facet.jsonObject["features"]?.jsonArray?.firstNotNullOfOrNull { feature ->
                        val f = feature.jsonObject
                        if (f["\$type"]?.jsonPrimitive?.contentOrNull == "app.bsky.richtext.facet#link")
                            f["uri"]?.jsonPrimitive?.contentOrNull
                        else null
                    }
                }
                if (facetUrl != null) {
                    val resolvedFacetUrl = urlShortenerResolver.resolve(facetUrl)
                    return ThreadLink(resolvedFacetUrl, fetchPageMeta(resolvedFacetUrl).title)
                }
            }
            null
        } catch (_: Exception) { null }
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
