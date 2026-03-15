package jvm.daily.workflow

import jvm.daily.model.ArticleCluster
import jvm.daily.model.ProcessedArticle
import jvm.daily.storage.ClusterRepository
import jvm.daily.storage.ProcessedArticleRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists
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

    // --- JSON digest tests ---

    @Test
    fun `with clusterRepository writes daily JSON file with clusters`(@TempDir tempDir: Path) = runTest {
        val fixedNow = Instant.parse("2026-03-15T12:00:00Z")
        val clock = object : Clock { override fun now() = fixedNow }

        val articles = listOf(
            processedArticle("a1", "Spring Boot 4", "https://example.com/spring", listOf("spring"), 90.0, fixedNow),
            processedArticle("a2", "Kotlin Coroutines", "https://example.com/kotlin", listOf("kotlin"), 80.0, fixedNow),
        )
        val cluster = ArticleCluster(
            id = "c1", title = "Spring News", summary = "Spring stuff",
            articles = listOf("a1"), sources = listOf("rss"), totalEngagement = 90.0,
            createdAt = fixedNow,
        )

        val repo = stubRepoWithData(articles)
        val clusterRepo = stubClusterRepo(listOf(cluster))

        OutgressWorkflow(repo, tempDir, clock = clock, clusterRepository = clusterRepo).execute()

        val jsonFile = tempDir.resolve("daily-2026-03-15.json")
        assertTrue(jsonFile.exists(), "JSON file should be created")
        val content = jsonFile.readText()
        assertContains(content, "Spring News")
        assertContains(content, "Spring Boot 4")
    }

    @Test
    fun `unclustered articles appear in unclustered list`(@TempDir tempDir: Path) = runTest {
        val fixedNow = Instant.parse("2026-03-15T12:00:00Z")
        val clock = object : Clock { override fun now() = fixedNow }

        val articles = listOf(
            processedArticle("a1", "Clustered Article", "https://example.com/1", listOf("spring"), 90.0, fixedNow),
            processedArticle("a2", "Unclustered Article", "https://example.com/2", listOf("misc"), 50.0, fixedNow),
        )
        val cluster = ArticleCluster(
            id = "c1", title = "Spring News", summary = "Spring stuff",
            articles = listOf("a1"), sources = listOf("rss"), totalEngagement = 90.0,
            createdAt = fixedNow,
        )

        val repo = stubRepoWithData(articles)
        val clusterRepo = stubClusterRepo(listOf(cluster))

        OutgressWorkflow(repo, tempDir, clock = clock, clusterRepository = clusterRepo).execute()

        val json = tempDir.resolve("daily-2026-03-15.json").readText()
        val digest = Json.decodeFromString<DigestJson>(json)
        assertEquals(1, digest.unclustered.size)
        assertEquals("Unclustered Article", digest.unclustered.first().title)
    }

    @Test
    fun `empty clusters produces empty clusters list with all articles in unclustered`(@TempDir tempDir: Path) = runTest {
        val fixedNow = Instant.parse("2026-03-15T12:00:00Z")
        val clock = object : Clock { override fun now() = fixedNow }

        val articles = listOf(
            processedArticle("a1", "Article One", "https://example.com/1", listOf("misc"), 70.0, fixedNow),
            processedArticle("a2", "Article Two", "https://example.com/2", listOf("misc"), 60.0, fixedNow),
        )

        val repo = stubRepoWithData(articles)
        val clusterRepo = stubClusterRepo(emptyList())

        OutgressWorkflow(repo, tempDir, clock = clock, clusterRepository = clusterRepo).execute()

        val json = tempDir.resolve("daily-2026-03-15.json").readText()
        val digest = Json.decodeFromString<DigestJson>(json)
        assertTrue(digest.clusters.isEmpty())
        assertEquals(2, digest.unclustered.size)
    }

    @Test
    fun `without clusterRepository only md file is written`(@TempDir tempDir: Path) = runTest {
        val fixedNow = Instant.parse("2026-03-15T12:00:00Z")
        val clock = object : Clock { override fun now() = fixedNow }

        val articles = listOf(
            processedArticle("a1", "Article", "https://example.com/1", emptyList(), 70.0, fixedNow),
        )
        val repo = stubRepo(articles)

        OutgressWorkflow(repo, tempDir, clock = clock).execute()

        assertTrue(tempDir.resolve("jvm-daily-2026-03-15.md").exists())
        assertFalse(tempDir.resolve("daily-2026-03-15.json").exists(), "JSON file should not be created without clusterRepository")
    }

    @Test
    fun `totalArticles equals clusteredCount plus unclusteredCount`(@TempDir tempDir: Path) = runTest {
        val fixedNow = Instant.parse("2026-03-15T12:00:00Z")
        val clock = object : Clock { override fun now() = fixedNow }

        val articles = listOf(
            processedArticle("a1", "Clustered 1", "https://example.com/1", listOf("spring"), 90.0, fixedNow),
            processedArticle("a2", "Clustered 2", "https://example.com/2", listOf("spring"), 80.0, fixedNow),
            processedArticle("a3", "Unclustered", "https://example.com/3", listOf("misc"), 50.0, fixedNow),
        )
        val cluster = ArticleCluster(
            id = "c1", title = "Spring", summary = "Spring stuff",
            articles = listOf("a1", "a2"), sources = listOf("rss"), totalEngagement = 170.0,
            createdAt = fixedNow,
        )

        val repo = stubRepoWithData(articles)
        val clusterRepo = stubClusterRepo(listOf(cluster))

        OutgressWorkflow(repo, tempDir, clock = clock, clusterRepository = clusterRepo).execute()

        val json = tempDir.resolve("daily-2026-03-15.json").readText()
        val digest = Json.decodeFromString<DigestJson>(json)
        assertEquals(3, digest.totalArticles)
        assertEquals(2, digest.clusters.first().articles.size)
        assertEquals(1, digest.unclustered.size)
    }

    // --- stubs ---

    private fun stubRepo(articles: List<ProcessedArticle>) = object : ProcessedArticleRepository {
        override fun save(article: ProcessedArticle) {}
        override fun saveAll(articles: List<ProcessedArticle>) {}
        override fun findAll(): List<ProcessedArticle> = articles
        override fun findByDateRange(startDate: Instant, endDate: Instant): List<ProcessedArticle> = articles
        override fun findFailedSince(since: Instant): List<ProcessedArticle> = emptyList()
        override fun findFailedRawArticleIds(since: Instant, limit: Int): List<String> = emptyList()
        override fun findFailedByIds(ids: List<String>): List<ProcessedArticle> = emptyList()
        override fun findInspectionCandidates(since: Instant, limit: Int, minWarnings: Int): List<ProcessedArticle> =
            articles
                .filter { it.processedAt >= since && it.warnings.size >= minWarnings }
                .sortedByDescending { it.processedAt }
                .take(limit.coerceAtLeast(0))
        override fun findByIds(ids: List<String>): List<ProcessedArticle> = emptyList()
        override fun findByIngestedAtRange(start: Instant, end: Instant): List<ProcessedArticle> = emptyList()
        override fun findUnprocessedRawArticles(since: Instant): List<String> = emptyList()
        override fun existsById(id: String): Boolean = false
        override fun count(): Long = articles.size.toLong()
    }

    private fun stubRepoWithData(articles: List<ProcessedArticle>) = object : ProcessedArticleRepository {
        override fun save(article: ProcessedArticle) {}
        override fun saveAll(articles: List<ProcessedArticle>) {}
        override fun findAll(): List<ProcessedArticle> = articles
        override fun findByDateRange(startDate: Instant, endDate: Instant): List<ProcessedArticle> = articles
        override fun findFailedSince(since: Instant): List<ProcessedArticle> = emptyList()
        override fun findFailedRawArticleIds(since: Instant, limit: Int): List<String> = emptyList()
        override fun findFailedByIds(ids: List<String>): List<ProcessedArticle> = emptyList()
        override fun findInspectionCandidates(since: Instant, limit: Int, minWarnings: Int): List<ProcessedArticle> = emptyList()
        override fun findByIds(ids: List<String>): List<ProcessedArticle> = articles.filter { it.id in ids }
        override fun findByIngestedAtRange(start: Instant, end: Instant): List<ProcessedArticle> = articles
        override fun findUnprocessedRawArticles(since: Instant): List<String> = emptyList()
        override fun existsById(id: String): Boolean = false
        override fun count(): Long = articles.size.toLong()
    }

    private fun stubClusterRepo(clusters: List<ArticleCluster>) = object : ClusterRepository {
        override fun save(cluster: ArticleCluster) {}
        override fun saveAll(clusters: List<ArticleCluster>) {}
        override fun findByDateRange(start: Instant, end: Instant): List<ArticleCluster> = clusters
        override fun deleteByDateRange(start: Instant, end: Instant) {}
    }
}
