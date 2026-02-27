package jvm.daily.storage

import jvm.daily.model.Article
import kotlinx.datetime.Instant
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.Connection
import kotlin.test.assertEquals

class DuckDbArticleRepositoryIdempotencyTest {

    private lateinit var connection: Connection
    private lateinit var repository: DuckDbArticleRepository

    @BeforeEach
    fun setUp() {
        connection = DuckDbConnectionFactory.inMemory()
        repository = DuckDbArticleRepository(connection)
    }

    @AfterEach
    fun tearDown() {
        connection.close()
    }

    @Test
    fun `save is idempotent for equivalent canonical id`() {
        val canonicalId = "rss:https://example.com/post-1"
        repository.save(article(canonicalId, "First title"))
        repository.save(article(canonicalId, "Updated title"))

        assertEquals(1, repository.count())
        assertEquals("Updated title", repository.findAll().single().title)
    }

    @Test
    fun `saveAll keeps stable cardinality for duplicate ids`() {
        repository.saveAll(
            listOf(
                article("md:daily-1", "Daily 1"),
                article("md:daily-2", "Daily 2"),
                article("md:daily-1", "Daily 1 replacement"),
            )
        )

        assertEquals(2, repository.count())
        val rows = repository.findAll().associateBy { it.id }
        assertEquals("Daily 1 replacement", rows.getValue("md:daily-1").title)
        assertEquals("Daily 2", rows.getValue("md:daily-2").title)
    }

    @Test
    fun `equivalent canonical ids from source variants keep cardinality stable`() {
        repository.save(article("rss:https://example.com/post-1", "First"))
        repository.save(article("rss:https://example.com/post-1", "First - updated payload"))
        repository.save(article("rss:https://example.com/post-2", "Second"))

        assertEquals(2, repository.count())
        val byId = repository.findAll().associateBy { it.id }
        assertEquals("First - updated payload", byId.getValue("rss:https://example.com/post-1").title)
        assertEquals("Second", byId.getValue("rss:https://example.com/post-2").title)
    }

    @Test
    fun `persisted row keeps source metadata and ingest timestamp`() {
        val ingestedAt = Instant.parse("2026-02-27T21:00:00Z")
        val article = Article(
            id = "rss:https://example.com/post-2",
            title = "Meta test",
            content = "content",
            sourceType = "rss",
            sourceId = "https://example.com/feed.xml",
            url = "https://example.com/post-2",
            author = "author",
            comments = "comments",
            ingestedAt = ingestedAt,
        )

        repository.save(article)
        val saved = repository.findAll().single()

        assertEquals("rss", saved.sourceType)
        assertEquals("https://example.com/feed.xml", saved.sourceId)
        assertEquals(ingestedAt, saved.ingestedAt)
    }

    private fun article(id: String, title: String) = Article(
        id = id,
        title = title,
        content = "content",
        sourceType = "rss",
        sourceId = "https://example.com/feed.xml",
        ingestedAt = Instant.parse("2026-02-27T20:00:00Z"),
    )
}
