package jvm.daily.workflow

import jvm.daily.model.Article
import jvm.daily.ai.LLMClient
import jvm.daily.model.ProcessedArticle
import jvm.daily.storage.ArticleRepository
import jvm.daily.storage.ProcessedArticleRepository
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.days

/**
 * Enrichment Workflow (Stage 1 of processing pipeline).
 *
 * Inspired by Latent Space AI News enrichment phase:
 * - Normalizes article titles for deduplication
 * - Generates LLM summaries (max 200 words)
 * - Extracts Named Entities (JDK versions, frameworks, companies, JEPs)
 * - Assigns topic tags for clustering
 * - Calculates engagement scores
 *
 * This prepares raw articles for thematic clustering.
 */
class EnrichmentWorkflow(
    private val rawArticleRepository: ArticleRepository,
    private val processedArticleRepository: ProcessedArticleRepository,
    private val llmClient: LLMClient,
    private val clock: Clock = Clock.System,
) : Workflow {

    override val name: String = "enrichment"

    override suspend fun execute() {
        println("[enrichment] Starting enrichment workflow")

        val unprocessedIds = processedArticleRepository.findUnprocessedRawArticles(
            since = clock.now().minus(7.days)
        )

        if (unprocessedIds.isEmpty()) {
            println("[enrichment] No new articles to process")
            return
        }

        println("[enrichment] Found ${unprocessedIds.size} unprocessed articles")

        val rawArticles = rawArticleRepository.findAll()
            .filter { it.id in unprocessedIds }

        var processedCount = 0
        var errorCount = 0

        for (article in rawArticles) {
            try {
                val processed = enrichArticle(article)
                processedArticleRepository.save(processed)
                processedCount++

                if (processedCount % 10 == 0) {
                    println("[enrichment] Processed $processedCount/${rawArticles.size}")
                }
            } catch (e: Exception) {
                println("[enrichment] Failed to process ${article.id}: ${e.message}")
                errorCount++
            }
        }

        println("[enrichment] Done. Processed: $processedCount, Errors: $errorCount, Total in DB: ${processedArticleRepository.count()}")
    }

    private suspend fun enrichArticle(article: Article): ProcessedArticle {
        val prompt = "$ENRICHMENT_SYSTEM_PROMPT\n\n${buildEnrichmentPrompt(article)}"
        val response = llmClient.chat(prompt)
        val enriched = parseEnrichmentResponse(response)

        return ProcessedArticle(
            id = article.id,
            originalTitle = article.title,
            normalizedTitle = normalizeTitle(article.title),
            summary = enriched.summary,
            originalContent = article.content,
            sourceType = article.sourceType,
            sourceId = article.sourceId,
            url = article.url,
            author = article.author,
            publishedAt = article.ingestedAt, // RSS doesn't have published_at, use ingested
            ingestedAt = article.ingestedAt,
            processedAt = clock.now(),
            entities = enriched.entities,
            topics = enriched.topics,
            engagementScore = calculateEngagementScore(article),
        )
    }

    private fun buildEnrichmentPrompt(article: Article): String {
        return """
        Analyze this JVM ecosystem article and extract key information:

        Title: ${article.title}
        Author: ${article.author ?: "Unknown"}
        Source: ${article.sourceId}
        Content (first 2000 chars): ${article.content.take(2000)}

        Provide:
        1. SUMMARY: Concise 100-150 word summary capturing main points
        2. ENTITIES: Extract JVM-related entities (format: comma-separated list)
           - JDK/Java versions (e.g., "JDK 21", "Java 25")
           - Frameworks (e.g., "Spring Boot", "Quarkus", "Micronaut")
           - Companies (e.g., "Oracle", "JetBrains", "Red Hat")
           - JEP numbers (e.g., "JEP 444")
           - Technologies (e.g., "Virtual Threads", "GraalVM", "Kotlin Coroutines")
        3. TOPICS: Assign 1-3 topic tags from this list:
           - language-updates, framework-releases, performance, tooling,
             developer-experience, community, conferences, tutorials, security,
             cloud-native, microservices, testing, debugging, migration

        Format response as:
        SUMMARY: [your summary here]
        ENTITIES: [entity1, entity2, entity3, ...]
        TOPICS: [topic1, topic2, topic3]
        """.trimIndent()
    }

    private fun parseEnrichmentResponse(response: String): EnrichedData {
        val lines = response.lines()
        val summary = lines.find { it.startsWith("SUMMARY:") }
            ?.substringAfter("SUMMARY:")?.trim()
            ?: "No summary available"

        val entities = lines.find { it.startsWith("ENTITIES:") }
            ?.substringAfter("ENTITIES:")
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()

        val topics = lines.find { it.startsWith("TOPICS:") }
            ?.substringAfter("TOPICS:")
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()

        return EnrichedData(summary, entities, topics)
    }

    private fun normalizeTitle(title: String): String {
        return title
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    private fun calculateEngagementScore(article: Article): Double {
        // Simple heuristic based on content length and source
        var score = 50.0

        // Longer articles get higher scores (capped at +20)
        score += minOf(article.content.length / 500.0, 20.0)

        // Author presence gives +10
        if (article.author != null) {
            score += 10.0
        }

        // Comments available gives +10
        if (article.comments != null) {
            score += 10.0
        }

        // URL presence gives +10
        if (article.url != null) {
            score += 10.0
        }

        return score.coerceIn(0.0, 100.0)
    }

    private data class EnrichedData(
        val summary: String,
        val entities: List<String>,
        val topics: List<String>,
    )

    companion object {
        private const val ENRICHMENT_SYSTEM_PROMPT = """
You are an expert JVM ecosystem analyst. Your job is to analyze JVM-related
articles (Java, Kotlin, Scala, Groovy, Clojure, GraalVM, Spring, Quarkus, etc.)
and extract structured information for news aggregation.

Focus on:
- Technical accuracy
- Identifying key JVM technologies and versions
- Categorizing content by topic
- Creating concise, informative summaries

Always be precise with version numbers and framework names.
"""
    }
}
