package jvm.daily.storage

import jvm.daily.model.ArticleCluster
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.sql.Connection

class DuckDbClusterRepository(private val connection: Connection) : ClusterRepository {

    init {
        createTable()
    }

    private fun createTable() {
        connection.createStatement().use { stmt ->
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS article_clusters (
                    id               VARCHAR PRIMARY KEY,
                    title            VARCHAR NOT NULL,
                    summary          VARCHAR NOT NULL,
                    article_ids      VARCHAR NOT NULL,
                    sources          VARCHAR NOT NULL,
                    total_engagement DOUBLE NOT NULL,
                    created_at       VARCHAR NOT NULL
                )
                """.trimIndent()
            )
            // Migration: add columns if they don't exist (idempotent)
            stmt.execute("ALTER TABLE article_clusters ADD COLUMN IF NOT EXISTS type VARCHAR DEFAULT 'topic'")
            stmt.execute("ALTER TABLE article_clusters ADD COLUMN IF NOT EXISTS bullets VARCHAR DEFAULT '[]'")
        }
    }

    override fun save(cluster: ArticleCluster) {
        connection.prepareStatement(
            """
            INSERT OR REPLACE INTO article_clusters
            (id, title, summary, article_ids, sources, total_engagement, created_at, type, bullets)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { stmt ->
            stmt.setString(1, cluster.id)
            stmt.setString(2, cluster.title)
            stmt.setString(3, cluster.summary)
            stmt.setString(4, Json.encodeToString(cluster.articles))
            stmt.setString(5, Json.encodeToString(cluster.sources))
            stmt.setDouble(6, cluster.totalEngagement)
            stmt.setString(7, cluster.createdAt.toString())
            stmt.setString(8, cluster.type)
            stmt.setString(9, Json.encodeToString(cluster.bullets))
            stmt.executeUpdate()
        }
    }

    override fun saveAll(clusters: List<ArticleCluster>) {
        clusters.forEach { save(it) }
    }

    override fun findByDateRange(start: Instant, end: Instant): List<ArticleCluster> {
        val results = mutableListOf<ArticleCluster>()
        connection.prepareStatement(
            "SELECT * FROM article_clusters WHERE created_at >= ? AND created_at <= ? ORDER BY created_at DESC"
        ).use { stmt ->
            stmt.setString(1, start.toString())
            stmt.setString(2, end.toString())
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    results.add(rs.toArticleCluster())
                }
            }
        }
        return results
    }

    override fun deleteByDateRange(start: Instant, end: Instant) {
        connection.prepareStatement(
            "DELETE FROM article_clusters WHERE created_at >= ? AND created_at <= ?"
        ).use { stmt ->
            stmt.setString(1, start.toString())
            stmt.setString(2, end.toString())
            stmt.executeUpdate()
        }
    }

    private fun java.sql.ResultSet.toArticleCluster(): ArticleCluster = ArticleCluster(
        id = getString("id"),
        title = getString("title"),
        summary = getString("summary"),
        articles = Json.decodeFromString(getString("article_ids")),
        sources = Json.decodeFromString(getString("sources")),
        totalEngagement = getDouble("total_engagement"),
        createdAt = Instant.parse(getString("created_at")),
        type = getString("type") ?: "topic",
        bullets = Json.decodeFromString(getString("bullets") ?: "[]"),
    )
}
