package jvm.daily.storage

import jvm.daily.model.ProcessedArticle
import jvm.daily.model.EnrichmentOutcomeStatus
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.sql.Connection

class DuckDbProcessedArticleRepository(private val connection: Connection) : ProcessedArticleRepository {

    init {
        createTable()
    }

    private fun createTable() {
        connection.createStatement().use { stmt ->
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS processed_articles (
                    id VARCHAR PRIMARY KEY,
                    original_title VARCHAR NOT NULL,
                    normalized_title VARCHAR NOT NULL,
                    summary VARCHAR NOT NULL,
                    original_content VARCHAR NOT NULL,
                    source_type VARCHAR NOT NULL,
                    source_id VARCHAR NOT NULL,
                    url VARCHAR,
                    author VARCHAR,
                    published_at VARCHAR NOT NULL,
                    ingested_at VARCHAR NOT NULL,
                    processed_at VARCHAR NOT NULL,
                    entities VARCHAR NOT NULL,
                    topics VARCHAR NOT NULL,
                    engagement_score DOUBLE NOT NULL,
                    outcome_status VARCHAR NOT NULL DEFAULT 'SUCCESS',
                    failure_reason VARCHAR,
                    last_attempt_at VARCHAR,
                    attempt_count INTEGER NOT NULL DEFAULT 1,
                    warnings VARCHAR NOT NULL DEFAULT '[]'
                )
                """.trimIndent()
            )

            // Index for date range queries
            stmt.execute(
                "CREATE INDEX IF NOT EXISTS idx_processed_at ON processed_articles(processed_at)"
            )
        }

        ensureColumn("processed_articles", "outcome_status", "VARCHAR NOT NULL DEFAULT 'SUCCESS'")
        ensureColumn("processed_articles", "failure_reason", "VARCHAR")
        ensureColumn("processed_articles", "last_attempt_at", "VARCHAR")
        ensureColumn("processed_articles", "attempt_count", "INTEGER NOT NULL DEFAULT 1")
        ensureColumn("processed_articles", "warnings", "VARCHAR NOT NULL DEFAULT '[]'")
    }

    override fun save(article: ProcessedArticle) {
        connection.prepareStatement(
            """
            INSERT OR REPLACE INTO processed_articles
            (id, original_title, normalized_title, summary, original_content,
             source_type, source_id, url, author, published_at, ingested_at,
             processed_at, entities, topics, engagement_score, outcome_status,
             failure_reason, last_attempt_at, attempt_count, warnings)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { stmt ->
            stmt.setString(1, article.id)
            stmt.setString(2, article.originalTitle)
            stmt.setString(3, article.normalizedTitle)
            stmt.setString(4, article.summary)
            stmt.setString(5, article.originalContent)
            stmt.setString(6, article.sourceType)
            stmt.setString(7, article.sourceId)
            stmt.setString(8, article.url)
            stmt.setString(9, article.author)
            stmt.setString(10, article.publishedAt.toString())
            stmt.setString(11, article.ingestedAt.toString())
            stmt.setString(12, article.processedAt.toString())
            stmt.setString(13, Json.encodeToString(article.entities))
            stmt.setString(14, Json.encodeToString(article.topics))
            stmt.setDouble(15, article.engagementScore)
            stmt.setString(16, article.outcomeStatus.name)
            stmt.setString(17, article.failureReason)
            stmt.setString(18, article.lastAttemptAt?.toString())
            stmt.setInt(19, article.attemptCount)
            stmt.setString(20, Json.encodeToString(article.warnings))
            stmt.executeUpdate()
        }
    }

    override fun saveAll(articles: List<ProcessedArticle>) {
        articles.forEach { save(it) }
    }

    override fun findAll(): List<ProcessedArticle> {
        val results = mutableListOf<ProcessedArticle>()
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT * FROM processed_articles ORDER BY processed_at DESC").use { rs ->
                while (rs.next()) {
                    results.add(rs.toProcessedArticle())
                }
            }
        }
        return results
    }

    override fun findByDateRange(startDate: Instant, endDate: Instant): List<ProcessedArticle> {
        val results = mutableListOf<ProcessedArticle>()
        connection.prepareStatement(
            "SELECT * FROM processed_articles WHERE processed_at >= ? AND processed_at <= ? ORDER BY processed_at DESC"
        ).use { stmt ->
            stmt.setString(1, startDate.toString())
            stmt.setString(2, endDate.toString())
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    results.add(rs.toProcessedArticle())
                }
            }
        }
        return results
    }

    override fun findFailedSince(since: Instant): List<ProcessedArticle> {
        val results = mutableListOf<ProcessedArticle>()
        connection.prepareStatement(
            """
            SELECT * FROM processed_articles
            WHERE processed_at >= ? AND outcome_status = 'FAILED'
            ORDER BY processed_at DESC
            """.trimIndent()
        ).use { stmt ->
            stmt.setString(1, since.toString())
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    results.add(rs.toProcessedArticle())
                }
            }
        }
        return results
    }

    override fun countFailedSince(since: Instant): Long {
        connection.prepareStatement(
            "SELECT COUNT(*) FROM processed_articles WHERE processed_at >= ? AND outcome_status = 'FAILED'"
        ).use { stmt ->
            stmt.setString(1, since.toString())
            stmt.executeQuery().use { rs ->
                rs.next()
                return rs.getLong(1)
            }
        }
    }

    override fun findFailedRawArticleIds(since: Instant, limit: Int): List<String> {
        if (limit <= 0) return emptyList()

        val results = mutableListOf<String>()
        connection.prepareStatement(
            """
            SELECT id FROM processed_articles
            WHERE processed_at >= ? AND outcome_status = 'FAILED'
            ORDER BY processed_at DESC, id ASC
            LIMIT ?
            """.trimIndent()
        ).use { stmt ->
            stmt.setString(1, since.toString())
            stmt.setInt(2, limit)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    results.add(rs.getString("id"))
                }
            }
        }
        return results
    }

    override fun findFailedByIds(ids: List<String>): List<ProcessedArticle> {
        if (ids.isEmpty()) return emptyList()

        val placeholders = ids.joinToString(",") { "?" }
        val sql = """
            SELECT * FROM processed_articles
            WHERE outcome_status = 'FAILED' AND id IN ($placeholders)
            """.trimIndent()

        val byId = mutableMapOf<String, ProcessedArticle>()
        connection.prepareStatement(sql).use { stmt ->
            ids.forEachIndexed { index, id -> stmt.setString(index + 1, id) }
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    val article = rs.toProcessedArticle()
                    byId[article.id] = article
                }
            }
        }
        return ids.mapNotNull { byId[it] }
    }

    override fun findUnprocessedRawArticles(since: Instant): List<String> {
        val results = mutableListOf<String>()
        connection.prepareStatement(
            """
            SELECT a.id FROM articles a
            WHERE a.ingested_at >= ?
            AND NOT EXISTS (
                SELECT 1 FROM processed_articles p WHERE p.id = a.id
            )
            ORDER BY a.ingested_at DESC
            """.trimIndent()
        ).use { stmt ->
            stmt.setString(1, since.toString())
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    results.add(rs.getString(1))
                }
            }
        }
        return results
    }

    override fun existsById(id: String): Boolean {
        connection.prepareStatement("SELECT 1 FROM processed_articles WHERE id = ?").use { stmt ->
            stmt.setString(1, id)
            stmt.executeQuery().use { rs ->
                return rs.next()
            }
        }
    }

    override fun count(): Long {
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT COUNT(*) FROM processed_articles").use { rs ->
                rs.next()
                return rs.getLong(1)
            }
        }
    }

    private fun java.sql.ResultSet.toProcessedArticle(): ProcessedArticle = ProcessedArticle(
        id = getString("id"),
        originalTitle = getString("original_title"),
        normalizedTitle = getString("normalized_title"),
        summary = getString("summary"),
        originalContent = getString("original_content"),
        sourceType = getString("source_type"),
        sourceId = getString("source_id"),
        url = getString("url"),
        author = getString("author"),
        publishedAt = Instant.parse(getString("published_at")),
        ingestedAt = Instant.parse(getString("ingested_at")),
        processedAt = Instant.parse(getString("processed_at")),
        entities = Json.decodeFromString(getString("entities")),
        topics = Json.decodeFromString(getString("topics")),
        engagementScore = getDouble("engagement_score"),
        outcomeStatus = runCatching { EnrichmentOutcomeStatus.valueOf(getString("outcome_status")) }
            .getOrDefault(EnrichmentOutcomeStatus.SUCCESS),
        failureReason = getString("failure_reason"),
        lastAttemptAt = getString("last_attempt_at")?.takeIf { it.isNotBlank() }?.let { Instant.parse(it) },
        attemptCount = getInt("attempt_count"),
        warnings = runCatching { Json.decodeFromString<List<String>>(getString("warnings")) }.getOrDefault(emptyList()),
    )

    private fun ensureColumn(table: String, column: String, definition: String) {
        runCatching {
            connection.createStatement().use { stmt ->
                stmt.execute("ALTER TABLE $table ADD COLUMN $column $definition")
            }
        }
    }
}
