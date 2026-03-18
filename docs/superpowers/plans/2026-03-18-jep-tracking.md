# JEP Tracking Source Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `JepSource` that scrapes openjdk.org/jeps/, diffs against DuckDB snapshots, and emits articles for JEP changes (new JEPs, status/title/content/release changes) into the existing pipeline.

**Architecture:** New `JepSource` implements `Source` interface with a `JepSnapshotRepository` for state. Articles tagged `[JEP TRACKING]` get `jep` topic injected in `EnrichmentWorkflow` as code guarantee. Registered in `SourceRegistry` via `App.kt` like all other sources.

**Tech Stack:** Kotlin, DuckDB plain JDBC, `HttpURLConnection` stdlib, kotlinx.datetime, JUnit 5

**Spec:** `docs/superpowers/specs/2026-03-18-jep-tracking-design.md`

---

## File map

| File | Action | Responsibility |
|---|---|---|
| `app/src/main/kotlin/jvm/daily/storage/JepSnapshotRepository.kt` | Create | Interface: `findAll()`, `upsert()` |
| `app/src/main/kotlin/jvm/daily/storage/DuckDbJepSnapshotRepository.kt` | Create | DuckDB impl, `jep_snapshots` table |
| `app/src/main/kotlin/jvm/daily/model/JepSnapshot.kt` | Create | Data class for snapshot row |
| `app/src/main/kotlin/jvm/daily/source/JepSource.kt` | Create | Scrape + diff + emit articles |
| `app/src/main/kotlin/jvm/daily/config/SourcesConfig.kt` | Modify | Add `JepConfig` + `jep: JepConfig?` |
| `app/src/main/kotlin/jvm/daily/workflow/EnrichmentWorkflow.kt` | Modify | Inject `jep` topic for `[JEP TRACKING]` articles |
| `app/src/main/kotlin/jvm/daily/App.kt` | Modify | Wire `JepSource` into `SourceRegistry` |
| `config/sources.yml` | Modify | Add `jep:` block |
| `app/src/test/kotlin/jvm/daily/storage/DuckDbJepSnapshotRepositoryTest.kt` | Create | DB tests |
| `app/src/test/kotlin/jvm/daily/source/JepSourceTest.kt` | Create | Unit tests with stubbed HTTP + repo |

---

## Chunk 1: Storage — JepSnapshot model + repository

### Task 1: JepSnapshot data class + repository interface

**Files:**
- Create: `app/src/main/kotlin/jvm/daily/model/JepSnapshot.kt`
- Create: `app/src/main/kotlin/jvm/daily/storage/JepSnapshotRepository.kt`

- [ ] **Create `JepSnapshot.kt`**

```kotlin
package jvm.daily.model

data class JepSnapshot(
    val jepNumber: Int,
    val title: String,
    val status: String,
    val targetRelease: String?,   // e.g. "JDK 26", null if unassigned
    val updatedDate: String?,     // "YYYY/MM/DD" from individual JEP page
    val summary: String?,         // first paragraph of JEP description
    val lastSeenAt: String,       // ISO-8601 timestamp
)
```

- [ ] **Create `JepSnapshotRepository.kt`**

```kotlin
package jvm.daily.storage

import jvm.daily.model.JepSnapshot

interface JepSnapshotRepository {
    fun findAll(): List<JepSnapshot>
    fun upsert(jep: JepSnapshot)
    fun count(): Int
}
```

- [ ] **Run build to confirm it compiles**

```bash
./gradlew :app:compileKotlin
```
Expected: BUILD SUCCESSFUL

- [ ] **Commit**

```bash
git add app/src/main/kotlin/jvm/daily/model/JepSnapshot.kt \
        app/src/main/kotlin/jvm/daily/storage/JepSnapshotRepository.kt
git commit -m "feat: add JepSnapshot model and repository interface"
```

---

### Task 2: DuckDbJepSnapshotRepository

**Files:**
- Create: `app/src/main/kotlin/jvm/daily/storage/DuckDbJepSnapshotRepository.kt`
- Create: `app/src/test/kotlin/jvm/daily/storage/DuckDbJepSnapshotRepositoryTest.kt`

