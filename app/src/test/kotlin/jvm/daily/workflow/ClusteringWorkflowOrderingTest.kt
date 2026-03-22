package jvm.daily.workflow

import jvm.daily.ai.LLMClient
import jvm.daily.model.ArticleCluster
import jvm.daily.model.ProcessedArticle
import jvm.daily.storage.ClusterRepository
import jvm.daily.storage.ProcessedArticleRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Tests the 3-tier cluster ordering produced by ClusteringWorkflow:
 *   1. MAJOR clusters (pinned to top, sorted by engagement)
 *   2. Topic clusters (sorted by engagement)
 *   3. Release clusters (type=release, sorted by engagement last)
 *
 * The LLM mock works as follows:
 *   - Call 0: returns the GROUP/INDICES grouping response
 *   - Calls 1+: returns synthesis in the same order GROUPs were listed (one call per cluster)
 */
class ClusteringWorkflowOrderingTest {

    private val fixedNow = Instant.parse("2026-03-15T12:00:00Z")
    private val clock = object : Clock { override fun now() = fixedNow }

    @Test
    fun `release clusters are saved last regardless of engagement score`() = runTest {
        val articles = articles("a1" to "Hibernate 7.3.0", "a2" to "Ktor 3.1.2", "a3" to "JDK Roadmap")

        // Hibernate (score=10) + Ktor (score=20) as separate release clusters, JDK Roadmap (score=30) topic
        // Release clusters must be last even though their combined engagement would be higher
        val saved = captureSaved(
            articles,
            groupResponse = """
                GROUP: JDK Roadmap
                INDICES: 2
                GROUP: Hibernate 7.3.0
                RELEASE: YES
                INDICES: 0
                GROUP: Ktor 3.1.2
                RELEASE: YES
                INDICES: 1
            """.trimIndent(),
            synthesisTitles = listOf("JDK Roadmap", "Hibernate 7.3.0", "Ktor 3.1.2"),
        )

        assertEquals(3, saved.size)
        assertEquals("JDK Roadmap", saved.first().title, "Topic cluster first")
        assertEquals("release", saved[1].type, "Second cluster is a release")
        assertEquals("release", saved[2].type, "Third cluster is a release")
    }

    @Test
    fun `MAJOR cluster is saved first regardless of engagement score`() = runTest {
        val articles = articles("a1" to "Hibernate 7.3.0", "a2" to "Normal Article", "a3" to "JDK 26 GA")
        // Article scores: a1=10, a2=20, a3=30 — but JDK 26 GA is MAJOR, listed last in GROUPs

        val saved = captureSaved(
            articles,
            groupResponse = """
                GROUP: Normal Cluster
                INDICES: 1
                GROUP: Hibernate 7.3.0
                RELEASE: YES
                INDICES: 0
                GROUP: JDK 26 GA
                MAJOR: YES
                INDICES: 2
            """.trimIndent(),
            synthesisTitles = listOf("Normal Cluster", "Hibernate 7.3.0", "JDK 26 GA"),
        )

        assertEquals(3, saved.size)
        assertEquals("JDK 26 GA", saved.first().title, "MAJOR cluster must be first")
        assertEquals("Normal Cluster", saved[1].title, "Topic cluster in middle")
        assertEquals("Hibernate 7.3.0", saved.last().title, "Release cluster must be last")
    }

    @Test
    fun `topic clusters between MAJOR and release clusters are ordered by engagement`() = runTest {
        // Scores: a1=10, a2=20, a3=30, a4=40, a5=50
        val articles = articles(
            "a1" to "Low Topic",        // score=10
            "a2" to "Mid Topic",        // score=20
            "a3" to "High Topic",       // score=30
            "a4" to "JDK 26 GA",        // score=40
            "a5" to "Hibernate 7.3.0",  // score=50
        )

        val saved = captureSaved(
            articles,
            groupResponse = """
                GROUP: JDK 26 GA
                MAJOR: YES
                INDICES: 3
                GROUP: Low Topic
                INDICES: 0
                GROUP: High Topic
                INDICES: 2
                GROUP: Mid Topic
                INDICES: 1
                GROUP: Hibernate 7.3.0
                RELEASE: YES
                INDICES: 4
            """.trimIndent(),
            synthesisTitles = listOf("JDK 26 GA", "Low Topic", "High Topic", "Mid Topic", "Hibernate 7.3.0"),
        )

        assertEquals(5, saved.size)
        assertEquals("JDK 26 GA", saved[0].title, "MAJOR first")
        assertEquals("High Topic", saved[1].title, "Highest topic engagement second (score=30)")
        assertEquals("Mid Topic", saved[2].title, "Mid engagement third (score=20)")
        assertEquals("Low Topic", saved[3].title, "Lowest engagement fourth (score=10)")
        assertEquals("Hibernate 7.3.0", saved[4].title, "Release cluster last")
    }

