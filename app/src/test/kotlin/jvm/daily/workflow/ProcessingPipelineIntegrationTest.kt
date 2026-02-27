package jvm.daily.workflow

import jvm.daily.ai.LLMClient
import jvm.daily.model.Article
import jvm.daily.storage.ArticleRepository
import jvm.daily.storage.DuckDbArticleRepository
import jvm.daily.storage.DuckDbConnectionFactory
import jvm.daily.storage.DuckDbProcessedArticleRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.sql.Connection
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration test for the full processing pipeline.
 *
 * Tests the complete flow:
 * 1. Raw articles in DB
 * 2. EnrichmentWorkflow processes them → ProcessedArticles
 * 3. ClusteringWorkflow groups them → ArticleClusters
 *
 * Uses mock LLM (no real API calls) but real database operations.
 *
 * Run with: ./gradlew integrationTest
 */
@Tag("integration")
class ProcessingPipelineIntegrationTest {
    private val json = Json

    private lateinit var connection: Connection
    private lateinit var articleRepo: ArticleRepository
    private lateinit var processedRepo: DuckDbProcessedArticleRepository

    @BeforeEach
    fun setUp() {
        connection = DuckDbConnectionFactory.inMemory()
        articleRepo = DuckDbArticleRepository(connection)
        processedRepo = DuckDbProcessedArticleRepository(connection)
    }

    @AfterEach
    fun tearDown() {
        connection.close()
    }

    @Test
    fun `full pipeline processes articles and creates clusters`() = runTest {
        // Seed with sample articles
        val articles = listOf(
            article(
                "1",
                "Spring Boot 4.0 RC1 Released with Virtual Threads",
                """
                Spring Boot 4.0 Release Candidate 1 has been released with exciting new features.
                The major highlight is built-in support for Java 21's virtual threads, which are
                now enabled by default. This brings significant performance improvements for
                blocking I/O operations. The team also improved GraalVM native image support
                and added new observability features with Micrometer 2.0.
                """.trimIndent()
            ),
            article(
                "2",
                "Spring Framework 6.2 Performance Benchmarks",
                """
                The Spring team published performance benchmarks for Spring Framework 6.2.
                With virtual threads enabled, throughput increased by 40% in typical web
                applications. The benchmarks show impressive gains in database-heavy workloads.
                """.trimIndent()
            ),
            article(
                "3",
                "Kotlin 2.3 Introduces Context Parameters",
                """
                JetBrains announced Kotlin 2.3 preview with context parameters feature.
                This replaces the experimental context receivers from earlier versions.
                The feature allows functions to implicitly receive context objects without
                explicit parameter passing, making code more concise.
                """.trimIndent()
            ),
            article(
                "4",
                "Kotlin Multiplatform Goes Stable",
                """
                Kotlin Multiplatform has reached stable status in Kotlin 2.3.
                Developers can now share code across JVM, Android, iOS, and web platforms
                with full tooling support. This is a major milestone for the Kotlin ecosystem.
                """.trimIndent()
            ),
            article(
                "5",
                "Quarkus 3.8 Released",
                """
                Quarkus 3.8 brings improved startup time and reduced memory footprint.
                The release includes better integration with Hibernate 6 and new dev UI features.
                """.trimIndent()
            ),
        )

        articleRepo.saveAll(articles)
        assertEquals(5, articleRepo.count())

        // Mock LLM that returns realistic responses
        val mockLLM = mockLLMClient()

        // Step 1: Enrichment
        val enrichmentWorkflow = EnrichmentWorkflow(articleRepo, processedRepo, mockLLM)
        enrichmentWorkflow.execute()

        // Verify processed articles were created
        val processed = processedRepo.findAll()
        assertEquals(5, processed.size, "Should have 5 processed articles")

        // Verify enrichment details
        processed.forEach { proc ->
            assertTrue(proc.summary.isNotBlank(), "Summary should not be blank")
            assertTrue(proc.entities.isNotEmpty(), "Entities should be extracted")
            assertTrue(proc.topics.isNotEmpty(), "Topics should be assigned")
            assertTrue(proc.normalizedTitle.isNotBlank(), "Normalized title should exist")
            assertTrue(proc.engagementScore > 0, "Engagement score should be calculated")
        }

        // Verify entity extraction worked
        val springArticles = processed.filter { it.entities.any { e -> e.contains("Spring") } }
        assertTrue(springArticles.size >= 2, "Should find Spring-related articles")

        val kotlinArticles = processed.filter { it.entities.any { e -> e.contains("Kotlin") } }
        assertTrue(kotlinArticles.size >= 2, "Should find Kotlin-related articles")

        // Verify topic assignment
        val frameworkReleases = processed.filter { it.topics.contains("framework-releases") }
        assertTrue(frameworkReleases.isNotEmpty(), "Should have framework-releases topic")

        // Step 2: Clustering
        val clusteringWorkflow = ClusteringWorkflow(processedRepo, mockLLM)
        clusteringWorkflow.execute()

        // Note: ClusteringWorkflow currently only logs, doesn't persist clusters
        // Once cluster persistence is added, we would verify:
        // - Clusters were created
        // - Cross-source grouping worked
        // - Synthesis summaries were generated
    }

