package jvm.daily.workflow

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object EnrichmentContract {
    private val json = Json { ignoreUnknownKeys = true }
    private const val MIN_SUMMARY_WORDS = 20
    private const val SUMMARY_WARNING_WORDS = 220
    private const val MAX_TOPICS = 5

    sealed interface ParseResult {
        data class Success(
            val summary: String,
            val entities: List<String>,
            val topics: List<String>,
            val warnings: List<String>,
        ) : ParseResult

        data class Failure(
            val code: String,
            val message: String,
        ) : ParseResult
    }

    fun parse(response: String, isContentEmpty: Boolean): ParseResult {
        val raw = runCatching { json.decodeFromString<EnrichmentJsonResponse>(response) }
            .getOrElse {
                return ParseResult.Failure("PARSE_JSON", it.message ?: "Invalid JSON response")
            }

        val summary = raw.summary?.trim().orEmpty()
        if (summary.isBlank()) {
            return ParseResult.Failure("VALIDATION_SUMMARY_EMPTY", "Summary cannot be blank")
        }

        val summaryWordCount = summary.split(Regex("\\s+")).filter { it.isNotBlank() }.size
        if (summaryWordCount < MIN_SUMMARY_WORDS) {
            return ParseResult.Failure(
                "VALIDATION_SUMMARY_TOO_SHORT",
                "Summary requires at least $MIN_SUMMARY_WORDS words"
            )
        }

        val warnings = mutableListOf<String>()
        if (summaryWordCount > SUMMARY_WARNING_WORDS) {
            warnings += "SUMMARY_LONG: summary contains $summaryWordCount words"
        }

        val entities = (raw.entities ?: emptyList())
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
        if (entities.isEmpty()) {
            warnings += "ENTITIES_EMPTY: no entities extracted"
        }

        val normalizedTopics = (raw.topics ?: emptyList())
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { it.lowercase() }
            .filter { it.length in 1..40 }
            .distinct()
            .toList()

        if (normalizedTopics.isEmpty()) {
            return ParseResult.Failure(
                "VALIDATION_TOPICS_EMPTY",
                "At least one valid topic is required"
            )
        }

        val topics = if (normalizedTopics.size > MAX_TOPICS) {
            warnings += "TOPICS_TRUNCATED: kept first $MAX_TOPICS topics"
            normalizedTopics.take(MAX_TOPICS)
        } else {
            normalizedTopics
        }

        if (isContentEmpty) {
            warnings += "CONTENT_EMPTY: generated from title and metadata"
        }

        return ParseResult.Success(
            summary = summary,
            entities = entities,
            topics = topics,
            warnings = warnings,
        )
    }

    @Serializable
    private data class EnrichmentJsonResponse(
        val summary: String? = null,
        val entities: List<String>? = null,
        val topics: List<String>? = null,
    )
}
