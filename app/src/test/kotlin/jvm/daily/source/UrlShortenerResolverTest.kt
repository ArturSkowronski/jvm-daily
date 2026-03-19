package jvm.daily.source

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UrlShortenerResolverTest {

    @Test
    fun `non-shortener URLs are returned unchanged without HTTP call`() {
        var httpCalled = false
        val resolver = UrlShortenerResolver(httpHead = { httpCalled = true; it })
        val url = "https://blog.jetbrains.com/kotlin/2024/01/kotlin-2-1-released/"
        assertEquals(url, resolver.resolve(url))
        assertFalse(httpCalled, "Should not call HTTP for non-shortener URL")
    }

    @Test
    fun `jb_gg URL is resolved via HTTP`() {
        val resolver = UrlShortenerResolver(httpHead = { "https://blog.jetbrains.com/resolved" })
        assertEquals("https://blog.jetbrains.com/resolved", resolver.resolve("https://jb.gg/abc123"))
    }

    @Test
    fun `t_co URL is resolved via HTTP`() {
        var resolved = false
        val resolver = UrlShortenerResolver(httpHead = { resolved = true; "https://example.com/article" })
        resolver.resolve("https://t.co/abc123")
        assertTrue(resolved, "t.co should be resolved")
    }

    @Test
    fun `HTTP failure returns original URL`() {
        val resolver = UrlShortenerResolver(httpHead = { null })
        val url = "https://jb.gg/abc123"
        assertEquals(url, resolver.resolve(url))
    }

    @Test
    fun `default shorteners include common URL shorteners`() {
        val shorteners = UrlShortenerResolver.DEFAULT_SHORTENERS
        assertTrue("jb.gg" in shorteners)
        assertTrue("t.co" in shorteners)
        assertTrue("bit.ly" in shorteners)
        assertTrue("ow.ly" in shorteners)
        assertTrue("tinyurl.com" in shorteners)
    }

    @Test
    fun `invalid URL returns original unchanged`() {
        val resolver = UrlShortenerResolver(httpHead = { "https://example.com" })
        val url = "not-a-valid-url"
        assertEquals(url, resolver.resolve(url))
    }
}