- [ ] **Write failing tests first**

```kotlin
package jvm.daily.storage

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DuckDbJepSnapshotRepositoryTest {

    @Test
    fun `findAll returns empty list when table is empty`(@TempDir tempDir: Path) {
        val repo = repo(tempDir)
        assertTrue(repo.findAll().isEmpty())
    }

    @Test
    fun `upsert and findAll roundtrip`(@TempDir tempDir: Path) {
        val repo = repo(tempDir)
        val snap = snapshot(491, "Null-Restricted Value Class Types", "Targeted", "JDK 26", "2026/03/01", "Summary text.")
        repo.upsert(snap)
        val result = repo.findAll()
        assertEquals(1, result.size)
        assertEquals(snap, result.first())
    }

    @Test
    fun `upsert replaces existing row for same jep_number`(@TempDir tempDir: Path) {
        val repo = repo(tempDir)
        repo.upsert(snapshot(491, "Old Title", "Candidate", null, null, null))
        repo.upsert(snapshot(491, "New Title", "Targeted", "JDK 26", "2026/03/15", "Summary."))
        val result = repo.findAll()
        assertEquals(1, result.size)
        assertEquals("New Title", result.first().title)
        assertEquals("Targeted", result.first().status)
    }

    @Test
    fun `count returns number of snapshots`(@TempDir tempDir: Path) {
        val repo = repo(tempDir)
        assertEquals(0, repo.count())
        repo.upsert(snapshot(491, "A", "Targeted", null, null, null))
        repo.upsert(snapshot(492, "B", "Candidate", null, null, null))
        assertEquals(2, repo.count())
    }

    private fun repo(tempDir: Path) =
        DuckDbJepSnapshotRepository(DuckDbConnectionFactory.create("${tempDir}/test.duckdb"))

    private fun snapshot(
        number: Int, title: String, status: String,
        release: String?, updatedDate: String?, summary: String?,
    ) = jvm.daily.model.JepSnapshot(
        jepNumber = number, title = title, status = status,
        targetRelease = release, updatedDate = updatedDate,
        summary = summary, lastSeenAt = "2026-03-18T07:00:00Z",
    )
}
```

- [ ] **Run tests to confirm they fail**

```bash
./gradlew :app:test --tests "jvm.daily.storage.DuckDbJepSnapshotRepositoryTest" 2>&1 | tail -5
```
Expected: compilation error (class not found)

- [ ] **Create `DuckDbJepSnapshotRepository.kt`**

```kotlin
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
                        jepNumber    = rs.getInt("jep_number"),
                        title        = rs.getString("title"),
                        status       = rs.getString("status"),
                        targetRelease = rs.getString("target_release"),
                        updatedDate  = rs.getString("updated_date"),
                        summary      = rs.getString("summary"),
                        lastSeenAt   = rs.getString("last_seen_at"),
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
```

- [ ] **Run tests — expect PASS**

```bash
./gradlew :app:test --tests "jvm.daily.storage.DuckDbJepSnapshotRepositoryTest" 2>&1 | tail -5
```
Expected: BUILD SUCCESSFUL

- [ ] **Commit**

```bash
git add app/src/main/kotlin/jvm/daily/storage/DuckDbJepSnapshotRepository.kt \
        app/src/test/kotlin/jvm/daily/storage/DuckDbJepSnapshotRepositoryTest.kt
git commit -m "feat: add DuckDbJepSnapshotRepository with jep_snapshots table"
```

---

## Chunk 2: JepSource — config + scraping + diff

### Task 3: JepConfig in SourcesConfig

**Files:**
- Modify: `app/src/main/kotlin/jvm/daily/config/SourcesConfig.kt`
- Modify: `app/src/test/kotlin/jvm/daily/config/SourcesConfigTest.kt`

- [ ] **Add `JepConfig` and `jep` field to `SourcesConfig.kt`**

