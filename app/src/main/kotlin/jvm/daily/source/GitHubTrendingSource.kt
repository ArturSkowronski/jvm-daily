package jvm.daily.source

import jvm.daily.config.GitHubTrendingConfig
import jvm.daily.model.Article
import jvm.daily.model.CanonicalArticleId
import jvm.daily.model.FeedIngestResult
import jvm.daily.model.FeedIngestStatus
import jvm.daily.model.SourceFetchOutcome
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URI

/**
 * Discovers trending/new JVM repositories via GitHub Search API.
 *
 * Searches for repos created in the last N days with minimum stars,
 * grouped by language (Java, Kotlin, Scala).
 *
 * Env: GITHUB_TOKEN (optional, raises rate limit from 10 to 30 req/min)
 */
class GitHubTrendingSource(
    private val config: GitHubTrendingConfig,
    private val clock: Clock = Clock.System,
) : Source {

    override val sourceType: String = "github_trending"

    override suspend fun fetch(): List<Article> = fetchOutcomes().flatMap { it.articles }

    override suspend fun fetchOutcomes(): List<SourceFetchOutcome> {
        return config.languages.map { language -> fetchLanguage(language) }
    }

    private fun fetchLanguage(language: String): SourceFetchOutcome {
        return try {
            val sinceDate = clock.now()
                .minus(config.sinceDays, DateTimeUnit.DAY, TimeZone.UTC)
                .toLocalDateTime(TimeZone.UTC).date

            val query = java.net.URLEncoder.encode("language:$language created:>=$sinceDate stars:>=${config.minStars}", "UTF-8")
            val url = "https://api.github.com/search/repositories?q=$query&sort=stars&order=desc&per_page=${config.limit}"
            val json = httpGet(url)
            val result = Json.parseToJsonElement(json).jsonObject
            val items = result["items"]?.jsonArray ?: emptyList()

            val articles = items.mapNotNull { item ->
                try { parseRepo(item.jsonObject, language) } catch (_: Exception) { null }
            }

            SourceFetchOutcome(
                feed = FeedIngestResult(
                    sourceType = sourceType,
                    sourceId = "trending/$language",
                    status = FeedIngestStatus.SUCCESS,
                    fetchedCount = articles.size,
                ),
                articles = articles,
            )
        } catch (e: Exception) {
            SourceFetchOutcome(
                feed = FeedIngestResult(
                    sourceType = sourceType,
                    sourceId = "trending/$language",
                    status = FeedIngestStatus.FAILED,
                    fetchedCount = 0,
                    errors = listOf("${e.javaClass.simpleName}: ${e.message}"),
                ),
                articles = emptyList(),
            )
        }
    }

    private fun parseRepo(repo: JsonObject, language: String): Article {
        val fullName = repo["full_name"]!!.jsonPrimitive.content
        val name = repo["name"]!!.jsonPrimitive.content
        val description = repo["description"]?.jsonPrimitive?.contentOrNull ?: ""
        val htmlUrl = repo["html_url"]!!.jsonPrimitive.content
        val stars = repo["stargazers_count"]?.jsonPrimitive?.intOrNull ?: 0
        val forks = repo["forks_count"]?.jsonPrimitive?.intOrNull ?: 0
        val owner = repo["owner"]?.jsonObject?.get("login")?.jsonPrimitive?.contentOrNull ?: ""
        val topics = repo["topics"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
        val createdAt = repo["created_at"]?.jsonPrimitive?.contentOrNull ?: ""
        val readme = fetchReadmeExcerpt(fullName)

        val content = buildString {
            appendLine("New $language repository: $fullName")
            appendLine("Stars: $stars | Forks: $forks | Created: $createdAt")
            if (description.isNotBlank()) appendLine("Description: $description")
            if (topics.isNotEmpty()) appendLine("Topics: ${topics.joinToString(", ")}")
            if (readme.isNotBlank()) {
                appendLine()
                appendLine("--- README excerpt ---")
                appendLine(readme)
            }
        }

        val canonicalId = CanonicalArticleId.from(sourceType, fullName, name, htmlUrl)

        return Article(
            id = canonicalId,
            title = "[NEW] $fullName — $description".take(200),
            content = content,
            sourceType = sourceType,
            sourceId = "trending/$language/$fullName",
            url = htmlUrl,
            author = owner,
            ingestedAt = clock.now(),
        )
    }

    private fun fetchReadmeExcerpt(fullName: String): String {
        return try {
            val url = "https://api.github.com/repos/$fullName/readme"
            val json = httpGet(url)
            val content = Json.parseToJsonElement(json).jsonObject["content"]?.jsonPrimitive?.contentOrNull ?: return ""
            // README is base64 encoded
            val decoded = java.util.Base64.getMimeDecoder().decode(content).decodeToString()
            decoded.take(2000)
        } catch (_: Exception) { "" }
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
