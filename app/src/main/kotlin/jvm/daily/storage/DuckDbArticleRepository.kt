package jvm.daily.storage

import jvm.daily.model.Article
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

    override fun count(): Long {
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT COUNT(*) FROM articles").use { rs ->
                rs.next()
                return rs.getLong(1)
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