    @Test
    fun `digest with only release clusters still works`() = runTest {
        val articles = articles("a1" to "Micronaut 4.x", "a2" to "Quarkus 3.y")

        val saved = captureSaved(
            articles,
            groupResponse = """
                GROUP: Micronaut 4.x
                RELEASE: YES
                INDICES: 0
                GROUP: Quarkus 3.y
                RELEASE: YES
                INDICES: 1
            """.trimIndent(),
            synthesisTitles = listOf("Micronaut 4.x", "Quarkus 3.y"),
        )

        assertEquals(2, saved.size)
        assertEquals("release", saved[0].type)
        assertEquals("release", saved[1].type)
    }

    @Test
    fun `MAJOR NO is treated same as omitting MAJOR line`() = runTest {
        val articles = articles("a1" to "Some Feature", "a2" to "Kotlin 2.3.20")

        val saved = captureSaved(
            articles,
            groupResponse = """
                GROUP: Some Feature
                MAJOR: NO
                INDICES: 0
                GROUP: Kotlin 2.3.20
                MAJOR: YES
                INDICES: 1
            """.trimIndent(),
            synthesisTitles = listOf("Some Feature", "Kotlin 2.3.20"),
        )

        assertEquals(2, saved.size)
        assertEquals("Kotlin 2.3.20", saved.first().title, "MAJOR: YES cluster pinned first")
        assertEquals("Some Feature", saved.last().title, "MAJOR: NO is a normal cluster")
    }

    @Test
    fun `multiple MAJOR clusters sorted by engagement among themselves`() = runTest {
        // Scores: a1=10, a2=20, a3=30, a4=40
        val articles = articles(
            "a1" to "JDK 26 GA",        // score=10
            "a2" to "Normal Low",        // score=20
            "a3" to "Kotlin 2.4.0",      // score=30
            "a4" to "Normal High",       // score=40
        )

        val saved = captureSaved(
            articles,
            groupResponse = """
                GROUP: JDK 26 GA
                MAJOR: YES
                INDICES: 0
                GROUP: Normal Low
                INDICES: 1
                GROUP: Kotlin 2.4.0
                MAJOR: YES
                INDICES: 2
                GROUP: Normal High
                INDICES: 3
            """.trimIndent(),
            synthesisTitles = listOf("JDK 26 GA", "Normal Low", "Kotlin 2.4.0", "Normal High"),
        )

        assertEquals(4, saved.size)
        // MAJOR tier: Kotlin 2.4.0 (score=30) > JDK 26 GA (score=10)
        assertEquals("Kotlin 2.4.0", saved[0].title, "Higher-engagement MAJOR first")
        assertEquals("JDK 26 GA", saved[1].title, "Lower-engagement MAJOR second")
        // Normal tier: Normal High (40) > Normal Low (20)
        assertEquals("Normal High", saved[2].title, "Higher normal engagement third")
        assertEquals("Normal Low", saved[3].title, "Lower normal engagement fourth")
    }

    @Test
    fun `Bluesky repost of RSS article is deduplicated — RSS article wins`() = runTest {
        val sharedUrl = "https://foojay.io/articles/leyden-part2"
        val rssArticle = article("rss-1", "Leyden Part 2", sourceType = "rss", url = sharedUrl, score = 5.0)
        val bskyArticle = article("bsky-1", "[Foojay] Leyden Part 2", sourceType = "bluesky", url = sharedUrl, score = 15.0)
        val uniqueArticle = article("rss-2", "Other News", sourceType = "rss", url = "https://example.com/other", score = 8.0)

        val saved = captureSaved(
            listOf(rssArticle, bskyArticle, uniqueArticle),
            groupResponse = "GROUP: Leyden\nINDICES: 0\nGROUP: Other\nINDICES: 1",
            synthesisTitles = listOf("Leyden", "Other"),
        )

        val allArticleIds = saved.flatMap { it.articles }
        assertTrue("rss-1" in allArticleIds, "RSS article should be kept")
        assertTrue("bsky-1" !in allArticleIds, "Bluesky repost should be removed")
        assertTrue("rss-2" in allArticleIds, "Unique article should be kept")
        assertEquals(2, allArticleIds.size)
    }

