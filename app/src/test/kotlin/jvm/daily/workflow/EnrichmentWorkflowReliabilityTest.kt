package jvm.daily.workflow

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class EnrichmentQualityGateTest {

    @Test
    fun `quality gate flags summarization failure threshold breach`() {
        val result = jvm.daily.PipelineService.evaluateQualityGate(
            counters = jvm.daily.PipelineService.QualityCounters(
                newItems = 5,
                duplicates = 0,
                feedFailures = 0,
                summarizationFailures = 3,
            ),
            thresholds = jvm.daily.PipelineService.QualityGateThresholds(
                maxDuplicates = 10,
                maxFeedFailures = 10,
                maxSummarizationFailures = 1,
            )
        )

        assertTrue(!result.passed)
        assertTrue(result.breaches.any { it.startsWith("summarization_failures") })
    }
}
