package jvm.daily

import jvm.daily.config.SourcesConfig
import jvm.daily.source.MarkdownFileSource
import jvm.daily.source.RssSource
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
    val configPath = System.getenv("CONFIG_PATH") ?: "config/sources.yml"

    println("JVM Daily — starting pipeline")
    println("Database: $dbPath")
    println("Config: $configPath")

    val config = SourcesConfig.load(Path.of(configPath))

    DuckDbConnectionFactory.persistent(dbPath).use { connection ->
        val repository = DuckDbArticleRepository(connection)

        val sourceRegistry = SourceRegistry().apply {
            register(MarkdownFileSource(Path.of(sourcesDir)))
            if (config.rss.isNotEmpty()) {
                register(RssSource(config.rss))
            }
        }

        val workflowRunner = WorkflowRunner().apply {
            register(IngressWorkflow(sourceRegistry, repository))
        }

        workflowRunner.run("ingress")

        println("Articles in DB: ${repository.count()}")
    }
}
