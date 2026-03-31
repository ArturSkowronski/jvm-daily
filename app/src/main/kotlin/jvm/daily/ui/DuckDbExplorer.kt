package jvm.daily.ui

import dev.vived.engine.storage.DuckDbConnectionFactory
import java.sql.Connection
import kotlin.system.exitProcess

object DuckDbExplorer {
    private lateinit var connection: Connection

    fun start(dbPath: String = "${System.getProperty("user.home")}/.jvm-daily/jvm-daily.duckdb") {
        connection = DuckDbConnectionFactory.persistent(dbPath)
        println("\n🔍 DuckDB Explorer — Interactive Database Browser")
        println("Database: $dbPath")
        println("Type 'help' for commands, 'exit' to quit\n")

        while (true) {
            print("> ")
            val input = readLine() ?: break
            val trimmed = input.trim()

            when {
                trimmed.isEmpty() -> continue
                trimmed.equals("exit", ignoreCase = true) -> {
                    connection.close()
                    println("Goodbye!")
                    exitProcess(0)
                }
                trimmed.equals("help", ignoreCase = true) -> printHelp()
                trimmed.equals("tables", ignoreCase = true) -> listTables()
                trimmed.equals("schema", ignoreCase = true) -> describeArticles()
                trimmed.equals("count", ignoreCase = true) -> countArticles()
                trimmed.equals("sources", ignoreCase = true) -> listSources()
                trimmed.equals("recent", ignoreCase = true) -> recentArticles()
                trimmed.equals("stats", ignoreCase = true) -> stats()
                trimmed.startsWith("find ", ignoreCase = true) -> {
                    val query = trimmed.substring(5)
                    searchArticles(query)
                }
                trimmed.startsWith("sql ", ignoreCase = true) -> {
                    val sql = trimmed.substring(4)
                    executeSql(sql)
                }
                else -> println("Unknown command. Type 'help' for commands.")
            }
        }
    }

    private fun printHelp() {
        println("""
            Commands:
              tables              — List all tables
              schema              — Show articles table schema
              count               — Count total articles
              sources             — List all RSS sources with counts
              recent              — Show 10 most recent articles
              stats               — Show article statistics
              find <text>         — Search articles by title/content
              sql <query>         — Execute raw SQL query
              help                — Show this help
              exit                — Exit explorer
        """.trimIndent())
    }

    private fun listTables() {
        val query = "SELECT table_name FROM duckdb_tables() WHERE table_catalog='memory' OR table_schema='main'"
        connection.createStatement().use { stmt ->
            stmt.executeQuery(query).use { rs ->
                println("\n📋 Tables:")
                while (rs.next()) {
                    println("  - ${rs.getString(1)}")
                }
            }
        }
    }

    private fun describeArticles() {
        val query = "DESCRIBE articles"
        connection.createStatement().use { stmt ->
            stmt.executeQuery(query).use { rs ->
                println("\n📊 Schema: articles")
                println(String.format("  %-15s %-15s %-5s", "Column", "Type", "Null"))
                println("  " + "-".repeat(35))
                while (rs.next()) {
                    val col = rs.getString(1)
                    val type = rs.getString(2)
                    val nullable = rs.getString(3)
                    println(String.format("  %-15s %-15s %-5s", col, type, nullable))
                }
            }
        }
    }

    private fun countArticles() {
        val query = "SELECT COUNT(*) FROM articles"
        connection.createStatement().use { stmt ->
            stmt.executeQuery(query).use { rs ->
                if (rs.next()) {
                    println("\n📈 Total Articles: ${rs.getLong(1)}")
                }
            }
        }
    }

    private fun listSources() {
        val query = """
            SELECT source_id, COUNT(*) as count
            FROM articles
            WHERE source_type = 'rss'
            GROUP BY source_id
            ORDER BY count DESC
        """
        connection.createStatement().use { stmt ->
            stmt.executeQuery(query).use { rs ->
                println("\n📡 RSS Sources:")
                println(String.format("  %-60s %s", "Source", "Count"))
                println("  " + "-".repeat(65))
                while (rs.next()) {
                    val source = rs.getString(1).take(60)
                    val count = rs.getLong(2)
                    println(String.format("  %-60s %d", source, count))
                }
            }
        }
    }

    private fun recentArticles() {
        val query = """
            SELECT title, source_type, source_id, ingested_at
            FROM articles
            ORDER BY ingested_at DESC
            LIMIT 10
        """
        connection.createStatement().use { stmt ->
            stmt.executeQuery(query).use { rs ->
                println("\n🆕 Recent Articles:")
                var i = 1
                while (rs.next()) {
                    val title = rs.getString(1).take(70)
                    val sourceType = rs.getString(2)
                    val sourceId = rs.getString(3).substringAfterLast("/").take(25)
                    val time = rs.getString(4).take(10)
                    println("  $i. [$sourceType] $title")
                    println("     $sourceId @ $time")
                    i++
                }
            }
        }
    }

    private fun stats() {
        val query = """
            SELECT
              source_type,
              COUNT(*) as count,
              ROUND(AVG(LENGTH(content))) as avg_content_len,
              MAX(LENGTH(content)) as max_content_len
            FROM articles
            GROUP BY source_type
        """
        connection.createStatement().use { stmt ->
            stmt.executeQuery(query).use { rs ->
                println("\n📊 Statistics:")
                println(String.format("  %-20s %-10s %-15s %-15s", "Source", "Count", "Avg Len", "Max Len"))
                println("  " + "-".repeat(60))
                while (rs.next()) {
                    val source = rs.getString(1)
                    val count = rs.getLong(2)
                    val avgLen = rs.getLong(3)
                    val maxLen = rs.getLong(4)
                    println(String.format("  %-20s %-10d %-15d %-15d", source, count, avgLen, maxLen))
                }
            }
        }
    }

    private fun searchArticles(query: String) {
        val sql = """
            SELECT title, source_type, url
            FROM articles
            WHERE LOWER(title) LIKE LOWER('%$query%')
               OR LOWER(content) LIKE LOWER('%$query%')
            LIMIT 10
        """
        connection.createStatement().use { stmt ->
            stmt.executeQuery(sql).use { rs ->
                val results = mutableListOf<Triple<String, String, String?>>()
                while (rs.next()) {
                    results.add(
                        Triple(rs.getString(1), rs.getString(2), rs.getString(3))
                    )
                }
                println("\n🔎 Found ${results.size} matching articles:")
                results.forEachIndexed { i, (title, source, url) ->
                    println("  ${i + 1}. [$source] $title")
                    if (url != null) println("     $url")
                }
            }
        }
    }

    private fun executeSql(sql: String) {
        try {
            connection.createStatement().use { stmt ->
                val result = stmt.executeQuery(sql)
                result.use { rs ->
                    val meta = rs.metaData
                    val colCount = meta.columnCount
                    val headers = (1..colCount).map { meta.getColumnName(it) }

                    println("\n📊 Query Results:")
                    headers.forEach { print("  $it | ") }
                    println()
                    println("  " + "-".repeat(headers.joinToString(" | ").length))

                    while (rs.next()) {
                        (1..colCount).forEach { col ->
                            val value = rs.getString(col)?.take(30) ?: "NULL"
                            print("  $value | ")
                        }
                        println()
                    }
                }
            }
        } catch (e: Exception) {
            println("❌ Error: ${e.message}")
        }
    }
}

fun main(args: Array<String>) {
    val dbPath = args.getOrNull(0) ?: "${System.getProperty("user.home")}/.jvm-daily/jvm-daily.duckdb"
    DuckDbExplorer.start(dbPath)
}
