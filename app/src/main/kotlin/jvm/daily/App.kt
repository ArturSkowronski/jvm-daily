package jvm.daily

import jvm.daily.ai.LLMClient
import jvm.daily.config.SourcesConfig
import jvm.daily.source.MarkdownFileSource
import jvm.daily.source.RssSource
import jvm.daily.source.SourceRegistry
import jvm.daily.storage.DuckDbArticleRepository
import jvm.daily.storage.DuckDbConnectionFactory
import jvm.daily.storage.DuckDbProcessedArticleRepository
import jvm.daily.workflow.ClusteringWorkflow
import jvm.daily.workflow.EnrichmentWorkflow
import jvm.daily.workflow.IngressWorkflow
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * Main entry point for JVM Daily workflows.
 *
 * Usage:
 *   ./gradlew run --args="ingress"       # Run ingress workflow
 *   ./gradlew run --args="enrichment"    # Run enrichment workflow
 *   ./gradlew run --args="clustering"    # Run clustering workflow
 *   ./gradlew run                         # Run ingress (default)
 */
fun main(args: Array<String>) {
    val workflow = args.getOrNull(0) ?: "ingress"
    val dbPath = System.getenv("DUCKDB_PATH") ?: "jvm-daily.duckdb"

    println("JVM Daily — running workflow: $workflow")
    println("Database: $dbPath")

    try {
        when (workflow) {
            "ingress" -> runIngress(dbPath)
            "enrichment" -> runEnrichment(dbPath)
            "clustering" -> runClustering(dbPath)
            else -> {
                System.err.println("Unknown workflow: $workflow")
                System.err.println("Valid workflows: ingress, enrichment, clustering")
                exitProcess(1)
            }
        }
    } catch (e: Exception) {
        System.err.println("Workflow '$workflow' failed: ${e.message}")
        e.printStackTrace(System.err)
        exitProcess(1)
    }
}

private fun runIngress(dbPath: String) {
    val sourcesDir = System.getenv("SOURCES_DIR") ?: "sources"
    val configPath = System.getenv("CONFIG_PATH") ?: "config/sources.yml"

    println("Config: $configPath")

    val config = SourcesConfig.load(Path.of(configPath))

    DuckDbConnectionFactory.persistent(dbPath).use { connection ->
        val repository = DuckDbArticleRepository(connection)

        val sourceRegistry = SourceRegistry().apply {
            register(MarkdownFileSource(Path.of(sourcesDir)))
            if (config.rss.isNotEmpty()) {
                register(RssSource(config.rss))
            }
        }

        runBlocking {
            IngressWorkflow(sourceRegistry, repository).execute()
        }

        println("Total articles in DB: ${repository.count()}")
    }
}

private fun runEnrichment(dbPath: String) {
    val llmProvider = System.getenv("LLM_PROVIDER") ?: "mock"
    val llmApiKey = System.getenv("LLM_API_KEY")
    val llmModel = System.getenv("LLM_MODEL") ?: "gpt-4"

    println("LLM Provider: $llmProvider")
    println("LLM Model: $llmModel")

    if (llmProvider != "mock" && llmApiKey == null) {
        System.err.println("ERROR: LLM_API_KEY environment variable required for non-mock provider")
        exitProcess(1)
    }

    DuckDbConnectionFactory.persistent(dbPath).use { connection ->
        val rawRepo = DuckDbArticleRepository(connection)
        val processedRepo = DuckDbProcessedArticleRepository(connection)

        val llmClient = createLLMClient(llmProvider, llmApiKey, llmModel)

        runBlocking {
            EnrichmentWorkflow(rawRepo, processedRepo, llmClient).execute()
        }

        println("Total processed articles: ${processedRepo.count()}")
    }
}

private fun runClustering(dbPath: String) {
    val llmProvider = System.getenv("LLM_PROVIDER") ?: "mock"
    val llmApiKey = System.getenv("LLM_API_KEY")
    val llmModel = System.getenv("LLM_MODEL") ?: "gpt-4"

    println("LLM Provider: $llmProvider")
    println("LLM Model: $llmModel")

    if (llmProvider != "mock" && llmApiKey == null) {
        System.err.println("ERROR: LLM_API_KEY environment variable required for non-mock provider")
        exitProcess(1)
    }

    DuckDbConnectionFactory.persistent(dbPath).use { connection ->
        val processedRepo = DuckDbProcessedArticleRepository(connection)
        val llmClient = createLLMClient(llmProvider, llmApiKey, llmModel)

        runBlocking {
            ClusteringWorkflow(processedRepo, llmClient).execute()
        }
    }
}

private fun createLLMClient(provider: String, apiKey: String?, model: String): LLMClient {
    return when (provider) {
        "mock" -> MockLLMClient()
        else -> {
            System.err.println("ERROR: LLM provider '$provider' not yet implemented")
            System.err.println("Supported providers: mock")
            System.err.println("TODO: Add real LLM integration (Koog Agents, OpenAI, Anthropic)")
            exitProcess(1)
        }
    }
}

/**
 * Mock LLM client for testing without API keys.
 */
private class MockLLMClient : LLMClient {
    override suspend fun chat(prompt: String): String {
        // Simple mock response
        return """
            SUMMARY: Mock summary of the article content. This is a placeholder response.
            ENTITIES: JDK 21, Spring Boot, Kotlin, Virtual Threads
            TOPICS: framework-releases, performance
        """.trimIndent()
    }
}
