package jvm.daily.storage

import jvm.daily.model.ProcessedArticle
import kotlinx.datetime.Instant

interface ProcessedArticleRepository {
    fun save(article: ProcessedArticle)
    fun saveAll(articles: List<ProcessedArticle>)
    fun findAll(): List<ProcessedArticle>
    fun findByDateRange(startDate: Instant, endDate: Instant): List<ProcessedArticle>
    fun findFailedSince(since: Instant): List<ProcessedArticle>
    fun countFailedSince(since: Instant): Long = findFailedSince(since).size.toLong()
    fun findFailedRawArticleIds(since: Instant, limit: Int): List<String>
    fun findFailedByIds(ids: List<String>): List<ProcessedArticle>
    fun findInspectionCandidates(since: Instant, limit: Int, minWarnings: Int = 1): List<ProcessedArticle>
    fun findUnprocessedRawArticles(since: Instant): List<String> // Returns raw article IDs
    fun findByIds(ids: List<String>): List<ProcessedArticle>
    fun findByIngestedAtRange(start: Instant, end: Instant): List<ProcessedArticle>
    fun existsById(id: String): Boolean
    fun count(): Long
}
