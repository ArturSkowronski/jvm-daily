package jvm.daily.workflow

import jvm.daily.model.Article
import jvm.daily.source.Source
import jvm.daily.source.SourceRegistry
import jvm.daily.storage.ArticleRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class IngressWorkflowIdempotencyTest {

    @Test
    fun `rerun with equivalent canonical ids keeps stable cardinality`() = runTest {
        val saved = mutableListOf<Article>()
        val repository = inMemoryRepo(saved)

        val firstRunSource = stubSource(
            listOf(
                article(id = "rss:https://example.com/post-1", title = "First"),
                article(id = "rss:https://example.com/post-2", title = "Second"),
            )
        )
        IngressWorkflow(SourceRegistry().apply { register(firstRunSource) }, repository).execute()
        assertEquals(2, saved.size)

        val secondRunSource = stubSource(
            listOf(
                article(id = "rss:https://example.com/post-1", title = "First changed title"),
                article(id = "rss:https://example.com/post-2", title = "Second changed title"),
            )
        )
        IngressWorkflow(SourceRegistry().apply { register(secondRunSource) }, repository).execute()

        assertEquals(2, saved.size)
    }

    @Test
    fun `duplicates are surfaced in run status and feed counters`() = runTest {
        val saved = mutableListOf<Article>()
        val repository = inMemoryRepo(saved)
        val source = stubSource(
            listOf(
                article(id = "rss:https://example.com/post-1", title = "First"),
                article(id = "rss:https://example.com/post-1", title = "First duplicate"),
            )
        )

        val workflow = IngressWorkflow(SourceRegistry().apply { register(source) }, repository)
        workflow.execute()
        workflow.execute()

        assertEquals(1, saved.size)
        assertEquals("First", saved.single().title)
        assertEquals("SUCCESS", workflow.lastRunStatus.name)
    }

    private fun article(id: String, title: String) = Article(
        id = id,
        title = title,
        content = "content",
        sourceType = "rss",
        sourceId = "https://example.com/feed.xml",
        ingestedAt = Clock.System.now(),
    )

    private class StubSource(private val articles: List<Article>) : Source {
        override val sourceType: String = "rss"

        override suspend fun fetch(): List<Article> = articles

        override suspend fun fetchOutcomes(): List<jvm.daily.model.SourceFetchOutcome> = super.fetchOutcomes()
    }

    private fun stubSource(articles: List<Article>) = StubSource(articles)

    private fun inMemoryRepo(storage: MutableList<Article>) = object : ArticleRepository {
        override fun save(article: Article) { storage.add(article) }
        override fun saveAll(articles: List<Article>) { storage.addAll(articles) }
        override fun findAll(): List<Article> = storage.toList()
        override fun findBySourceType(sourceType: String) = storage.filter { it.sourceType == sourceType }
        override fun existsById(id: String) = storage.any { it.id == id }
        override fun count(): Long = storage.size.toLong()
    }
}
