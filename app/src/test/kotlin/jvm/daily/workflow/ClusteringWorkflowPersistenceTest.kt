package jvm.daily.workflow

import jvm.daily.ai.LLMClient
import jvm.daily.model.ArticleCluster
import jvm.daily.model.ProcessedArticle
import jvm.daily.storage.ClusterRepository
import jvm.daily.storage.ProcessedArticleRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClusteringWorkflowPersistenceTest {

    private val fixedNow = Instant.parse("2026-03-15T12:00:00Z")
    private val clock = object : Clock { override fun now() = fixedNow }

    @Test
    fun `saveAll is called with clusters produced by clusterArticles`() = runTest {
        val articles = (1..3).map { i ->
            processedArticle(
                id = "art-$i",
                title = "Spring Boot Article $i",
                topics = listOf("spring", "framework"),
                engagementScore = 10.0 * i,
                ingestedAt = Instant.parse("2026-03-15T06:00:00Z"),
            )
        }

        val savedClusters = mutableListOf<List<ArticleCluster>>()

        val clusterRepo = object : ClusterRepository {
            override fun save(cluster: ArticleCluster) {}
            override fun saveAll(clusters: List<ArticleCluster>) { savedClusters.add(clusters) }
            override fun findByDateRange(start: Instant, end: Instant): List<ArticleCluster> = emptyList()
            override fun deleteByDateRange(start: Instant, end: Instant) {}
        }

        val llm = object : LLMClient {
            override suspend fun chat(prompt: String): String =
                "TITLE: Spring News\nSYNTHESIS: Spring stuff happened."
        }

        val repo = object : ProcessedArticleRepository {
            override fun save(article: ProcessedArticle) {}
            override fun saveAll(articles: List<ProcessedArticle>) {}
            override fun findAll(): List<ProcessedArticle> = articles
            override fun findByDateRange(startDate: Instant, endDate: Instant): List<ProcessedArticle> = articles
            override fun findFailedSince(since: Instant): List<ProcessedArticle> = emptyList()
            override fun findFailedRawArticleIds(since: Instant, limit: Int): List<String> = emptyList()
            override fun findFailedByIds(ids: List<String>): List<ProcessedArticle> = emptyList()
            override fun findInspectionCandidates(since: Instant, limit: Int, minWarnings: Int): List<ProcessedArticle> = emptyList()
            override fun findByIds(ids: List<String>): List<ProcessedArticle> = articles.filter { it.id in ids }
            override fun findByIngestedAtRange(start: Instant, end: Instant): List<ProcessedArticle> = articles
            override fun findUnprocessedRawArticles(since: Instant): List<String> = emptyList()
            override fun existsById(id: String): Boolean = false
            override fun count(): Long = articles.size.toLong()
        }

        ClusteringWorkflow(repo, clusterRepo, llm, clock).execute()

        assertEquals(1, savedClusters.size, "saveAll should be called once")
        val clusters = savedClusters.first()
        assertTrue(clusters.isNotEmpty(), "should produce at least one cluster")
        assertTrue(clusters.first().articles.isNotEmpty(), "cluster should contain article IDs")
    }

    private fun processedArticle(
        id: String,
        title: String,
        topics: List<String>,
        engagementScore: Double,
        ingestedAt: Instant,
    ) = ProcessedArticle(
        id = id,
        originalTitle = title,
        normalizedTitle = title.lowercase(),
        summary = "Summary of $title",
        originalContent = "Content",
        sourceType = "rss",
        sourceId = "test",
        url = "https://example.com/$id",
        publishedAt = ingestedAt,
        ingestedAt = ingestedAt,
        processedAt = ingestedAt,
        topics = topics,
        engagementScore = engagementScore,
    )
}
