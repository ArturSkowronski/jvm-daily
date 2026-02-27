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
        val enriched = when (val result = EnrichmentContract.parse(response, article.content.isBlank())) {
            is EnrichmentContract.ParseResult.Success -> result
            is EnrichmentContract.ParseResult.Failure ->
                error("${result.code}: ${result.message}")
        }

        if (enriched.warnings.isNotEmpty()) {
            println("[enrichment] ${article.id}: ${enriched.warnings.joinToString(" | ")}")
        }

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
        Analyze this JVM ecosystem article and return STRICT JSON only.

        Title: ${article.title}
        Author: ${article.author ?: "Unknown"}
        Source Type: ${article.sourceType}
        Source ID: ${article.sourceId}
        URL: ${article.url ?: "N/A"}
        Content: ${article.content}

        Return JSON with this exact shape:
        {
          "summary": "minimum 40 words",
          "entities": ["entity1", "entity2"],
          "topics": ["topic1", "topic2"]
        }
        """.trimIndent()
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
