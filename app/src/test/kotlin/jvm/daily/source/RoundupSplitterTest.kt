package jvm.daily.source

import jvm.daily.ai.LLMClient
import jvm.daily.model.Article
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class RoundupSplitterTest {

    @Test
    fun `looksLikeRoundup detects roundup titles`() {
        assertTrue(RoundupSplitter.looksLikeRoundup(article("Java News Roundup: March 2026")))
        assertTrue(RoundupSplitter.looksLikeRoundup(article("This Week in Spring")))
        assertTrue(RoundupSplitter.looksLikeRoundup(article("Weekly Digest: JVM Ecosystem")))
        assertTrue(RoundupSplitter.looksLikeRoundup(article("News Round-Up: Kotlin Updates")))
    }

    @Test
    fun `looksLikeRoundup rejects normal titles`() {
        assertFalse(RoundupSplitter.looksLikeRoundup(article("Spring Boot 4.0 Released")))
        assertFalse(RoundupSplitter.looksLikeRoundup(article("Understanding Virtual Threads")))
        assertFalse(RoundupSplitter.looksLikeRoundup(article("GraalVM Native Image Guide")))
    }

    @Test
    fun `parseResponse extracts sub-articles from JSON`() {
        val json = """
        [
            {"title":"Spring Boot 4.0 M3","url":"https://spring.io/blog/4.0-m3","summary":"Third milestone with Jakarta EE 11."},
            {"title":"GraalVM 24 Released","url":null,"summary":"New metadata repository for native image."},
            {"title":"Hibernate 7.0 GA","url":"https://hibernate.org/7.0","summary":"Jakarta Data support and new HQL parser."}
        ]
        """.trimIndent()

        val items = RoundupSplitter.parseResponse(json)
        assertEquals(3, items.size)
        assertEquals("Spring Boot 4.0 M3", items[0].title)
        assertEquals("https://spring.io/blog/4.0-m3", items[0].url)
        assertEquals(null, items[1].url)
        assertEquals("Hibernate 7.0 GA", items[2].title)
    }

    @Test
    fun `parseResponse handles markdown-wrapped JSON`() {
        val json = """
        ```json
        [{"title":"Test","url":null,"summary":"A test."}]
        ```
        """.trimIndent()

        val items = RoundupSplitter.parseResponse(json)
        assertEquals(1, items.size)
    }

    @Test
    fun `parseResponse returns empty on malformed input`() {
        assertEquals(0, RoundupSplitter.parseResponse("not json").size)
        assertEquals(0, RoundupSplitter.parseResponse("").size)
    }

    @Test
    fun `splitIfRoundup passes through non-roundup articles`() = runBlocking {
        val splitter = RoundupSplitter(MockLLMClient())
        val normal = article("Spring Boot 4.0 Released")
        val result = splitter.splitIfRoundup(normal)
        assertEquals(1, result.size)
        assertEquals(normal, result[0])
    }

    @Test
    fun `splitIfRoundup splits roundup articles`() = runBlocking {
        val llm = object : LLMClient {
            override suspend fun chat(prompt: String) = """
                [
                    {"title":"Spring Boot 4.0 M3","url":"https://spring.io/4.0-m3","summary":"Jakarta EE 11 baseline."},
                    {"title":"Kotlin 2.3 Released","url":"https://kotlinlang.org/2.3","summary":"New K2 compiler features."},
                    {"title":"Quarkus 4.0 GA","url":null,"summary":"Virtual threads by default."}
                ]
            """.trimIndent()
        }
        val splitter = RoundupSplitter(llm)
        val roundup = article("Java News Roundup: March 2026", "Spring Boot 4.0, Kotlin 2.3, Quarkus 4.0...")

        val result = splitter.splitIfRoundup(roundup)
        assertEquals(3, result.size)
        assertEquals("Spring Boot 4.0 M3", result[0].title)
        assertEquals("https://spring.io/4.0-m3", result[0].url)
        assertEquals("rss", result[0].sourceType)
        assertEquals("Kotlin 2.3 Released", result[1].title)
        assertEquals("Quarkus 4.0 GA", result[2].title)
        // All sub-articles have unique IDs
        assertEquals(3, result.map { it.id }.toSet().size)
    }

    @Test
    fun `splitIfRoundup falls back on LLM failure`() = runBlocking {
        val llm = object : LLMClient {
            override suspend fun chat(prompt: String) = "sorry I can't do that"
        }
        val splitter = RoundupSplitter(llm)
        val roundup = article("Java News Roundup: March 2026")

        val result = splitter.splitIfRoundup(roundup)
        assertEquals(1, result.size)
        assertEquals(roundup.title, result[0].title)
    }

    @Test
    fun `extractArticleSections extracts h4 sections`() {
        val html = """
            <html><head><script>alert('x')</script></head>
            <body>
            <h4>Spring Boot</h4><p>Spring Boot 4.0 released with <a href="https://spring.io">Jakarta EE 11</a>.</p>
            <h4>Kotlin 2.3</h4><p>New K2 compiler features for <a href="https://kotlinlang.org">multiplatform</a>.</p>
            </body></html>
        """.trimIndent()
        val text = RoundupSplitter.extractArticleSections(html)
        assertTrue(text.contains("## Spring Boot"))
        assertTrue(text.contains("## Kotlin 2.3"))
        assertTrue(text.contains("https://spring.io"))
        assertFalse(text.contains("alert"))
    }

    private fun article(title: String, content: String = "Some content") = Article(
        id = "test:$title",
        title = title,
        content = content,
        sourceType = "rss",
        sourceId = "https://feed.infoq.com/java",
        url = "https://example.com/article",
        ingestedAt = Clock.System.now(),
    )

    private class MockLLMClient : LLMClient {
        override suspend fun chat(prompt: String) = """{"summary":"mock","entities":[],"topics":[]}"""
    }
}
