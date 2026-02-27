package jvm.daily.source

import jvm.daily.model.Article
import jvm.daily.model.FeedIngestResult
import jvm.daily.model.FeedIngestStatus
import jvm.daily.model.SourceFetchOutcome

/**
 * Boundary contract for all ingestion adapters.
 *
 * Implementations are responsible for fetching source-native content and
 * returning normalized [Article] records.
 *
 * Contract guarantees:
 * - `id`, `title`, `sourceType`, `sourceId`, `ingestedAt` are always populated
 * - partial records are allowed (`url`, `author`, `comments` may be null)
 * - adapter exceptions should be handled by the adapter where possible
 */
interface Source {
    val sourceType: String
    suspend fun fetch(): List<Article>

    /**
     * Returns source outcomes that can be aggregated into ingest reliability reports.
     *
     * Default implementation wraps the legacy fetch() contract and treats this
     * source as a single logical feed.
     */
    suspend fun fetchOutcomes(): List<SourceFetchOutcome> {
        return runCatching { fetch() }
            .fold(
                onSuccess = { articles ->
                    listOf(
                        SourceFetchOutcome(
                            feed = FeedIngestResult(
                                sourceType = sourceType,
                                sourceId = sourceType,
                                status = FeedIngestStatus.SUCCESS,
                                fetchedCount = articles.size,
                            ),
                            articles = articles,
                        )
                    )
                },
                onFailure = { error ->
                    listOf(
                        SourceFetchOutcome(
                            feed = FeedIngestResult(
                                sourceType = sourceType,
                                sourceId = sourceType,
                                status = FeedIngestStatus.FAILED,
                                fetchedCount = 0,
                                errors = listOf(error.message ?: "Unknown source fetch error"),
                            ),
                            articles = emptyList(),
                        )
                    )
                }
            )
    }
}
