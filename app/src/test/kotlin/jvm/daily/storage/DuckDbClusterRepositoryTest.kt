package jvm.daily.storage

import jvm.daily.model.ArticleCluster
import kotlinx.datetime.Instant
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.Connection
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DuckDbClusterRepositoryTest {

    private lateinit var connection: Connection
    private lateinit var repository: DuckDbClusterRepository

    @BeforeEach
    fun setUp() {
        connection = DuckDbConnectionFactory.inMemory()
        repository = DuckDbClusterRepository(connection)
    }

    @AfterEach
    fun tearDown() {
        connection.close()
    }

    @Test
    fun `saveAll then findByDateRange returns clusters in range`() {
        val inRange = cluster(
            id = "c1",
            createdAt = Instant.parse("2026-03-15T10:00:00Z"),
        )
        val outOfRange = cluster(
            id = "c2",
            createdAt = Instant.parse("2026-03-14T08:00:00Z"),
        )
        repository.saveAll(listOf(inRange, outOfRange))

        val results = repository.findByDateRange(
            start = Instant.parse("2026-03-15T00:00:00Z"),
            end = Instant.parse("2026-03-15T23:59:59Z"),
        )

        assertEquals(1, results.size)
        assertEquals("c1", results[0].id)
        assertEquals("Cluster c1", results[0].title)
        assertEquals("Summary for c1", results[0].summary)
        assertEquals(listOf("a1", "a2"), results[0].articles)
        assertEquals(listOf("rss", "twitter"), results[0].sources)
        assertEquals(42.0, results[0].totalEngagement)
    }

    @Test
    fun `saveAll idempotency - INSERT OR REPLACE produces no duplicates`() {
        val cluster = cluster(id = "c1", title = "Original")
        repository.saveAll(listOf(cluster))

        val updated = cluster.copy(title = "Updated")
        repository.saveAll(listOf(updated))

        val results = repository.findByDateRange(
            start = Instant.parse("2026-03-15T00:00:00Z"),
            end = Instant.parse("2026-03-15T23:59:59Z"),
        )

        assertEquals(1, results.size)
        assertEquals("Updated", results[0].title)
    }

    @Test
    fun `findByDateRange with empty range returns empty list`() {
        repository.saveAll(
            listOf(
                cluster(id = "c1", createdAt = Instant.parse("2026-03-15T10:00:00Z")),
            )
        )

        val results = repository.findByDateRange(
            start = Instant.parse("2026-03-16T00:00:00Z"),
            end = Instant.parse("2026-03-16T23:59:59Z"),
        )

        assertTrue(results.isEmpty())
    }

    @Test
    fun `deleteByDateRange removes clusters in range and leaves others intact`() {
        val keep = cluster(id = "c-keep", createdAt = Instant.parse("2026-03-14T10:00:00Z"))
        val remove = cluster(id = "c-remove", createdAt = Instant.parse("2026-03-15T10:00:00Z"))
        repository.saveAll(listOf(keep, remove))

        repository.deleteByDateRange(
            start = Instant.parse("2026-03-15T00:00:00Z"),
            end = Instant.parse("2026-03-15T23:59:59Z"),
        )

        val remaining = repository.findByDateRange(
            start = Instant.parse("2026-03-14T00:00:00Z"),
            end = Instant.parse("2026-03-15T23:59:59Z"),
        )

        assertEquals(1, remaining.size)
        assertEquals("c-keep", remaining[0].id)
    }

    @Test
    fun `type and bullets round-trip through DB`() {
        val releaseCluster = cluster(
            id = "rc1",
            type = "release",
            bullets = listOf("Virtual threads are now default", "New @RestClientTest slice"),
        )
        repository.saveAll(listOf(releaseCluster))

        val results = repository.findByDateRange(
            start = Instant.parse("2026-03-15T00:00:00Z"),
            end = Instant.parse("2026-03-15T23:59:59Z"),
        )

        assertEquals(1, results.size)
        assertEquals("release", results[0].type)
        assertEquals(listOf("Virtual threads are now default", "New @RestClientTest slice"), results[0].bullets)
    }

    @Test
    fun `topic cluster has default type and empty bullets`() {
        val topicCluster = cluster(id = "tc1")
        repository.saveAll(listOf(topicCluster))

        val results = repository.findByDateRange(
            start = Instant.parse("2026-03-15T00:00:00Z"),
            end = Instant.parse("2026-03-15T23:59:59Z"),
        )

        assertEquals(1, results.size)
        assertEquals("topic", results[0].type)
        assertEquals(emptyList<String>(), results[0].bullets)
    }

    private fun cluster(
        id: String,
        title: String = "Cluster $id",
        summary: String = "Summary for $id",
        articles: List<String> = listOf("a1", "a2"),
        sources: List<String> = listOf("rss", "twitter"),
        totalEngagement: Double = 42.0,
        createdAt: Instant = Instant.parse("2026-03-15T10:00:00Z"),
        type: String = "topic",
        bullets: List<String> = emptyList(),
    ) = ArticleCluster(
        id = id, title = title, summary = summary, articles = articles, sources = sources,
        totalEngagement = totalEngagement, createdAt = createdAt, type = type, bullets = bullets,
    )
}