Add at end of file:
```kotlin
@Serializable
data class JepConfig(
    val enabled: Boolean = false,
    val initialSeed: Boolean = false,
    val activeStatuses: List<String> = listOf(
        "Draft", "Candidate", "Proposed to Target", "Targeted", "Integrated"
    ),
)
```

Add field to `SourcesConfig`:
```kotlin
val jep: JepConfig? = null,
```

- [ ] **Add test in `SourcesConfigTest.kt`** — check `jep` parses from YAML:

```kotlin
@Test
fun `jep config parses from yaml`() {
    val yaml = """
        jep:
          enabled: true
          initialSeed: false
          activeStatuses: [Targeted, Integrated]
    """.trimIndent()
    val config = Yaml.default.decodeFromString(SourcesConfig.serializer(), yaml)
    assertEquals(true, config.jep?.enabled)
    assertEquals(listOf("Targeted", "Integrated"), config.jep?.activeStatuses)
}
```

- [ ] **Run build**

```bash
./gradlew :app:test --tests "jvm.daily.config.SourcesConfigTest" 2>&1 | tail -5
```
Expected: BUILD SUCCESSFUL

- [ ] **Commit**

```bash
git add app/src/main/kotlin/jvm/daily/config/SourcesConfig.kt \
        app/src/test/kotlin/jvm/daily/config/SourcesConfigTest.kt
git commit -m "feat: add JepConfig to SourcesConfig"
```

---

### Task 4: JepSource implementation

**Files:**
- Create: `app/src/main/kotlin/jvm/daily/source/JepSource.kt`
- Create: `app/src/test/kotlin/jvm/daily/source/JepSourceTest.kt`

- [ ] **Write failing tests**

```kotlin
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
        val releaseCell = if (release != null) "<td>$release</td>" else "<td></td>"
        return """
            <html><body><table>
            <tr><td><a href="/jeps/$number">JEP $number</a></td>
                <td><a href="/jeps/$number">$title</a></td>
                <td>$status</td>
                $releaseCell</tr>
            </table></body></html>
        """.trimIndent()
    }

    private fun stubFetcher(html: String): (String) -> String = { html }
}
```

- [ ] **Run tests — expect compilation failure**

```bash
./gradlew :app:test --tests "jvm.daily.source.JepSourceTest" 2>&1 | tail -5
```
Expected: compilation error

- [ ] **Create `JepSource.kt`**

