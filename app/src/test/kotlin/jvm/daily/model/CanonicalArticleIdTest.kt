package jvm.daily.model

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class CanonicalArticleIdTest {

    @Test
    fun `uses normalized url when available`() {
        val id = CanonicalArticleId.from(
            namespace = "rss",
            sourceId = "feed-a",
            title = "Fallback Title",
            url = "https://Example.com/Article/Path/#comments",
            sourceNativeId = "urn:entry:1",
        )

        assertEquals("rss:https://example.com/article/path", id)
    }

    @Test
    fun `falls back to source native id when url missing`() {
        val id = CanonicalArticleId.from(
            namespace = "rss",
            sourceId = "feed-a",
            title = "Fallback Title",
            sourceNativeId = "URN:Entry:ABC-123",
        )

        assertEquals("rss:urn:entry:abc-123", id)
    }

    @Test
    fun `falls back to title when no url and native id`() {
        val id = CanonicalArticleId.from(
            namespace = "md",
            sourceId = "readme.md",
            title = "  Hello JVM Weekly  ",
        )

        assertEquals("md:hello-jvm-weekly", id)
    }
}
