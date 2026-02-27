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
    fun findUnprocessedRawArticles(since: Instant): List<String> // Returns raw article IDs
    fun existsById(id: String): Boolean
    fun count(): Long
}