    @Test
    fun `GROUP response with leading whitespace is parsed correctly`() = runTest {
        val articles = articles("a1" to "Kotlin 2.3.20", "a2" to "Hibernate 7.3.0")

        val saved = captureSaved(
            articles,
            // Gemini sometimes adds leading spaces or indentation
            groupResponse = "  GROUP: Kotlin 2.3.20\n  INDICES: 0\n  GROUP: Hibernate 7.3.0\n  RELEASE: YES\n  INDICES: 1",
            synthesisTitles = listOf("Kotlin 2.3.20", "Hibernate 7.3.0"),
        )

        assertEquals(2, saved.size)
        assertEquals("Kotlin 2.3.20", saved.first().title)
    }

    @Test
    fun `GROUP response with markdown bold markers is parsed correctly`() = runTest {
        val articles = articles("a1" to "Spring Boot 4", "a2" to "Micronaut 4.x")

        val saved = captureSaved(
            articles,
            // Gemini sometimes wraps keys in **bold**
            groupResponse = "**GROUP:** Spring Boot 4\n**INDICES:** 0\n**GROUP:** Micronaut 4.x\n**RELEASE:** YES\n**INDICES:** 1",
            synthesisTitles = listOf("Spring Boot 4", "Micronaut 4.x"),
        )

        assertEquals(2, saved.size)
        assertEquals("Spring Boot 4", saved.first().title)
    }

    @Test
    fun `MAJOR YES with trailing comment is still recognized`() = runTest {
        val articles = articles("a1" to "JDK 26 GA", "a2" to "Micronaut 4.x")

        val saved = captureSaved(
            articles,
            groupResponse = "GROUP: JDK 26 GA\nMAJOR: YES   ← important\nINDICES: 0\nGROUP: Micronaut 4.x\nRELEASE: YES\nINDICES: 1",
            synthesisTitles = listOf("JDK 26 GA", "Micronaut 4.x"),
        )

        assertEquals(2, saved.size)
        assertEquals("JDK 26 GA", saved.first().title, "MAJOR cluster should be first")
    }

    @Test
    fun `Reddit repost is also deduplicated when URL matches RSS`() = runTest {
        val sharedUrl = "https://spring.io/blog/spring-boot-4"
        val rssArticle = article("rss-1", "Spring Boot 4", sourceType = "rss", url = sharedUrl, score = 10.0)
        val redditArticle = article("reddit-1", "Spring Boot 4 on Reddit", sourceType = "reddit", url = sharedUrl, score = 50.0)

        val saved = captureSaved(
            listOf(rssArticle, redditArticle),
            groupResponse = "GROUP: Spring Boot\nINDICES: 0",
            synthesisTitles = listOf("Spring Boot"),
        )

        val allArticleIds = saved.flatMap { it.articles }
        assertTrue("rss-1" in allArticleIds, "RSS article should be kept")
        assertTrue("reddit-1" !in allArticleIds, "Reddit repost should be removed")
        assertEquals(1, allArticleIds.size)
    }

    @Test
    fun `RELEASE YES produces cluster with type release`() = runTest {
        val articles = articles("a1" to "Spring Boot 4.1.0-M3 Announced", "a2" to "Kotlin 2.3.20")

        val saved = captureSavedRaw(
            articles,
            groupResponse = """
                GROUP: Spring Boot 4.1.0-M3
                RELEASE: YES
                INDICES: 0
                GROUP: Kotlin 2.3.20
                RELEASE: YES
                INDICES: 1
            """.trimIndent(),
            synthesisResponses = listOf(
                "TITLE: Spring Boot 4.1.0-M3\nBULLET: Virtual threads on by default\nBULLET: New @RestClientTest slice",
                "TITLE: Kotlin 2.3.20\nBULLET: Context parameters preview\nBULLET: K2 compiler stabilized",
            ),
        )

        assertEquals(2, saved.size)
        val springCluster = saved.first { it.title == "Spring Boot 4.1.0-M3" }
        assertEquals("release", springCluster.type)
        assertEquals(listOf("Virtual threads on by default", "New @RestClientTest slice"), springCluster.bullets)
        assertEquals("", springCluster.summary, "Release clusters have empty summary")
    }

    @Test
    fun `RELEASE NO is treated same as omitting RELEASE line — type is topic`() = runTest {
        val articles = articles("a1" to "Spring Security Discussion")

        val saved = captureSavedRaw(
            articles,
            groupResponse = "GROUP: Spring Security\nRELEASE: NO\nINDICES: 0",
            synthesisResponses = listOf("TITLE: Spring Security\nSYNTHESIS: Spring security discussion."),
        )

        assertEquals(1, saved.size)
        assertEquals("topic", saved.first().type)
        assertEquals(emptyList<String>(), saved.first().bullets)
    }

