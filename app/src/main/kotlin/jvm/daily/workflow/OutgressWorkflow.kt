package jvm.daily.workflow

import jvm.daily.config.DomainProfile
import jvm.daily.model.EnrichmentOutcomeStatus
import jvm.daily.model.ProcessedArticle
import jvm.daily.storage.ClusterRepository
import jvm.daily.storage.ProcessedArticleRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * Outgress Workflow — writes one markdown file per processed date.
 *
 * Groups processed_articles by processed_at date, sorted by engagement_score DESC.
 * Generates output/jvm-daily-YYYY-MM-DD.md for each date that has articles.
 */
class OutgressWorkflow(
    private val processedArticleRepository: ProcessedArticleRepository,
    private val outputDir: Path,
    private val outgressDays: Int = 1,
    private val clock: Clock = Clock.System,
    private val clusterRepository: ClusterRepository? = null,
    private val domainProfile: DomainProfile? = null,
) : Workflow {

    private val domain: DomainProfile get() = domainProfile ?: DomainProfile.default()

    override val name: String = "outgress"

    override suspend fun execute() {
        val now = clock.now()
        val since = now.minus(outgressDays.days)

        val byDate = processedArticleRepository.findByDateRange(since, now)
            .filter { it.outcomeStatus == EnrichmentOutcomeStatus.SUCCESS }
            .groupBy { it.processedAt.toLocalDateTime(TimeZone.UTC).date }

        if (byDate.isEmpty()) {
            println("[outgress] No articles found in the last $outgressDays day(s)")
            return
        }

        outputDir.createDirectories()

        for ((date, articles) in byDate.entries.sortedByDescending { it.key }) {
            val sorted = articles.sortedByDescending { it.engagementScore }
            val dateStr = date.toString()
            val outputFile = outputDir.resolve("${domain.slug}-$dateStr.md")

            val content = buildString {
                appendLine("# ${domain.name} — $dateStr")
                appendLine("Generated: $now | Articles: ${sorted.size}")
                appendLine()
                for (article in sorted) {
                    appendLine("## ${article.originalTitle}")
                    appendLine("**URL:** ${article.url ?: "N/A"}")
                    appendLine("**Topics:** ${article.topics.joinToString(", ")}")
                    appendLine("**Summary:** ${article.summary}")
                    appendLine("---")
                    appendLine()
                }
            }

            outputFile.writeText(content)
            println("[outgress] Wrote ${sorted.size} articles to $outputFile")
        }

        if (clusterRepository != null) {
            writeDigestJson(now, clusterRepository)
        }
    }

    private fun writeDigestJson(now: kotlinx.datetime.Instant, clusterRepository: ClusterRepository) {
        val windowStart = now.minus(24.hours)
        val clusters = clusterRepository.findByDateRange(windowStart, now)
        val allClusterArticleIds = clusters.flatMap { it.articles }.toSet()
        val clusterArticlesById = processedArticleRepository
            .findByIds(allClusterArticleIds.toList())
            .associateBy { it.id }
        val allIngested = processedArticleRepository.findByIngestedAtRange(windowStart, now)
        val successIngested = allIngested.filter { it.outcomeStatus == EnrichmentOutcomeStatus.SUCCESS }

        // Social articles grouped by URL — used to attach social links to primary articles
        val socialByUrl: Map<String, List<ProcessedArticle>> = successIngested
            .filter { it.sourceType in SOCIAL_SOURCES && !it.url.isNullOrBlank() }
            .groupBy { it.url!! }

        // URLs already covered by clustered primary articles — social articles with these URLs
        // are shown as social links, not as standalone unclustered entries
        val clusteredPrimaryUrls = clusterArticlesById.values
            .filter { it.sourceType !in SOCIAL_SOURCES && !it.url.isNullOrBlank() }
            .map { it.url!! }.toSet()

        val unclusteredArticles = successIngested.filter { a ->
            a.id !in allClusterArticleIds &&
                !(a.sourceType in SOCIAL_SOURCES && a.url != null && a.url in clusteredPrimaryUrls)
        }
        val totalArticles = allClusterArticleIds.size + unclusteredArticles.size

        val digestClusters = clusters.map { cluster ->
            DigestCluster(
                id = cluster.id, title = cluster.title, summary = cluster.summary,
                engagementScore = cluster.totalEngagement,
                type = cluster.type,
                bullets = cluster.bullets,
                articles = cluster.articles
                    .mapNotNull { clusterArticlesById[it] }
                    .sortedByDescending { it.engagementScore }
                    .map { it.toDigestArticle(socialByUrl) },
            )
        }
        // Mirror ClusteringWorkflow ordering: release clusters sink to bottom, sorted by engagement
        val releasesDigest = digestClusters.filter { it.type == "release" }.sortedByDescending { it.engagementScore }
        val normalDigest   = digestClusters.filter { it.type != "release" }.sortedByDescending { it.engagementScore }
        val sortedDigestClusters = normalDigest + releasesDigest

        val rejected = allIngested
            .filter { it.outcomeStatus == EnrichmentOutcomeStatus.SKIPPED || it.outcomeStatus == EnrichmentOutcomeStatus.FAILED }
            .map { DebugRejected(
                title = it.originalTitle,
                url = it.url,
                sourceType = it.sourceType,
                reason = it.failureReason ?: it.outcomeStatus.name.lowercase(),
            ) }

        val digest = DigestJson(
            date = now.toLocalDateTime(TimeZone.UTC).date.toString(),
            generatedAt = now.toString(),
            totalArticles = totalArticles,
            clusters = sortedDigestClusters,
            unclustered = unclusteredArticles.sortedByDescending { it.engagementScore }.map { it.toDigestArticle(socialByUrl) },
            debug = rejected,
        )

        outputDir.createDirectories()
        val date = now.toLocalDateTime(TimeZone.UTC).date
        outputDir.resolve("daily-$date.json").writeText(json.encodeToString(digest))
        println("[outgress] Wrote digest JSON to ${outputDir.resolve("daily-$date.json")}")
    }

    private fun ProcessedArticle.toDigestArticle(
        socialByUrl: Map<String, List<ProcessedArticle>> = emptyMap(),
    ): DigestArticle {
        val handle = blueskyHandle()
        // Strip "[DisplayName] " prefix that BlueskySource prepends to titles
        val cleanTitle = if (sourceType == "bluesky" && originalTitle.startsWith("["))
            originalTitle.substringAfter("] ").ifBlank { originalTitle }
        else originalTitle
        // For Bluesky articles with external URL, expose the bsky.app post as a social link
        val selfLink = if (sourceType == "bluesky" && !url.isNullOrBlank() && !url.startsWith("https://bsky.app"))
            toSocialLink()
        else null
        val companionLinks = url?.let { socialByUrl[it] }
            ?.filter { it.id != id }
            ?.mapNotNull { it.toSocialLink() }
            ?: emptyList()
        return DigestArticle(
            id = id, title = cleanTitle, url = url, summary = summary,
            topics = topics, entities = entities, engagementScore = engagementScore,
            publishedAt = publishedAt, ingestedAt = ingestedAt, sourceType = sourceType,
            handle = handle,
            socialLinks = listOfNotNull(selfLink) + companionLinks,
            taxonomyArea = taxonomyArea,
            taxonomySubArea = taxonomySubArea,
            taxonomyImpact = taxonomyImpact,
            taxonomyConfidence = taxonomyConfidence,
        )
    }

    private fun ProcessedArticle.blueskyHandle(): String? =
        if (sourceType == "bluesky") sourceId.substringBefore('/').ifBlank { null } else null

    private fun ProcessedArticle.toSocialLink(): DigestSocialLink? = when (sourceType) {
        "bluesky" -> {
            val slash = sourceId.indexOf('/')
            if (slash > 0) {
                val handle = sourceId.substring(0, slash)
                val rkey = sourceId.substring(slash + 1)
                DigestSocialLink("bluesky", "https://bsky.app/profile/$handle/post/$rkey", handle)
            } else null
        }
        "reddit" -> url?.let { DigestSocialLink("reddit", it, sourceId) }
        else -> null
    }

    companion object {
        private val SOCIAL_SOURCES = setOf("bluesky", "twitter", "reddit")
        private val json = Json { encodeDefaults = true }
    }
}
