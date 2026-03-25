package jvm.daily.source

import jvm.daily.model.Article
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SourceRegistryTest {

    @Test
    fun `empty registry returns empty list`() {
        val registry = SourceRegistry()
        assertTrue(registry.all().isEmpty())
    }

    @Test
    fun `register adds source`() {
        val registry = SourceRegistry()
        registry.register(stubSource("type-a"))

        assertEquals(1, registry.all().size)
        assertEquals("type-a", registry.all()[0].sourceType)
    }

    @Test
    fun `register multiple sources`() {
        val registry = SourceRegistry()
        registry.register(stubSource("type-a"))
        registry.register(stubSource("type-b"))

        assertEquals(2, registry.all().size)
    }

    private fun stubSource(type: String) = object : Source {
        override val sourceType: String = type
        override suspend fun fetch(): List<Article> = emptyList()
    }
}
