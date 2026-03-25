package jvm.daily.workflow

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class QualityGateTest {

    @Test
    fun `quality gate flags feed failure threshold breach`() {
        val result = jvm.daily.PipelineService.evaluateQualityGate(
            counters = jvm.daily.PipelineService.QualityCounters(
                newItems = 5,
                duplicates = 1,
                feedFailures = 2,
                summarizationFailures = 0,
            ),
            thresholds = jvm.daily.PipelineService.QualityGateThresholds(
                maxDuplicates = 10,
                maxFeedFailures = 1,
                maxSummarizationFailures = 10,
            )
        )

        assertTrue(!result.passed)
        assertTrue(result.breaches.any { it.startsWith("feed_failures") })
    }
}
