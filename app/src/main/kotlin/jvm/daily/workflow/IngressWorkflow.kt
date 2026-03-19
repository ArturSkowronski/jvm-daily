package jvm.daily.workflow

import jvm.daily.model.FeedIngestResult
import jvm.daily.model.FeedIngestStatus
import jvm.daily.model.FeedRunSnapshot
import jvm.daily.model.IngestRunStatus
import jvm.daily.source.SourceRegistry
import jvm.daily.storage.ArticleRepository
import jvm.daily.storage.ProcessedArticleRepository
import kotlinx.datetime.Clock
import java.util.UUID

class IngressWorkflow(
    private val sourceRegistry: SourceRegistry,
    private val articleRepository: ArticleRepository,
    private val clock: Clock = Clock.System,
    private val processedArticleRepository: ProcessedArticleRepository? = null,
) : Workflow {

    override val name: String = "ingress"
    var lastRunStatus: IngestRunStatus = IngestRunStatus.FAIL
        private set

    override suspend fun execute() {
        val runId = UUID.randomUUID().toString()
        val recordedAt = clock.now()
        val sources = sourceRegistry.all()
        println("[ingress] Starting ingress workflow with ${sources.size} source(s)")

        var totalNew = 0
        var totalSkipped = 0
        val feedResults = mutableListOf<FeedIngestResult>()

        for (source in sources) {
            val outcomes = source.fetchOutcomes()

            for (outcome in outcomes) {
                var newCount = 0
                var skippedCount = 0

                for (article in outcome.articles) {
                    // Dedup quality gate: cardinality must remain stable for repeated canonical IDs.
                    if (articleRepository.existsById(article.id)) {
                        // For Bluesky: if a real external URL exists, sync title+url to both raw and processed records
                        if (article.sourceType == "bluesky" && article.url?.startsWith("https://bsky.app") == false) {
                            articleRepository.save(article)
                            processedArticleRepository?.updateUrl(article.id, article.url!!)
                            processedArticleRepository?.updateTitle(article.id, article.title, article.title.lowercase())
                        }
                        skippedCount++
                    } else {
                        articleRepository.save(article)
                        newCount++
                    }
                }

                val status = when {
                    outcome.feed.status == FeedIngestStatus.FAILED -> FeedIngestStatus.FAILED
                    outcome.feed.status == FeedIngestStatus.PARTIAL_SUCCESS -> FeedIngestStatus.PARTIAL_SUCCESS
                    else -> FeedIngestStatus.SUCCESS
                }

                val updated = outcome.feed.copy(
                    status = status,
                    newCount = newCount,
                    duplicateCount = skippedCount,
                )

                val warning = if (updated.errors.isNotEmpty()) " | ${updated.errors.joinToString("; ")}" else ""
                println(
                    "[ingress] ${updated.sourceId}: status=${updated.status}, fetched=${updated.fetchedCount}, " +
                        "new=${updated.newCount}, duplicate=${updated.duplicateCount}$warning"
                )

                feedResults += updated
                totalNew += newCount
                totalSkipped += skippedCount
            }
        }

        val runStatus = classifyRunStatus(feedResults)
        articleRepository.recordFeedRunSnapshots(
            feedResults.map {
                FeedRunSnapshot(
                    runId = runId,
                    recordedAt = recordedAt,
                    sourceType = it.sourceType,
                    sourceId = it.sourceId,
                    status = it.status,
                    fetchedCount = it.fetchedCount,
                    newCount = it.newCount,
                    duplicateCount = it.duplicateCount,
                )
            }
        )
        lastRunStatus = runStatus
        println("[ingress] Feed summary:")
        println("[ingress] source_id | status | fetched | new | duplicate | errors")
        feedResults.forEach { result ->
            println(
                "[ingress] ${result.sourceId} | ${result.status} | ${result.fetchedCount} | " +
                    "${result.newCount} | ${result.duplicateCount} | ${result.errors.joinToString(" / ")}"
            )
        }

        println(
            "[ingress] Done. status=$runStatus, New=$totalNew, Duplicates=$totalSkipped, Total in DB=${articleRepository.count()}"
        )
    }

    companion object {
        internal fun classifyRunStatus(feedResults: List<FeedIngestResult>): IngestRunStatus {
            if (feedResults.isEmpty()) return IngestRunStatus.FAIL

            val allFailed = feedResults.all { it.status == FeedIngestStatus.FAILED }
            if (allFailed) return IngestRunStatus.FAIL

            val hasWarnings = feedResults.any { it.status != FeedIngestStatus.SUCCESS }
            return if (hasWarnings) IngestRunStatus.SUCCESS_WITH_WARNINGS else IngestRunStatus.SUCCESS
        }
    }
}
