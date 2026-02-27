package jvm.daily.tools

import jvm.daily.model.Article
import jvm.daily.storage.DuckDbArticleRepository
import jvm.daily.storage.DuckDbConnectionFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.Connection
import kotlinx.datetime.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ValidateRawArticleIdsTest {

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
    fun `dry run reports mismatches without mutating ids`() {
        repository.save(
            article(
                id = "rss:legacy-id",
                title = "First",
                sourceType = "rss",
                sourceId = "https://example.com/feed.xml",
                url = "https://example.com/post-1",
            )
        )

        val summary = ValidateRawArticleIds(connection).run(applyUpdates = false)

        assertEquals(1, summary.totalRows)
        assertEquals(1, summary.mismatches)
        assertEquals(0, summary.updated)
        assertTrue(repository.existsById("rss:legacy-id"))
    }

    @Test
    fun `apply mode updates mismatched id when no collision`() {
        repository.save(
            article(
                id = "rss:legacy-id",
                title = "First",
                sourceType = "rss",
                sourceId = "https://example.com/feed.xml",
                url = "https://example.com/post-1",
            )
        )

        val summary = ValidateRawArticleIds(connection).run(applyUpdates = true)

        assertEquals(1, summary.mismatches)
        assertEquals(1, summary.updated)
        assertTrue(repository.existsById("rss:https://example.com/post-1"))
    }

    @Test
    fun `apply mode reports collision and keeps original id`() {
        repository.save(
            article(
                id = "rss:https://example.com/post-1",
                title = "First canonical",
                sourceType = "rss",
                sourceId = "https://example.com/feed.xml",
                url = "https://example.com/post-1",
            )
        )
        repository.save(
            article(
                id = "rss:legacy-id",
                title = "First legacy",
                sourceType = "rss",
                sourceId = "https://example.com/feed.xml",
                url = "https://example.com/post-1",
            )
        )

        val summary = ValidateRawArticleIds(connection).run(applyUpdates = true)

        assertEquals(1, summary.mismatches)
        assertEquals(1, summary.collisions)
        assertEquals(0, summary.updated)
        assertTrue(repository.existsById("rss:legacy-id"))
        assertTrue(repository.existsById("rss:https://example.com/post-1"))
    }

    private fun article(
        id: String,
        title: String,
        sourceType: String,
        sourceId: String,
        url: String?,
    ) = Article(
        id = id,
        title = title,
        content = "content",
        sourceType = sourceType,
        sourceId = sourceId,
        url = url,
        ingestedAt = Instant.parse("2026-02-27T22:00:00Z"),
    )
}