```kotlin
package jvm.daily.source

import jvm.daily.config.JepConfig
import jvm.daily.model.Article
import jvm.daily.model.CanonicalArticleId
import jvm.daily.model.FeedIngestResult
import jvm.daily.model.FeedIngestStatus
import jvm.daily.model.JepSnapshot
import jvm.daily.model.SourceFetchOutcome
import jvm.daily.storage.JepSnapshotRepository
import kotlinx.datetime.Clock
import java.net.HttpURLConnection
import java.net.URI

class JepSource(
    private val repository: JepSnapshotRepository,
    private val config: JepConfig = JepConfig(),
    private val clock: Clock = Clock.System,
    private val fetcher: (String) -> String = ::httpGet,
) : Source {

    override val sourceType: String = "jep"

    override suspend fun fetch(): List<Article> = fetchOutcomes().flatMap { it.articles }

    override suspend fun fetchOutcomes(): List<SourceFetchOutcome> {
        val listHtml = try { fetcher(LIST_URL) } catch (e: Exception) {
            println("[jep] Failed to fetch list page: ${e.message}")
            return listOf(SourceFetchOutcome(
                feed = FeedIngestResult(
                    sourceType = sourceType, sourceId = SOURCE_ID,
                    status = FeedIngestStatus.FAILED, fetchedCount = 0,
                    errors = listOf(e.message ?: "Failed to fetch $LIST_URL"),
                ),
                articles = emptyList(),
            ))
        }

        val current = parseListPage(listHtml)
        val snapshots = repository.findAll().associateBy { it.jepNumber }

        // initialSeed with empty table: populate without emitting
        if (config.initialSeed && snapshots.isEmpty()) {
            println("[jep] initialSeed=true — populating snapshot table, emitting no articles")
            current.forEach { repository.upsert(toSnapshot(it, snapshots[it.jepNumber])) }
            return listOf(SourceFetchOutcome(
                feed = FeedIngestResult(sourceType, SOURCE_ID, FeedIngestStatus.SUCCESS, 0),
                articles = emptyList(),
            ))
        }

        if (config.initialSeed && snapshots.isNotEmpty()) {
            println("[jep] initialSeed=true ignored — snapshot table already has ${snapshots.size} rows, running change detection normally")
        }

        // Fetch individual pages for active JEPs to get updatedDate + summary
        val individualData = mutableMapOf<Int, Pair<String?, String?>>() // number -> (updatedDate, summary)
        current.filter { it.status in config.activeStatuses }.forEach { jep ->
            try {
                Thread.sleep(200)
                val html = fetcher("$JEP_BASE_URL/${jep.jepNumber}")
                val updatedDate = parseUpdatedDate(html)
                val summary = parseSummary(html)
                individualData[jep.jepNumber] = updatedDate to summary
            } catch (e: Exception) {
                println("[jep] Warning: failed to fetch JEP ${jep.jepNumber}: ${e.message}")
            }
        }

        val articles = mutableListOf<Article>()
        val now = clock.now().toString()

        for (fetched in current) {
            val old = snapshots[fetched.jepNumber]
            val (updatedDate, summary) = individualData[fetched.jepNumber] ?: (null to null)

            val changes = detectChanges(old, fetched, updatedDate)
            if (changes.isEmpty()) continue

            val changeType = if (changes.size > 1) "multi" else changes.keys.first()
            val effectiveUpdatedDate = updatedDate
                ?: old?.updatedDate
                ?: "unknown"

            val article = Article(
                id = CanonicalArticleId.from(
                    namespace = "jep",
                    sourceId = SOURCE_ID,
                    title = fetched.title,
                    url = null,
                    sourceNativeId = "jep-${fetched.jepNumber}-$effectiveUpdatedDate-$changeType",
                ),
                title = buildTitle(fetched.jepNumber, fetched.title, changes),
                content = buildContent(changes, summary),
                sourceType = sourceType,
                sourceId = SOURCE_ID,
                url = "$JEP_BASE_URL/${fetched.jepNumber}",
                ingestedAt = clock.now(),
            )
            articles.add(article)

            // Update snapshot after emitting (update-after-emit)
            repository.upsert(JepSnapshot(
                jepNumber = fetched.jepNumber,
                title = fetched.title,
                status = fetched.status,
                targetRelease = fetched.targetRelease,
                updatedDate = updatedDate ?: old?.updatedDate,
                summary = summary ?: old?.summary,
                lastSeenAt = now,
            ))
        }

        println("[jep] Detected ${articles.size} JEP change(s)")
        return listOf(SourceFetchOutcome(
            feed = FeedIngestResult(sourceType, SOURCE_ID, FeedIngestStatus.SUCCESS, articles.size),
            articles = articles,
        ))
    }

    private data class FetchedJep(
        val jepNumber: Int,
        val title: String,
        val status: String,
        val targetRelease: String?,
    )

    private fun parseListPage(html: String): List<FetchedJep> {
        val results = mutableListOf<FetchedJep>()
        // Match table rows: <tr>...<a href="/jeps/NNN">...</a>...
        val rowRegex = Regex("""<tr[^>]*>(.*?)</tr>""", RegexOption.DOT_MATCHES_ALL)
        val hrefRegex = Regex("""href="/jeps/(\d+)"""")
        val tdRegex = Regex("""<td[^>]*>(.*?)</td>""", RegexOption.DOT_MATCHES_ALL)
        val tagRegex = Regex("""<[^>]+>""")

        for (row in rowRegex.findAll(html)) {
            val rowContent = row.groupValues[1]
            val jepNumber = hrefRegex.find(rowContent)?.groupValues?.get(1)?.toIntOrNull() ?: continue
            val cells = tdRegex.findAll(rowContent).map { it.groupValues[1].replace(tagRegex, "").trim() }.toList()
            if (cells.size < 3) continue
            val title = cells.getOrElse(1) { cells[0] }.ifBlank { continue }
            val status = cells.getOrElse(2) { "" }.ifBlank { continue }
            val release = cells.getOrNull(3)?.takeIf { it.isNotBlank() }
            results.add(FetchedJep(jepNumber, title, status, release))
        }
        return results
    }

    private fun parseUpdatedDate(html: String): String? {
        val regex = Regex("""Updated:\s*(\d{4}/\d{2}/\d{2})""")
        return regex.find(html)?.groupValues?.get(1)
    }

    private fun parseSummary(html: String): String? {
        val regex = Regex("""<h2[^>]*>Summary</h2>\s*<p[^>]*>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
        val tagRegex = Regex("""<[^>]+>""")
        return regex.find(html)?.groupValues?.get(1)?.replace(tagRegex, "")?.trim()?.take(500)
    }

    private fun detectChanges(old: JepSnapshot?, new: FetchedJep, updatedDate: String?): Map<String, String> {
        val changes = mutableMapOf<String, String>()
        if (old == null) {
            changes["new"] = "status: ${new.status}"
            return changes
        }
        if (old.status != new.status) changes["status"] = "${old.status} → ${new.status}"
        if (old.title != new.title) changes["title"] = "${old.title} → ${new.title}"
        if (old.targetRelease != new.targetRelease) changes["release"] = "${old.targetRelease} → ${new.targetRelease}"
        if (updatedDate != null && updatedDate != old.updatedDate) changes["content"] = "updated $updatedDate"
        return changes
    }

    private fun buildTitle(number: Int, title: String, changes: Map<String, String>): String {
        val desc = when {
            "new" in changes -> "new JEP"
            changes.size > 1 -> changes.values.joinToString(", ")
            "status" in changes -> "status: ${changes["status"]}"
            "title" in changes -> "title updated"
            "release" in changes -> "release: ${changes["release"]}"
            "content" in changes -> "content ${changes["content"]}"
            else -> changes.values.first()
        }
        return "JEP $number: $title — $desc"
    }

    private fun buildContent(changes: Map<String, String>, summary: String?): String = buildString {
        appendLine("[JEP TRACKING]")
        appendLine("topics: jep")
        for ((key, value) in changes) appendLine("$key: $value")
        if (summary != null) { appendLine(); appendLine("summary: $summary") }
    }

    private fun toSnapshot(jep: FetchedJep, existing: JepSnapshot?) = JepSnapshot(
        jepNumber = jep.jepNumber, title = jep.title, status = jep.status,
        targetRelease = jep.targetRelease, updatedDate = existing?.updatedDate,
        summary = existing?.summary, lastSeenAt = clock.now().toString(),
    )

    companion object {
        const val LIST_URL = "https://openjdk.org/jeps/"
        const val JEP_BASE_URL = "https://openjdk.org/jeps"
        const val SOURCE_ID = "openjdk.org/jeps"

        fun httpGet(url: String): String {
            val conn = URI(url).toURL().openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "JVM-Daily/1.0")
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            return conn.inputStream.bufferedReader().readText()
        }
    }
}
```

