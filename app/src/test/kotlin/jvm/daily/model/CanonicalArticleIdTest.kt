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

        assertEquals("https://example.com/article/path", id)
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

    @Test
    fun `url normalization removes trailing slash and query fragment noise`() {
        val a = CanonicalArticleId.from(
            namespace = "rss",
            sourceId = "feed-a",
            title = "ignored",
            url = "https://example.com/post/1?utm_source=rss#comments",
        )
        val b = CanonicalArticleId.from(
            namespace = "rss",
            sourceId = "feed-a",
            title = "ignored",
            url = "https://EXAMPLE.com/post/1?utm_source=rss",
        )
        assertEquals(a, b)
        assertEquals("https://example.com/post/1utm_sourcerss", a)
    }

    @Test
    fun `same url from different sources produces same id`() {
        val url = "https://example.com/post/1"
        val fromRss = CanonicalArticleId.from(namespace = "rss", sourceId = "feed-a", title = "T", url = url)
        val fromBluesky = CanonicalArticleId.from(namespace = "bluesky", sourceId = "user.bsky", title = "T", url = url)
        assertEquals(fromRss, fromBluesky)
        assertEquals("https://example.com/post/1", fromRss)
    }

    @Test
    fun `namespace normalization keeps dedup namespace deterministic`() {
        val id = CanonicalArticleId.from(
            namespace = "RSS Feed",
            sourceId = "feed-a",
            title = "Title",
            sourceNativeId = "entry-1",
        )
        assertEquals("rss_feed:entry-1", id)
    }

    @Test
    fun `source id fallback is used when url native id and title are blank`() {
        val id = CanonicalArticleId.from(
            namespace = "rss",
            sourceId = "Feed Source Id 42",
            title = "   ",
            url = "",
            sourceNativeId = " ",
        )
        assertEquals("rss:feed-source-id-42", id)
    }
}
