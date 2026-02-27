package jvm.daily

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertContains

class PipelineServiceTest {

    @Test
    fun `should execute all four steps in order`() {
        val executed = mutableListOf<String>()
        val service = PipelineService(
            ingressFn    = { executed.add("ingress") },
            enrichmentFn = { executed.add("enrichment") },
            clusteringFn = { executed.add("clustering") },
            outgressFn   = { executed.add("outgress") },
        )

        service.runSteps("/dev/null")

        assertEquals(listOf("ingress", "enrichment", "clustering", "outgress"), executed)
    }

    @Test
    fun `should fail fast when a step throws`() {
        val executed = mutableListOf<String>()
        val service = PipelineService(
            ingressFn    = { executed.add("ingress"); throw RuntimeException("ingress failed") },
            enrichmentFn = { executed.add("enrichment") },
            clusteringFn = { executed.add("clustering") },
            outgressFn   = { executed.add("outgress") },
        )

        assertThrows<RuntimeException> { service.runSteps("/dev/null") }
        assertEquals(listOf("ingress"), executed)
    }

    @Test
    fun `should emit start and end markers for each stage in order`() {
        val logs = mutableListOf<String>()
        val service = PipelineService(
            ingressFn = {},
            enrichmentFn = {},
            clusteringFn = {},
            outgressFn = {},
        )

        service.runSteps("/dev/null", log = logs::add)

        val starts = logs.filter { it.startsWith("[pipeline] ▶") }
        val ends = logs.filter { it.startsWith("[pipeline] ✓") }

        assertEquals(4, starts.size)
        assertEquals(4, ends.size)
        assertTrue(starts[0].contains("ingress"))
        assertTrue(starts[1].contains("enrichment"))
        assertTrue(starts[2].contains("clustering"))
        assertTrue(starts[3].contains("outgress"))
    }

    @Test
    fun `should emit structured telemetry with duration and status for successful stages`() {
        val logs = mutableListOf<String>()
        val service = PipelineService(
            ingressFn = {},
            enrichmentFn = {},
            clusteringFn = {},
            outgressFn = {},
        )

        service.runSteps("/dev/null", log = logs::add)

        val telemetry = logs.filter { it.startsWith("[pipeline][telemetry]") }
        assertEquals(4, telemetry.size)
        telemetry.forEach { line ->
            assertTrue(line.contains("status=SUCCESS"))
            assertTrue(line.contains("duration_ms="))
            assertTrue(line.contains("stage="))
            assertTrue(line.contains("run_id="))
        }
    }

    @Test
    fun `should emit failed telemetry when stage throws`() {
        val logs = mutableListOf<String>()
        val service = PipelineService(
            ingressFn = { error("boom") },
            enrichmentFn = {},
            clusteringFn = {},
            outgressFn = {},
        )

        assertThrows<IllegalStateException> { service.runSteps("/dev/null", log = logs::add) }
        val failedTelemetry = logs.single { it.startsWith("[pipeline][telemetry]") }
        assertTrue(failedTelemetry.contains("status=FAILED"))
        assertTrue(failedTelemetry.contains("stage=ingress"))
        assertTrue(failedTelemetry.contains("error=\"boom\""))
    }

    @Test
    fun `smoke check should emit pipeline and stage telemetry envelope`() {
        val logs = mutableListOf<String>()
        val service = PipelineService(
            ingressFn = {},
            enrichmentFn = {},
            clusteringFn = {},
            outgressFn = {},
        )

        service.runSteps("/dev/null", log = logs::add)

        assertTrue(logs.any { it.contains("▶ pipeline") })
        assertTrue(logs.any { it.contains("✓ pipeline") })

        val telemetry = logs.filter { it.startsWith("[pipeline][telemetry]") }
        assertEquals(4, telemetry.size)
        assertTrue(telemetry.all { it.contains("status=SUCCESS") && it.contains("duration_ms=") })
    }

    @Test
    fun `renderQualityReport should include required counters`() {
        val report = PipelineService.renderQualityReport(
            PipelineService.QualityCounters(
                newItems = 11,
                duplicates = 5,
                feedFailures = 2,
                summarizationFailures = 3,
            )
        )

        assertContains(report, "| New Items | 11 |")
        assertContains(report, "| Duplicates | 5 |")
        assertContains(report, "| Feed Failures | 2 |")
        assertContains(report, "| Summarization Failures | 3 |")
    }

    @Test
    fun `evaluateQualityGate should fail when counters breach thresholds`() {
        val result = PipelineService.evaluateQualityGate(
            counters = PipelineService.QualityCounters(
                newItems = 10,
                duplicates = 4,
                feedFailures = 2,
                summarizationFailures = 3,
            ),
            thresholds = PipelineService.QualityGateThresholds(
                maxDuplicates = 1,
                maxFeedFailures = 0,
                maxSummarizationFailures = 1,
            )
        )

        assertTrue(!result.passed)
        assertTrue(result.breaches.any { it.startsWith("duplicates") })
        assertTrue(result.breaches.any { it.startsWith("feed_failures") })
        assertTrue(result.breaches.any { it.startsWith("summarization_failures") })
    }
}