- [ ] **Run tests — expect PASS**

```bash
./gradlew :app:test --tests "jvm.daily.source.JepSourceTest" 2>&1 | tail -10
```
Expected: BUILD SUCCESSFUL, 8 tests passed

- [ ] **Commit**

```bash
git add app/src/main/kotlin/jvm/daily/source/JepSource.kt \
        app/src/test/kotlin/jvm/daily/source/JepSourceTest.kt
git commit -m "feat: add JepSource with snapshot diff and article generation"
```

---

## Chunk 3: Integration — EnrichmentWorkflow + App wiring + config

### Task 5: Inject `jep` topic in EnrichmentWorkflow

**Files:**
- Modify: `app/src/main/kotlin/jvm/daily/workflow/EnrichmentWorkflow.kt`
- Modify: `app/src/test/kotlin/jvm/daily/workflow/EnrichmentWorkflowTest.kt`

- [ ] **Add test for jep topic injection**

In `EnrichmentWorkflowTest.kt`, add:

```kotlin
@Test
fun `jep topic is injected when LLM omits it for JEP TRACKING articles`() = runTest {
    val llm = object : LLMClient {
        override suspend fun chat(prompt: String) = """
            RELEVANCE: YES
            SUMMARY: JEP 491 moved to Targeted.
            ENTITIES: JEP 491
            TOPICS: java, openjdk
            ENGAGEMENT: 70
        """.trimIndent()
    }
    val article = rawArticle(
        content = "[JEP TRACKING]\ntopics: jep\nstatus: Candidate → Targeted",
        sourceType = "jep",
    )
    val workflow = EnrichmentWorkflow(stubRepo(listOf(article)), stubProcessedRepo(), llm)
    workflow.execute()

    val saved = captureProcessed()
    assertTrue(saved.first().topics.contains("jep"), "jep topic must be injected")
}
```

