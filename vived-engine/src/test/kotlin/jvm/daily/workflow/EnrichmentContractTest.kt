package jvm.daily.workflow

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnrichmentContractTest {

    @Test
    fun `parse fails for invalid json`() {
        val result = EnrichmentContract.parse("SUMMARY: not json", isContentEmpty = false)
        assertTrue(result is EnrichmentContract.ParseResult.Failure)
        assertEquals("PARSE_JSON", (result as EnrichmentContract.ParseResult.Failure).code)
    }

    @Test
    fun `parse fails for blank summary`() {
        val result = EnrichmentContract.parse(
            """
            {"summary":"   ","entities":["JDK 21"],"topics":["performance"]}
            """.trimIndent(),
            isContentEmpty = false
        )
        assertTrue(result is EnrichmentContract.ParseResult.Failure)
        assertEquals("VALIDATION_SUMMARY_EMPTY", (result as EnrichmentContract.ParseResult.Failure).code)
    }

    @Test
    fun `parse normalizes missing fields and topic constraints`() {
        val result = EnrichmentContract.parse(
            """
            {
              "summary": "This summary deliberately contains enough words to pass the configured minimum limit while still being concise and useful for automated test validation within the enrichment contract parser behavior checks for deterministic execution.",
              "topics": ["Performance", " ", "framework-releases", "a-very-long-topic-name-that-is-definitely-over-forty-characters", "tooling", "security", "language-updates", "microservices"]
            }
            """.trimIndent(),
            isContentEmpty = true
        )

        assertTrue(result is EnrichmentContract.ParseResult.Success)
        val success = result as EnrichmentContract.ParseResult.Success
        assertEquals(emptyList(), success.entities)
        assertEquals(5, success.topics.size)
        assertTrue(success.topics.contains("performance"))
        assertTrue(success.warnings.any { it.startsWith("ENTITIES_EMPTY") })
        assertTrue(success.warnings.any { it.startsWith("TOPICS_TRUNCATED") })
        assertTrue(success.warnings.any { it.startsWith("CONTENT_EMPTY") })
    }
}
