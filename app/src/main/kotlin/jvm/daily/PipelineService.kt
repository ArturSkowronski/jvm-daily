package jvm.daily

import org.jobrunr.jobs.annotations.Job
import org.jobrunr.jobs.context.JobContext
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
    /** Entry point called by JobRunr — JobContext is injected automatically. */
    @Job(name = "JVM Daily Pipeline")
    fun run(jobContext: JobContext) {
        val dbPath = System.getenv("DUCKDB_PATH") ?: "jvm-daily.duckdb"
        runSteps(dbPath) { msg -> jobContext.logger().info(msg) }
    }

    /** Runs all steps sequentially. Exposed as internal for direct testing. */
    internal fun runSteps(dbPath: String, log: (String) -> Unit = ::println) {
        step("ingress",    log) { ingressFn(dbPath) }
        step("enrichment", log) { enrichmentFn(dbPath) }
        step("clustering", log) { clusteringFn(dbPath) }
        step("outgress",   log) { outgressFn(dbPath) }
    }

    private fun step(name: String, log: (String) -> Unit, block: () -> Unit) {
        log("[pipeline] ▶ $name")
        val ms = measureTimeMillis { block() }
        log("[pipeline] ✓ $name (${ms}ms)")
    }
}
