package jvm.daily

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
}
