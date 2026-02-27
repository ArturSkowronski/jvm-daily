package jvm.daily.model

import kotlinx.datetime.Instant

enum class FeedIngestStatus {
    SUCCESS,
    PARTIAL_SUCCESS,
    FAILED,
}

data class FeedIngestResult(
    val sourceType: String,
    val sourceId: String,
    val status: FeedIngestStatus,
    val fetchedCount: Int,
    val newCount: Int = 0,
    val duplicateCount: Int = 0,
    val errors: List<String> = emptyList(),
)

data class SourceFetchOutcome(
    val feed: FeedIngestResult,
    val articles: List<Article>,
)

data class FeedRunSnapshot(
    val runId: String,
    val recordedAt: Instant,
    val sourceType: String,
    val sourceId: String,
    val status: FeedIngestStatus,
    val fetchedCount: Int,
    val newCount: Int,
    val duplicateCount: Int,
)
