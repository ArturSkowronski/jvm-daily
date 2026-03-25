package jvm.daily.source

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownFileSourceTest {

    @TempDir
    lateinit var tempDir: Path

    private val fixedClock = object : Clock {
        override fun now(): Instant = Instant.parse("2026-02-09T10:00:00Z")
    }

    @Test
    fun `fetch reads markdown files from directory`() = runTest {
        tempDir.resolve("article1.md").writeText("# Hello World\n\nSome content here.")
        tempDir.resolve("article2.md").writeText("# Second Article\n\nMore content.")

        val source = MarkdownFileSource(tempDir, fixedClock)
        val articles = source.fetch()

        assertEquals(2, articles.size)
        assertEquals("markdown_file", source.sourceType)
    }

    @Test
    fun `fetch extracts title from first heading`() = runTest {
        tempDir.resolve("test.md").writeText("# My Great Title\n\nBody text.")

        val source = MarkdownFileSource(tempDir, fixedClock)
        val articles = source.fetch()

        assertEquals("My Great Title", articles[0].title)
    }

    @Test
    fun `fetch uses filename as fallback title when no heading`() = runTest {
        tempDir.resolve("no-heading.md").writeText("Just plain text without a heading.")

        val source = MarkdownFileSource(tempDir, fixedClock)
        val articles = source.fetch()

        assertEquals("no-heading", articles[0].title)
    }

    @Test
    fun `fetch returns empty list for nonexistent directory`() = runTest {
        val source = MarkdownFileSource(tempDir.resolve("nonexistent"), fixedClock)
        val articles = source.fetch()

        assertTrue(articles.isEmpty())
    }

    @Test
    fun `fetch ignores non-markdown files`() = runTest {
        tempDir.resolve("article.md").writeText("# Article\n\nContent.")
        tempDir.resolve("readme.txt").writeText("Not a markdown file.")
        tempDir.resolve("data.json").writeText("{}")

        val source = MarkdownFileSource(tempDir, fixedClock)
        val articles = source.fetch()

        assertEquals(1, articles.size)
    }

    @Test
    fun `article id is prefixed with md`() = runTest {
        tempDir.resolve("my-article.md").writeText("# Title\n\nContent.")

        val source = MarkdownFileSource(tempDir, fixedClock)
        val articles = source.fetch()

        assertEquals("md:my-article", articles[0].id)
    }

    @Test
    fun `article has correct ingestedAt from clock`() = runTest {
        tempDir.resolve("test.md").writeText("# Test\n\nContent.")

        val source = MarkdownFileSource(tempDir, fixedClock)
        val articles = source.fetch()

        assertEquals(Instant.parse("2026-02-09T10:00:00Z"), articles[0].ingestedAt)
    }
}
