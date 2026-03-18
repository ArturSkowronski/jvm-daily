package jvm.daily.workflow

import jvm.daily.model.Article
import jvm.daily.ai.LLMClient
import jvm.daily.model.EnrichmentOutcomeStatus
import jvm.daily.model.ProcessedArticle
import jvm.daily.storage.ArticleRepository
import jvm.daily.storage.ProcessedArticleRepository
import kotlinx.coroutines.delay
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
    private val replayRawArticleIds: Set<String>? = null,
    private val clock: Clock = Clock.System,
    private val retryBackoffMs: Long = RETRY_BACKOFF_MS,
    private val sinceDays: Int = 1,
) : Workflow {

    override val name: String = "enrichment"

    override suspend fun execute() {
        println("[enrichment] Starting enrichment workflow")

        val targetIds = replayRawArticleIds?.toList() ?: processedArticleRepository.findUnprocessedRawArticles(
            since = clock.now().minus(sinceDays.days)
        )

        if (targetIds.isEmpty()) {
            val message = if (replayRawArticleIds == null) {
                "No new articles to process"
            } else {
                "No replay candidates to process"
            }
            println("[enrichment] $message")
            return
        }

        if (replayRawArticleIds == null) {
            println("[enrichment] Found ${targetIds.size} unprocessed articles")
        } else {
            println("[enrichment] Replaying ${targetIds.size} targeted failed article(s)")
        }

        val rawArticlesById = rawArticleRepository.findAll().associateBy { it.id }
        val rawArticles = targetIds.mapNotNull { rawArticlesById[it] }
        val missingCount = targetIds.size - rawArticles.size
        if (missingCount > 0) {
            println("[enrichment] Skipping $missingCount target(s) missing in raw article storage")
        }

        var processedCount = 0
        var failedCount = 0
        var warningCount = 0

        for (article in rawArticles) {
            val processed = enrichArticle(article)
            processedArticleRepository.save(processed)

            if (processed.outcomeStatus == EnrichmentOutcomeStatus.SUCCESS) {
                processedCount++
            } else {
                failedCount++
            }
            if (processed.warnings.isNotEmpty()) {
                warningCount++
            }

            if ((processedCount + failedCount) % 10 == 0) {
                println("[enrichment] Processed ${processedCount + failedCount}/${rawArticles.size}")
            }
        }

        val stageStatus = if (failedCount > 0 || warningCount > 0) "SUCCESS_WITH_WARNINGS" else "SUCCESS"
        println(
            "[enrichment] Done. status=$stageStatus, Processed=$processedCount, Failed=$failedCount, " +
                "Warnings=$warningCount, Total in DB=${processedArticleRepository.count()}"
        )
    }

    private suspend fun enrichArticle(article: Article): ProcessedArticle {
        if (article.sourceType in RELEVANCE_GATED_SOURCES && !isRelevant(article)) {
            println("[enrichment] ${article.id}: SKIPPED (not relevant for digest)")
            return skippedArticle(article, reason = "relevance_gate")
        }

        val prompt = "$ENRICHMENT_SYSTEM_PROMPT\n\n${buildEnrichmentPrompt(article)}"
        val warnings = mutableListOf<String>()
        var attempt = 0

        while (attempt < MAX_ATTEMPTS) {
            attempt++
            val response = try {
                llmClient.chat(prompt)
            } catch (e: Exception) {
                if (attempt < MAX_ATTEMPTS) {
                    delay(retryBackoffMs)
                    continue
                }
                return failedArticle(
                    article = article,
                    reason = "TRANSPORT: ${e.message ?: "LLM transport failure"}",
                    attempt = attempt,
                )
            }

            when (val result = EnrichmentContract.parse(response, article.content.isBlank())) {
                is EnrichmentContract.ParseResult.Success -> {
                    warnings += result.warnings
                    if (warnings.isNotEmpty()) {
                        println("[enrichment] ${article.id}: ${warnings.joinToString(" | ")}")
                    }

                    return ProcessedArticle(
                        id = article.id,
                        originalTitle = article.title,
                        normalizedTitle = normalizeTitle(article.title),
                        summary = result.summary,
                        originalContent = article.content,
                        sourceType = article.sourceType,
                        sourceId = article.sourceId,
                        url = article.url,
                        author = article.author,
                        publishedAt = article.ingestedAt,
                        ingestedAt = article.ingestedAt,
                        processedAt = clock.now(),
                        entities = result.entities,
                        topics = result.topics,
                        engagementScore = calculateEngagementScore(article),
                        outcomeStatus = EnrichmentOutcomeStatus.SUCCESS,
                        attemptCount = attempt,
                        warnings = warnings,
                    )
                }
                is EnrichmentContract.ParseResult.Failure -> {
                    return failedArticle(
                        article = article,
                        reason = "${result.code}: ${result.message}",
                        attempt = attempt,
                    )
                }
            }
        }

        return failedArticle(
            article = article,
            reason = "TRANSPORT: unknown enrichment failure",
            attempt = attempt.coerceAtLeast(1),
        )
    }

    private fun buildEnrichmentPrompt(article: Article): String {
        val commentsSection = if (!article.comments.isNullOrBlank()) {
            "\nDiscussion:\n${article.comments!!.take(3000)}"
        } else ""

        return """
        Analyze this article. Return STRICT JSON only — no markdown fences, no explanation.

        Title: ${article.title}
        Author: ${article.author ?: "Unknown"}
        Source: ${article.sourceType} / ${article.sourceId}
        URL: ${article.url ?: "N/A"}

        Content:
        ${article.content.take(4000)}
        $commentsSection

        Return JSON:
        {
          "summary": "Dense, fact-packed summary (50-120 words). Include specific versions, APIs, numbers. If this is a discussion thread, summarize the key arguments and consensus. Never restate the title. Never use filler phrases.",
          "entities": ["Exact tech names with versions, e.g. JDK 25, Spring Boot 4.0, GraalVM 23, JEP 511"],
          "topics": ["2-4 lowercase topic tags, e.g. virtual-threads, spring-security, kotlin-coroutines, graalvm-native"]
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

    /**
     * Asks the LLM if this article is worth including in the daily digest.
     * Cheap pre-filter: runs one small LLM call before full enrichment.
     * Defaults to true (include) on any error to avoid false negatives.
     */
    private suspend fun isRelevant(article: Article): Boolean {
        val prompt = """
Is this content worth featuring in a daily digest for experienced JVM/backend developers?

Title: ${article.title}
Content preview: ${article.content.take(600)}

Reply YES if the content covers:
- JVM language features, releases, or roadmap (Java, Kotlin, Scala, Groovy)
- JVM frameworks, libraries, or tools (Spring, Quarkus, Micronaut, Gradle, etc.)
- JVM performance, security vulnerabilities, or architecture
- OpenJDK development, JEPs, runtime internals
- Developer tooling, build systems, or CI/CD relevant to JVM ecosystem
- Technical community news with clear relevance to backend/JVM engineers

Reply NO if the content is:
- Politics, social commentary, or cultural criticism unrelated to software
- General world news, sports, entertainment
- Personal opinions on non-technical topics
- Minor housekeeping (CI failures, dependency bumps, copyright updates)
- Very narrow implementation detail with no broad interest

Answer with exactly YES or NO.
        """.trimIndent()
        return try {
            val response = llmClient.chat(prompt)
            !response.trim().uppercase().startsWith("NO")
        } catch (e: Exception) {
            true // default to including on LLM error
        }
    }

    private fun skippedArticle(article: Article, reason: String = "relevance_gate"): ProcessedArticle = ProcessedArticle(
        id = article.id,
        originalTitle = article.title,
        normalizedTitle = normalizeTitle(article.title),
        summary = "[SKIPPED]",
        originalContent = article.content,
        sourceType = article.sourceType,
        sourceId = article.sourceId,
        url = article.url,
        author = article.author,
        publishedAt = article.ingestedAt,
        ingestedAt = article.ingestedAt,
        processedAt = clock.now(),
        outcomeStatus = EnrichmentOutcomeStatus.SKIPPED,
        failureReason = reason,
    )

    private fun failedArticle(article: Article, reason: String, attempt: Int): ProcessedArticle {
        println("[enrichment] Failed to process ${article.id}: $reason")
        return ProcessedArticle(
            id = article.id,
            originalTitle = article.title,
            normalizedTitle = normalizeTitle(article.title),
            summary = "[FAILED]",
            originalContent = article.content,
            sourceType = article.sourceType,
            sourceId = article.sourceId,
            url = article.url,
            author = article.author,
            publishedAt = article.ingestedAt,
            ingestedAt = article.ingestedAt,
            processedAt = clock.now(),
            entities = emptyList(),
            topics = emptyList(),
            engagementScore = 0.0,
            outcomeStatus = EnrichmentOutcomeStatus.FAILED,
            failureReason = reason,
            lastAttemptAt = clock.now(),
            attemptCount = attempt,
        )
    }

    companion object {
        private const val MAX_ATTEMPTS = 3
        private const val RETRY_BACKOFF_MS = 2_000L

        /** Source types where a cheap relevance check runs before full enrichment. */
        private val RELEVANCE_GATED_SOURCES = setOf("openjdk_mail", "bluesky", "rss")
        private const val ENRICHMENT_SYSTEM_PROMPT = """
You are a JVM ecosystem news analyst writing for experienced engineers.

Your summaries must be DENSE and SPECIFIC — every sentence must contain facts the reader cannot guess from the title alone. Extract:
- Exact version numbers, JEP numbers, CVE IDs
- Concrete technical changes (what API changed, what got deprecated, what's new)
- Performance numbers, benchmarks, migration steps if mentioned
- Key community opinions or controversies from comments/discussion
- Breaking changes, deprecations, compatibility notes

NEVER write filler like "users are encouraged to update" or "promises new features."
NEVER restate the title. Start with the most important technical fact.

If the article is a Reddit discussion, summarize the TOP ARGUMENTS from the thread — what do people agree/disagree on, what's the consensus, what interesting insights were shared.
"""
    }
}
