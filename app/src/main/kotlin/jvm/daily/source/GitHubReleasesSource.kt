package jvm.daily.source

import jvm.daily.config.GitHubReleasesConfig
import jvm.daily.model.Article
import jvm.daily.model.CanonicalArticleId
import jvm.daily.model.FeedIngestResult
import jvm.daily.model.FeedIngestStatus
import jvm.daily.model.SourceFetchOutcome
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URI

/**
 * Tracks releases of popular JVM libraries and frameworks via GitHub Releases API.
 *
 * For each configured repo, fetches recent releases (last N days) with:
 * - Release name, tag, body (changelog)
 * - Pre-release flag
 *
 * Env: GITHUB_TOKEN (optional, raises rate limit)
 */
class GitHubReleasesSource(
    private val config: GitHubReleasesConfig,
    private val clock: Clock = Clock.System,
) : Source {

    override val sourceType: String = "github_releases"

    override suspend fun fetch(): List<Article> = fetchOutcomes().flatMap { it.articles }

    override suspend fun fetchOutcomes(): List<SourceFetchOutcome> {
        return config.repos.map { repo -> fetchRepo(repo) }
    }

    private fun fetchRepo(repo: String): SourceFetchOutcome {
        return try {
            val cutoff = clock.now()
                .minus(config.sinceDays, DateTimeUnit.DAY, TimeZone.UTC)

            val url = "https://api.github.com/repos/$repo/releases?per_page=10"
            val json = httpGet(url)
            val releases = Json.parseToJsonElement(json).jsonArray

            val articles = releases.mapNotNull { release ->
                try {
                    val rel = release.jsonObject
                    val publishedAt = rel["published_at"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val publishedInstant = Instant.parse(publishedAt)
                    if (publishedInstant < cutoff) return@mapNotNull null
                    parseRelease(rel, repo)
                } catch (_: Exception) { null }
            }

            SourceFetchOutcome(
                feed = FeedIngestResult(
                    sourceType = sourceType,
                    sourceId = repo,
                    status = FeedIngestStatus.SUCCESS,
                    fetchedCount = articles.size,
                ),
                articles = articles,
            )
        } catch (e: Exception) {
            SourceFetchOutcome(
                feed = FeedIngestResult(
                    sourceType = sourceType,
                    sourceId = repo,
                    status = FeedIngestStatus.FAILED,
                    fetchedCount = 0,
                    errors = listOf("${e.javaClass.simpleName}: ${e.message}"),
                ),
                articles = emptyList(),
            )
        }
    }

    private fun parseRelease(release: JsonObject, repo: String): Article {
        val tagName = release["tag_name"]!!.jsonPrimitive.content
        val name = release["name"]?.jsonPrimitive?.contentOrNull ?: tagName
        val body = release["body"]?.jsonPrimitive?.contentOrNull ?: ""
        val htmlUrl = release["html_url"]!!.jsonPrimitive.content
        val prerelease = release["prerelease"]?.jsonPrimitive?.booleanOrNull ?: false
        val author = release["author"]?.jsonObject?.get("login")?.jsonPrimitive?.contentOrNull ?: ""

        val prefix = if (prerelease) "[PRE-RELEASE]" else "[RELEASE]"
        val title = "$prefix $repo $tagName"

        val content = buildString {
            appendLine("Release: $repo $tagName")
            if (name != tagName) appendLine("Name: $name")
            if (prerelease) appendLine("Pre-release: yes")
            appendLine()
            if (body.isNotBlank()) {
                appendLine("--- Changelog ---")
                appendLine(body.take(4000))
            }
        }

        val canonicalId = CanonicalArticleId.from(sourceType, repo, "$repo/$tagName", htmlUrl)

        return Article(
            id = canonicalId,
            title = title,
            content = content,
            sourceType = sourceType,
            sourceId = "$repo/$tagName",
            url = htmlUrl,
            author = author,
            ingestedAt = clock.now(),
        )
    }

    private fun httpGet(url: String): String {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", "JVM-Daily/1.0")
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        val token = System.getenv("GITHUB_TOKEN")
        if (!token.isNullOrBlank()) {
            connection.setRequestProperty("Authorization", "Bearer $token")
        }
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        if (connection.responseCode !in 200..299) {
            error("HTTP ${connection.responseCode} for $url")
        }
        return connection.inputStream.use { it.bufferedReader().readText() }
    }
}
