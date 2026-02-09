package jvm.daily.workflow

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkflowRunnerTest {

    @Test
    fun `run executes named workflow`() = runTest {
        var executed = false
        val runner = WorkflowRunner()
        runner.register(stubWorkflow("test") { executed = true })

        runner.run("test")

        assertTrue(executed)
    }

    @Test
    fun `run throws for unknown workflow`() = runTest {
        val runner = WorkflowRunner()

        assertThrows<IllegalArgumentException> {
            runner.run("nonexistent")
        }
    }

    @Test
    fun `runAll executes all registered workflows`() = runTest {
        val executed = mutableListOf<String>()
        val runner = WorkflowRunner()
        runner.register(stubWorkflow("first") { executed.add("first") })
        runner.register(stubWorkflow("second") { executed.add("second") })

        runner.runAll()

        assertEquals(listOf("first", "second"), executed)
    }

    private fun stubWorkflow(workflowName: String, action: () -> Unit) = object : Workflow {
        override val name: String = workflowName
        override suspend fun execute() { action() }
    }
}
