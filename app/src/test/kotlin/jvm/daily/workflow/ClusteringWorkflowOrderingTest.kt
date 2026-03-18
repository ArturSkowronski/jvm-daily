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
import kotlin.test.assertTrue

/**
 * Tests the 3-tier cluster ordering produced by ClusteringWorkflow:
 *   1. MAJOR clusters (pinned to top, sorted by engagement)
 *   2. Normal clusters (sorted by engagement)
 *   3. "Releases" roundup cluster (always last)
 *
 * The LLM mock works as follows:
 *   - Call 0: returns the GROUP/INDICES grouping response
 *   - Calls 1+: returns synthesis in the same order GROUPs were listed (one call per cluster)
 */
class ClusteringWorkflowOrderingTest {

    private val fixedNow = Instant.parse("2026-03-15T12:00:00Z")
    private val clock = object : Clock { override fun now() = fixedNow }

    @Test
    fun `Releases cluster is saved last regardless of engagement score`() = runTest {
        val articles = articles("a1" to "Hibernate 7.3.0", "a2" to "Ktor 3.1.2", "a3" to "JDK Roadmap")

        // GROUP order: JDK Roadmap first, Releases second
        // Releases has higher combined engagement (a1+a2=30) than JDK Roadmap (a3=10) — but must still be last
        val saved = captureSaved(
            articles,
            groupResponse = """
                GROUP: JDK Roadmap
                INDICES: 2
                GROUP: Releases
                INDICES: 0, 1
            """.trimIndent(),
            synthesisTitles = listOf("JDK Roadmap", "Releases"),
        )

        assertEquals(2, saved.size)
        assertEquals("Releases", saved.last().title, "Releases must be last")
        assertEquals("JDK Roadmap", saved.first().title, "Non-releases cluster first")
    }

    @Test
    fun `MAJOR cluster is saved first regardless of engagement score`() = runTest {
        val articles = articles("a1" to "Low Score Article", "a2" to "Normal Article", "a3" to "JDK 26 GA")
        // Article scores: a1=10, a2=20, a3=30 — but JDK 26 GA is MAJOR, listed last in GROUPs

        val saved = captureSaved(
            articles,
            groupResponse = """
                GROUP: Normal Cluster
                INDICES: 1
                GROUP: Releases
                INDICES: 0
                GROUP: JDK 26 GA
                MAJOR: YES
                INDICES: 2
            """.trimIndent(),
            synthesisTitles = listOf("Normal Cluster", "Releases", "JDK 26 GA"),
        )

        assertEquals(3, saved.size)
        assertEquals("JDK 26 GA", saved.first().title, "MAJOR cluster must be first")
        assertEquals("Normal Cluster", saved[1].title, "Normal cluster in middle")
        assertEquals("Releases", saved.last().title, "Releases must be last")
    }

    @Test
    fun `normal clusters between MAJOR and Releases are ordered by engagement`() = runTest {
        // Scores: a1=10, a2=20, a3=30, a4=40, a5=50
        val articles = articles(
            "a1" to "Low Topic",     // score=10
            "a2" to "Mid Topic",     // score=20
            "a3" to "High Topic",    // score=30
            "a4" to "JDK 26 GA",     // score=40
            "a5" to "Hibernate Release", // score=50
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
                GROUP: Releases
                INDICES: 4
            """.trimIndent(),
            synthesisTitles = listOf("JDK 26 GA", "Low Topic", "High Topic", "Mid Topic", "Releases"),
        )

        assertEquals(5, saved.size)
        assertEquals("JDK 26 GA", saved[0].title, "MAJOR first")
        assertEquals("High Topic", saved[1].title, "Highest normal engagement second (score=30)")
        assertEquals("Mid Topic", saved[2].title, "Mid engagement third (score=20)")
        assertEquals("Low Topic", saved[3].title, "Lowest engagement fourth (score=10)")
        assertEquals("Releases", saved[4].title, "Releases last")
    }

    @Test
    fun `digest with only Releases cluster still works`() = runTest {
        val articles = articles("a1" to "Micronaut 4.x", "a2" to "Quarkus 3.y")

        val saved = captureSaved(
            articles,
            groupResponse = "GROUP: Releases\nINDICES: 0, 1",
            synthesisTitles = listOf("Releases"),
        )

        assertEquals(1, saved.size)
        assertEquals("Releases", saved.first().title)
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
