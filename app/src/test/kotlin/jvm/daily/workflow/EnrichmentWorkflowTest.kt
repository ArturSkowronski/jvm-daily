package jvm.daily.workflow

import jvm.daily.ai.LLMClient
import jvm.daily.model.Article
import jvm.daily.model.EnrichmentOutcomeStatus
import jvm.daily.model.ProcessedArticle
import jvm.daily.storage.ArticleRepository
import jvm.daily.storage.ProcessedArticleRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnrichmentWorkflowTest {

    @Test
    fun `enrichment processes unprocessed articles`() = runTest {
        val rawArticles = mutableListOf(
            article("1", "Spring Boot 3.3 Released"),
            article("2", "Kotlin Coroutines Update"),
        )
        val rawRepo = inMemoryRawRepo(rawArticles)

        val processedArticles = mutableListOf<ProcessedArticle>()
        val processedRepo = inMemoryProcessedRepo(processedArticles, rawArticles.map { it.id })

        val llmClient = stubLLMClient(
            """
            {
              "summary": "Spring Boot 3.3 brings virtual threads, improved observability, and a cleaner operational model for JVM services. The release improves startup behavior, refines metrics defaults, and strengthens integration paths for production teams that need safer upgrades across Spring components in active systems.",
              "entities": ["Spring Boot", "Spring Boot 3.3", "Virtual Threads"],
              "topics": ["framework-releases", "performance"]
            }
            """.trimIndent()
        )

        val workflow = EnrichmentWorkflow(rawRepo, processedRepo, llmClient)
        workflow.execute()

        assertEquals(2, processedArticles.size)
        assertEquals("Spring Boot 3.3 Released", processedArticles[0].originalTitle)
        assertTrue(processedArticles[0].summary.contains("Spring Boot 3.3"))
        assertTrue(processedArticles[0].entities.contains("Spring Boot 3.3"))
        assertTrue(processedArticles[0].topics.contains("framework-releases"))
    }

    @Test
    fun `enrichment skips already processed articles`() = runTest {
        val rawArticles = mutableListOf(
            article("1", "Article 1"),
        )
        val rawRepo = inMemoryRawRepo(rawArticles)

        val processedArticles = mutableListOf<ProcessedArticle>()
        val processedRepo = inMemoryProcessedRepo(processedArticles, emptyList()) // None unprocessed

        val llmClient = stubLLMClient(
            """{"summary":"This response is intentionally long enough to satisfy minimum summary length requirements while no processing should happen in this test case anyway.","entities":["none"],"topics":["language-updates"]}"""
        )

        val workflow = EnrichmentWorkflow(rawRepo, processedRepo, llmClient)
        workflow.execute()

        assertEquals(0, processedArticles.size)
    }

    @Test
    fun `enrichment normalizes titles`() = runTest {
        val rawArticles = mutableListOf(
            article("1", "Spring Boot 4.0: New Features! (2026)"),
        )
        val rawRepo = inMemoryRawRepo(rawArticles)

        val processedArticles = mutableListOf<ProcessedArticle>()
        val processedRepo = inMemoryProcessedRepo(processedArticles, listOf("1"))

        val llmClient = stubLLMClient(
            """{"summary":"This summary contains enough words to satisfy the minimum validation requirement while still remaining simple for title normalization checks in the enrichment workflow unit test.","entities":["Spring"],"topics":["releases"]}"""
        )

        val workflow = EnrichmentWorkflow(rawRepo, processedRepo, llmClient)
        workflow.execute()

        assertEquals("spring boot 40 new features 2026", processedArticles[0].normalizedTitle)
    }

    private fun article(id: String, title: String) = Article(
        id = id,
        title = title,
        content = "Content of $title with detailed information about the topic",
        sourceType = "rss",
        sourceId = "test-source",
        ingestedAt = Clock.System.now(),
    )

    private fun stubLLMClient(response: String) = object : LLMClient {
        override suspend fun chat(prompt: String): String = response
    }

    private fun inMemoryRawRepo(storage: MutableList<Article>) = object : ArticleRepository {
        override fun save(article: Article) { storage.add(article) }
        override fun saveAll(articles: List<Article>) { storage.addAll(articles) }
        override fun findAll(): List<Article> = storage.toList()
        override fun findBySourceType(sourceType: String) = storage.filter { it.sourceType == sourceType }
        override fun existsById(id: String) = storage.any { it.id == id }
        override fun count(): Long = storage.size.toLong()
    }

    private fun inMemoryProcessedRepo(
        storage: MutableList<ProcessedArticle>,
        unprocessedIds: List<String>
    ) = object : ProcessedArticleRepository {
        override fun save(article: ProcessedArticle) { storage.add(article) }
        override fun saveAll(articles: List<ProcessedArticle>) { storage.addAll(articles) }
        override fun findAll(): List<ProcessedArticle> = storage.toList()
        override fun findByDateRange(startDate: Instant, endDate: Instant) =
            storage.filter { it.processedAt >= startDate && it.processedAt <= endDate }
        override fun findFailedSince(since: Instant) =
            storage.filter { it.processedAt >= since && it.outcomeStatus == EnrichmentOutcomeStatus.FAILED }
        override fun findUnprocessedRawArticles(since: Instant) = unprocessedIds
        override fun existsById(id: String) = storage.any { it.id == id }
        override fun count(): Long = storage.size.toLong()
    }
}
