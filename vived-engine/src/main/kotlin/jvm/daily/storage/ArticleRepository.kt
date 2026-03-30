package jvm.daily.storage

import jvm.daily.model.Article
import jvm.daily.model.FeedRunSnapshot
import kotlinx.datetime.Instant

interface ArticleRepository {
    fun save(article: Article)
    fun saveAll(articles: List<Article>)
    fun findAll(): List<Article>
    fun findBySourceType(sourceType: String): List<Article>
    fun existsById(id: String): Boolean
    fun count(): Long
    fun countSince(since: Instant): Long = findAll().count { it.ingestedAt >= since }.toLong()
    fun recordFeedRunSnapshots(snapshots: List<FeedRunSnapshot>) {}
    fun sumDuplicateCountSince(since: Instant): Long = 0
    fun countFeedFailuresSince(since: Instant): Long = 0
    fun queryFeedRunSummaries(): List<FeedRunSummary> = emptyList()
}

data class FeedRunSummary(
    val sourceType: String,
    val sourceId: String,
    val lastRunAt: String,
    val lastRunStatus: String,
    val lastSuccessAt: String?,
    val last24hRuns: Int,
    val last24hSuccesses: Int,
    val last24hFailures: Int,
    val last24hNewCount: Int,
)
