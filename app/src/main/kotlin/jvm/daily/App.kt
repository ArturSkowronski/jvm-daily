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
import jvm.daily.workflow.OutgressWorkflow
import kotlinx.coroutines.runBlocking
import org.h2.jdbcx.JdbcDataSource
import org.jobrunr.configuration.JobRunr
import org.jobrunr.jobs.context.JobContext
import org.jobrunr.jobs.lambdas.IocJobLambda
import org.jobrunr.server.JobActivator
import org.jobrunr.storage.sql.h2.H2StorageProvider
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.system.exitProcess

/**
 * Main entry point for JVM Daily.
 *
 * No args              → start JobRunr daemon (scheduler + dashboard)
 * pipeline             → run full pipeline once (useful for first run / testing)
 * ingress|enrichment|clustering|outgress → run single step (debugging)
 */
fun main(args: Array<String>) {
    val dbPath = System.getenv("DUCKDB_PATH") ?: "jvm-daily.duckdb"

    when (val cmd = args.getOrNull(0)) {
        null       -> startDaemon(dbPath)
        "pipeline" -> {
            println("JVM Daily — running full pipeline once")
            PipelineService().runSteps(dbPath)
        }
        "ingress"     -> { println("JVM Daily — ingress");     runIngress(dbPath) }
        "enrichment"  -> { println("JVM Daily — enrichment");  runEnrichment(dbPath) }
        "clustering"  -> { println("JVM Daily — clustering");  runClustering(dbPath) }
        "outgress"    -> { println("JVM Daily — outgress");     runOutgress(dbPath) }
        else -> {
            System.err.println("Unknown command: $cmd")
            System.err.println("Valid: pipeline | ingress | enrichment | clustering | outgress")
            exitProcess(1)
        }
    }
}

private fun startDaemon(dbPath: String) {
    val cron          = System.getenv("PIPELINE_CRON")    ?: "0 7 * * *"
    val dashboardPort = System.getenv("DASHBOARD_PORT")?.toIntOrNull() ?: 8000
    val storePath     = System.getenv("JOBRUNR_STORE")    ?: "data/jobrunr"

    Path.of(storePath).parent?.createDirectories()

    val ds = JdbcDataSource().apply {
        setURL("jdbc:h2:file:./$storePath;DB_CLOSE_ON_EXIT=FALSE;AUTO_SERVER=TRUE")
        user     = "sa"
        password = ""
    }

    val jobRunr = JobRunr.configure()
        .useJobActivator(object : JobActivator {
            @Suppress("UNCHECKED_CAST")
            override fun <T : Any> activateJob(type: Class<T>): T = when (type) {
                PipelineService::class.java -> PipelineService() as T
                else -> throw IllegalArgumentException("Unknown job type: $type")
            }
        })
        .useStorageProvider(H2StorageProvider(ds))
        .useBackgroundJobServer()
        .useDashboard(dashboardPort)
        .initialize()

    jobRunr.jobScheduler.scheduleRecurrently(
        "jvm-daily-pipeline",
        cron,
        IocJobLambda<PipelineService> { it.run(JobContext.Null) },
    )

    println("════════════════════════════════════════")
    println(" JVM Daily daemon started")
    println(" Schedule  : $cron")
    println(" Database  : $dbPath")
    println(" Dashboard : http://localhost:$dashboardPort/dashboard")
    println("════════════════════════════════════════")

    Thread.currentThread().join()
}

// ── Workflow runners (internal so PipelineService can reference them) ─────────

internal fun runIngress(dbPath: String) {
    val sourcesDir = System.getenv("SOURCES_DIR") ?: "sources"
    val configPath = System.getenv("CONFIG_PATH") ?: "config/sources.yml"

    println("Config: $configPath")
    val config = SourcesConfig.load(Path.of(configPath))

    DuckDbConnectionFactory.persistent(dbPath).use { connection ->
        val repository = DuckDbArticleRepository(connection)
        val sourceRegistry = SourceRegistry().apply {
            register(MarkdownFileSource(Path.of(sourcesDir)))
            if (config.rss.isNotEmpty()) register(RssSource(config.rss))
        }
        runBlocking { IngressWorkflow(sourceRegistry, repository).execute() }
        println("Total articles in DB: ${repository.count()}")
    }
}

internal fun runEnrichment(dbPath: String) {
    val llmProvider = System.getenv("LLM_PROVIDER") ?: "mock"
    val llmApiKey   = System.getenv("LLM_API_KEY")
    val llmModel    = System.getenv("LLM_MODEL") ?: "gpt-4"

    println("LLM Provider: $llmProvider / Model: $llmModel")
    if (llmProvider != "mock" && llmApiKey == null) {
        error("LLM_API_KEY required for provider '$llmProvider'")
    }

    DuckDbConnectionFactory.persistent(dbPath).use { connection ->
        val rawRepo       = DuckDbArticleRepository(connection)
        val processedRepo = DuckDbProcessedArticleRepository(connection)
        runBlocking { EnrichmentWorkflow(rawRepo, processedRepo, createLLMClient(llmProvider, llmApiKey, llmModel)).execute() }
        println("Total processed articles: ${processedRepo.count()}")
    }
}

internal fun runClustering(dbPath: String) {
    val llmProvider = System.getenv("LLM_PROVIDER") ?: "mock"
    val llmApiKey   = System.getenv("LLM_API_KEY")
    val llmModel    = System.getenv("LLM_MODEL") ?: "gpt-4"

    if (llmProvider != "mock" && llmApiKey == null) {
        error("LLM_API_KEY required for provider '$llmProvider'")
    }

    DuckDbConnectionFactory.persistent(dbPath).use { connection ->
        val processedRepo = DuckDbProcessedArticleRepository(connection)
        runBlocking { ClusteringWorkflow(processedRepo, createLLMClient(llmProvider, llmApiKey, llmModel)).execute() }
    }
}

internal fun runOutgress(dbPath: String) {
    val outputDirPath = System.getenv("OUTPUT_DIR")    ?: "output"
    val outgressDays  = System.getenv("OUTGRESS_DAYS")?.toIntOrNull() ?: 30
    val outputDir     = Path.of(outputDirPath)
    outputDir.createDirectories()

    println("Output dir: $outputDir  |  Days: $outgressDays")

    DuckDbConnectionFactory.persistent(dbPath).use { connection ->
        val processedRepo = DuckDbProcessedArticleRepository(connection)
        runBlocking { OutgressWorkflow(processedRepo, outputDir, outgressDays = outgressDays).execute() }
    }
}

internal fun createLLMClient(provider: String, apiKey: String?, model: String): LLMClient =
    when (provider) {
        "mock" -> MockLLMClient()
        else   -> error("LLM provider '$provider' not yet implemented. Supported: mock")
    }

private class MockLLMClient : LLMClient {
    override suspend fun chat(prompt: String) = """
        SUMMARY: Mock summary of the article content. This is a placeholder response.
        ENTITIES: JDK 21, Spring Boot, Kotlin, Virtual Threads
        TOPICS: framework-releases, performance
    """.trimIndent()
}
