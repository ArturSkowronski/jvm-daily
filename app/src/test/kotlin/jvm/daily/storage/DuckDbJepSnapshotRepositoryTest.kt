package jvm.daily.storage

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.Connection
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DuckDbJepSnapshotRepositoryTest {

    private lateinit var connection: Connection
    private lateinit var repo: DuckDbJepSnapshotRepository

    @BeforeEach fun setUp() {
        connection = DuckDbConnectionFactory.inMemory()
        repo = DuckDbJepSnapshotRepository(connection)
    }

    @AfterEach fun tearDown() { connection.close() }

    @Test
    fun `findAll returns empty list when table is empty`() {
        assertTrue(repo.findAll().isEmpty())
    }

    @Test
    fun `upsert and findAll roundtrip`() {
        val snap = snapshot(491, "Null-Restricted Value Class Types", "Targeted", "JDK 26", "2026/03/01", "Summary text.")
        repo.upsert(snap)
        val result = repo.findAll()
        assertEquals(1, result.size)
        assertEquals(snap, result.first())
    }

    @Test
    fun `upsert replaces existing row for same jep_number`() {
        repo.upsert(snapshot(491, "Old Title", "Candidate", null, null, null))
        repo.upsert(snapshot(491, "New Title", "Targeted", "JDK 26", "2026/03/15", "Summary."))
        val result = repo.findAll()
        assertEquals(1, result.size)
        assertEquals("New Title", result.first().title)
        assertEquals("Targeted", result.first().status)
    }

    @Test
    fun `count returns number of snapshots`() {
        assertEquals(0, repo.count())
        repo.upsert(snapshot(491, "A", "Targeted", null, null, null))
        repo.upsert(snapshot(492, "B", "Candidate", null, null, null))
        assertEquals(2, repo.count())
    }

    private fun snapshot(
        number: Int, title: String, status: String,
        release: String?, updatedDate: String?, summary: String?,
    ) = jvm.daily.model.JepSnapshot(
        jepNumber = number, title = title, status = status,
        targetRelease = release, updatedDate = updatedDate,
        summary = summary, lastSeenAt = "2026-03-18T07:00:00Z",
    )
}
