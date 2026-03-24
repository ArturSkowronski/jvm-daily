package jvm.daily.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

private val lenientJson = Json { ignoreUnknownKeys = true }

@Serializable
data class DomainProfile(
    val name: String,
    val slug: String,
    val description: String,
    val audience: String,
    val relevanceGate: RelevanceGateConfig,
    val enrichment: EnrichmentConfig,
    val clustering: ClusteringConfig,
) {
    companion object {
        fun load(path: Path): DomainProfile {
            require(path.exists()) { "Domain profile not found: $path" }
            return lenientJson.decodeFromString(serializer(), path.readText())
        }

        /** Returns the hardcoded JVM Daily profile — identical to config/domain.json. */
        fun default(): DomainProfile = DomainProfile(
            name = "JVM Daily",
            slug = "jvm-daily",
            description = "JVM ecosystem news aggregator",
            audience = "experienced JVM/backend developers",
            relevanceGate = RelevanceGateConfig(
                include = listOf(
                    "JVM language features, releases, or roadmap (Java, Kotlin, Scala, Groovy)",
                    "JVM frameworks, libraries, or tools (Spring, Quarkus, Micronaut, Gradle, Maven)",
                    "JVM performance, security vulnerabilities, or architecture",
                    "OpenJDK development, JEPs, runtime internals",
                    "Developer tooling, build systems, or CI/CD relevant to JVM ecosystem",
                    "Technical community news with clear relevance to backend/JVM engineers",
                ),
                exclude = listOf(
                    "General programming tips not specific to JVM ecosystem",
                    "Non-technical content: politics, entertainment, sports, memes",
                    "Pure frontend/mobile development unless JVM-related (e.g. Kotlin Multiplatform is relevant)",
                ),
            ),
            enrichment = EnrichmentConfig(
                systemPrompt = """
You are a JVM ecosystem news analyst writing for experienced engineers.

Your summaries must be DENSE and SPECIFIC — every sentence must contain facts the reader cannot guess from the title alone. Extract:
- Exact version numbers, JEP numbers, CVE IDs
- Concrete technical changes (what API changed, what got deprecated, what's new)
- Performance numbers, benchmarks, migration steps if mentioned
- Key community opinions or controversies from comments/discussion
- Breaking changes, deprecations, compatibility notes

NEVER write filler like "users are encouraged to update" or "promises new features."
NEVER restate the title. Start with the most important technical fact.

If the article is a Reddit discussion, summarize the TOP ARGUMENTS from the thread — what do people agree/disagree on, what's the consensus, what interesting insights were shared.
                """.trimIndent(),
                entityExamples = listOf("JDK 25", "Spring Boot 4.0", "GraalVM 23", "JEP 511"),
                topicExamples = listOf("virtual-threads", "spring-security", "kotlin-coroutines", "graalvm-native"),
            ),
            clustering = ClusteringConfig(
                systemPrompt = """
You are an expert JVM news curator. Your job is to analyze clusters of related
articles from multiple sources (RSS feeds, Bluesky, Reddit, blogs) and
create compelling synthesis summaries.

Your synthesis should:
- Identify the core story or development
- Synthesize insights across sources
- Highlight technical details that matter to experienced JVM developers
- Note community sentiment and reactions
- Be concise but informative (150-200 words)

Write for an audience of experienced JVM engineers who want signal, not noise.
                """.trimIndent(),
                audienceDescription = "experienced JVM engineers who want signal, not noise",
                exampleClusters = listOf("Kotlin 2.3.20 Release", "GraalVM Native Image Performance", "Spring Security 6.5 Hardening"),
                majorReleaseRules = listOf(
                    "Java/JDK GA releases (any version number)",
                    "Kotlin feature releases (X.Y.Z where Z=0 or Z=20)",
                    "Spring Boot major or minor (e.g. 3.5.0, 4.0.0)",
                    "Any other platform-level milestone that overshadows all other news",
                ),
            ),
        )
    }
}

@Serializable
data class RelevanceGateConfig(
    val include: List<String>,
    val exclude: List<String>,
)

@Serializable
data class EnrichmentConfig(
    val systemPrompt: String,
    val entityExamples: List<String>,
    val topicExamples: List<String>,
)

@Serializable
data class ClusteringConfig(
    val systemPrompt: String,
    val audienceDescription: String,
    val exampleClusters: List<String>,
    val majorReleaseRules: List<String>,
)
