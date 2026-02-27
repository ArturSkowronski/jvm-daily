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

class EnrichmentWorkflowReliabilityTest {

    @Test
    fun `invalid json response persists failed outcome`() = runTest {
        val raw = mutableListOf(article("a1", "Title", "Some content for processing and summary generation."))
        val saved = mutableListOf<ProcessedArticle>()
        val workflow = EnrichmentWorkflow(
            rawArticleRepository = inMemoryRawRepo(raw),
            processedArticleRepository = inMemoryProcessedRepo(saved, listOf("a1")),
            llmClient = stubLLMClient(listOf("not-json")),
            retryBackoffMs = 0,
        )

        workflow.execute()

        assertEquals(1, saved.size)
        assertEquals(EnrichmentOutcomeStatus.FAILED, saved[0].outcomeStatus)
        assertTrue(saved[0].failureReason!!.startsWith("PARSE_JSON"))
    }

    @Test
    fun `transport failure retries then succeeds`() = runTest {
        val raw = mutableListOf(article("a2", "Title", "Some content for processing and summary generation."))
        val saved = mutableListOf<ProcessedArticle>()
        var callCount = 0
        val llm = object : LLMClient {
            override suspend fun chat(prompt: String): String {
                callCount++
                if (callCount == 1) error("timeout")
                return validJson(summary = longSummary())
            }
        }

        EnrichmentWorkflow(
            rawArticleRepository = inMemoryRawRepo(raw),
            processedArticleRepository = inMemoryProcessedRepo(saved, listOf("a2")),
            llmClient = llm,
            retryBackoffMs = 0,
        ).execute()

        assertEquals(2, callCount)
        assertEquals(1, saved.size)
        assertEquals(EnrichmentOutcomeStatus.SUCCESS, saved[0].outcomeStatus)
        assertEquals(2, saved[0].attemptCount)
    }

    @Test
    fun `workflow continues with partial failures`() = runTest {
        val raw = mutableListOf(
            article("ok", "Valid", "Valid content for article processing."),
            article("bad", "Invalid", "Invalid content for article processing."),
        )
        val saved = mutableListOf<ProcessedArticle>()
        val llm = object : LLMClient {
            override suspend fun chat(prompt: String): String {
                return if (prompt.contains("Title: Invalid")) "invalid-json" else validJson(summary = longSummary())
            }
        }

        EnrichmentWorkflow(
            rawArticleRepository = inMemoryRawRepo(raw),
            processedArticleRepository = inMemoryProcessedRepo(saved, listOf("ok", "bad")),
            llmClient = llm,
            retryBackoffMs = 0,
        ).execute()

        assertEquals(2, saved.size)
        assertEquals(1, saved.count { it.outcomeStatus == EnrichmentOutcomeStatus.SUCCESS })
        assertEquals(1, saved.count { it.outcomeStatus == EnrichmentOutcomeStatus.FAILED })
    }

    @Test
    fun `empty content adds warning while succeeding`() = runTest {
        val raw = mutableListOf(article("a3", "Title", ""))
        val saved = mutableListOf<ProcessedArticle>()

        EnrichmentWorkflow(
            rawArticleRepository = inMemoryRawRepo(raw),
            processedArticleRepository = inMemoryProcessedRepo(saved, listOf("a3")),
            llmClient = stubLLMClient(listOf(validJson(summary = longSummary()))),
            retryBackoffMs = 0,
        ).execute()

        assertEquals(1, saved.size)
        assertEquals(EnrichmentOutcomeStatus.SUCCESS, saved[0].outcomeStatus)
        assertTrue(saved[0].warnings.any { it.startsWith("CONTENT_EMPTY") })
    }

    @Test
    fun `topic constraints are enforced in persisted record`() = runTest {
        val raw = mutableListOf(article("a4", "Title", "Content for topic checks."))
        val saved = mutableListOf<ProcessedArticle>()
        val response = """
            {
              "summary": "${longSummary()}",
              "entities": ["JDK 21"],
              "topics": ["Performance","Framework-Releases","Tooling","Security","Language-Updates","Microservices","this-topic-name-is-longer-than-forty-characters-and-should-drop"]
            }
        """.trimIndent()

        EnrichmentWorkflow(
            rawArticleRepository = inMemoryRawRepo(raw),
            processedArticleRepository = inMemoryProcessedRepo(saved, listOf("a4")),
            llmClient = stubLLMClient(listOf(response)),
            retryBackoffMs = 0,
        ).execute()

        assertEquals(1, saved.size)
        assertEquals(5, saved[0].topics.size)
        assertTrue(saved[0].topics.all { it.length in 1..40 })
    }

    @Test
    fun `replay mode processes only targeted ids`() = runTest {
        val raw = mutableListOf(
            article("keep", "Keep", "Keep content"),
            article("replay", "Replay", "Replay content"),
        )
        val saved = mutableListOf<ProcessedArticle>()

        EnrichmentWorkflow(
            rawArticleRepository = inMemoryRawRepo(raw),
            processedArticleRepository = inMemoryProcessedRepo(saved, emptyList()),
            llmClient = stubLLMClient(listOf(validJson(summary = longSummary()))),
            replayRawArticleIds = setOf("replay"),
            retryBackoffMs = 0,
        ).execute()

        assertEquals(1, saved.size)
        assertEquals("replay", saved[0].id)
    }

    @Test
    fun `replay mode skips missing raw ids deterministically`() = runTest {
        val raw = mutableListOf(article("exists", "Exists", "Content"))
        val saved = mutableListOf<ProcessedArticle>()

        EnrichmentWorkflow(
            rawArticleRepository = inMemoryRawRepo(raw),
            processedArticleRepository = inMemoryProcessedRepo(saved, emptyList()),
            llmClient = stubLLMClient(listOf(validJson(summary = longSummary()))),
            replayRawArticleIds = linkedSetOf("missing", "exists"),
            retryBackoffMs = 0,
        ).execute()

        assertEquals(1, saved.size)
        assertEquals("exists", saved[0].id)
    }

    private fun article(id: String, title: String, content: String) = Article(
        id = id,
        title = title,
        content = content,
        sourceType = "rss",
        sourceId = "test-source",
        url = "https://example.com/$id",
        ingestedAt = Clock.System.now(),
    )

    private fun validJson(summary: String): String = """
        {"summary":"$summary","entities":["Spring Boot","JDK 21"],"topics":["framework-releases","performance"]}
    """.trimIndent()

    private fun longSummary(): String =
        "This generated summary intentionally contains more than twenty words so that validation passes consistently across workflow tests while keeping deterministic behavior for strict parsing and persistence checks in the enrichment stage."

    private fun stubLLMClient(responses: List<String>) = object : LLMClient {
        private var idx = 0
        override suspend fun chat(prompt: String): String {
            val response = responses.getOrElse(idx) { responses.last() }
            idx++
            return response
        }
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
        override fun findFailedRawArticleIds(since: Instant, limit: Int): List<String> =
            storage
                .filter { it.processedAt >= since && it.outcomeStatus == EnrichmentOutcomeStatus.FAILED }
                .sortedByDescending { it.processedAt }
                .map { it.id }
                .take(limit.coerceAtLeast(0))
        override fun findFailedByIds(ids: List<String>): List<ProcessedArticle> =
            ids.mapNotNull { id -> storage.find { it.id == id && it.outcomeStatus == EnrichmentOutcomeStatus.FAILED } }
        override fun findUnprocessedRawArticles(since: Instant) = unprocessedIds
        override fun existsById(id: String) = storage.any { it.id == id }
        override fun count(): Long = storage.size.toLong()
    }
}
