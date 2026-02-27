package jvm.daily

import org.jobrunr.jobs.annotations.Job
import org.jobrunr.jobs.context.JobContext
import java.time.Instant
import java.util.UUID
import kotlin.system.measureTimeMillis

/**
 * Pipeline orchestrator — runs all workflow steps in sequence.
 *
 * Constructor accepts step functions so the class is testable without a real DB.
 * Default parameters reference the internal run* functions in App.kt.
 */
class PipelineService(
    private val ingressFn:    (String) -> Unit = ::runIngress,
    private val enrichmentFn: (String) -> Unit = ::runEnrichment,
    private val clusteringFn: (String) -> Unit = ::runClustering,
    private val outgressFn:   (String) -> Unit = ::runOutgress,
) {
    internal data class StageTelemetry(
        val runId: String,
        val stage: String,
        val status: String,
        val startedAt: Instant,
        val endedAt: Instant,
        val durationMs: Long,
        val error: String? = null,
    ) {
        fun toLogLine(): String = buildString {
            append("[pipeline][telemetry] ")
            append("run_id=").append(runId)
            append(" stage=").append(stage)
            append(" status=").append(status)
            append(" started_at=").append(startedAt)
            append(" ended_at=").append(endedAt)
            append(" duration_ms=").append(durationMs)
            if (!error.isNullOrBlank()) {
                append(" error=\"").append(error.replace("\"", "'")).append('"')
            }
        }
    }

    /** Entry point called by JobRunr — JobContext is injected automatically. */
    @Job(name = "JVM Daily Pipeline")
    fun run(jobContext: JobContext) {
        val dbPath = System.getenv("DUCKDB_PATH") ?: "jvm-daily.duckdb"
        runSteps(dbPath) { msg -> jobContext.logger().info(msg) }
    }

    /** Runs all steps sequentially. Exposed as internal for direct testing. */
    internal fun runSteps(dbPath: String, log: (String) -> Unit = ::println) {
        val runId = UUID.randomUUID().toString()
        log("[pipeline] run_id=$runId ▶ pipeline")
        step(runId, "ingress", log) { ingressFn(dbPath) }
        step(runId, "enrichment", log) { enrichmentFn(dbPath) }
        step(runId, "clustering", log) { clusteringFn(dbPath) }
        step(runId, "outgress", log) { outgressFn(dbPath) }
        log("[pipeline] run_id=$runId ✓ pipeline")
    }

    private fun step(runId: String, name: String, log: (String) -> Unit, block: () -> Unit) {
        val startedAt = Instant.now()
        log("[pipeline] ▶ $name")
        try {
            val ms = measureTimeMillis { block() }
            val telemetry = StageTelemetry(
                runId = runId,
                stage = name,
                status = "SUCCESS",
                startedAt = startedAt,
                endedAt = Instant.now(),
                durationMs = ms,
            )
            log(telemetry.toLogLine())
            log("[pipeline] ✓ $name (${ms}ms)")
        } catch (e: Exception) {
            val endedAt = Instant.now()
            val durationMs = (endedAt.toEpochMilli() - startedAt.toEpochMilli()).coerceAtLeast(0)
            val telemetry = StageTelemetry(
                runId = runId,
                stage = name,
                status = "FAILED",
                startedAt = startedAt,
                endedAt = endedAt,
                durationMs = durationMs,
                error = e.message ?: e::class.simpleName,
            )
            log(telemetry.toLogLine())
            log("[pipeline] ✗ $name (${durationMs}ms)")
            throw e
        }
    }
}
