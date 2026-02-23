package jvm.daily.workflow

import jvm.daily.storage.ProcessedArticleRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.days

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
    }
}
