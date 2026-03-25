package jvm.daily.architecture

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class LayerDependencyTest {

    @Test
    fun `source package does not depend on workflow package`() {
        val sourceFiles = ArchitectureInvariantsSupport.packageFiles("jvm.daily.source")
        assertTrue(sourceFiles.isNotEmpty(), "Expected source package files")

        sourceFiles.forEach { file ->
            val imports = ArchitectureInvariantsSupport.importsOf(file)
            val forbidden = imports.filter { it.startsWith("jvm.daily.workflow") }
            assertTrue(
                forbidden.isEmpty(),
                "Source file $file imports workflow package: $forbidden"
            )
        }
    }

    @Test
    fun `storage package does not depend on workflow or source implementation packages`() {
        val storageFiles = ArchitectureInvariantsSupport.packageFiles("jvm.daily.storage")
        assertTrue(storageFiles.isNotEmpty(), "Expected storage package files")

        storageFiles.forEach { file ->
            val imports = ArchitectureInvariantsSupport.importsOf(file)
            val forbidden = imports.filter {
                it.startsWith("jvm.daily.workflow") ||
                    it == "jvm.daily.source.RssSource" ||
                    it == "jvm.daily.source.MarkdownFileSource"
            }
            assertTrue(
                forbidden.isEmpty(),
                "Storage file $file has forbidden dependency imports: $forbidden"
            )
        }
    }
}
