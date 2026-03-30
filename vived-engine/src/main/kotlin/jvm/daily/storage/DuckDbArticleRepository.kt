package jvm.daily.storage

import jvm.daily.model.Article
import jvm.daily.model.FeedRunSnapshot
import kotlinx.datetime.Instant
import java.sql.Connection

class DuckDbArticleRepository(private val connection: Connection) : ArticleRepository {

    init {
        createTable()
    }

    private fun createTable() {
        connection.createStatement().use { stmt ->
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS articles (
                    id VARCHAR PRIMARY KEY,
                    title VARCHAR NOT NULL,
                    content VARCHAR NOT NULL,
                    source_type VARCHAR NOT NULL,
                    source_id VARCHAR NOT NULL,
                    url VARCHAR,
                    author VARCHAR,
                    comments VARCHAR,
                    ingested_at VARCHAR NOT NULL
                )
                """.trimIndent()
            )

            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS ingest_feed_runs (
                    run_id VARCHAR NOT NULL,
                    recorded_at VARCHAR NOT NULL,
                    source_type VARCHAR NOT NULL,
                    source_id VARCHAR NOT NULL,
                    status VARCHAR NOT NULL,
                    fetched_count INTEGER NOT NULL,
                    new_count INTEGER NOT NULL,
                    duplicate_count INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    override fun save(article: Article) {
        connection.prepareStatement(
            """
            INSERT OR REPLACE INTO articles (id, title, content, source_type, source_id, url, author, comments, ingested_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { stmt ->
            stmt.setString(1, article.id)
            stmt.setString(2, article.title)
            stmt.setString(3, article.content)
            stmt.setString(4, article.sourceType)
            stmt.setString(5, article.sourceId)
            stmt.setString(6, article.url)
            stmt.setString(7, article.author)
            stmt.setString(8, article.comments)
            stmt.setString(9, article.ingestedAt.toString())
            stmt.executeUpdate()
        }
    }

    override fun saveAll(articles: List<Article>) {
        articles.forEach { save(it) }
    }

    override fun findAll(): List<Article> {
        val results = mutableListOf<Article>()
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT * FROM articles ORDER BY ingested_at DESC").use { rs ->
                while (rs.next()) {
                    results.add(rs.toArticle())
                }
            }
        }
        return results
    }

