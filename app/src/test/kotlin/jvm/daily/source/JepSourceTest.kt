package jvm.daily.source

import jvm.daily.config.JepConfig
import jvm.daily.model.JepSnapshot
import jvm.daily.storage.JepSnapshotRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JepSourceTest {

    private val fixedNow = Instant.parse("2026-03-18T07:00:00Z")
    private val clock = object : Clock { override fun now() = fixedNow }

    @Test
    fun `new JEP emits article`() = runTest {
        val repo = stubRepo(emptyList())
        val html = listPageWith(491, "Null-Restricted Value Class Types", "Targeted", "JDK 26")
        val source = JepSource(repo, JepConfig(enabled = true), clock, fetcher = stubFetcher(html))

        val articles = source.fetch()

        assertEquals(1, articles.size)
        assertTrue(articles.first().title.contains("491"))
        assertTrue(articles.first().content.startsWith("[JEP TRACKING]"))
        assertTrue(articles.first().content.contains("topics: jep"))
    }

    @Test
    fun `no change emits no article`() = runTest {
        val existing = snapshot(491, "Title", "Targeted", "JDK 26", null)
        val repo = stubRepo(listOf(existing))
        val html = listPageWith(491, "Title", "Targeted", "JDK 26")
        val source = JepSource(repo, JepConfig(enabled = true), clock, fetcher = stubFetcher(html))

        val articles = source.fetch()

        assertTrue(articles.isEmpty())
    }

    @Test
    fun `status change emits article`() = runTest {
        val existing = snapshot(491, "Title", "Candidate", null, null)
        val repo = stubRepo(listOf(existing))
        val html = listPageWith(491, "Title", "Targeted", "JDK 26")
        val source = JepSource(repo, JepConfig(enabled = true), clock, fetcher = stubFetcher(html))

        val articles = source.fetch()

        assertEquals(1, articles.size)
        assertTrue(articles.first().content.contains("Candidate"))
        assertTrue(articles.first().content.contains("Targeted"))
    }

    @Test
    fun `title change emits article`() = runTest {
        val existing = snapshot(491, "Old Title", "Targeted", "JDK 26", null)
        val repo = stubRepo(listOf(existing))
        val html = listPageWith(491, "New Title", "Targeted", "JDK 26")
        val source = JepSource(repo, JepConfig(enabled = true), clock, fetcher = stubFetcher(html))

        val articles = source.fetch()

        assertEquals(1, articles.size)
    }

    @Test
    fun `initialSeed with empty table emits no articles but populates snapshot`() = runTest {
        val upserted = mutableListOf<JepSnapshot>()
        val repo = object : JepSnapshotRepository {
            override fun findAll() = emptyList<JepSnapshot>()
            override fun upsert(jep: JepSnapshot) { upserted.add(jep) }
            override fun count() = 0
        }
        val html = listPageWith(491, "Title", "Targeted", "JDK 26")
        val source = JepSource(repo, JepConfig(enabled = true, initialSeed = true), clock, fetcher = stubFetcher(html))

        val articles = source.fetch()

        assertTrue(articles.isEmpty(), "initialSeed should emit no articles")
        assertEquals(1, upserted.size, "snapshot should be populated")
    }

    @Test
    fun `initialSeed with non-empty table runs normal change detection`() = runTest {
        val existing = snapshot(491, "Old Title", "Candidate", null, null)
        val repo = stubRepo(listOf(existing))
        val html = listPageWith(491, "New Title", "Targeted", "JDK 26")
        val source = JepSource(repo, JepConfig(enabled = true, initialSeed = true), clock, fetcher = stubFetcher(html))

        val articles = source.fetch()

        assertEquals(1, articles.size, "initialSeed=true with populated table should detect changes normally")
    }

    @Test
    fun `list page failure returns FAILED outcome`() = runTest {
        val repo = stubRepo(emptyList())
        val source = JepSource(repo, JepConfig(enabled = true), clock,
            fetcher = { _ -> throw RuntimeException("connection refused") })

        val outcomes = source.fetchOutcomes()

        assertEquals(1, outcomes.size)
        assertEquals(jvm.daily.model.FeedIngestStatus.FAILED, outcomes.first().feed.status)
        assertTrue(outcomes.first().articles.isEmpty())
    }

    @Test
    fun `article url points to individual JEP page`() = runTest {
        val repo = stubRepo(emptyList())
        val html = listPageWith(491, "Title", "Targeted", "JDK 26")
        val source = JepSource(repo, JepConfig(enabled = true), clock, fetcher = stubFetcher(html))

        val articles = source.fetch()

        assertEquals("https://openjdk.org/jeps/491", articles.first().url)
    }

    @Test
    fun `sourceType is jep`() = runTest {
        assertEquals("jep", JepSource(stubRepo(emptyList()), JepConfig(), clock).sourceType)
    }

    // Helpers

    private fun stubRepo(snapshots: List<JepSnapshot>) = object : JepSnapshotRepository {
        private val stored = snapshots.toMutableList()
        override fun findAll() = stored.toList()
        override fun upsert(jep: JepSnapshot) { stored.removeIf { it.jepNumber == jep.jepNumber }; stored.add(jep) }
        override fun count() = stored.size
    }

    private fun snapshot(number: Int, title: String, status: String, release: String?, updatedDate: String?) =
        JepSnapshot(number, title, status, release, updatedDate, null, fixedNow.toString())

    private fun listPageWith(number: Int, title: String, status: String, release: String?): String {
        val releaseSpan = if (release != null) """<span title="Release: $release">$release</span>""" else ""
        return """
            <html><body><table>
            <tr><td><span title="Type: Feature">F</span></td>
                <td><span title="Status: $status">$status</span></td>
                <td>$releaseSpan</td>
                <td class="jep">$number</td>
                <td><a href="$number">$title</a></td></tr>
            </table></body></html>
        """.trimIndent()
    }

    private fun stubFetcher(html: String): (String) -> String = { html }
}
