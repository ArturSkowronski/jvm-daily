package jvm.daily.workflow

import jvm.daily.ai.LLMClient
import jvm.daily.model.ArticleCluster
import jvm.daily.model.ProcessedArticle
import jvm.daily.storage.ClusterRepository
import jvm.daily.storage.ProcessedArticleRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.UUID
import kotlin.time.Duration.Companion.days

/**
 * Clustering Workflow (Stage 2 of processing pipeline).
 *
 * Inspired by Latent Space AI News thematic clustering:
 * - Groups articles by shared entities and topics
 * - Creates cross-source thematic clusters (same topic from RSS + Twitter + Reddit)
 * - Generates synthesis summaries for each cluster
 * - Maximum 8 categories per day
 * - Prioritizes by total engagement score
 *
 * Output: ArticleClusters ready for final compilation.
 */
class ClusteringWorkflow(
    private val processedArticleRepository: ProcessedArticleRepository,
    private val clusterRepository: ClusterRepository,
    private val llmClient: LLMClient,
    private val clock: Clock = Clock.System,
) : Workflow {

    override val name: String = "clustering"

    override suspend fun execute() {
        println("[clustering] Starting clustering workflow")

        // Get articles from last 24 hours
        val now = clock.now()
        val yesterday = now.minus(1.days)
        val articles = processedArticleRepository.findByIngestedAtRange(yesterday, now)

        if (articles.isEmpty()) {
            println("[clustering] No processed articles found in last 24h")
            return
        }

        println("[clustering] Found ${articles.size} processed articles")

        val clusters = clusterArticles(articles)

        println("[clustering] Created ${clusters.size} thematic clusters")
        clusters.forEachIndexed { i, cluster ->
            println("  ${i + 1}. ${cluster.title} (${cluster.articles.size} articles, ${cluster.sources.size} sources)")
        }

        clusterRepository.saveAll(clusters)
        println("[clustering] Done. Saved ${clusters.size} clusters.")
    }

    private suspend fun clusterArticles(articles: List<ProcessedArticle>): List<ArticleCluster> {
        // Step 1: Group by topic similarity
        val topicGroups = groupByTopics(articles)

        // Step 2: Use LLM to refine clusters and create synthesis
        val clusters = mutableListOf<ArticleCluster>()

        for ((topics, articleGroup) in topicGroups.entries.take(8)) {
            val cluster = createCluster(articleGroup, topics)
            clusters.add(cluster)
        }

        // Sort by engagement score descending
        return clusters.sortedByDescending { it.totalEngagement }
    }

    private fun groupByTopics(articles: List<ProcessedArticle>): Map<Set<String>, List<ProcessedArticle>> {
        val groups = mutableMapOf<Set<String>, MutableList<ProcessedArticle>>()

        for (article in articles) {
            val primaryTopics = article.topics.take(2).toSet()
            if (primaryTopics.isEmpty()) continue

            groups.getOrPut(primaryTopics) { mutableListOf() }.add(article)
        }

        // Merge small groups (< 3 articles) into "misc"
        val miscGroup = groups.filter { it.value.size < 3 }
            .flatMap { it.value }

        val finalGroups = groups
            .filter { it.value.size >= 3 }
            .toMutableMap()

        if (miscGroup.isNotEmpty()) {
            finalGroups[setOf("misc")] = miscGroup.toMutableList()
        }

        return finalGroups
    }

    private suspend fun createCluster(
        articles: List<ProcessedArticle>,
        topics: Set<String>,
    ): ArticleCluster {
        val prompt = "$CLUSTERING_SYSTEM_PROMPT\n\n${buildClusteringPrompt(articles, topics)}"
        val response = llmClient.chat(prompt)
        val synthesis = parseClusterResponse(response)

        val uniqueSources = articles.map { it.sourceType }.toSet()
        val totalEngagement = articles.sumOf { it.engagementScore }

        return ArticleCluster(
            id = UUID.randomUUID().toString(),
            title = synthesis.title,
            summary = synthesis.summary,
            articles = articles.map { it.id },
            sources = uniqueSources.toList(),
            totalEngagement = totalEngagement,
            createdAt = clock.now(),
        )
    }

    private fun buildClusteringPrompt(articles: List<ProcessedArticle>, topics: Set<String>): String {
        val articleSummaries = articles.take(10).joinToString("\n\n") { article ->
            """
            [${article.sourceType.uppercase()}] ${article.originalTitle}
            Author: ${article.author ?: "Unknown"}
            Source: ${article.sourceId.substringAfterLast("/")}
            Entities: ${article.entities.take(5).joinToString(", ")}
            Summary: ${article.summary}
            """.trimIndent()
        }

        return """
        You are analyzing a cluster of ${articles.size} related JVM ecosystem articles.
        Topics: ${topics.joinToString(", ")}

        Articles in this cluster:
        $articleSummaries

        ${if (articles.size > 10) "\n[... and ${articles.size - 10} more articles]" else ""}

        Provide:
        1. TITLE: A catchy, specific title for this cluster (5-10 words max)
           Examples: "Spring Boot 4.0 RC1 Sparks Migration Discussion"
                    "Kotlin Context Parameters Land in 2.3 Preview"
                    "Virtual Threads Performance Gains Confirmed in Production"

        2. SYNTHESIS: Cross-source synthesis (150-200 words) that:
           - Identifies the main story or theme
           - Highlights what different sources are saying (e.g., "Twitter developers are excited...", "Reddit discussions focus on...")
           - Mentions key technical details (versions, features, benchmarks)
           - Notes any controversy or differing opinions
           - Includes direct quotes if impactful

        Format:
        TITLE: [your title]
        SYNTHESIS: [your synthesis]
        """.trimIndent()
    }

    private fun parseClusterResponse(response: String): ClusterSynthesis {
        val lines = response.lines()
        val title = lines.find { it.startsWith("TITLE:") }
            ?.substringAfter("TITLE:")?.trim()
            ?: "Untitled Cluster"

        val synthesisStart = lines.indexOfFirst { it.startsWith("SYNTHESIS:") }
        val summary = if (synthesisStart != -1) {
            lines.drop(synthesisStart)
                .joinToString("\n")
                .substringAfter("SYNTHESIS:")
                .trim()
        } else {
            "No synthesis available"
        }

        return ClusterSynthesis(title, summary)
    }

    private data class ClusterSynthesis(
        val title: String,
        val summary: String,
    )

    companion object {
        private const val CLUSTERING_SYSTEM_PROMPT = """
You are an expert JVM news curator. Your job is to analyze clusters of related
articles from multiple sources (RSS feeds, Twitter, Reddit, Discord, blogs) and
create compelling synthesis summaries.

Your synthesis should:
- Identify the core story or development
- Synthesize insights across sources
- Highlight technical details that matter to experienced JVM developers
- Note community sentiment and reactions
- Be concise but informative (150-200 words)

Write for an audience of experienced JVM engineers who want signal, not noise.
"""
    }
}
