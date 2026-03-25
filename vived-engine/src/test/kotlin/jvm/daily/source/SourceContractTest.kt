package jvm.daily.source

import jvm.daily.model.Article
import jvm.daily.model.FeedIngestStatus
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SourceContractTest {

    private val fixedInstant = Instant.parse("2026-02-27T21:30:00Z")

    @Test
    fun `source fetch returns normalized records with required fields`() = runTest {
        val source = object : Source {
            override val sourceType: String = "contract-test"

            override suspend fun fetch(): List<Article> = listOf(
                Article(
                    id = "src:1",
                    title = "Normalized title",
                    content = "Some content",
                    sourceType = sourceType,
                    sourceId = "contract-fixture",
                    ingestedAt = fixedInstant,
                )
            )
        }

        val articles = source.fetch()

        assertTrue(articles.isNotEmpty())
        articles.forEach { article ->
            assertTrue(article.id.isNotBlank(), "id must be populated")
            assertTrue(article.title.isNotBlank(), "title must be populated")
            assertTrue(article.sourceType.isNotBlank(), "sourceType must be populated")
            assertTrue(article.sourceId.isNotBlank(), "sourceId must be populated")
        }
    }

    @Test
    fun `source contract allows partial records`() = runTest {
        val source = object : Source {
            override val sourceType: String = "partial-records"

            override suspend fun fetch(): List<Article> = listOf(
                Article(
                    id = "src:partial",
                    title = "Record with optional fields missing",
                    content = "",
                    sourceType = sourceType,
                    sourceId = "partial-fixture",
                    url = null,
                    author = null,
                    comments = null,
                    ingestedAt = Clock.System.now(),
                )
            )
        }

        val articles = source.fetch()

        assertTrue(articles.size == 1)
        assertTrue(articles[0].url == null)
        assertTrue(articles[0].author == null)
        assertTrue(articles[0].comments == null)
    }

    @Test
    fun `default fetchOutcomes maps successful fetch into deterministic feed result`() = runTest {
        val source = object : Source {
            override val sourceType: String = "connector-cert"

            override suspend fun fetch(): List<Article> = listOf(
                Article(
                    id = "src:ok",
                    title = "OK",
                    content = "ok",
                    sourceType = sourceType,
                    sourceId = "source-1",
                    ingestedAt = fixedInstant,
                )
            )
        }

        val outcomes = source.fetchOutcomes()

        assertEquals(1, outcomes.size)
        val outcome = outcomes.single()
        assertEquals(FeedIngestStatus.SUCCESS, outcome.feed.status)
        assertEquals(source.sourceType, outcome.feed.sourceType)
        assertEquals(source.sourceType, outcome.feed.sourceId)
        assertEquals(1, outcome.feed.fetchedCount)
        assertEquals(1, outcome.articles.size)
    }

    @Test
    fun `default fetchOutcomes maps exceptions into failed feed result`() = runTest {
        val source = object : Source {
            override val sourceType: String = "connector-cert-fail"

            override suspend fun fetch(): List<Article> {
                error("boom")
            }
        }

        val outcomes = source.fetchOutcomes()

        assertEquals(1, outcomes.size)
        val outcome = outcomes.single()
        assertEquals(FeedIngestStatus.FAILED, outcome.feed.status)
        assertEquals(0, outcome.feed.fetchedCount)
        assertTrue(outcome.feed.errors.single().contains("boom"))
        assertTrue(outcome.articles.isEmpty())
    }
}
