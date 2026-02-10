package jvm.daily.storage

import jvm.daily.model.Article
import kotlinx.datetime.Clock
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.Connection
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DuckDbArticleRepositoryTest {

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
    fun `save and retrieve article`() {
        val article = createArticle("1", "Test Title", "Test content")
        repository.save(article)

        val all = repository.findAll()
        assertEquals(1, all.size)
        assertEquals("Test Title", all[0].title)
        assertEquals("Test content", all[0].content)
    }

    @Test
    fun `saveAll persists multiple articles`() {
        val articles = listOf(
            createArticle("1", "First", "Content 1"),
            createArticle("2", "Second", "Content 2"),
        )
        repository.saveAll(articles)

        assertEquals(2, repository.count())
    }

    @Test
    fun `findBySourceType filters correctly`() {
        repository.save(createArticle("1", "MD Article", "Content", sourceType = "markdown_file"))
        repository.save(createArticle("2", "RSS Article", "Content", sourceType = "rss_feed"))

        val mdArticles = repository.findBySourceType("markdown_file")
        assertEquals(1, mdArticles.size)
        assertEquals("MD Article", mdArticles[0].title)
    }

    @Test
    fun `count returns zero for empty table`() {
        assertEquals(0, repository.count())
    }

    @Test
    fun `existsById returns true for existing article`() {
        repository.save(createArticle("1", "Title", "Content"))
        assertTrue(repository.existsById("1"))
    }

    @Test
    fun `existsById returns false for missing article`() {
        assertFalse(repository.existsById("nonexistent"))
    }

    @Test
    fun `save with same id replaces article`() {
        repository.save(createArticle("1", "Original", "Old content"))
        repository.save(createArticle("1", "Updated", "New content"))

        assertEquals(1, repository.count())
        assertEquals("Updated", repository.findAll()[0].title)
    }

    private fun createArticle(
        id: String,
        title: String,
        content: String,
        sourceType: String = "test",
        sourceId: String = "test-source",
    ) = Article(
        id = id,
        title = title,
        content = content,
        sourceType = sourceType,
        sourceId = sourceId,
        ingestedAt = Clock.System.now(),
    )
}
