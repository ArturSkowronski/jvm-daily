package jvm.daily.tools

import jvm.daily.model.CanonicalArticleId
import java.sql.Connection

class ValidateRawArticleIds(private val connection: Connection) {
    data class Summary(
        val totalRows: Int,
        val mismatches: Int,
        val collisions: Int,
        val updated: Int,
    )

    fun run(applyUpdates: Boolean = false): Summary {
        val rows = loadRows()
        var mismatches = 0
        var collisions = 0
        var updated = 0

        for (row in rows) {
            val expectedId = expectedId(row)
            if (row.id == expectedId) {
                continue
            }

            mismatches++
            if (!applyUpdates) {
                continue
            }

            if (idExists(expectedId)) {
                collisions++
                continue
            }

            updateId(row.id, expectedId)
            updated++
        }

        return Summary(
            totalRows = rows.size,
            mismatches = mismatches,
            collisions = collisions,
            updated = updated,
        )
    }

    private fun expectedId(row: RawArticleRow): String {
        val namespace = namespaceFor(row.sourceType)
        val markdownNativeId = if (namespace == "md") row.sourceId.substringBeforeLast('.') else null
        return CanonicalArticleId.from(
            namespace = namespace,
            sourceId = row.sourceId,
            title = row.title,
            url = row.url,
            sourceNativeId = markdownNativeId,
        )
    }

    private fun namespaceFor(sourceType: String): String =
        when (sourceType) {
            "markdown_file" -> "md"
            else -> sourceType
        }

    private fun loadRows(): List<RawArticleRow> {
        val rows = mutableListOf<RawArticleRow>()
        connection.createStatement().use { stmt ->
            stmt.executeQuery(
                """
                SELECT id, title, source_type, source_id, url
                FROM articles
                ORDER BY id
                """.trimIndent()
            ).use { rs ->
                while (rs.next()) {
                    rows += RawArticleRow(
                        id = rs.getString("id"),
                        title = rs.getString("title"),
                        sourceType = rs.getString("source_type"),
                        sourceId = rs.getString("source_id"),
                        url = rs.getString("url"),
                    )
                }
            }
        }
        return rows
    }

    private fun idExists(id: String): Boolean {
        connection.prepareStatement("SELECT 1 FROM articles WHERE id = ?").use { stmt ->
            stmt.setString(1, id)
            stmt.executeQuery().use { rs -> return rs.next() }
        }
    }

    private fun updateId(fromId: String, toId: String) {
        connection.prepareStatement("UPDATE articles SET id = ? WHERE id = ?").use { stmt ->
            stmt.setString(1, toId)
            stmt.setString(2, fromId)
            stmt.executeUpdate()
        }
    }

    private data class RawArticleRow(
        val id: String,
        val title: String,
        val sourceType: String,
        val sourceId: String,
        val url: String?,
    )
}
