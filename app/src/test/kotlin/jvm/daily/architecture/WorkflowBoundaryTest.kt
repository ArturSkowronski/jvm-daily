package jvm.daily.architecture

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkflowBoundaryTest {

    @Test
    fun `workflow layer does not import concrete source implementations`() {
        val workflowFiles = ArchitectureInvariantsSupport.packageFiles("jvm.daily.workflow")
        assertTrue(workflowFiles.isNotEmpty(), "Expected workflow package files")

        val forbiddenImports = setOf(
            "jvm.daily.source.RssSource",
            "jvm.daily.source.MarkdownFileSource",
        )

        workflowFiles.forEach { file ->
            val imports = ArchitectureInvariantsSupport.importsOf(file)
            val forbidden = imports.filter { it in forbiddenImports }
            assertTrue(
                forbidden.isEmpty(),
                "Workflow file $file imports concrete source implementations: $forbidden"
            )
        }
    }

    @Test
    fun `workflow layer does not import concrete storage repositories`() {
        val workflowFiles = ArchitectureInvariantsSupport.packageFiles("jvm.daily.workflow")
        assertTrue(workflowFiles.isNotEmpty(), "Expected workflow package files")

        workflowFiles.forEach { file ->
            val imports = ArchitectureInvariantsSupport.importsOf(file)
            val forbidden = imports.filter { it.startsWith("jvm.daily.storage.DuckDb") }
            assertFalse(
                forbidden.isNotEmpty(),
                "Workflow file $file imports concrete storage class(es): $forbidden"
            )
        }
    }
}
