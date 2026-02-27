package jvm.daily.source

import jvm.daily.model.Article
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SourceRegistryContractTest {

    @Test
    fun `registry rejects blank source type`() {
        val registry = SourceRegistry()

        assertFailsWith<IllegalArgumentException> {
            registry.register(stubSource(""))
        }
    }

    @Test
    fun `registry rejects duplicate source type`() {
        val registry = SourceRegistry()

        registry.register(stubSource("rss"))

        assertFailsWith<IllegalArgumentException> {
            registry.register(stubSource("rss"))
        }
    }

    @Test
    fun `registry returns all registered adapters`() = runTest {
        val registry = SourceRegistry()

        registry.register(stubSource("rss"))
        registry.register(stubSource("markdown_file"))

        val types = registry.all().map { it.sourceType }.sorted()

        assertEquals(listOf("markdown_file", "rss"), types)
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
                ingestedAt = Clock.System.now(),
            )
        )
    }
}
