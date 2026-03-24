package jvm.daily.workflow

import jvm.daily.ai.LLMClient
import kotlinx.serialization.json.*

/**
 * Classifies articles into the JVM Daily taxonomy using a single LLM call.
 * Returns area, optional sub-area, impact tags, and confidence.
 */
class TaxonomyClassifier(
    private val taxonomy: TaxonomyLoader,
    private val llmClient: LLMClient,
) {
    /**
     * Classify an article. Returns null if classification fails (LLM error, invalid response).
     */
    suspend fun classify(
        title: String,
        summary: String,
        entities: List<String>,
        sourceType: String,
    ): ClassificationResult? {
        val prompt = buildPrompt(title, summary, entities, sourceType)
        val response = try {
            llmClient.chat(prompt)
        } catch (_: Exception) {
            return null
        }
        return parseResponse(response)
    }

    private fun buildPrompt(
        title: String,
        summary: String,
        entities: List<String>,
        sourceType: String,
    ): String = buildString {
        appendLine("Classify this JVM ecosystem article into the taxonomy.")
        appendLine("Return STRICT JSON only — no markdown fences, no explanation.")
        appendLine()
        appendLine("Article: $title | $summary | Entities: ${entities.joinToString(", ")} | Source: $sourceType")
        appendLine()
        append(taxonomy.buildAreasPrompt())
        appendLine()
        appendLine("Return JSON: {\"area\":\"slug\",\"subArea\":\"slug-or-null\",\"impact\":[\"tag1\"],\"confidence\":0.85}")
    }

    internal fun parseResponse(response: String): ClassificationResult? {
        val cleaned = response
            .let { if (it.contains("```")) it.substringAfter("```").substringBefore("```") else it }
            .let { if (it.trimStart().startsWith("json")) it.trimStart().removePrefix("json") else it }
            .trim()

        val obj = try {
            Json.parseToJsonElement(cleaned).jsonObject
        } catch (_: Exception) {
            return null
        }

        val area = obj["area"]?.jsonPrimitive?.contentOrNull ?: return null
        if (!taxonomy.isValidArea(area)) return null

        val subArea = obj["subArea"]?.jsonPrimitive?.contentOrNull
        val validatedSubArea = if (subArea != null && taxonomy.isValidSubArea(area, subArea)) subArea else null

        val impact = obj["impact"]?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?.filter { taxonomy.isValidImpactTag(it) }
            ?: emptyList()

        val confidence = obj["confidence"]?.jsonPrimitive?.doubleOrNull?.coerceIn(0.0, 1.0)

        return ClassificationResult(area, validatedSubArea, impact, confidence)
    }
}

data class ClassificationResult(
    val area: String,
    val subArea: String?,
    val impact: List<String>,
    val confidence: Double?,
)
