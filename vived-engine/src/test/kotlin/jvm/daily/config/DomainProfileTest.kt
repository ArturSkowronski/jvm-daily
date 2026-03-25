package jvm.daily.config

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DomainProfileTest {

    @Test
    fun `default returns JVM Daily profile`() {
        val profile = DomainProfile.default()
        assertEquals("JVM Daily", profile.name)
        assertEquals("jvm-daily", profile.slug)
        assertEquals("experienced JVM/backend developers", profile.audience)
        assertTrue(profile.relevanceGate.include.isNotEmpty())
        assertTrue(profile.relevanceGate.exclude.isNotEmpty())
        assertTrue(profile.enrichment.entityExamples.isNotEmpty())
        assertTrue(profile.enrichment.topicExamples.isNotEmpty())
        assertTrue(profile.clustering.exampleClusters.isNotEmpty())
        assertTrue(profile.clustering.majorReleaseRules.isNotEmpty())
    }

    @Test
    fun `load parses domain json file`(@TempDir tempDir: Path) {
        val json = """
        {
          "name": "AI Daily",
          "slug": "ai-daily",
          "description": "AI ecosystem news aggregator",
          "audience": "ML engineers",
          "relevanceGate": {
            "include": ["Machine learning papers", "LLM releases"],
            "exclude": ["Non-AI content"]
          },
          "enrichment": {
            "systemPrompt": "You are an AI news analyst.",
            "entityExamples": ["GPT-5", "Claude 4"],
            "topicExamples": ["transformers", "rlhf"]
          },
          "clustering": {
            "systemPrompt": "You are an AI news curator.",
            "audienceDescription": "ML engineers who want signal",
            "exampleClusters": ["GPT-5 Release", "RLHF Advances"],
            "majorReleaseRules": ["Major model releases"]
          }
        }
        """.trimIndent()
        val file = tempDir.resolve("domain.json")
        file.writeText(json)

        val profile = DomainProfile.load(file)
        assertEquals("AI Daily", profile.name)
        assertEquals("ai-daily", profile.slug)
        assertEquals("ML engineers", profile.audience)
        assertEquals(listOf("GPT-5", "Claude 4"), profile.enrichment.entityExamples)
        assertEquals(listOf("transformers", "rlhf"), profile.enrichment.topicExamples)
        assertEquals("You are an AI news curator.", profile.clustering.systemPrompt)
    }

    @Test
    fun `load ignores unknown keys for forward compatibility`(@TempDir tempDir: Path) {
        val json = """
        {
          "name": "Test",
          "slug": "test",
          "description": "Test desc",
          "audience": "testers",
          "futureField": "should be ignored",
          "relevanceGate": { "include": ["a"], "exclude": ["b"] },
          "enrichment": { "systemPrompt": "p", "entityExamples": [], "topicExamples": [] },
          "clustering": { "systemPrompt": "p", "audienceDescription": "a", "exampleClusters": [], "majorReleaseRules": [] }
        }
        """.trimIndent()
        val file = tempDir.resolve("domain.json")
        file.writeText(json)

        val profile = DomainProfile.load(file)
        assertEquals("Test", profile.name)
    }

    @Test
    fun `load throws when file does not exist`(@TempDir tempDir: Path) {
        val missing = tempDir.resolve("nonexistent.json")
        assertFailsWith<IllegalArgumentException> {
            DomainProfile.load(missing)
        }
    }

    @Test
    fun `default profile enrichment system prompt contains key phrases`() {
        val profile = DomainProfile.default()
        assertTrue(profile.enrichment.systemPrompt.contains("DENSE and SPECIFIC"))
        assertTrue(profile.enrichment.systemPrompt.contains("JVM ecosystem"))
    }

    @Test
    fun `default profile clustering system prompt contains key phrases`() {
        val profile = DomainProfile.default()
        assertTrue(profile.clustering.systemPrompt.contains("JVM news curator"))
        assertTrue(profile.clustering.systemPrompt.contains("signal, not noise"))
    }
}
