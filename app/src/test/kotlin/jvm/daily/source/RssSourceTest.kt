package jvm.daily.source

import jvm.daily.config.RssFeedConfig
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RssSourceTest {

    @TempDir
    lateinit var tempDir: Path

    private val fixedClock = object : Clock {
        override fun now(): Instant = Instant.parse("2026-02-09T12:00:00Z")
    }

    private val sampleRss = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0">
          <channel>
            <title>Test Feed</title>
            <link>https://example.com</link>
            <item>
              <title>First Article</title>
              <link>https://example.com/first</link>
              <description>Content of first article</description>
              <author>john@example.com (John Doe)</author>
              <comments>https://example.com/first#comments</comments>
              <guid>https://example.com/first</guid>
            </item>
            <item>
              <title>Second Article</title>
              <link>https://example.com/second</link>
              <description>Content of second article</description>
              <guid>https://example.com/second</guid>
            </item>
          </channel>
        </rss>
    """.trimIndent()

    @Test
    fun `fetch parses rss feed entries`() = runTest {
        val feedFile = writeFeedFile("feed.xml", sampleRss)
        val source = RssSource(listOf(RssFeedConfig(feedFile)), fixedClock)

        val articles = source.fetch()

        assertEquals(2, articles.size)
    }

    @Test
    fun `fetch extracts title`() = runTest {
        val feedFile = writeFeedFile("feed.xml", sampleRss)
        val source = RssSource(listOf(RssFeedConfig(feedFile)), fixedClock)

        val articles = source.fetch()

        assertEquals("First Article", articles[0].title)
        assertEquals("Second Article", articles[1].title)
    }

    @Test
    fun `fetch extracts author`() = runTest {
        val feedFile = writeFeedFile("feed.xml", sampleRss)
        val source = RssSource(listOf(RssFeedConfig(feedFile)), fixedClock)

        val articles = source.fetch()

        assertEquals("john@example.com (John Doe)", articles[0].author)
        assertNull(articles[1].author)
    }

    @Test
    fun `fetch extracts content from description`() = runTest {
        val feedFile = writeFeedFile("feed.xml", sampleRss)
        val source = RssSource(listOf(RssFeedConfig(feedFile)), fixedClock)

        val articles = source.fetch()

        assertEquals("Content of first article", articles[0].content)
    }

    @Test
    fun `fetch extracts comments url`() = runTest {
        val feedFile = writeFeedFile("feed.xml", sampleRss)
        val source = RssSource(listOf(RssFeedConfig(feedFile)), fixedClock)

        val articles = source.fetch()

        assertEquals("https://example.com/first#comments", articles[0].comments)
        assertNull(articles[1].comments)
    }

    @Test
    fun `fetch sets url from link`() = runTest {
        val feedFile = writeFeedFile("feed.xml", sampleRss)
        val source = RssSource(listOf(RssFeedConfig(feedFile)), fixedClock)

        val articles = source.fetch()

        assertEquals("https://example.com/first", articles[0].url)
    }

    @Test
    fun `fetch generates rss-prefixed id`() = runTest {
        val feedFile = writeFeedFile("feed.xml", sampleRss)
        val source = RssSource(listOf(RssFeedConfig(feedFile)), fixedClock)

        val articles = source.fetch()

        assertEquals("rss:https://example.com/first", articles[0].id)
    }

    @Test
    fun `sourceType is rss`() {
        val source = RssSource(emptyList(), fixedClock)
        assertEquals("rss", source.sourceType)
    }

    @Test
    fun `fetch returns empty for invalid url`() = runTest {
        val source = RssSource(listOf(RssFeedConfig("https://nonexistent.invalid/feed.xml")), fixedClock)

        val articles = source.fetch()

        assertTrue(articles.isEmpty())
    }

    @Test
    fun `fetch handles empty feed`() = runTest {
        val emptyFeed = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>Empty Feed</title>
                <link>https://example.com</link>
              </channel>
            </rss>
        """.trimIndent()

        val feedFile = writeFeedFile("empty.xml", emptyFeed)
        val source = RssSource(listOf(RssFeedConfig(feedFile)), fixedClock)

        val articles = source.fetch()

        assertTrue(articles.isEmpty())
    }

    @Test
    fun `fetch aggregates from multiple feeds`() = runTest {
        val feed1 = writeFeedFile("feed1.xml", sampleRss)
        val feed2Xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>Another Feed</title>
                <item>
                  <title>Third Article</title>
                  <link>https://other.com/third</link>
                  <description>Third content</description>
                </item>
              </channel>
            </rss>
        """.trimIndent()
        val feed2 = writeFeedFile("feed2.xml", feed2Xml)

        val source = RssSource(
            listOf(RssFeedConfig(feed1), RssFeedConfig(feed2)),
            fixedClock
        )

        val articles = source.fetch()

        assertEquals(3, articles.size)
    }

    @Test
    fun `fetch uses clock for ingestedAt`() = runTest {
        val feedFile = writeFeedFile("feed.xml", sampleRss)
        val source = RssSource(listOf(RssFeedConfig(feedFile)), fixedClock)

        val articles = source.fetch()

        assertEquals(Instant.parse("2026-02-09T12:00:00Z"), articles[0].ingestedAt)
    }

    private fun writeFeedFile(name: String, content: String): String {
        val file = tempDir.resolve(name)
        file.writeText(content)
        return file.toUri().toString()
    }
}
