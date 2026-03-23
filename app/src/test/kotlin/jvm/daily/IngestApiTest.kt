package jvm.daily

import jvm.daily.model.Article
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals

class IngestApiTest {

    @Test
    fun `serialize and parse round-trip preserves articles`() {
        val now = Clock.System.now()
        val articles = listOf(
            Article(
                id = "reddit:java/abc123",
                title = "[java] Test Post",
                content = "Some content here",
                sourceType = "reddit",
                sourceId = "r/java/abc123",
                url = "https://www.reddit.com/r/java/comments/abc123",
                author = "testuser",
                comments = "Comment thread",
                ingestedAt = now,
            ),
            Article(
                id = "reddit:kotlin/def456",
                title = "[Kotlin] Another Post",
                content = "Kotlin content",
                sourceType = "reddit",
                sourceId = "r/Kotlin/def456",
                url = null,
                author = null,
                comments = null,
                ingestedAt = now,
            ),
        )

        val json = serializeArticles(articles)
        val parsed = parseIngestPayload(json)

        assertEquals(2, parsed.size)
        assertEquals(articles[0].id, parsed[0].id)
        assertEquals(articles[0].title, parsed[0].title)
        assertEquals(articles[0].content, parsed[0].content)
        assertEquals(articles[0].sourceType, parsed[0].sourceType)
        assertEquals(articles[0].sourceId, parsed[0].sourceId)
        assertEquals(articles[0].url, parsed[0].url)
        assertEquals(articles[0].author, parsed[0].author)
        assertEquals(articles[0].comments, parsed[0].comments)
        assertEquals(articles[0].ingestedAt, parsed[0].ingestedAt)

        // Nullable fields
        assertEquals(articles[1].url, parsed[1].url)
        assertEquals(articles[1].author, parsed[1].author)
        assertEquals(articles[1].comments, parsed[1].comments)
    }

    @Test
    fun `parse empty array returns empty list`() {
        val parsed = parseIngestPayload("[]")
        assertEquals(0, parsed.size)
    }

    @Test
    fun `serialize empty list returns empty array`() {
        val json = serializeArticles(emptyList())
        assertEquals("[]", json)
    }
}