    @Test
    fun `bullet synthesis caps at 5 even if LLM returns more`() = runTest {
        val articles = articles("a1" to "Spring Boot 4.1.0-M3")

        val saved = captureSavedRaw(
            articles,
            groupResponse = "GROUP: Spring Boot 4.1.0-M3\nRELEASE: YES\nINDICES: 0",
            synthesisResponses = listOf(
                "TITLE: Spring Boot 4.1.0-M3\n" +
                "BULLET: One\nBULLET: Two\nBULLET: Three\nBULLET: Four\nBULLET: Five\nBULLET: Six\nBULLET: Seven",
            ),
        )

        assertEquals(1, saved.size)
        assertEquals(5, saved.first().bullets.size, "bullets must be capped at 5")
    }

    @Test
    fun `all release clusters sink to bottom sorted by engagement among themselves`() = runTest {
        val articles = articles(
            "a1" to "Spring Boot 4.1.0-M3",  // score=10, release
            "a2" to "Hibernate 7.3.0",        // score=20, release
            "a3" to "JVM Roadmap Discussion", // score=30, topic
        )

        val saved = captureSavedRaw(
            articles,
            groupResponse = """
                GROUP: Spring Boot 4.1.0-M3
                RELEASE: YES
                INDICES: 0
                GROUP: JVM Roadmap Discussion
                INDICES: 2
                GROUP: Hibernate 7.3.0
                RELEASE: YES
                INDICES: 1
            """.trimIndent(),
            synthesisResponses = listOf(
                "TITLE: Spring Boot 4.1.0-M3\nBULLET: Something new",
                "TITLE: JVM Roadmap Discussion\nSYNTHESIS: Roadmap stuff.",
                "TITLE: Hibernate 7.3.0\nBULLET: Hibernate released",
            ),
        )

        assertEquals(3, saved.size)
        assertEquals("JVM Roadmap Discussion", saved.first().title, "Topic cluster first")
        // Release clusters last, sorted by engagement: Hibernate (score=20) > Spring Boot (score=10)
        assertEquals("Hibernate 7.3.0", saved[1].title, "Higher-engagement release second")
        assertEquals("Spring Boot 4.1.0-M3", saved[2].title, "Lower-engagement release last")
        assertEquals("release", saved[1].type)
        assertEquals("release", saved[2].type)
    }

    // Helpers

    private fun articles(vararg pairs: Pair<String, String>): List<ProcessedArticle> =
        pairs.mapIndexed { i, (id, title) ->
            ProcessedArticle(
                id = id, originalTitle = title, normalizedTitle = title.lowercase(),
                summary = "Summary of $title", originalContent = "content",
                sourceType = "rss", sourceId = "test", url = "https://example.com/$id",
                publishedAt = fixedNow, ingestedAt = fixedNow, processedAt = fixedNow,
                topics = listOf("jvm"),
                engagementScore = 10.0 * (i + 1),
            )
        }

    private fun article(
        id: String, title: String,
        sourceType: String = "rss",
        url: String = "https://example.com/$id",
        score: Double = 10.0,
    ) = ProcessedArticle(
        id = id, originalTitle = title, normalizedTitle = title.lowercase(),
        summary = "Summary of $title", originalContent = "content",
        sourceType = sourceType, sourceId = "test", url = url,
        publishedAt = fixedNow, ingestedAt = fixedNow, processedAt = fixedNow,
        topics = listOf("jvm"), engagementScore = score,
    )

    /**
     * LLM mock: call 0 = grouping response; calls 1+ = synthesis in GROUP order.
     * [synthesisTitles] must match the GROUP order in [groupResponse].
     */
    private fun llmWith(groupResponse: String, synthesisTitles: List<String>) = object : LLMClient {
        private var callCount = 0
        override suspend fun chat(prompt: String): String {
            return if (callCount == 0) {
                callCount++
                groupResponse
            } else {
                val name = synthesisTitles.getOrElse(callCount - 1) { "Cluster" }
                callCount++
                "TITLE: $name\nSYNTHESIS: Synthesis about $name."
            }
        }
    }

    /**
     * LLM mock with explicit raw synthesis responses.
     * Call 0 = grouping; calls 1+ = synthesis responses in GROUP order.
     * NOTE: assumes all articles are non-social (rss/github). Social articles (bluesky/twitter)
     * trigger an extra isEventLogisticsPost LLM call per article before grouping, which would
     * misalign the synthesisResponses indices.
     */
    private fun llmWithRaw(groupResponse: String, synthesisResponses: List<String>) = object : LLMClient {
        private var callCount = 0
        override suspend fun chat(prompt: String): String {
            return if (callCount == 0) {
                callCount++
                groupResponse
            } else {
                val r = synthesisResponses.getOrElse(callCount - 1) { "TITLE: Cluster\nSYNTHESIS: ..." }
                callCount++
                r
            }
        }
    }