- [ ] **Modify `enrichArticle` in `EnrichmentWorkflow.kt`**

After `topics = result.topics,` on line ~143, change to:

```kotlin
topics = if (article.content.startsWith("[JEP TRACKING]") && "jep" !in result.topics)
    result.topics + "jep" else result.topics,
```

- [ ] **Run build**

```bash
./gradlew :app:build 2>&1 | tail -5
```
Expected: BUILD SUCCESSFUL

- [ ] **Commit**

```bash
git add app/src/main/kotlin/jvm/daily/workflow/EnrichmentWorkflow.kt \
        app/src/test/kotlin/jvm/daily/workflow/EnrichmentWorkflowTest.kt
git commit -m "feat: inject jep topic for [JEP TRACKING] articles in EnrichmentWorkflow"
```

---

### Task 6: Wire JepSource into App + update config

**Files:**
- Modify: `app/src/main/kotlin/jvm/daily/App.kt`
- Modify: `config/sources.yml`

- [ ] **Add `DuckDbJepSnapshotRepository` init and `JepSource` registration in `App.kt`**

In the `runIngress` function (find the block where `SourceRegistry` is built), add:

```kotlin
import jvm.daily.source.JepSource
import jvm.daily.storage.DuckDbJepSnapshotRepository

// Inside the SourceRegistry.apply { ... } block, after bluesky:
config.jep?.takeIf { it.enabled }?.let {
    val jepRepo = DuckDbJepSnapshotRepository(connection)
    register(JepSource(jepRepo, it))
}
```

- [ ] **Add `jep:` block to `config/sources.yml`**

```yaml
jep:
  enabled: true
  initialSeed: true    # set to false after first run
  activeStatuses:
    - Draft
    - Candidate
    - "Proposed to Target"
    - Targeted
    - Integrated
```

- [ ] **Run full build**

```bash
./gradlew build 2>&1 | tail -5
```
Expected: BUILD SUCCESSFUL

- [ ] **Commit**

```bash
git add app/src/main/kotlin/jvm/daily/App.kt config/sources.yml
git commit -m "feat: wire JepSource into pipeline, add jep config (initialSeed=true)"
```

---

### Task 7: First-run seed + flip initialSeed

- [ ] **Run ingress locally to seed the snapshot table**

```bash
./gradlew run --args="ingress"
```
Expected: `[jep] initialSeed=true — populating snapshot table, emitting no articles`

- [ ] **Set `initialSeed: false` in `config/sources.yml`**

```yaml
jep:
  enabled: true
  initialSeed: false
```

- [ ] **Commit**

```bash
git add config/sources.yml
git commit -m "config: disable initialSeed after first JEP snapshot populated"
```

---

### Task 8: Final build + push

- [ ] **Run full test suite**

```bash
./gradlew build 2>&1 | tail -5
```
Expected: BUILD SUCCESSFUL

- [ ] **Push branch and update PR**

```bash
git push
gh pr view --json url -q .url
```
