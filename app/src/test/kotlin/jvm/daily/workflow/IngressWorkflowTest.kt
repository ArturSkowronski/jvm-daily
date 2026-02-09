package jvm.daily.workflow

import jvm.daily.model.Article
import jvm.daily.source.Source
import jvm.daily.source.SourceRegistry
import jvm.daily.storage.ArticleRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class IngressWorkflowTest {

    @Test
    fun `ingress saves fetched articles to repository`() = runTest {
        val saved = mutableListOf<Article>()
        val repo = inMemoryRepo(saved)

        val source = stubSource(
            listOf(
                article("1", "First"),
                article("2", "Second"),
            )
        )

        val registry = SourceRegistry().apply { register(source) }
        val workflow = IngressWorkflow(registry, repo)

        workflow.execute()

        assertEquals(2, saved.size)
        assertEquals("First", saved[0].title)
        assertEquals("Second", saved[1].title)
    }

    @Test
    fun `ingress handles multiple sources`() = runTest {
        val saved = mutableListOf<Article>()
        val repo = inMemoryRepo(saved)

        val registry = SourceRegistry().apply {
            register(stubSource(listOf(article("1", "From A")), "source-a"))
            register(stubSource(listOf(article("2", "From B")), "source-b"))
        }

        val workflow = IngressWorkflow(registry, repo)
        workflow.execute()

        assertEquals(2, saved.size)
    }

    @Test
    fun `ingress handles empty sources gracefully`() = runTest {
        val saved = mutableListOf<Article>()
        val repo = inMemoryRepo(saved)

        val registry = SourceRegistry().apply {
            register(stubSource(emptyList()))
        }

        val workflow = IngressWorkflow(registry, repo)
        workflow.execute()

        assertEquals(0, saved.size)
    }

    private fun article(id: String, title: String) = Article(
        id = id,
        title = title,
        content = "content of $title",
        sourceType = "test",
        sourceId = "test-source",
        ingestedAt = Clock.System.now(),
    )

    private fun stubSource(articles: List<Article>, type: String = "test") = object : Source {
        override val sourceType: String = type
        override suspend fun fetch(): List<Article> = articles
    }

    private fun inMemoryRepo(storage: MutableList<Article>) = object : ArticleRepository {
        override fun save(article: Article) { storage.add(article) }
        override fun saveAll(articles: List<Article>) { storage.addAll(articles) }
        override fun findAll(): List<Article> = storage.toList()
        override fun findBySourceType(sourceType: String) = storage.filter { it.sourceType == sourceType }
        override fun count(): Long = storage.size.toLong()
    }
}
