package jvm.daily.workflow

import jvm.daily.model.Article
import jvm.daily.model.FeedIngestResult
import jvm.daily.model.FeedIngestStatus
import jvm.daily.model.IngestRunStatus
import jvm.daily.model.SourceFetchOutcome
import jvm.daily.source.Source
import jvm.daily.source.SourceRegistry
import jvm.daily.storage.ArticleRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IngressReliabilityTest {

    @Test
    fun `classifyRunStatus returns fail when all feeds fail`() {
        val results = listOf(
            FeedIngestResult("rss", "feed-1", FeedIngestStatus.FAILED, fetchedCount = 0),
            FeedIngestResult("rss", "feed-2", FeedIngestStatus.FAILED, fetchedCount = 0),
        )

        assertEquals(IngestRunStatus.FAIL, IngressWorkflow.classifyRunStatus(results))
    }

    @Test
    fun `classifyRunStatus returns success with warnings for partial failures`() {
        val results = listOf(
            FeedIngestResult("rss", "feed-1", FeedIngestStatus.SUCCESS, fetchedCount = 3),
            FeedIngestResult("rss", "feed-2", FeedIngestStatus.FAILED, fetchedCount = 0),
        )

        assertEquals(IngestRunStatus.SUCCESS_WITH_WARNINGS, IngressWorkflow.classifyRunStatus(results))
    }

    @Test
    fun `classifyRunStatus returns success when all feeds are successful`() {
        val results = listOf(
            FeedIngestResult("rss", "feed-1", FeedIngestStatus.SUCCESS, fetchedCount = 3),
            FeedIngestResult("rss", "feed-2", FeedIngestStatus.SUCCESS, fetchedCount = 0),
        )

        assertEquals(IngestRunStatus.SUCCESS, IngressWorkflow.classifyRunStatus(results))
    }

    @Test
    fun `ingress continues processing after a feed failure`() = runTest {
        val saved = mutableListOf<Article>()
        val repo = inMemoryRepo(saved)

        val successfulSource = stubSource(
            type = "rss",
            sourceId = "healthy-feed",
            articles = listOf(
                article("1", "A"),
                article("2", "B")
            ),
            status = FeedIngestStatus.SUCCESS
        )

        val failingSource = stubSource(
            type = "rss_failing",
            sourceId = "failing-feed",
            articles = emptyList(),
            status = FeedIngestStatus.FAILED,
            errors = listOf("timeout")
        )

        val registry = SourceRegistry().apply {
            register(successfulSource)
            register(failingSource)
        }

        IngressWorkflow(registry, repo).execute()

        assertEquals(2, saved.size)
    }

    @Test
    fun `quality gate flags feed failure threshold breach`() {
        val result = jvm.daily.PipelineService.evaluateQualityGate(
            counters = jvm.daily.PipelineService.QualityCounters(
                newItems = 5,
                duplicates = 1,
                feedFailures = 2,
                summarizationFailures = 0,
            ),
            thresholds = jvm.daily.PipelineService.QualityGateThresholds(
                maxDuplicates = 10,
                maxFeedFailures = 1,
                maxSummarizationFailures = 10,
            )
        )

        assertTrue(!result.passed)
        assertTrue(result.breaches.any { it.startsWith("feed_failures") })
    }

    private fun article(id: String, title: String) = Article(
        id = id,
        title = title,
        content = "content-$title",
        sourceType = "rss",
        sourceId = "fixture",
        ingestedAt = Clock.System.now(),
    )

    private fun stubSource(
        type: String,
        sourceId: String,
        articles: List<Article>,
        status: FeedIngestStatus,
        errors: List<String> = emptyList(),
    ) = object : Source {
        override val sourceType: String = type

        override suspend fun fetch(): List<Article> = articles

        override suspend fun fetchOutcomes(): List<SourceFetchOutcome> = listOf(
            SourceFetchOutcome(
                feed = FeedIngestResult(
                    sourceType = type,
                    sourceId = sourceId,
                    status = status,
                    fetchedCount = articles.size,
                    errors = errors,
                ),
                articles = articles,
            )
        )
    }

    private fun inMemoryRepo(storage: MutableList<Article>) = object : ArticleRepository {
        override fun save(article: Article) { storage.add(article) }
        override fun saveAll(articles: List<Article>) { storage.addAll(articles) }
        override fun findAll(): List<Article> = storage.toList()
        override fun findBySourceType(sourceType: String) = storage.filter { it.sourceType == sourceType }
        override fun existsById(id: String) = storage.any { it.id == id }
        override fun count(): Long = storage.size.toLong()
    }
}
