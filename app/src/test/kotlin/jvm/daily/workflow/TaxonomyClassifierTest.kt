package jvm.daily.workflow

import jvm.daily.ai.LLMClient
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaxonomyClassifierTest {

    private val taxonomy = TaxonomyLoader.loadFromClasspath()

    @Test
    fun `TaxonomyLoader loads areas from taxonomy json`() {
        val areas = taxonomy.areas()
        assertTrue(areas.isNotEmpty(), "Should load at least one area")
        assertTrue(areas.any { it.slug == "java" }, "Should contain 'java' area")
        assertTrue(areas.any { it.slug == "kotlin" }, "Should contain 'kotlin' area")
        assertTrue(areas.any { it.slug == "spring" }, "Should contain 'spring' area")
    }

    @Test
    fun `TaxonomyLoader loads sub-areas for a given area`() {
        val subAreas = taxonomy.subAreasFor("java")
        assertTrue(subAreas.isNotEmpty(), "Java should have sub-areas")
        assertTrue(subAreas.any { it.slug == "virtual-threads" }, "Java should have virtual-threads sub-area")
        assertTrue(subAreas.any { it.slug == "gc" }, "Java should have gc sub-area")
    }

    @Test
    fun `TaxonomyLoader returns empty sub-areas for area without children`() {
        val subAreas = taxonomy.subAreasFor("scala")
        assertTrue(subAreas.isEmpty(), "Scala should have no sub-areas")
    }

    @Test
    fun `TaxonomyLoader loads impact tags`() {
        val tags = taxonomy.impactTags()
        assertTrue(tags.isNotEmpty(), "Should have impact tags")
        assertTrue(tags.any { it.slug == "breaking-change" })
        assertTrue(tags.any { it.slug == "new-capability" })
        assertTrue(tags.any { it.slug == "performance" })
    }

    @Test
    fun `classifier parses valid LLM JSON response`() = runTest {
        val llm = stubLLM("""{"area":"java","subArea":"virtual-threads","impact":["new-capability"],"confidence":0.92}""")
        val classifier = TaxonomyClassifier(taxonomy, llm)

        val result = classifier.classify(
            title = "Project Loom Virtual Threads in JDK 25",
            summary = "Virtual threads are now production-ready",
            entities = listOf("JDK 25", "Project Loom"),
            sourceType = "rss",
        )

        assertNotNull(result)
        assertEquals("java", result.area)
        assertEquals("virtual-threads", result.subArea)
        assertEquals(listOf("new-capability"), result.impact)
        assertEquals(0.92, result.confidence)
    }

    @Test
    fun `classifier handles response with no sub-area`() = runTest {
        val llm = stubLLM("""{"area":"scala","subArea":null,"impact":["ecosystem"],"confidence":0.78}""")
        val classifier = TaxonomyClassifier(taxonomy, llm)

        val result = classifier.classify(
            title = "Scala 3.5 Released",
            summary = "New Scala release with improvements",
            entities = listOf("Scala 3.5"),
            sourceType = "rss",
        )

        assertNotNull(result)
        assertEquals("scala", result.area)
        assertNull(result.subArea)
        assertEquals(listOf("ecosystem"), result.impact)
    }

    @Test
    fun `classifier returns null for invalid area slug`() = runTest {
        val llm = stubLLM("""{"area":"nonexistent","subArea":null,"impact":[],"confidence":0.5}""")
        val classifier = TaxonomyClassifier(taxonomy, llm)

        val result = classifier.classify(
            title = "Some article",
            summary = "Some summary",
            entities = emptyList(),
            sourceType = "rss",
        )

        assertNull(result, "Should return null for invalid area")
    }

    @Test
    fun `classifier handles LLM response wrapped in markdown fences`() = runTest {
        val llm = stubLLM("```json\n{\"area\":\"spring\",\"subArea\":\"boot\",\"impact\":[\"new-capability\"],\"confidence\":0.88}\n```")
        val classifier = TaxonomyClassifier(taxonomy, llm)

        val result = classifier.classify(
            title = "Spring Boot 4.0 Released",
            summary = "Major Spring Boot release",
            entities = listOf("Spring Boot 4.0"),
            sourceType = "rss",
        )

        assertNotNull(result)
        assertEquals("spring", result.area)
        assertEquals("boot", result.subArea)
    }

    @Test
    fun `classifier returns null when LLM throws exception`() = runTest {
        val llm = object : LLMClient {
            override suspend fun chat(prompt: String): String = throw RuntimeException("LLM unavailable")
        }
        val classifier = TaxonomyClassifier(taxonomy, llm)

        val result = classifier.classify(
            title = "Some article",
            summary = "Some summary",
            entities = emptyList(),
            sourceType = "rss",
        )

        assertNull(result, "Should return null on LLM error")
    }

    @Test
    fun `classifier filters invalid impact tags`() = runTest {
        val llm = stubLLM("""{"area":"kotlin","subArea":"coroutines","impact":["performance","fake-tag"],"confidence":0.85}""")
        val classifier = TaxonomyClassifier(taxonomy, llm)

        val result = classifier.classify(
            title = "Kotlin Coroutines Performance",
            summary = "Coroutine improvements",
            entities = listOf("Kotlin"),
            sourceType = "rss",
        )

        assertNotNull(result)
        assertEquals(listOf("performance"), result.impact, "Should filter out invalid impact tags")
    }

    @Test
    fun `classifier filters invalid sub-area for given area`() = runTest {
        val llm = stubLLM("""{"area":"kotlin","subArea":"virtual-threads","impact":[],"confidence":0.7}""")
        val classifier = TaxonomyClassifier(taxonomy, llm)

        val result = classifier.classify(
            title = "Kotlin language article",
            summary = "About Kotlin",
            entities = listOf("Kotlin"),
            sourceType = "rss",
        )

        assertNotNull(result)
        assertEquals("kotlin", result.area)
        assertNull(result.subArea, "virtual-threads is not a sub-area of kotlin, should be null")
    }

    private fun stubLLM(response: String) = object : LLMClient {
        override suspend fun chat(prompt: String): String = response
    }
}
