package jvm.daily.source

import jvm.daily.config.JepConfig
import dev.vived.engine.storage.DuckDbConnectionFactory
import jvm.daily.storage.DuckDbJepSnapshotRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.Connection
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end simulation: seeds the DB with stale JEP snapshots, then feeds JepSource
 * with "updated" list-page HTML, verifying change detection + article emission + snapshot update.
 */
class JepSourceSimulationTest {

    private lateinit var connection: Connection
    private lateinit var repo: DuckDbJepSnapshotRepository
    private val fixedNow = Instant.parse("2026-03-18T07:00:00Z")
    private val clock = object : Clock { override fun now() = fixedNow }

    @BeforeEach fun setUp() {
        connection = DuckDbConnectionFactory.inMemory()
        repo = DuckDbJepSnapshotRepository(connection)
    }

    @AfterEach fun tearDown() { connection.close() }

    @Test
    fun `simulation - status change and new release detected, snapshots updated`() = runTest {
        // ── Seed: stale snapshots (what we "last saw") ────────────────────────
        repo.upsert(snapshot(491, "Null-Restricted Value Class Types", "Candidate", null))
        repo.upsert(snapshot(495, "Simple Source Files and Instance Main Methods", "Candidate", "JDK 25"))
        repo.upsert(snapshot(502, "Stable Values", "Candidate", null))
        assertEquals(3, repo.count())

        // ── Simulate: openjdk.org now shows updated statuses ──────────────────
        val updatedListHtml = """
            <html><body>
            <table>
              <tr>
                <td><span title="Type: Feature">F</span></td>
                <td><span title="Status: Targeted">Tar</span></td>
                <td><span title="Release: JDK 26">JDK 26</span></td>
                <td class="jep">491</td>
                <td><a href="491">Null-Restricted Value Class Types</a></td>
              </tr>
              <tr>
                <td><span title="Type: Feature">F</span></td>
                <td><span title="Status: Candidate">Can</span></td>
                <td><span title="Release: JDK 25">JDK 25</span></td>
                <td class="jep">495</td>
                <td><a href="495">Simple Source Files and Instance Main Methods</a></td>
              </tr>
              <tr>
                <td><span title="Type: Feature">F</span></td>
                <td><span title="Status: Targeted">Tar</span></td>
                <td><span title="Release: JDK 26">JDK 26</span></td>
                <td class="jep">502</td>
                <td><a href="502">Stable Values</a></td>
              </tr>
            </table>
            </body></html>
        """.trimIndent()

        val source = JepSource(
            repository = repo,
            config = JepConfig(enabled = true, activeStatuses = listOf("Candidate", "Targeted")),
            clock = clock,
            fetcher = { url ->
                when {
                    url.endsWith("/jeps/") || url == JepSource.LIST_URL -> updatedListHtml
                    else -> "<html><body><h2>Summary</h2><p>No detailed content.</p></body></html>"
                }
            },
        )

        // ── Execute ────────────────────────────────────────────────────────────
        val outcomes = source.fetchOutcomes()
        val articles = outcomes.flatMap { it.articles }

        // ── Verify: 2 changes (491: Candidate→Targeted+release, 502: Candidate→Targeted+release) ──
        println("\n=== Emitted articles ===")
        articles.forEach { println("  • ${it.title}\n    ${it.content.lines().take(3).joinToString(" | ")}") }

        assertEquals(2, articles.size, "Expected 2 changed JEPs (491 and 502)")

        val jep491 = articles.first { it.url == "https://openjdk.org/jeps/491" }
        assertTrue(jep491.title.contains("491"))
        assertTrue(jep491.content.startsWith("[JEP TRACKING]"), "Article content must start with [JEP TRACKING]")
        assertTrue(jep491.content.contains("Candidate"), "Must mention old status")
        assertTrue(jep491.content.contains("Targeted"), "Must mention new status")
        assertTrue(jep491.content.contains("topics: jep"), "Must include jep topic hint")

        val jep495 = articles.find { it.url == "https://openjdk.org/jeps/495" }
        assertEquals(null, jep495, "JEP 495 unchanged — should emit no article")

        // ── Verify snapshots updated ───────────────────────────────────────────
        val updated = repo.findAll().associateBy { it.jepNumber }
        assertEquals("Targeted", updated[491]?.status, "Snapshot for 491 should be updated to Targeted")
        assertEquals("JDK 26", updated[491]?.targetRelease, "Snapshot for 491 should have JDK 26 release")
        assertEquals("Targeted", updated[502]?.status, "Snapshot for 502 should be updated to Targeted")
        assertEquals("Candidate", updated[495]?.status, "Snapshot for 495 (unchanged) should stay Candidate")

        println("\n=== Updated snapshots ===")
        updated.values.sortedBy { it.jepNumber }.forEach {
            println("  JEP ${it.jepNumber}: ${it.status} / release=${it.targetRelease}")
        }
    }

    private fun snapshot(number: Int, title: String, status: String, release: String?) =
        jvm.daily.model.JepSnapshot(number, title, status, release, null, null, "2026-03-17T00:00:00Z")
}
