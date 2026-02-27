package jvm.daily.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

internal object ArchitectureInvariantsSupport {

    fun packageFiles(relativePackage: String): List<Path> {
        val root = resolveMainKotlinRoot()
        val pkgPath = root.resolve(relativePackage.replace('.', '/'))
        if (!pkgPath.exists()) {
            return emptyList()
        }
        return Files.walk(pkgPath)
            .filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
            .toList()
    }

    fun importsOf(file: Path): List<String> =
        Files.readAllLines(file)
            .map { it.trim() }
            .filter { it.startsWith("import ") }
            .map { it.removePrefix("import ").trim() }

    private fun resolveMainKotlinRoot(): Path {
        val cwd = Path.of("").toAbsolutePath().normalize()
        val candidates = listOf(
            cwd.resolve("app/src/main/kotlin"),
            cwd.resolve("src/main/kotlin"),
            cwd.parent?.resolve("app/src/main/kotlin"),
        ).filterNotNull()

        return candidates.firstOrNull { it.exists() }
            ?: error("Cannot locate app/src/main/kotlin from working dir: $cwd")
    }
}
