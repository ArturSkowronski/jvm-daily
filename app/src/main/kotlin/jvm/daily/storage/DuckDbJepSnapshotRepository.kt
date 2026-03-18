package jvm.daily.storage

import jvm.daily.model.JepSnapshot
import java.sql.Connection

class DuckDbJepSnapshotRepository(private val connection: Connection) : JepSnapshotRepository {

    init { createTable() }

    private fun createTable() {
        connection.createStatement().use { stmt ->
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS jep_snapshots (
                    jep_number     INTEGER PRIMARY KEY,
                    title          VARCHAR NOT NULL,
                    status         VARCHAR NOT NULL,
                    target_release VARCHAR,
                    updated_date   VARCHAR,
                    summary        VARCHAR,
                    last_seen_at   VARCHAR NOT NULL
                )
            """.trimIndent())
        }
    }

    override fun findAll(): List<JepSnapshot> {
        val results = mutableListOf<JepSnapshot>()
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT * FROM jep_snapshots").use { rs ->
                while (rs.next()) {
                    results.add(JepSnapshot(
                        jepNumber     = rs.getInt("jep_number"),
                        title         = rs.getString("title"),
                        status        = rs.getString("status"),
                        targetRelease = rs.getString("target_release"),
                        updatedDate   = rs.getString("updated_date"),
                        summary       = rs.getString("summary"),
                        lastSeenAt    = rs.getString("last_seen_at"),
                    ))
                }
            }
        }
        return results
    }

    override fun upsert(jep: JepSnapshot) {
        connection.prepareStatement("""
            INSERT OR REPLACE INTO jep_snapshots
            (jep_number, title, status, target_release, updated_date, summary, last_seen_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()).use { stmt ->
            stmt.setInt(1, jep.jepNumber)
            stmt.setString(2, jep.title)
            stmt.setString(3, jep.status)
            stmt.setString(4, jep.targetRelease)
            stmt.setString(5, jep.updatedDate)
            stmt.setString(6, jep.summary)
            stmt.setString(7, jep.lastSeenAt)
            stmt.executeUpdate()
        }
    }

    override fun count(): Int {
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT COUNT(*) FROM jep_snapshots").use { rs ->
                rs.next()
                return rs.getInt(1)
            }
        }
    }
}
