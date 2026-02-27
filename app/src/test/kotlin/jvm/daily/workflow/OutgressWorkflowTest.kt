package jvm.daily.workflow

import jvm.daily.model.ProcessedArticle
import jvm.daily.storage.ProcessedArticleRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OutgressWorkflowTest {

    @Test
    fun `shouldWriteMarkdownFileWithArticles`(@TempDir tempDir: Path) = runTest {
        val fixedNow = Instant.parse("2026-02-23T12:00:00Z")
        val clock = object : Clock { override fun now() = fixedNow }

        val articles = listOf(
            processedArticle("1", "Spring Boot 4.0 Released", "https://example.com/spring", listOf("framework-releases"), 80.0, fixedNow),
            processedArticle("2", "Kotlin 2.1 Features", "https://example.com/kotlin", listOf("language-updates"), 70.0, fixedNow),
        )
        val repo = stubRepo(articles)

        OutgressWorkflow(repo, tempDir, clock = clock).execute()

        val outputFile = tempDir.resolve("jvm-daily-2026-02-23.md")
        assertTrue(outputFile.toFile().exists())

        val content = outputFile.readText()
        assertContains(content, "# JVM Daily — 2026-02-23")
        assertContains(content, "Articles: 2")
        assertContains(content, "## Spring Boot 4.0 Released")
        assertContains(content, "https://example.com/spring")
        assertContains(content, "framework-releases")
        assertContains(content, "## Kotlin 2.1 Features")
    }

    @Test
    fun `shouldWriteOneFilePerDate`(@TempDir tempDir: Path) = runTest {
        val fixedNow = Instant.parse("2026-02-23T12:00:00Z")
        val day1 = Instant.parse("2026-02-10T10:00:00Z")
        val day2 = Instant.parse("2026-02-11T10:00:00Z")
        val clock = object : Clock { override fun now() = fixedNow }

        val articles = listOf(
            processedArticle("1", "Article on Day 1", "https://example.com/1", emptyList(), 70.0, day1),
            processedArticle("2", "Article on Day 2", "https://example.com/2", emptyList(), 80.0, day2),
        )
        val repo = stubRepo(articles)

        OutgressWorkflow(repo, tempDir, clock = clock).execute()

        assertTrue(tempDir.resolve("jvm-daily-2026-02-10.md").toFile().exists())
        assertTrue(tempDir.resolve("jvm-daily-2026-02-11.md").toFile().exists())

        val files = tempDir.toFile().listFiles()!!
        assertEquals(2, files.size)

        assertContains(tempDir.resolve("jvm-daily-2026-02-10.md").readText(), "Article on Day 1")
        assertContains(tempDir.resolve("jvm-daily-2026-02-11.md").readText(), "Article on Day 2")
    }

    @Test
    fun `shouldHandleNoArticles`(@TempDir tempDir: Path) = runTest {
        val fixedNow = Instant.parse("2026-02-23T12:00:00Z")
        val clock = object : Clock { override fun now() = fixedNow }

        OutgressWorkflow(stubRepo(emptyList()), tempDir, clock = clock).execute()

        val files = tempDir.toFile().listFiles() ?: emptyArray()
        assertFalse(files.any { it.name.startsWith("jvm-daily-") })
    }

    private fun processedArticle(
        id: String,
        title: String,
        url: String,
        topics: List<String>,
        engagementScore: Double,
        processedAt: Instant,
    ) = ProcessedArticle(
        id = id,
        originalTitle = title,
        normalizedTitle = title.lowercase(),
        summary = "Summary of $title",
        originalContent = "Content",
        sourceType = "rss",
        sourceId = "test",
        url = url,
        publishedAt = processedAt,
        ingestedAt = processedAt,
        processedAt = processedAt,
        topics = topics,
        engagementScore = engagementScore,
    )

    private fun stubRepo(articles: List<ProcessedArticle>) = object : ProcessedArticleRepository {
        override fun save(article: ProcessedArticle) {}
        override fun saveAll(articles: List<ProcessedArticle>) {}
        override fun findAll(): List<ProcessedArticle> = articles
        override fun findByDateRange(startDate: Instant, endDate: Instant): List<ProcessedArticle> = articles
        override fun findFailedSince(since: Instant): List<ProcessedArticle> = emptyList()
        override fun findFailedRawArticleIds(since: Instant, limit: Int): List<String> = emptyList()
        override fun findFailedByIds(ids: List<String>): List<ProcessedArticle> = emptyList()
        override fun findUnprocessedRawArticles(since: Instant): List<String> = emptyList()
        override fun existsById(id: String): Boolean = false
        override fun count(): Long = articles.size.toLong()
    }
}