    override fun findBySourceType(sourceType: String): List<Article> {
        val results = mutableListOf<Article>()
        connection.prepareStatement("SELECT * FROM articles WHERE source_type = ? ORDER BY ingested_at DESC").use { stmt ->
            stmt.setString(1, sourceType)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    results.add(rs.toArticle())
                }
            }
        }
        return results
    }

    override fun existsById(id: String): Boolean {
        connection.prepareStatement("SELECT 1 FROM articles WHERE id = ?").use { stmt ->
            stmt.setString(1, id)
            stmt.executeQuery().use { rs ->
                return rs.next()
            }
        }
    }

    override fun count(): Long {
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT COUNT(*) FROM articles").use { rs ->
                rs.next()
                return rs.getLong(1)
            }
        }
    }

    override fun countSince(since: Instant): Long {
        connection.prepareStatement("SELECT COUNT(*) FROM articles WHERE ingested_at >= ?").use { stmt ->
            stmt.setString(1, since.toString())
            stmt.executeQuery().use { rs ->
                rs.next()
                return rs.getLong(1)
            }
        }
    }

    override fun recordFeedRunSnapshots(snapshots: List<FeedRunSnapshot>) {
        if (snapshots.isEmpty()) return
        connection.prepareStatement(
            """
            INSERT INTO ingest_feed_runs
            (run_id, recorded_at, source_type, source_id, status, fetched_count, new_count, duplicate_count)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { stmt ->
            snapshots.forEach { s ->
                stmt.setString(1, s.runId)
                stmt.setString(2, s.recordedAt.toString())
                stmt.setString(3, s.sourceType)
                stmt.setString(4, s.sourceId)
                stmt.setString(5, s.status.name)
                stmt.setInt(6, s.fetchedCount)
                stmt.setInt(7, s.newCount)
                stmt.setInt(8, s.duplicateCount)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    override fun sumDuplicateCountSince(since: Instant): Long {
        connection.prepareStatement(
            "SELECT COALESCE(SUM(duplicate_count), 0) FROM ingest_feed_runs WHERE recorded_at >= ?"
        ).use { stmt ->
            stmt.setString(1, since.toString())
            stmt.executeQuery().use { rs ->
                rs.next()
                return rs.getLong(1)
            }
        }
    }

    override fun countFeedFailuresSince(since: Instant): Long {
        connection.prepareStatement(
            "SELECT COUNT(*) FROM ingest_feed_runs WHERE recorded_at >= ? AND status = 'FAILED'"
        ).use { stmt ->
            stmt.setString(1, since.toString())
            stmt.executeQuery().use { rs ->
                rs.next()
                return rs.getLong(1)
            }
        }
    }

    override fun queryFeedRunSummaries(): List<FeedRunSummary> {
        val sql = """
            WITH last24h AS (
                SELECT * FROM ingest_feed_runs
                WHERE recorded_at >= ?
            ),
            latest_run AS (
                SELECT source_type, source_id,
                       recorded_at AS last_run_at,
                       status AS last_run_status,
                       ROW_NUMBER() OVER (PARTITION BY source_type, source_id ORDER BY recorded_at DESC) AS rn
                FROM ingest_feed_runs
            ),
            last_success AS (
                SELECT source_type, source_id,
                       MAX(recorded_at) AS last_success_at
                FROM ingest_feed_runs
                WHERE status = 'SUCCESS'
                GROUP BY source_type, source_id
            ),
            agg AS (
                SELECT source_type, source_id,
                       COUNT(*) AS runs,
                       COUNT(*) FILTER (WHERE status = 'SUCCESS') AS successes,
                       COUNT(*) FILTER (WHERE status = 'FAILED') AS failures,
                       COALESCE(SUM(new_count), 0) AS new_total
                FROM last24h
                GROUP BY source_type, source_id
            )
            SELECT
                COALESCE(lr.source_type, a.source_type) AS source_type,
                COALESCE(lr.source_id, a.source_id) AS source_id,
                lr.last_run_at,
                lr.last_run_status,
                ls.last_success_at,
                COALESCE(a.runs, 0) AS last_24h_runs,
                COALESCE(a.successes, 0) AS last_24h_successes,
                COALESCE(a.failures, 0) AS last_24h_failures,
                COALESCE(a.new_total, 0) AS last_24h_new
            FROM latest_run lr
            LEFT JOIN last_success ls ON lr.source_type = ls.source_type AND lr.source_id = ls.source_id
            LEFT JOIN agg a ON lr.source_type = a.source_type AND lr.source_id = a.source_id
            WHERE lr.rn = 1
            ORDER BY lr.source_type, lr.source_id
        """.trimIndent()

        val since = kotlinx.datetime.Clock.System.now().minus(kotlin.time.Duration.parse("24h"))
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, since.toString())
            stmt.executeQuery().use { rs ->
                val results = mutableListOf<FeedRunSummary>()
                while (rs.next()) {
                    results.add(
                        FeedRunSummary(
                            sourceType = rs.getString("source_type"),
                            sourceId = rs.getString("source_id"),
                            lastRunAt = rs.getString("last_run_at") ?: "",
                            lastRunStatus = rs.getString("last_run_status") ?: "",
                            lastSuccessAt = rs.getString("last_success_at"),
                            last24hRuns = rs.getInt("last_24h_runs"),
                            last24hSuccesses = rs.getInt("last_24h_successes"),
                            last24hFailures = rs.getInt("last_24h_failures"),
                            last24hNewCount = rs.getInt("last_24h_new"),
                        )
                    )
                }
                return results
            }
        }
    }

    private fun java.sql.ResultSet.toArticle(): Article = Article(
        id = getString("id"),
        title = getString("title"),
        content = getString("content"),
        sourceType = getString("source_type"),
        sourceId = getString("source_id"),
        url = getString("url"),
        author = getString("author"),
        comments = getString("comments"),
        ingestedAt = Instant.parse(getString("ingested_at")),
    )
}
