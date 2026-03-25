package jvm.daily.source

import jvm.daily.model.Article
import jvm.daily.model.FeedIngestStatus
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConnectorDryRunContractTest {

    private val fixedInstant = Instant.parse("2026-02-27T23:00:00Z")

    @Test
    fun `dry-run connector skeleton passes source contract and outcome semantics`() = runTest {
        val connector = DryRunConnectorSource()

        val articles = connector.fetch()
        val outcomes = connector.fetchOutcomes()

        assertEquals(1, articles.size)
        assertEquals("dryrun_forum", connector.sourceType)
        assertEquals("dryrun_forum", articles.single().sourceType)

        assertEquals(1, outcomes.size)
        assertEquals(FeedIngestStatus.SUCCESS, outcomes.single().feed.status)
        assertEquals("dryrun_forum", outcomes.single().feed.sourceType)
        assertEquals(1, outcomes.single().feed.fetchedCount)
    }

    @Test
    fun `dry-run connector skeleton registers without workflow changes`() = runTest {
        val registry = SourceRegistry()
        registry.register(stubSource("rss"))
        registry.register(DryRunConnectorSource())

        assertEquals(listOf("dryrun_forum", "rss"), registry.all().map { it.sourceType }.sorted())
    }

    @Test
    fun `dry-run connector type must remain unique in registry`() {
        val registry = SourceRegistry()
        registry.register(DryRunConnectorSource())

        assertFailsWith<IllegalArgumentException> {
            registry.register(stubSource("DRYRUN_FORUM"))
        }
    }

    private fun stubSource(type: String) = object : Source {
        override val sourceType: String = type

        override suspend fun fetch(): List<Article> = listOf(
            Article(
                id = "id-$type",
                title = "title-$type",
                content = "",
                sourceType = type,
                sourceId = "stub",
                ingestedAt = fixedInstant,
            )
        )
    }

    private class DryRunConnectorSource : Source {
        override val sourceType: String = "dryrun_forum"

        override suspend fun fetch(): List<Article> = listOf(
            Article(
                id = "dryrun:1",
                title = "Connector dry-run sample",
                content = "sample payload",
                sourceType = sourceType,
                sourceId = "thread-1",
                ingestedAt = Instant.parse("2026-02-27T23:00:00Z"),
            )
        )
    }
}
