package jvm.daily

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

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
}
