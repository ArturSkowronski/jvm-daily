package jvm.daily

import jvm.daily.source.MarkdownFileSource
import jvm.daily.source.SourceRegistry
import jvm.daily.storage.DuckDbArticleRepository
import jvm.daily.storage.DuckDbConnectionFactory
import jvm.daily.workflow.IngressWorkflow
import jvm.daily.workflow.WorkflowRunner
import kotlinx.coroutines.runBlocking
import java.nio.file.Path

fun main() = runBlocking {
    val dbPath = System.getenv("DUCKDB_PATH") ?: "jvm-daily.duckdb"
    val sourcesDir = System.getenv("SOURCES_DIR") ?: "sources"

    println("JVM Daily — starting pipeline")
    println("Database: $dbPath")
    println("Sources directory: $sourcesDir")

    val connection = DuckDbConnectionFactory.persistent(dbPath)
    val repository = DuckDbArticleRepository(connection)

    val sourceRegistry = SourceRegistry().apply {
        register(MarkdownFileSource(Path.of(sourcesDir)))
    }

    val workflowRunner = WorkflowRunner().apply {
        register(IngressWorkflow(sourceRegistry, repository))
    }

    workflowRunner.run("ingress")

    println("Articles in DB: ${repository.count()}")
    connection.close()
}
