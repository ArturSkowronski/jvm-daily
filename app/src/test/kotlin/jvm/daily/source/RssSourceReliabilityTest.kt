package jvm.daily.source

import jvm.daily.config.RssFeedConfig
import jvm.daily.model.FeedIngestStatus
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RssSourceReliabilityTest {

    @TempDir
    lateinit var tempDir: Path

    private val fixedClock = object : Clock {
        override fun now(): Instant = Instant.parse("2026-02-27T22:00:00Z")
    }

    @Test
    fun `fetchOutcomes isolates failing feed from healthy feed`() = runTest {
        val healthyFeed = writeFeedFile(
            "healthy.xml",
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>Healthy Feed</title>
                <item>
                  <title>Article One</title>
                  <link>https://example.com/1</link>
                  <description>content</description>
                </item>
              </channel>
            </rss>
            """.trimIndent()
        )

        val source = RssSource(
            listOf(
                RssFeedConfig(healthyFeed),
                RssFeedConfig("https://nonexistent.invalid/feed.xml")
            ),
            fixedClock
        )

        val outcomes = source.fetchOutcomes()

        assertEquals(2, outcomes.size)

        val success = outcomes.first { it.feed.status != FeedIngestStatus.FAILED }
        val failure = outcomes.first { it.feed.status == FeedIngestStatus.FAILED }

        assertEquals(1, success.articles.size)
        assertEquals(FeedIngestStatus.SUCCESS, success.feed.status)

        assertTrue(failure.articles.isEmpty())
        assertTrue(failure.feed.errors.isNotEmpty())
    }

    @Test
    fun `fetchOutcomes marks malformed-entry feed as partial success`() = runTest {
        val malformedFeed = writeFeedFile(
            "partial.xml",
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>Partial Feed</title>
                <item>
                  <title>Valid Entry</title>
                  <link>https://example.com/valid</link>
                  <description>ok</description>
                </item>
                <item>
                  <title>Missing link entry</title>
                  <description>broken</description>
                </item>
              </channel>
            </rss>
            """.trimIndent()
        )

        val source = RssSource(listOf(RssFeedConfig(malformedFeed)), fixedClock)

        val outcomes = source.fetchOutcomes()

        assertEquals(1, outcomes.size)
        assertEquals(FeedIngestStatus.PARTIAL_SUCCESS, outcomes[0].feed.status)
        assertEquals(1, outcomes[0].articles.size)
        assertTrue(outcomes[0].feed.errors.any { it.contains("Skipped") })
    }

    private fun writeFeedFile(name: String, content: String): String {
        val file = tempDir.resolve(name)
        file.writeText(content)
        return file.toUri().toString()
    }
}