    @Test
    fun `enrichment handles empty content gracefully`() = runTest {
        val articles = listOf(
            article("1", "Short Title", ""),
            article("2", "Another Title", "Very short content"),
        )

        articleRepo.saveAll(articles)

        val mockLLM = mockLLMClient()
        val workflow = EnrichmentWorkflow(articleRepo, processedRepo, mockLLM)
        workflow.execute()

        val processed = processedRepo.findAll()
        assertEquals(2, processed.size)

        // Should still create summaries even for short content
        processed.forEach { proc ->
            assertTrue(proc.summary.isNotBlank())
        }
    }

    @Test
    fun `enrichment skips already processed articles`() = runTest {
        val articles = listOf(
            article("1", "Title 1", "Content 1"),
            article("2", "Title 2", "Content 2"),
        )

        articleRepo.saveAll(articles)

        val mockLLM = mockLLMClient()
        val workflow = EnrichmentWorkflow(articleRepo, processedRepo, mockLLM)

        // First run
        workflow.execute()
        assertEquals(2, processedRepo.count())

        // Second run - should skip already processed
        workflow.execute()
        assertEquals(2, processedRepo.count(), "Should still have 2, no duplicates")
    }

    private fun article(id: String, title: String, content: String) = Article(
        id = id,
        title = title,
        content = content,
        sourceType = "rss",
        sourceId = "test-source",
        url = "https://example.com/$id",
        author = "Test Author",
        ingestedAt = Clock.System.now(),
    )

    /**
     * Mock LLM that returns realistic responses based on the input.
     * Simulates entity extraction and topic assignment.
     */
    private fun mockLLMClient() = object : LLMClient {
        override suspend fun chat(prompt: String): String {
            // Extract title from prompt for context-aware responses
            val titleMatch = Regex("Title: (.+)").find(prompt)
            val title = titleMatch?.groupValues?.get(1) ?: ""

            // Generate realistic entities based on content
            val entities = mutableListOf<String>()
            when {
                title.contains("Spring", ignoreCase = true) -> {
                    entities.addAll(listOf("Spring Boot", "Spring Framework", "Virtual Threads"))
                }
                title.contains("Kotlin", ignoreCase = true) -> {
                    entities.addAll(listOf("Kotlin", "Kotlin 2.3", "JetBrains"))
                }
                title.contains("Quarkus", ignoreCase = true) -> {
                    entities.addAll(listOf("Quarkus", "Hibernate", "Red Hat"))
                }
            }

            // Assign topics based on title
            val topics = mutableListOf<String>()
            when {
                title.contains("Released", ignoreCase = true) -> topics.add("framework-releases")
                title.contains("Performance", ignoreCase = true) -> topics.add("performance")
                title.contains("Benchmarks", ignoreCase = true) -> topics.add("performance")
                else -> topics.add("language-updates")
            }

            // Generate summary
            val summary = when {
                title.contains("Spring Boot 4.0") ->
                    "Spring Boot 4.0 RC1 introduces virtual threads by default, improving performance for blocking I/O. Includes GraalVM native image enhancements and Micrometer 2.0 observability."
                title.contains("Kotlin 2.3") ->
                    "Kotlin 2.3 preview introduces context parameters, replacing experimental context receivers. Provides cleaner syntax for implicit context passing."
                title.contains("Kotlin Multiplatform") ->
                    "Kotlin Multiplatform reaches stability, enabling code sharing across JVM, Android, iOS, and web with full tooling support."
                title.contains("Performance Benchmarks") ->
                    "Spring Framework 6.2 benchmarks show 40% throughput increase with virtual threads in typical web applications and database workloads."
                title.contains("Quarkus") ->
                    "Quarkus 3.8 improves startup time and memory usage with better Hibernate 6 integration and enhanced dev UI."
                else ->
                    "Article discusses recent developments in the JVM ecosystem with technical details and community insights."
            }

            return buildJsonObject {
                put("summary", summary)
                put("entities", buildJsonArray { entities.forEach { add(JsonPrimitive(it)) } })
                put("topics", buildJsonArray { topics.forEach { add(JsonPrimitive(it)) } })
            }.toString()
        }
    }
}
