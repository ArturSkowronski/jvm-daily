package jvm.daily.source

import jvm.daily.ai.LLMClient
import jvm.daily.model.Article
import jvm.daily.model.CanonicalArticleId
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URI

/**
 * Detects roundup/digest articles and splits them into individual sub-articles using LLM.
 *
 * Roundups (e.g., InfoQ "Java News Roundup") contain 5-10 sub-topics in a single entry.
 * Splitting them lets each sub-topic get its own enrichment, clustering, and display.
 */
class RoundupSplitter(
    private val llmClient: LLMClient,
) {
    suspend fun splitIfRoundup(article: Article): List<Article> {
        if (!looksLikeRoundup(article)) return listOf(article)

        // Fetch full article content (RSS only has summary)
        val fullContent = fetchFullContent(article.url) ?: article.content
        val enrichedArticle = article.copy(content = fullContent)

        return try {
            val subArticles = extractSubArticles(enrichedArticle)
            if (subArticles.size > 1) {
                println("[roundup-split] ${article.title} → ${subArticles.size} sub-articles")
                subArticles
            } else {
                listOf(article)
            }
        } catch (e: Exception) {
            println("[roundup-split] Failed to split '${article.title}': ${e.message}")
            listOf(article)
        }
    }

    private suspend fun extractSubArticles(article: Article): List<Article> {
        val prompt = buildSplitPrompt(article)
        val response = llmClient.chat(prompt)
        val items = parseResponse(response)

        return items.map { item ->
            Article(
                id = CanonicalArticleId.from(
                    namespace = "rss_roundup",
                    sourceId = article.sourceId,
                    title = item.title,
                    url = item.url,
                ),
                title = item.title,
                content = item.summary,
                sourceType = article.sourceType,
                sourceId = article.sourceId,
                url = item.url ?: article.url,
                author = article.author,
                ingestedAt = article.ingestedAt,
            )
        }
    }

    companion object {
        private val ROUNDUP_KEYWORDS = listOf(
            "roundup", "round-up", "news round", "this week in",
            "weekly digest", "weekly review", "news recap",
        )

        fun looksLikeRoundup(article: Article): Boolean {
            val title = article.title.lowercase()
            return ROUNDUP_KEYWORDS.any { title.contains(it) }
        }

        internal fun fetchFullContent(url: String?): String? {
            if (url.isNullOrBlank()) return null
            return try {
                val connection = URI(url).toURL().openConnection() as HttpURLConnection
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; JVM-Daily/1.0)")
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                connection.instanceFollowRedirects = true
                if (connection.responseCode !in 200..299) return null
                val html = connection.inputStream.use { it.bufferedReader().readText() }
                extractArticleSections(html).take(12_000)
            } catch (_: Exception) { null }
        }

        /**
         * Extract structured content from roundup HTML.
         * Splits on h4 headings and extracts heading + body text + links for each section.
         */
        internal fun extractArticleSections(html: String): String {
            val cleaned = html
                .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
                .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")

            val sections = cleaned.split(Regex("<h4[^>]*>"))
            val result = StringBuilder()
            for (section in sections.drop(1)) { // skip preamble before first h4
                val headingEnd = section.indexOf("</h4>")
                if (headingEnd < 0) continue
                val heading = section.substring(0, headingEnd)
                    .replace(Regex("<[^>]+>"), "").trim()
                if (heading.isBlank() || heading.length > 100) continue

                val body = section.substring(headingEnd + 5)
                    .substringBefore("<h4") // stop at next heading
                    .take(3000)

                // Extract links
                val links = Regex("""href="(https?://[^"]+)"""").findAll(body)
                    .map { it.groupValues[1] }.toList()

                val bodyText = body
                    .replace(Regex("<[^>]+>"), " ")
                    .replace(Regex("&[a-zA-Z]+;"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()

                if (bodyText.length > 30) {
                    result.appendLine("## $heading")
                    if (links.isNotEmpty()) result.appendLine("Links: ${links.take(3).joinToString(", ")}")
                    result.appendLine(bodyText.take(500))
                    result.appendLine()
                }
            }
            return result.toString().ifBlank {
                // Fallback: simple strip
                html.replace(Regex("<[^>]+>"), " ")
                    .replace(Regex("&[a-zA-Z]+;"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
            }
        }

        internal fun buildSplitPrompt(article: Article): String = """
You are analyzing a news roundup article that covers multiple topics.
Extract each distinct topic/project as a separate item.

Title: ${article.title}
Content:
${article.content.take(10_000)}

For each distinct topic covered in this roundup, extract:
- title: A specific, descriptive title for this sub-topic (e.g., "Spring Boot 4.0 Milestone 3 Released")
- url: The primary URL linked for this topic (if any). Return null if no specific URL.
- summary: A 2-3 sentence factual summary of this topic.

Return ONLY a JSON array, no markdown, no explanation:
[{"title":"...","url":"...","summary":"..."},...]

Rules:
- Each item must be about a DISTINCT project, release, or topic
- Do NOT include the roundup article itself as an item
- Titles should be specific (include version numbers, project names)
- If a topic has no distinct URL, set url to null
- Minimum 2 items, otherwise this is not a roundup
""".trimIndent()

        internal fun parseResponse(response: String): List<SubArticle> {
            val cleaned = response.trim()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```")
                .trim()
            return try {
                val array = Json.parseToJsonElement(cleaned).jsonArray
                array.mapNotNull { elem ->
                    val obj = elem.jsonObject
                    val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val url = obj["url"]?.jsonPrimitive?.contentOrNull
                    val summary = obj["summary"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    SubArticle(title, url, summary)
                }
            } catch (_: Exception) { emptyList() }
        }
    }

    internal data class SubArticle(val title: String, val url: String?, val summary: String)
}
