package jvm.daily

import jvm.daily.ai.LLMClient
import jvm.daily.config.SourcesConfig
import jvm.daily.source.MarkdownFileSource
import jvm.daily.source.RssSource
import jvm.daily.source.SourceRegistry
import jvm.daily.storage.DuckDbArticleRepository
import jvm.daily.storage.DuckDbConnectionFactory
import jvm.daily.storage.DuckDbProcessedArticleRepository
import jvm.daily.tools.ValidateRawArticleIds
import jvm.daily.workflow.ClusteringWorkflow
import jvm.daily.workflow.EnrichmentWorkflow
import jvm.daily.workflow.IngressWorkflow
import jvm.daily.workflow.OutgressWorkflow
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.h2.jdbcx.JdbcDataSource
import org.jobrunr.configuration.JobRunr
import org.jobrunr.jobs.context.JobContext
import org.jobrunr.jobs.lambdas.IocJobLambda
import org.jobrunr.server.JobActivator
import org.jobrunr.storage.sql.h2.H2StorageProvider
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.hours

/**
 * Main entry point for JVM Daily.
 *
 * No args              → start JobRunr daemon (scheduler + dashboard)
 * pipeline             → run full pipeline once (useful for first run / testing)
 * ingress|enrichment|clustering|outgress → run single step (debugging)
 * enrichment-replay     → rerun failed enrichment items only (recoverability)
 * quality-report        → generate daily quality counters artifact
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
        "enrichment-replay" -> { println("JVM Daily — enrichment-replay"); runEnrichmentReplay(dbPath, args.drop(1)) }
        "quality-report" -> { println("JVM Daily — quality-report"); runQualityReport(dbPath, args.drop(1)) }
        "clustering"  -> { println("JVM Daily — clustering");  runClustering(dbPath) }
        "outgress"    -> { println("JVM Daily — outgress");     runOutgress(dbPath) }
        "validate-raw-ids" -> { println("JVM Daily — validate-raw-ids"); runValidateRawIds(dbPath, args.drop(1)) }
        else -> {
            System.err.println("Unknown command: $cmd")
            System.err.println("Valid: pipeline | ingress | enrichment | enrichment-replay | quality-report | clustering | outgress | validate-raw-ids [--apply]")
            exitProcess(1)
        }
    }
}

private fun startDaemon(dbPath: String) {
    val cron          = System.getenv("PIPELINE_CRON")    ?: DEFAULT_PIPELINE_CRON
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
        val workflow = IngressWorkflow(sourceRegistry, repository)
        runBlocking { workflow.execute() }
        println("Ingest status: ${workflow.lastRunStatus}")
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

internal data class ReplayOptions(
    val sinceHours: Int = 24 * 7,
    val limit: Int = 50,
    val ids: List<String> = emptyList(),
    val dryRun: Boolean = false,
)

internal data class QualityReportOptions(
    val sinceHours: Int = 24,
    val outputDir: String = "output",
    val maxDuplicates: Long? = null,
    val maxFeedFailures: Long? = null,
    val maxSummarizationFailures: Long? = null,
    val failOnThreshold: Boolean = false,
)

internal fun runEnrichmentReplay(dbPath: String, args: List<String>) {
    val llmProvider = System.getenv("LLM_PROVIDER") ?: "mock"
    val llmApiKey   = System.getenv("LLM_API_KEY")
    val llmModel    = System.getenv("LLM_MODEL") ?: "gpt-4"

    if (llmProvider != "mock" && llmApiKey == null) {
        error("LLM_API_KEY required for provider '$llmProvider'")
    }

    val options = parseReplayOptions(args)

    DuckDbConnectionFactory.persistent(dbPath).use { connection ->
        val rawRepo = DuckDbArticleRepository(connection)
        val processedRepo = DuckDbProcessedArticleRepository(connection)

        val replayIds = if (options.ids.isNotEmpty()) {
            processedRepo.findFailedByIds(options.ids).map { it.id }
        } else {
            val since = Clock.System.now().minus(options.sinceHours.hours)
            processedRepo.findFailedRawArticleIds(since = since, limit = options.limit)
        }

        if (replayIds.isEmpty()) {
            println("Replay candidates: 0 (no failed items matched selector)")
            return
        }

        if (options.dryRun) {
            println("Replay candidates (${replayIds.size}): ${replayIds.joinToString(", ")}")
            println("Dry-run only. No enrichment replay executed.")
            return
        }

        println("Replaying ${replayIds.size} failed item(s): ${replayIds.joinToString(", ")}")
        runBlocking {
            EnrichmentWorkflow(
                rawArticleRepository = rawRepo,
                processedArticleRepository = processedRepo,
                llmClient = createLLMClient(llmProvider, llmApiKey, llmModel),
                replayRawArticleIds = replayIds.toSet(),
            ).execute()
        }

        val stillFailed = processedRepo.findFailedByIds(replayIds)
        println("Replay finished. requested=${replayIds.size}, still-failed=${stillFailed.size}")
    }
}

internal fun parseReplayOptions(args: List<String>): ReplayOptions {
    var sinceHours = 24 * 7
    var limit = 50
    var dryRun = false
    var ids: List<String> = emptyList()

    var index = 0
    while (index < args.size) {
        when (val arg = args[index]) {
            "--since-hours" -> {
                sinceHours = args.getOrNull(index + 1)?.toIntOrNull()
                    ?: error("Invalid --since-hours value. Expected non-negative integer.")
                index++
            }
            "--limit" -> {
                limit = args.getOrNull(index + 1)?.toIntOrNull()
                    ?: error("Invalid --limit value. Expected positive integer.")
                index++
            }
            "--ids" -> {
                val rawIds = args.getOrNull(index + 1)
                    ?: error("Missing --ids value. Expected comma-separated raw article IDs.")
                ids = rawIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                index++
            }
            "--dry-run" -> dryRun = true
            else -> error(
                "Unknown enrichment-replay option: $arg. " +
                    "Valid: --since-hours <n> | --limit <n> | --ids <id1,id2> | --dry-run"
            )
        }
        index++
    }

    require(sinceHours >= 0) { "--since-hours must be >= 0" }
    require(limit > 0) { "--limit must be > 0" }
    require(ids.isEmpty() || (sinceHours == 24 * 7 && limit == 50)) {
        "Use either --ids OR (--since-hours/--limit) selectors, not both."
    }

    return ReplayOptions(
        sinceHours = sinceHours,
        limit = limit,
        ids = ids,
        dryRun = dryRun,
    )
}

internal fun parseQualityReportOptions(args: List<String>): QualityReportOptions {
    var sinceHours = 24
    var outputDir = "output"
    var maxDuplicates: Long? = null
    var maxFeedFailures: Long? = null
    var maxSummarizationFailures: Long? = null
    var failOnThreshold = false
    var index = 0
    while (index < args.size) {
        when (val arg = args[index]) {
            "--since-hours" -> {
                sinceHours = args.getOrNull(index + 1)?.toIntOrNull()
                    ?: error("Invalid --since-hours value. Expected non-negative integer.")
                index++
            }
            "--output" -> {
                outputDir = args.getOrNull(index + 1)
                    ?.takeIf { it.isNotBlank() }
                    ?: error("Invalid --output value. Expected a non-empty directory path.")
                index++
            }
            "--max-duplicates" -> {
                maxDuplicates = args.getOrNull(index + 1)?.toLongOrNull()
                    ?: error("Invalid --max-duplicates value. Expected non-negative integer.")
                index++
            }
            "--max-feed-failures" -> {
                maxFeedFailures = args.getOrNull(index + 1)?.toLongOrNull()
                    ?: error("Invalid --max-feed-failures value. Expected non-negative integer.")
                index++
            }
            "--max-summarization-failures" -> {
                maxSummarizationFailures = args.getOrNull(index + 1)?.toLongOrNull()
                    ?: error("Invalid --max-summarization-failures value. Expected non-negative integer.")
                index++
            }
            "--fail-on-threshold" -> failOnThreshold = true
            else -> error(
                "Unknown quality-report option: $arg. " +
                    "Valid: --since-hours <n> | --output <dir> | --max-duplicates <n> | " +
                    "--max-feed-failures <n> | --max-summarization-failures <n> | --fail-on-threshold"
            )
        }
        index++
    }
    require(sinceHours >= 0) { "--since-hours must be >= 0" }
    require(maxDuplicates == null || maxDuplicates >= 0) { "--max-duplicates must be >= 0" }
    require(maxFeedFailures == null || maxFeedFailures >= 0) { "--max-feed-failures must be >= 0" }
    require(maxSummarizationFailures == null || maxSummarizationFailures >= 0) { "--max-summarization-failures must be >= 0" }
    return QualityReportOptions(sinceHours = sinceHours, outputDir = outputDir)
        .copy(
            maxDuplicates = maxDuplicates,
            maxFeedFailures = maxFeedFailures,
            maxSummarizationFailures = maxSummarizationFailures,
            failOnThreshold = failOnThreshold,
        )
}

internal fun runQualityReport(dbPath: String, args: List<String>) {
    val options = parseQualityReportOptions(args)
    val since = Clock.System.now().minus(options.sinceHours.hours)

    DuckDbConnectionFactory.persistent(dbPath).use { connection ->
        val rawRepo = DuckDbArticleRepository(connection)
        val processedRepo = DuckDbProcessedArticleRepository(connection)

        val counters = PipelineService.QualityCounters(
            newItems = rawRepo.countSince(since),
            duplicates = rawRepo.sumDuplicateCountSince(since),
            feedFailures = rawRepo.countFeedFailuresSince(since),
            summarizationFailures = processedRepo.countFailedSince(since),
        )

        val report = PipelineService.renderQualityReport(counters)
        val qualityGate = PipelineService.evaluateQualityGate(
            counters,
            PipelineService.QualityGateThresholds(
                maxDuplicates = options.maxDuplicates ?: Long.MAX_VALUE,
                maxFeedFailures = options.maxFeedFailures ?: Long.MAX_VALUE,
                maxSummarizationFailures = options.maxSummarizationFailures ?: Long.MAX_VALUE,
            )
        )
        val thresholdSection = buildString {
            appendLine()
            appendLine("## Quality Gate")
            appendLine("Status: ${if (qualityGate.passed) "PASS" else "FAIL"}")
            if (qualityGate.breaches.isNotEmpty()) {
                appendLine("Breaches:")
                qualityGate.breaches.forEach { appendLine("- $it") }
            }
        }.trimEnd()
        val reportDir = Path.of(options.outputDir)
        reportDir.createDirectories()
        val date = Clock.System.now().toLocalDateTime(TimeZone.UTC).date
        val reportPath = reportDir.resolve("quality-report-$date.md")
        reportPath.writeText(report + "\n\n" + thresholdSection + "\n")

        println(report)
        println(thresholdSection)
        println("Quality report written to: $reportPath")
        if (options.failOnThreshold && !qualityGate.passed) {
            error("Quality gate failed: ${qualityGate.breaches.joinToString("; ")}")
        }
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

internal fun runValidateRawIds(dbPath: String, args: List<String>) {
    val applyUpdates = args.contains("--apply")
    DuckDbConnectionFactory.persistent(dbPath).use { connection ->
        val summary = ValidateRawArticleIds(connection).run(applyUpdates = applyUpdates)
        val mode = if (applyUpdates) "apply" else "dry-run"
        println("Validate raw IDs mode: $mode")
        println(
            "Raw ID summary: total=${summary.totalRows}, mismatches=${summary.mismatches}, " +
                "collisions=${summary.collisions}, updated=${summary.updated}"
        )
    }
}

internal fun createLLMClient(provider: String, apiKey: String?, model: String): LLMClient =
    when (provider) {
        "mock" -> MockLLMClient()
        else   -> error("LLM provider '$provider' not yet implemented. Supported: mock")
    }

private class MockLLMClient : LLMClient {
    override suspend fun chat(prompt: String) = """
        {
          "summary": "Mock summary of the article content for local development and tests. This placeholder keeps enrichment contract validation stable while providing deterministic entities and topics for pipeline smoke runs in the JVM Daily project.",
          "entities": ["JDK 21", "Spring Boot", "Kotlin", "Virtual Threads"],
          "topics": ["framework-releases", "performance"]
        }
    """.trimIndent()
}

internal const val DEFAULT_PIPELINE_CRON = "0 7 * * *"