    private suspend fun captureSavedRaw(
        articles: List<ProcessedArticle>,
        groupResponse: String,
        synthesisResponses: List<String>,
    ): List<ArticleCluster> {
        val saved = mutableListOf<ArticleCluster>()
        val clusterRepo = object : ClusterRepository {
            override fun save(c: ArticleCluster) {}
            override fun saveAll(clusters: List<ArticleCluster>) { saved.addAll(clusters) }
            override fun findByDateRange(start: Instant, end: Instant): List<ArticleCluster> = emptyList()
            override fun deleteByDateRange(start: Instant, end: Instant) {}
        }
        val articleRepo = object : ProcessedArticleRepository {
            override fun save(a: ProcessedArticle) {}
            override fun saveAll(a: List<ProcessedArticle>) {}
            override fun findAll(): List<ProcessedArticle> = articles
            override fun findByDateRange(s: Instant, e: Instant): List<ProcessedArticle> = articles
            override fun findFailedSince(since: Instant): List<ProcessedArticle> = emptyList()
            override fun findFailedRawArticleIds(since: Instant, limit: Int): List<String> = emptyList()
            override fun findFailedByIds(ids: List<String>): List<ProcessedArticle> = emptyList()
            override fun findInspectionCandidates(since: Instant, limit: Int, minWarnings: Int): List<ProcessedArticle> = emptyList()
            override fun findByIds(ids: List<String>): List<ProcessedArticle> = articles.filter { it.id in ids }
            override fun findByIngestedAtRange(s: Instant, e: Instant): List<ProcessedArticle> = articles
            override fun findUnprocessedRawArticles(since: Instant): List<String> = emptyList()
            override fun existsById(id: String): Boolean = false
            override fun count(): Long = articles.size.toLong()
            override fun deleteByProcessedAtSince(since: Instant): Int = 0
        }
        ClusteringWorkflow(articleRepo, clusterRepo, llmWithRaw(groupResponse, synthesisResponses), clock).execute()
        assertTrue(saved.isNotEmpty(), "ClusteringWorkflow should produce at least one cluster")
        return saved
    }

    private suspend fun captureSaved(
        articles: List<ProcessedArticle>,
        groupResponse: String,
        synthesisTitles: List<String>,
    ): List<ArticleCluster> {
        val saved = mutableListOf<ArticleCluster>()
        val clusterRepo = object : ClusterRepository {
            override fun save(c: ArticleCluster) {}
            override fun saveAll(clusters: List<ArticleCluster>) { saved.addAll(clusters) }
            override fun findByDateRange(start: Instant, end: Instant): List<ArticleCluster> = emptyList()
            override fun deleteByDateRange(start: Instant, end: Instant) {}
        }
        val articleRepo = object : ProcessedArticleRepository {
            override fun save(a: ProcessedArticle) {}
            override fun saveAll(a: List<ProcessedArticle>) {}
            override fun findAll(): List<ProcessedArticle> = articles
            override fun findByDateRange(s: Instant, e: Instant): List<ProcessedArticle> = articles
            override fun findFailedSince(since: Instant): List<ProcessedArticle> = emptyList()
            override fun findFailedRawArticleIds(since: Instant, limit: Int): List<String> = emptyList()
            override fun findFailedByIds(ids: List<String>): List<ProcessedArticle> = emptyList()
            override fun findInspectionCandidates(since: Instant, limit: Int, minWarnings: Int): List<ProcessedArticle> = emptyList()
            override fun findByIds(ids: List<String>): List<ProcessedArticle> = articles.filter { it.id in ids }
            override fun findByIngestedAtRange(s: Instant, e: Instant): List<ProcessedArticle> = articles
            override fun findUnprocessedRawArticles(since: Instant): List<String> = emptyList()
            override fun existsById(id: String): Boolean = false
            override fun count(): Long = articles.size.toLong()
            override fun deleteByProcessedAtSince(since: Instant): Int = 0
        }
        ClusteringWorkflow(articleRepo, clusterRepo, llmWith(groupResponse, synthesisTitles), clock).execute()
        assertTrue(saved.isNotEmpty(), "ClusteringWorkflow should produce at least one cluster")
        return saved
    }
}
