package jvm.daily.storage

import jvm.daily.model.EnrichmentOutcomeStatus
import jvm.daily.model.ProcessedArticle
import kotlinx.datetime.Instant
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.Connection
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DuckDbProcessedArticleRepositoryTest {

    private lateinit var connection: Connection
    private lateinit var repository: DuckDbProcessedArticleRepository

    @BeforeEach
    fun setUp() {
        connection = DuckDbConnectionFactory.inMemory()
        repository = DuckDbProcessedArticleRepository(connection)
    }

    @AfterEach
    fun tearDown() {
        connection.close()
    }

    @Test
    fun `save and load successful processed article`() {
        val article = processedArticle(id = "a1")
        repository.save(article)

        val loaded = repository.findAll().single()
        assertEquals(EnrichmentOutcomeStatus.SUCCESS, loaded.outcomeStatus)
        assertEquals(listOf("JDK 21"), loaded.entities)
        assertEquals(listOf("performance"), loaded.topics)
    }

    @Test
    fun `save and load failed processed article with metadata`() {
        val failed = processedArticle(
            id = "a2",
            summary = "[FAILED]",
            entities = emptyList(),
            topics = emptyList(),
            outcomeStatus = EnrichmentOutcomeStatus.FAILED,
            failureReason = "PARSE_JSON: invalid token",
            lastAttemptAt = Instant.parse("2026-02-27T22:00:00Z"),
            attemptCount = 3,
            warnings = listOf("CONTENT_EMPTY: generated from title and metadata"),
        )
        repository.save(failed)

        val loaded = repository.findAll().single()
        assertEquals(EnrichmentOutcomeStatus.FAILED, loaded.outcomeStatus)
        assertEquals("PARSE_JSON: invalid token", loaded.failureReason)
        assertNotNull(loaded.lastAttemptAt)
        assertEquals(3, loaded.attemptCount)
        assertEquals(listOf("CONTENT_EMPTY: generated from title and metadata"), loaded.warnings)
    }

    @Test
    fun `findFailedSince returns only failed outcomes`() {
        repository.save(processedArticle(id = "success-1"))
        repository.save(
            processedArticle(
                id = "failed-1",
                outcomeStatus = EnrichmentOutcomeStatus.FAILED,
                failureReason = "VALIDATION: topic empty",
            )
        )

        val failed = repository.findFailedSince(Instant.parse("2026-02-26T00:00:00Z"))
        assertEquals(1, failed.size)
        assertEquals("failed-1", failed[0].id)
    }

    @Test
    fun `findFailedRawArticleIds returns deterministic latest-first ids with limit`() {
        repository.save(
            processedArticle(
                id = "failed-newer",
                outcomeStatus = EnrichmentOutcomeStatus.FAILED,
                failureReason = "TRANSPORT: timeout",
                processedAt = Instant.parse("2026-02-27T21:00:00Z"),
            )
        )
        repository.save(
            processedArticle(
                id = "failed-older",
                outcomeStatus = EnrichmentOutcomeStatus.FAILED,
                failureReason = "PARSE_JSON: invalid token",
                processedAt = Instant.parse("2026-02-27T20:00:00Z"),
            )
        )
        repository.save(
            processedArticle(
                id = "success",
                outcomeStatus = EnrichmentOutcomeStatus.SUCCESS,
                processedAt = Instant.parse("2026-02-27T22:00:00Z"),
            )
        )

        val ids = repository.findFailedRawArticleIds(
            since = Instant.parse("2026-02-27T00:00:00Z"),
            limit = 1,
        )
        assertEquals(listOf("failed-newer"), ids)
    }

    @Test
    fun `findFailedByIds returns only failed records preserving input order`() {
        repository.save(
            processedArticle(
                id = "failed-1",
                outcomeStatus = EnrichmentOutcomeStatus.FAILED,
                failureReason = "VALIDATION: empty summary",
            )
        )
        repository.save(
            processedArticle(
                id = "success-1",
                outcomeStatus = EnrichmentOutcomeStatus.SUCCESS,
            )
        )
        repository.save(
            processedArticle(
                id = "failed-2",
                outcomeStatus = EnrichmentOutcomeStatus.FAILED,
                failureReason = "TRANSPORT: timeout",
            )
        )

        val failed = repository.findFailedByIds(listOf("failed-2", "success-1", "missing", "failed-1"))
        assertEquals(listOf("failed-2", "failed-1"), failed.map { it.id })
    }

    private fun processedArticle(
        id: String,
        summary: String = "This is a long enough summary to satisfy enrichment validation and keep repository tests deterministic for processed record persistence checks in this phase.",
        entities: List<String> = listOf("JDK 21"),
        topics: List<String> = listOf("performance"),
        outcomeStatus: EnrichmentOutcomeStatus = EnrichmentOutcomeStatus.SUCCESS,
        failureReason: String? = null,
        lastAttemptAt: Instant? = null,
        attemptCount: Int = 1,
        warnings: List<String> = emptyList(),
        processedAt: Instant = Instant.parse("2026-02-27T21:00:00Z"),
    ) = ProcessedArticle(
        id = id,
        originalTitle = "Title $id",
        normalizedTitle = "title $id",
        summary = summary,
        originalContent = "Original content for $id",
        sourceType = "rss",
        sourceId = "source-$id",
        url = "https://example.com/$id",
        author = "author",
        publishedAt = Instant.parse("2026-02-27T20:00:00Z"),
        ingestedAt = Instant.parse("2026-02-27T20:00:00Z"),
        processedAt = processedAt,
        entities = entities,
        topics = topics,
        engagementScore = 67.0,
        outcomeStatus = outcomeStatus,
        failureReason = failureReason,
        lastAttemptAt = lastAttemptAt,
        attemptCount = attemptCount,
        warnings = warnings,
    )
}
