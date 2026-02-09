package jvm.daily.source

import jvm.daily.model.Article
import kotlinx.datetime.Clock
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.streams.toList

class MarkdownFileSource(
    private val directory: Path,
    private val clock: Clock = Clock.System,
) : Source {

    override val sourceType: String = "markdown_file"

    override suspend fun fetch(): List<Article> {
        if (!Files.exists(directory) || !Files.isDirectory(directory)) {
            return emptyList()
        }

        return Files.list(directory)
            .filter { it.extension == "md" }
            .toList()
            .map { path ->
                val content = path.readText()
                val title = extractTitle(content, path.nameWithoutExtension)
                Article(
                    id = "md:${path.nameWithoutExtension}",
                    title = title,
                    content = content,
                    sourceType = sourceType,
                    sourceId = path.fileName.toString(),
                    ingestedAt = clock.now(),
                )
            }
    }

    private fun extractTitle(content: String, fallback: String): String {
        val firstLine = content.lineSequence().firstOrNull { it.isNotBlank() } ?: return fallback
        return if (firstLine.startsWith("# ")) {
            firstLine.removePrefix("# ").trim()
        } else {
            fallback
        }
    }
}
