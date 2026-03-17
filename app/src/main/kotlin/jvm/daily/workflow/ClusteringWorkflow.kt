package jvm.daily.workflow

import jvm.daily.ai.LLMClient
import jvm.daily.model.ArticleCluster
import jvm.daily.model.EnrichmentOutcomeStatus
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
 * - Groups articles semantically using LLM (replaces rigid topic-pair matching)
 * - Creates cross-source thematic clusters (same topic from RSS + Bluesky + Reddit)
 * - Generates synthesis summaries for each cluster
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

        val now = clock.now()
        val yesterday = now.minus(1.days)
        val candidates = processedArticleRepository.findByIngestedAtRange(yesterday, now)
            .filter { it.outcomeStatus == EnrichmentOutcomeStatus.SUCCESS }

        // Move pure event-logistics social posts to unclustered (bottom of digest)
        val articles = candidates.filterNot { isEventLogisticsPost(it) }
        val skippedCount = candidates.size - articles.size
        if (skippedCount > 0) {
            println("[clustering] Moved $skippedCount event-logistics post(s) to unclustered")
        }

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

        // Replace any stale clusters from earlier runs in the same window
        clusterRepository.deleteByDateRange(yesterday, now)
        clusterRepository.saveAll(clusters)
        println("[clustering] Done. Saved ${clusters.size} clusters.")
    }

    private suspend fun clusterArticles(articles: List<ProcessedArticle>): List<ArticleCluster> {
        val groups = groupBySemantic(articles)
        return groups
            .map { (name, articleGroup) -> createCluster(articleGroup, name) }
            .sortedByDescending { it.totalEngagement }
    }

    /**
     * Asks the LLM to semantically group articles into thematic clusters.
     * Returns pairs of (cluster name, articles) so synthesis can use the intended name.
     * Falls back to a single group if the response cannot be parsed.
     */
    private suspend fun groupBySemantic(articles: List<ProcessedArticle>): List<Pair<String, List<ProcessedArticle>>> {
        val listing = articles.mapIndexed { i, a ->
            "[$i] ${a.originalTitle} | ${a.topics.take(3).joinToString(", ")}"
        }.joinToString("\n")

        val prompt = """
You are grouping JVM ecosystem news articles into thematic clusters for a daily digest.

Articles (index | title | topics):
$listing

Group these ${articles.size} articles into thematic clusters following these rules:
- Each cluster must cover exactly ONE specific topic, technology, or event (single responsibility)
- Create as many clusters as needed — prefer more smaller clusters over fewer big ones
- Every article must appear in exactly one cluster
- Articles that share a specific technology or event belong together (e.g. all Kotlin 2.3.20 articles in one cluster, all Quarkus articles in another)
- Only merge articles if they are genuinely about the same specific topic
- Use precise, specific cluster names (e.g. "Kotlin 2.3.20 Release", "GraalVM Native Image Performance", "Spring Security 6.5 Hardening")

**Releases rule** (important):
- Routine library releases (e.g. "Hibernate 7.3.0", "Ktor 3.1.2", "Micronaut 4.x.y", random OSS patch) must be grouped together into a single cluster named exactly: "Releases"
- Only give a release its own dedicated cluster if it is a significant milestone with real impact, for example:
  - Java GA release (any version)
  - Kotlin feature releases: Kotlin uses X.Y.Z versioning where Z=20 is a full feature release (e.g. 2.3.20 is major), Z=0 is a new minor (2.3.0), only Z=10/30/40 are incremental
  - Spring Boot major/minor (e.g. 3.5.0, 4.0.0)
  - A release that generated notable community discussion across multiple sources
- When in doubt, put the release in the "Releases" cluster

Output ONLY the groups in this exact format (no extra text):
GROUP: [cluster name]
INDICES: [comma-separated indices]
        """.trimIndent()

        val response = llmClient.chat(prompt)
        val groups = parseGroupResponse(response, articles)

        if (groups.isEmpty()) {
            println("[clustering] Warning: semantic grouping returned no groups, falling back to single cluster")
            return listOf(Pair("", articles))
        }
        return groups
    }

    private fun parseGroupResponse(
        response: String,
        articles: List<ProcessedArticle>,
    ): List<Pair<String, List<ProcessedArticle>>> {
        val groups = mutableListOf<Pair<String, List<ProcessedArticle>>>()
        val assignedIndices = mutableSetOf<Int>()

        var pendingName = ""
        var pendingIndices: List<Int> = emptyList()

        for (line in response.lines()) {
            when {
                line.startsWith("GROUP:") -> {
                    // flush previous group
                    if (pendingIndices.isNotEmpty()) {
                        val group = pendingIndices.mapNotNull { articles.getOrNull(it) }
                        if (group.isNotEmpty()) { groups.add(Pair(pendingName, group)); assignedIndices.addAll(pendingIndices) }
                    }
                    pendingName = line.substringAfter("GROUP:").trim()
                    pendingIndices = emptyList()
                }
                line.startsWith("INDICES:") -> {
                    pendingIndices = line.substringAfter("INDICES:")
                        .split(",")
                        .mapNotNull { it.trim().toIntOrNull() }
                }
            }
        }
        // flush last group
        if (pendingIndices.isNotEmpty()) {
            val group = pendingIndices.mapNotNull { articles.getOrNull(it) }
            if (group.isNotEmpty()) { groups.add(Pair(pendingName, group)); assignedIndices.addAll(pendingIndices) }
        }

        // Any article the LLM missed goes into a catch-all group
        val unassigned = articles.indices.filter { it !in assignedIndices }.map { articles[it] }
        if (unassigned.isNotEmpty()) groups.add(Pair("", unassigned))

        return groups.filter { it.second.isNotEmpty() }
    }

    private suspend fun createCluster(articles: List<ProcessedArticle>, clusterName: String = ""): ArticleCluster {
        val isReleasesCluster = clusterName.equals("Releases", ignoreCase = true)

        val articleSummaries = articles.take(10).joinToString("\n\n") { article ->
            """
            [${article.sourceType.uppercase()}] ${article.originalTitle}
            Author: ${article.author ?: "Unknown"}
            Entities: ${article.entities.take(5).joinToString(", ")}
            Summary: ${article.summary}
            """.trimIndent()
        }

        val prompt = if (isReleasesCluster) """
You are writing a brief roundup for a "Releases" section in a JVM ecosystem digest.

Releases in this section:
$articleSummaries
${if (articles.size > 10) "\n[... and ${articles.size - 10} more releases]" else ""}

Provide:
1. TITLE: Use exactly: "Releases"
2. SYNTHESIS: A brief roundup (60-80 words) listing the releases with one sentence each.
   Format as a flowing paragraph, not a list.

Format:
TITLE: Releases
SYNTHESIS: [your roundup]
        """.trimIndent() else """
$CLUSTERING_SYSTEM_PROMPT

You are analyzing a cluster of ${articles.size} related JVM ecosystem articles.

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
   - Highlights what different sources are saying
   - Mentions key technical details (versions, features, benchmarks)
   - Notes any controversy or differing opinions

Format:
TITLE: [your title]
SYNTHESIS: [your synthesis]
        """.trimIndent()

        val response = llmClient.chat(prompt)
        val synthesis = parseClusterResponse(response)

        return ArticleCluster(
            id = UUID.randomUUID().toString(),
            title = synthesis.title,
            summary = synthesis.summary,
            articles = articles.map { it.id },
            sources = articles.map { it.sourceType }.toSet().toList(),
            totalEngagement = articles.sumOf { it.engagementScore },
            createdAt = clock.now(),
        )
    }

    private fun parseClusterResponse(response: String): ClusterSynthesis {
        val lines = response.lines()
        val title = lines.find { it.startsWith("TITLE:") }
            ?.substringAfter("TITLE:")?.trim()
            ?: "Untitled Cluster"

        val synthesisStart = lines.indexOfFirst { it.startsWith("SYNTHESIS:") }
        val summary = if (synthesisStart != -1) {
            lines.drop(synthesisStart).joinToString("\n").substringAfter("SYNTHESIS:").trim()
        } else {
            "No synthesis available"
        }

        return ClusterSynthesis(title, summary)
    }

    private data class ClusterSynthesis(val title: String, val summary: String)

    /**
     * Bluesky/Twitter posts that are purely event logistics (speaker lineups, ticket sales,
     * "join us" countdowns, schedule announcements) stay unclustered at the bottom.
     * Technical announcements made at/about events (new releases, product news) are kept.
     * Defaults to false (keep in clustering) on any LLM error.
     */
    private suspend fun isEventLogisticsPost(article: ProcessedArticle): Boolean {
        if (article.sourceType !in SOCIAL_SOURCES) return false
        val prompt = """
Is this social media post purely about event logistics rather than actual technical/product news?

Post: ${article.originalTitle}
Summary: ${article.summary.take(300)}

Reply YES (move to bottom) for posts that are ONLY about:
- Speaker lineup or session schedule announcements
- Ticket sales, discounts, or registration reminders
- "Join us at [event]", countdown posts, venue/time info
- "We're attending / come find us at [event]"

Reply NO (keep in digest) for posts that contain:
- Actual product or technology announcements (new releases, features, APIs)
- Technical talks with specific content described
- News that happens to be announced at a conference

Answer with exactly YES or NO.
        """.trimIndent()
        return try {
            llmClient.chat(prompt).trim().uppercase().startsWith("YES")
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        private val SOCIAL_SOURCES = setOf("bluesky", "twitter")

        private const val CLUSTERING_SYSTEM_PROMPT = """
You are an expert JVM news curator. Your job is to analyze clusters of related
articles from multiple sources (RSS feeds, Bluesky, Reddit, blogs) and
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
