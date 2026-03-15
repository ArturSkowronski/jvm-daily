package jvm.daily.workflow

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
) : Workflow {

    override val name: String = "outgress"

    override suspend fun execute() {
        val now = clock.now()
        val since = now.minus(outgressDays.days)

        val byDate = processedArticleRepository.findByDateRange(since, now)
            .groupBy { it.processedAt.toLocalDateTime(TimeZone.UTC).date }

        if (byDate.isEmpty()) {
            println("[outgress] No articles found in the last $outgressDays day(s)")
            return
        }

        outputDir.createDirectories()

        for ((date, articles) in byDate.entries.sortedByDescending { it.key }) {
            val sorted = articles.sortedByDescending { it.engagementScore }
            val dateStr = date.toString()
            val outputFile = outputDir.resolve("jvm-daily-$dateStr.md")

            val content = buildString {
                appendLine("# JVM Daily — $dateStr")
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
        val unclusteredArticles = allIngested.filter { it.id !in allClusterArticleIds }
        val totalArticles = allClusterArticleIds.size + unclusteredArticles.size

        val digestClusters = clusters.map { cluster ->
            DigestCluster(
                id = cluster.id, title = cluster.title, summary = cluster.summary,
                engagementScore = cluster.totalEngagement,
                articles = cluster.articles
                    .mapNotNull { clusterArticlesById[it] }
                    .sortedByDescending { it.engagementScore }
                    .map { it.toDigestArticle() },
            )
        }.sortedByDescending { it.engagementScore }

        val digest = DigestJson(
            date = now.toLocalDateTime(TimeZone.UTC).date.toString(),
            generatedAt = now.toString(),
            totalArticles = totalArticles,
            clusters = digestClusters,
            unclustered = unclusteredArticles.sortedByDescending { it.engagementScore }.map { it.toDigestArticle() },
        )

        outputDir.createDirectories()
        val date = now.toLocalDateTime(TimeZone.UTC).date
        outputDir.resolve("daily-$date.json").writeText(Json.encodeToString(digest))
        println("[outgress] Wrote digest JSON to ${outputDir.resolve("daily-$date.json")}")
    }

    private fun ProcessedArticle.toDigestArticle() = DigestArticle(
        id = id, title = originalTitle, url = url, summary = summary,
        topics = topics, entities = entities, engagementScore = engagementScore,
        publishedAt = publishedAt, ingestedAt = ingestedAt, sourceType = sourceType,
    )
}
