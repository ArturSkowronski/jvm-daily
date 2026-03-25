package jvm.daily.workflow

import jvm.daily.ai.LLMClient
import jvm.daily.config.DomainProfile
import jvm.daily.model.ArticleCluster
import jvm.daily.model.EnrichmentOutcomeStatus
import jvm.daily.model.ProcessedArticle
import jvm.daily.storage.ClusterRepository
import jvm.daily.storage.ProcessedArticleRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.UUID
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

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
    private val sinceHours: Int = 24,
    private val domainProfile: DomainProfile? = null,
) : Workflow {

    private val domain: DomainProfile get() = domainProfile ?: DomainProfile.default()

    override val name: String = "clustering"

    override suspend fun execute() {
        println("[clustering] Starting clustering workflow")

        val now = clock.now()
        val yesterday = now.minus(sinceHours.hours)
        val candidates = processedArticleRepository.findByIngestedAtRange(yesterday, now)
            .filter { it.outcomeStatus == EnrichmentOutcomeStatus.SUCCESS }

        // Move pure event-logistics social posts to unclustered (bottom of digest)
        val articles = candidates.filterNot { isEventLogisticsPost(it) }
        val skippedCount = candidates.size - articles.size
        if (skippedCount > 0) {
            println("[clustering] Moved $skippedCount event-logistics post(s) to unclustered")
        }

        val deduped = deduplicateByUrl(articles)
        val dupCount = articles.size - deduped.size
        if (dupCount > 0) {
            println("[clustering] Removed $dupCount duplicate(s) by URL (social reposts of RSS articles)")
        }

        if (deduped.isEmpty()) {
            println("[clustering] No processed articles found in last 24h")
            return
        }

        println("[clustering] Found ${deduped.size} processed articles")

        val clusters = clusterArticles(deduped)

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
        val clusters = groups
            .map { group ->
                // Force release type for clusters with only GitHub trending/release sources
                val effectiveType = if (group.type == "topic" && group.articles.all {
                    it.sourceType in setOf("github_trending", "github_release")
                }) "release" else group.type

                Pair(createCluster(group.articles, group.name, clusterType = effectiveType), group.isMajor)
            }
        // Sort: MAJOR first → topic clusters by engagement → release clusters by engagement last
        val major    = clusters.filter { (_, m) -> m }.sortedByDescending { (c, _) -> c.totalEngagement }.map { it.first }
        val releases = clusters.filter { (c, m) -> !m && c.type == "release" }.sortedByDescending { (c, _) -> c.totalEngagement }.map { it.first }
        val normal   = clusters.filter { (c, m) -> !m && c.type != "release" }
                               .sortedByDescending { (c, _) -> c.totalEngagement }.map { it.first }
        return major + normal + releases
    }

    /**
     * Asks the LLM to semantically group articles into thematic clusters.
     * Returns GroupResult list so synthesis and sorting can use them.
     * Falls back to a single group if the response cannot be parsed.
     */
    private suspend fun groupBySemantic(articles: List<ProcessedArticle>): List<GroupResult> {
        val listing = articles.mapIndexed { i, a ->
            "[$i] ${a.originalTitle} | ${a.topics.take(3).joinToString(", ")}"
        }.joinToString("\n")

        val exampleClusters = domain.clustering.exampleClusters.joinToString("\", \"", "\"", "\"")
        val majorReleaseRules = domain.clustering.majorReleaseRules.joinToString("\n") { "  - $it" }
        val prompt = """
You are grouping ${domain.description} articles into thematic clusters for a daily digest.

Articles (index | title | topics):
$listing

Group these ${articles.size} articles into thematic clusters following these rules:
- Each cluster must cover exactly ONE specific topic, technology, or event (single responsibility)
- Create as many clusters as needed — prefer more smaller clusters over fewer big ones
- Every article must appear in exactly one cluster
- Articles that share a specific technology or event belong together
- Only merge articles if they are genuinely about the same specific topic
- Use precise, specific cluster names (e.g. $exampleClusters)

**Releases rule** (important):
- Every distinct software release must be its own cluster, even routine patch releases
- Name the cluster after the specific release
- Multiple articles about the same release belong together (e.g. the GitHub Release entry + a blog post about it)
- Do NOT group unrelated releases into a single catch-all cluster

**Priority rule**:
- Add `MAJOR: YES` for clusters covering a milestone that every reader should see today:
$majorReleaseRules
- Omit the MAJOR line (or write `MAJOR: NO`) for everything else

**Cluster type rule** (pick exactly one per cluster):
- `TYPE: release` — routine version release (patch, minor maintenance). Shown as compact card with bullets.
  Examples: "Hibernate 7.3.0", "Spring Boot 4.0.4", "Ktor 3.1.2", "Gradle Actions v6"
- `TYPE: announcement` — significant new project, product launch, or major milestone worth reading in full.
  Examples: "JetBrains Central Platform Launch", "JDK 26 GA Release", "GraalVM Goes Fully Open Source"
- `TYPE: topic` — discussion, tutorial, analysis, opinion, or anything else. This is the default.
  Examples: "Virtual Threads in Production", "Quarkus Testing Strategies", "Kotlin Multiplatform Debate"

Rule of thumb: if it has a version number and is routine → `release`. If it's a big deal people should read about → `announcement`. If neither → `topic`.

Output ONLY the groups in this exact format (no extra text):
GROUP: [cluster name]
MAJOR: YES   ← only when applicable
TYPE: release | announcement | topic
INDICES: [comma-separated indices]
        """.trimIndent()

        val response = llmClient.chat(prompt)
        val groups = parseGroupResponse(response, articles)

        if (groups.isEmpty()) {
            println("[clustering] Warning: semantic grouping returned no groups, falling back to single cluster")
            println("[clustering] LLM response (first 500 chars): ${response.take(500)}")
            return listOf(GroupResult("", articles, false, "topic"))
        }
        return groups
    }

    private fun parseGroupResponse(
        response: String,
        articles: List<ProcessedArticle>,
    ): List<GroupResult> {
        val groups = mutableListOf<GroupResult>()
        val assignedIndices = mutableSetOf<Int>()

        var pendingName = ""
        var pendingMajor = false
        var pendingType = "topic"
        var pendingIndices: List<Int> = emptyList()

        for (rawLine in response.lines()) {
            // Strip leading whitespace and markdown bold markers (**TEXT:** → TEXT:)
            val line = rawLine.trim().removePrefix("**").let {
                if (it.contains("**")) it.substringBefore("**") + it.substringAfter("**") else it
            }
            when {
                line.startsWith("GROUP:") -> {
                    // flush previous group
                    if (pendingIndices.isNotEmpty()) {
                        val group = pendingIndices.mapNotNull { articles.getOrNull(it) }
                        if (group.isNotEmpty()) {
                            groups.add(GroupResult(pendingName, group, pendingMajor, pendingType))
                            assignedIndices.addAll(pendingIndices)
                        }
                    }
                    pendingName = line.substringAfter("GROUP:").trim()
                    pendingMajor = false
                    pendingType = "topic"
                    pendingIndices = emptyList()
                }
                line.startsWith("MAJOR:") -> {
                    pendingMajor = line.substringAfter("MAJOR:").trim().uppercase().startsWith("YES")
                }
                line.startsWith("TYPE:") -> {
                    val raw = line.substringAfter("TYPE:").trim().lowercase()
                    pendingType = when {
                        raw.startsWith("release") -> "release"
                        raw.startsWith("announcement") -> "announcement"
                        else -> "topic"
                    }
                }
                // Backward compat: old RELEASE: YES format
                line.startsWith("RELEASE:") -> {
                    if (line.substringAfter("RELEASE:").trim().uppercase().startsWith("YES")) {
                        pendingType = "release"
                    }
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
            if (group.isNotEmpty()) {
                groups.add(GroupResult(pendingName, group, pendingMajor, pendingType))
                assignedIndices.addAll(pendingIndices)
            }
        }

        // Any article the LLM missed goes into a catch-all group
        val unassigned = articles.indices.filter { it !in assignedIndices }.map { articles[it] }
        if (unassigned.isNotEmpty()) groups.add(GroupResult("", unassigned, false, "topic"))

        return groups.filter { it.articles.isNotEmpty() }
    }

    private suspend fun createCluster(
        articles: List<ProcessedArticle>,
        clusterName: String = "",
        clusterType: String = "topic",
    ): ArticleCluster {
        val articleSummaries = articles.take(10).joinToString("\n\n") { article ->
            """
            [${article.sourceType.uppercase()}] ${article.originalTitle}
            Author: ${article.author ?: "Unknown"}
            Entities: ${article.entities.take(5).joinToString(", ")}
            Summary: ${article.summary}
            """.trimIndent()
        }

        val prompt = if (clusterType == "release") """
You are writing a concise release summary for a ${domain.description} digest.

Release: $clusterName
Articles:
$articleSummaries
${if (articles.size > 10) "\n[... and ${articles.size - 10} more releases]" else ""}

Provide:
1. TITLE: The specific release name (e.g. "Spring Boot 4.1.0-M3", "Releases")
2. Up to 5 BULLET lines, each 1–2 sentences, covering the key highlights:
   - New features or APIs
   - Breaking changes or deprecations
   - Performance or compatibility improvements
   - Notable community reaction

Format:
TITLE: [title]
BULLET: [highlight 1]
BULLET: [highlight 2]
    """.trimIndent() else """
${domain.clustering.systemPrompt}

You are analyzing a cluster of ${articles.size} related ${domain.description} articles.

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
            type = clusterType,
            bullets = synthesis.bullets,
        )
    }

    private fun parseClusterResponse(response: String): ClusterSynthesis {
        val lines = response.lines().map { it.trim().let { l ->
            l.removePrefix("**").let { if (it.contains("**")) it.substringBefore("**") + it.substringAfter("**") else it }
        } }
        val title = lines.find { it.startsWith("TITLE:") }
            ?.substringAfter("TITLE:")?.trim()
            ?: "Untitled Cluster"

        val bullets = lines.filter { it.startsWith("BULLET:") }
            .map { it.substringAfter("BULLET:").trim() }
            .take(5)

        val synthesisStart = lines.indexOfFirst { it.startsWith("SYNTHESIS:") }
        val summary = if (synthesisStart != -1) {
            lines.drop(synthesisStart).joinToString("\n").substringAfter("SYNTHESIS:").trim()
        } else {
            ""
        }

        return ClusterSynthesis(title, summary, bullets)
    }

    private data class ClusterSynthesis(val title: String, val summary: String, val bullets: List<String> = emptyList())

    private data class GroupResult(
        val name: String,
        val articles: List<ProcessedArticle>,
        val isMajor: Boolean,
        val type: String, // "topic" | "release" | "announcement"
    )

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

    /**
     * Removes duplicate articles that share the same URL.
     * When a Bluesky/Reddit post embeds a link to an RSS article, both end up with identical URLs.
     * We keep the highest-priority source type per URL (canonical article beats social repost).
     */
    private fun deduplicateByUrl(articles: List<ProcessedArticle>): List<ProcessedArticle> {
        val byUrl = articles
            .filter { !it.url.isNullOrBlank() }
            .groupBy { it.url!! }
        val noUrl = articles.filter { it.url.isNullOrBlank() }

        val deduped = byUrl.values.map { group ->
            group.minByOrNull { SOURCE_PRIORITY.getOrDefault(it.sourceType, 99) }!!
        }
        return (deduped + noUrl).sortedByDescending { it.ingestedAt }
    }

    companion object {
        private val SOCIAL_SOURCES = setOf("bluesky", "twitter")

        /** Lower = higher priority. Social sources are kept only when no canonical source has the same URL. */
        private val SOURCE_PRIORITY = mapOf(
            "rss"              to 1,
            "github-releases"  to 2,
            "github-trending"  to 3,
            "openjdk-mail"     to 4,
            "jep"              to 5,
            "reddit"           to 10,
            "bluesky"          to 11,
            "twitter"          to 12,
        )

        // System prompt moved to DomainProfile.clustering.systemPrompt
    }
}
