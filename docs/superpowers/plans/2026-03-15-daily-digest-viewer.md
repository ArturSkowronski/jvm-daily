# Daily Digest Viewer Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Latent Space AI News-style morning digest viewer that groups last-24h articles into thematic clusters and serves them via a JSON API.

**Architecture:** Outgress writes `daily-YYYY-MM-DD.json` alongside the existing `.md`. Clustering saves `ArticleCluster` rows to DuckDB via a new `ClusterRepository`. The Python viewer tries the JSON endpoint first and renders clusters; falls back to markdown for old dates.

**Tech Stack:** Kotlin, DuckDB JDBC, kotlinx.serialization, Python 3 stdlib HTTP server, vanilla JS

**Spec:** `docs/superpowers/specs/2026-03-15-daily-digest-viewer-design.md`

---

## Chunk 1: Storage — ClusterRepository + ProcessedArticleRepository extensions

### Files
- Create: `app/src/main/kotlin/jvm/daily/storage/ClusterRepository.kt`
- Create: `app/src/main/kotlin/jvm/daily/storage/DuckDbClusterRepository.kt`
- Modify: `app/src/main/kotlin/jvm/daily/storage/ProcessedArticleRepository.kt`
- Modify: `app/src/main/kotlin/jvm/daily/storage/DuckDbProcessedArticleRepository.kt`
- Create: `app/src/test/kotlin/jvm/daily/storage/DuckDbClusterRepositoryTest.kt`
- Modify: `app/src/test/kotlin/jvm/daily/storage/DuckDbProcessedArticleRepositoryTest.kt`

---

### Task 1: ClusterRepository interface

- [ ] **Create `ClusterRepository.kt`**

```kotlin
package jvm.daily.storage

import jvm.daily.model.ArticleCluster
import kotlinx.datetime.Instant

interface ClusterRepository {
    fun save(cluster: ArticleCluster)
    fun saveAll(clusters: List<ArticleCluster>)
    fun findByDateRange(start: Instant, end: Instant): List<ArticleCluster>
    fun deleteByDateRange(start: Instant, end: Instant)
}
```

- [ ] **Run build to confirm it compiles**

```bash
./gradlew build
```
Expected: BUILD SUCCESSFUL

- [ ] **Commit**

```bash
git checkout -b feat/daily-digest-viewer
git add app/src/main/kotlin/jvm/daily/storage/ClusterRepository.kt
git commit -m "feat: add ClusterRepository interface"
```

---

### Task 2: DuckDbClusterRepository — write tests first

- [ ] **Write failing tests in `DuckDbClusterRepositoryTest.kt`**

```kotlin
package jvm.daily.storage

import jvm.daily.model.ArticleCluster
import kotlinx.datetime.Instant
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.Connection
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DuckDbClusterRepositoryTest {

    private lateinit var connection: Connection
    private lateinit var repository: DuckDbClusterRepository

    @BeforeEach fun setUp() {
        connection = DuckDbConnectionFactory.inMemory()
        repository = DuckDbClusterRepository(connection)
    }

    @AfterEach fun tearDown() { connection.close() }

    @Test
    fun `saveAll then findByDateRange returns clusters in range`() {
        val t = Instant.parse("2026-03-15T07:00:00Z")
        repository.saveAll(listOf(cluster("c1", t), cluster("c2", t)))

        val result = repository.findByDateRange(
            Instant.parse("2026-03-15T00:00:00Z"),
            Instant.parse("2026-03-15T23:59:59Z"),
        )
        assertEquals(2, result.size)
        assertTrue(result.map { it.id }.containsAll(listOf("c1", "c2")))
    }

    @Test
    fun `saveAll idempotency — saving same cluster twice does not duplicate`() {
        val t = Instant.parse("2026-03-15T07:00:00Z")
        val c = cluster("c1", t)
        repository.saveAll(listOf(c))
        repository.saveAll(listOf(c))

        val result = repository.findByDateRange(
            Instant.parse("2026-03-15T00:00:00Z"),
            Instant.parse("2026-03-15T23:59:59Z"),
        )
        assertEquals(1, result.size)
    }

    @Test
    fun `findByDateRange with empty range returns empty list`() {
        val t = Instant.parse("2026-03-15T07:00:00Z")
        repository.save(cluster("c1", t))

        val result = repository.findByDateRange(
            Instant.parse("2026-03-14T00:00:00Z"),
            Instant.parse("2026-03-14T23:59:59Z"),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `deleteByDateRange removes clusters in range leaves others intact`() {
        repository.save(cluster("old", Instant.parse("2026-03-14T07:00:00Z")))
        repository.save(cluster("new", Instant.parse("2026-03-15T07:00:00Z")))

        repository.deleteByDateRange(
            Instant.parse("2026-03-15T00:00:00Z"),
            Instant.parse("2026-03-15T23:59:59Z"),
        )

        val remaining = repository.findByDateRange(
            Instant.parse("2026-03-13T00:00:00Z"),
            Instant.parse("2026-03-16T00:00:00Z"),
        )
        assertEquals(1, remaining.size)
        assertEquals("old", remaining[0].id)
    }

    private fun cluster(id: String, createdAt: Instant) = ArticleCluster(
        id = id,
        title = "Cluster $id",
        summary = "Summary of $id",
        articles = listOf("art-1", "art-2"),
        sources = listOf("rss"),
        totalEngagement = 50.0,
        createdAt = createdAt,
    )
}
```

- [ ] **Run tests to confirm they fail** (class not found)

```bash
./gradlew test --tests "jvm.daily.storage.DuckDbClusterRepositoryTest" 2>&1 | tail -5
```
Expected: compilation error or test failure

---

### Task 3: DuckDbClusterRepository implementation

- [ ] **Create `DuckDbClusterRepository.kt`**

```kotlin
package jvm.daily.storage

import jvm.daily.model.ArticleCluster
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.sql.Connection

class DuckDbClusterRepository(private val connection: Connection) : ClusterRepository {

    init { createTable() }

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
            stmt.execute(
                "CREATE INDEX IF NOT EXISTS idx_cluster_created_at ON article_clusters(created_at)"
            )
        }
    }

    override fun save(cluster: ArticleCluster) {
        connection.prepareStatement(
            """
            INSERT OR REPLACE INTO article_clusters
            (id, title, summary, article_ids, sources, total_engagement, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { stmt ->
            stmt.setString(1, cluster.id)
            stmt.setString(2, cluster.title)
            stmt.setString(3, cluster.summary)
            stmt.setString(4, Json.encodeToString(cluster.articles))
            stmt.setString(5, Json.encodeToString(cluster.sources))
            stmt.setDouble(6, cluster.totalEngagement)
            stmt.setString(7, cluster.createdAt.toString())
            stmt.executeUpdate()
        }
    }

    override fun saveAll(clusters: List<ArticleCluster>) { clusters.forEach { save(it) } }

    override fun findByDateRange(start: Instant, end: Instant): List<ArticleCluster> {
        val results = mutableListOf<ArticleCluster>()
        connection.prepareStatement(
            "SELECT * FROM article_clusters WHERE created_at >= ? AND created_at <= ? ORDER BY total_engagement DESC"
        ).use { stmt ->
            stmt.setString(1, start.toString())
            stmt.setString(2, end.toString())
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    results.add(
                        ArticleCluster(
                            id = rs.getString("id"),
                            title = rs.getString("title"),
                            summary = rs.getString("summary"),
                            articles = Json.decodeFromString(rs.getString("article_ids")),
                            sources = Json.decodeFromString(rs.getString("sources")),
                            totalEngagement = rs.getDouble("total_engagement"),
                            createdAt = Instant.parse(rs.getString("created_at")),
                        )
                    )
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
}
```

- [ ] **Run tests**

```bash
./gradlew test --tests "jvm.daily.storage.DuckDbClusterRepositoryTest"
```
Expected: 4 tests pass

- [ ] **Commit**

```bash
git add app/src/main/kotlin/jvm/daily/storage/DuckDbClusterRepository.kt \
        app/src/test/kotlin/jvm/daily/storage/DuckDbClusterRepositoryTest.kt
git commit -m "feat: add DuckDbClusterRepository with cluster persistence"
```

---

### Task 4: ProcessedArticleRepository — add findByIds and findByIngestedAtRange

- [ ] **Add tests to `DuckDbProcessedArticleRepositoryTest.kt`** (add after existing tests)

```kotlin
@Test
fun `findByIds returns SUCCESS articles only, excludes FAILED`() {
    repository.save(processedArticle(id = "s1"))
    repository.save(processedArticle(
        id = "f1",
        outcomeStatus = EnrichmentOutcomeStatus.FAILED,
        failureReason = "parse error",
    ))

    val result = repository.findByIds(listOf("s1", "f1"))
    assertEquals(1, result.size)
    assertEquals("s1", result[0].id)
}

@Test
fun `findByIds with empty list returns empty without error`() {
    repository.save(processedArticle(id = "a1"))
    val result = repository.findByIds(emptyList())
    assertTrue(result.isEmpty())
}

@Test
fun `findByIngestedAtRange boundary — article at now-25h excluded, now-23h included`() {
    val now = Instant.parse("2026-03-15T07:00:00Z")
    val inWindow  = processedArticle(id = "in",  ingestedAt = Instant.parse("2026-03-14T08:00:00Z")) // 23h ago
    val outWindow = processedArticle(id = "out", ingestedAt = Instant.parse("2026-03-14T06:00:00Z")) // 25h ago
    repository.save(inWindow)
    repository.save(outWindow)

    val windowStart = Instant.parse("2026-03-14T07:00:00Z") // now - 24h
    val result = repository.findByIngestedAtRange(windowStart, now)
    assertEquals(1, result.size)
    assertEquals("in", result[0].id)
}
```

Note: `processedArticle()` is an existing helper in that test file — add an `ingestedAt` parameter override to it, or create new overloaded helpers as needed. The existing helper already sets `ingestedAt = processedAt`.

- [ ] **Run tests to confirm they fail**

```bash
./gradlew test --tests "jvm.daily.storage.DuckDbProcessedArticleRepositoryTest" 2>&1 | tail -10
```
Expected: compilation errors (methods not found)

- [ ] **Add methods to `ProcessedArticleRepository` interface**

In `ProcessedArticleRepository.kt`, add after `findUnprocessedRawArticles`:

```kotlin
// Returns SUCCESS articles matching the given IDs. Returns empty list when ids is empty.
fun findByIds(ids: List<String>): List<ProcessedArticle>

// Filters on ingested_at column (not processed_at).
fun findByIngestedAtRange(start: Instant, end: Instant): List<ProcessedArticle>
```

- [ ] **Add stub implementations to `OutgressWorkflowTest.stubRepo`**

In `OutgressWorkflowTest.kt`, inside the `stubRepo` anonymous object, add:

```kotlin
override fun findByIds(ids: List<String>): List<ProcessedArticle> = emptyList()
override fun findByIngestedAtRange(start: Instant, end: Instant): List<ProcessedArticle> = emptyList()
```

Then scan for any other anonymous-object implementations of `ProcessedArticleRepository` in the test suite and add the same two stubs. Common locations:
- `EnrichmentWorkflowTest.kt`
- `ProcessingPipelineIntegrationTest.kt`
- `PipelineServiceTest.kt`

Add these two stubs to every anonymous object that implements `ProcessedArticleRepository`.

- [ ] **Add implementations to `DuckDbProcessedArticleRepository.kt`**

Add the index in `createTable()` after the existing `idx_processed_at` line:

```kotlin
stmt.execute(
    "CREATE INDEX IF NOT EXISTS idx_ingested_at ON processed_articles(ingested_at)"
)
```

Add the two methods after `findAll()`:

```kotlin
override fun findByIds(ids: List<String>): List<ProcessedArticle> {
    if (ids.isEmpty()) return emptyList()
    val placeholders = ids.joinToString(",") { "?" }
    val results = mutableListOf<ProcessedArticle>()
    connection.prepareStatement(
        "SELECT * FROM processed_articles WHERE id IN ($placeholders) AND outcome_status = 'SUCCESS'"
    ).use { stmt ->
        ids.forEachIndexed { i, id -> stmt.setString(i + 1, id) }
        stmt.executeQuery().use { rs ->
            while (rs.next()) results.add(rs.toProcessedArticle())
        }
    }
    return results
}

override fun findByIngestedAtRange(start: Instant, end: Instant): List<ProcessedArticle> {
    val results = mutableListOf<ProcessedArticle>()
    connection.prepareStatement(
        "SELECT * FROM processed_articles WHERE ingested_at >= ? AND ingested_at <= ? ORDER BY ingested_at DESC"
    ).use { stmt ->
        stmt.setString(1, start.toString())
        stmt.setString(2, end.toString())
        stmt.executeQuery().use { rs ->
            while (rs.next()) results.add(rs.toProcessedArticle())
        }
    }
    return results
}
```

- [ ] **Run all tests**

```bash
./gradlew test
```
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Commit**

```bash
git add app/src/main/kotlin/jvm/daily/storage/ProcessedArticleRepository.kt \
        app/src/main/kotlin/jvm/daily/storage/DuckDbProcessedArticleRepository.kt \
        app/src/test/kotlin/jvm/daily/storage/DuckDbProcessedArticleRepositoryTest.kt \
        app/src/test/kotlin/jvm/daily/workflow/OutgressWorkflowTest.kt
git commit -m "feat: add findByIds and findByIngestedAtRange to ProcessedArticleRepository"
```

---

## Chunk 2: ClusteringWorkflow fix + DigestJson + OutgressWorkflow JSON path

### Files
- Create: `app/src/main/kotlin/jvm/daily/workflow/DigestJson.kt`
- Modify: `app/src/main/kotlin/jvm/daily/workflow/ClusteringWorkflow.kt`
- Modify: `app/src/main/kotlin/jvm/daily/workflow/OutgressWorkflow.kt`
- Modify: `app/src/test/kotlin/jvm/daily/workflow/OutgressWorkflowTest.kt`
- Create: `app/src/test/kotlin/jvm/daily/workflow/ClusteringWorkflowPersistenceTest.kt`

---

### Task 5: DigestJson serialization model

- [ ] **Create `DigestJson.kt`**

```kotlin
package jvm.daily.workflow

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class DigestJson(
    val date: String,
    val generatedAt: String,
    val windowHours: Int = 24,
    val totalArticles: Int,
    val clusters: List<DigestCluster>,
    val unclustered: List<DigestArticle>,
)

@Serializable
data class DigestCluster(
    val id: String,
    val title: String,
    val summary: String,
    val engagementScore: Double,
    val articles: List<DigestArticle>,
)

@Serializable
data class DigestArticle(
    val id: String,
    val title: String,
    val url: String?,
    val summary: String,
    val topics: List<String>,
    val entities: List<String>,
    val engagementScore: Double,
    val publishedAt: Instant,
    val ingestedAt: Instant,
    val sourceType: String,
)
```

- [ ] **Run build**

```bash
./gradlew build
```
Expected: BUILD SUCCESSFUL

- [ ] **Commit**

```bash
git add app/src/main/kotlin/jvm/daily/workflow/DigestJson.kt
git commit -m "feat: add DigestJson serialization model for daily digest"
```

---

### Task 6: ClusteringWorkflow — fix persistence TODO

- [ ] **Write failing test**

Create `app/src/test/kotlin/jvm/daily/workflow/ClusteringWorkflowPersistenceTest.kt`:

```kotlin
package jvm.daily.workflow

import jvm.daily.ai.LLMClient
import jvm.daily.model.ArticleCluster
import jvm.daily.model.ProcessedArticle
import jvm.daily.storage.ClusterRepository
import jvm.daily.storage.ProcessedArticleRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClusteringWorkflowPersistenceTest {

    @Test
    fun `saveAll called with clusters produced by clusterArticles`() = runTest {
        val now = Instant.parse("2026-03-15T07:00:00Z")
        val clock = object : Clock { override fun now() = now }

        val articles = listOf(
            processedArticle("a1", listOf("spring", "releases"), now),
            processedArticle("a2", listOf("spring", "releases"), now),
            processedArticle("a3", listOf("spring", "releases"), now),
        )

        val savedClusters = mutableListOf<ArticleCluster>()
        val clusterRepo = object : ClusterRepository {
            override fun save(cluster: ArticleCluster) { savedClusters.add(cluster) }
            override fun saveAll(clusters: List<ArticleCluster>) { savedClusters.addAll(clusters) }
            override fun findByDateRange(start: Instant, end: Instant) = emptyList<ArticleCluster>()
            override fun deleteByDateRange(start: Instant, end: Instant) {}
        }

        val processedRepo = object : ProcessedArticleRepository {
            override fun save(article: ProcessedArticle) {}
            override fun saveAll(articles: List<ProcessedArticle>) {}
            override fun findAll() = articles
            override fun findByDateRange(startDate: Instant, endDate: Instant) = articles
            override fun findByIngestedAtRange(start: Instant, end: Instant) = articles
            override fun findByIds(ids: List<String>) = emptyList<ProcessedArticle>()
            override fun findFailedSince(since: Instant) = emptyList<ProcessedArticle>()
            override fun findFailedRawArticleIds(since: Instant, limit: Int) = emptyList<String>()
            override fun findFailedByIds(ids: List<String>) = emptyList<ProcessedArticle>()
            override fun findInspectionCandidates(since: Instant, limit: Int, minWarnings: Int) = emptyList<ProcessedArticle>()
            override fun findUnprocessedRawArticles(since: Instant) = emptyList<String>()
            override fun existsById(id: String) = false
            override fun count() = articles.size.toLong()
        }

        val llm = object : LLMClient {
            override suspend fun chat(prompt: String) = "TITLE: Spring News\nSYNTHESIS: Spring stuff happened."
        }

        ClusteringWorkflow(processedRepo, llm, clusterRepo, clock).execute()

        assertTrue(savedClusters.isNotEmpty(), "saveAll must have been called with at least one cluster")
        assertEquals("Spring News", savedClusters.first().title)
    }

    private fun processedArticle(id: String, topics: List<String>, at: Instant) = ProcessedArticle(
        id = id, originalTitle = "Title $id", normalizedTitle = "title $id",
        summary = "Summary", originalContent = "Content", sourceType = "rss", sourceId = "feed",
        publishedAt = at, ingestedAt = at, processedAt = at, topics = topics, engagementScore = 50.0,
    )
}
```

- [ ] **Run test to confirm it fails** (ClusteringWorkflow constructor mismatch)

```bash
./gradlew test --tests "jvm.daily.workflow.ClusteringWorkflowPersistenceTest" 2>&1 | tail -10
```

- [ ] **Update `ClusteringWorkflow.kt`** — add `clusterRepository` param, fix fetch + save

Change constructor:
```kotlin
class ClusteringWorkflow(
    private val processedArticleRepository: ProcessedArticleRepository,
    private val llmClient: LLMClient,
    private val clusterRepository: ClusterRepository,
    private val clock: Clock = Clock.System,
) : Workflow {
```

In `execute()`, replace:
```kotlin
val articles = processedArticleRepository.findByDateRange(yesterday, now)
```
with:
```kotlin
val articles = processedArticleRepository.findByIngestedAtRange(yesterday, now)
```

Replace the `// TODO: Save clusters to database (future PR)` line and the `println("Done. Clusters ready...")` with:
```kotlin
clusterRepository.saveAll(clusters)
println("[clustering] Done. Saved ${clusters.size} clusters.")
```

Also add import at the top: `import jvm.daily.storage.ClusterRepository`

- [ ] **Run tests**

```bash
./gradlew test --tests "jvm.daily.workflow.ClusteringWorkflowPersistenceTest"
```
Expected: 1 test passes

- [ ] **Run full test suite**

```bash
./gradlew test
```
Expected: BUILD SUCCESSFUL

- [ ] **Commit**

```bash
git add app/src/main/kotlin/jvm/daily/workflow/ClusteringWorkflow.kt \
        app/src/test/kotlin/jvm/daily/workflow/ClusteringWorkflowPersistenceTest.kt
git commit -m "feat: ClusteringWorkflow now persists clusters via ClusterRepository"
```

---

### Task 7: OutgressWorkflow — JSON path (PATH B)

- [ ] **Write failing tests** (add to `OutgressWorkflowTest.kt`)

Add a `stubClusterRepo` helper and JSON-focused tests:

```kotlin
// Add inside OutgressWorkflowTest class:

@Test
fun `with clusterRepository writes daily JSON file with clusters`(@TempDir tempDir: Path) = runTest {
    val fixedNow = Instant.parse("2026-03-15T07:00:00Z")
    val clock = object : Clock { override fun now() = fixedNow }
    val windowStart = Instant.parse("2026-03-14T07:00:00Z")

    val article = processedArticle("a1", "Spring Boot 4.0 Released", "https://example.com/spring",
        listOf("spring"), 80.0, fixedNow)
    val cluster = ArticleCluster(
        id = "c1", title = "Spring News", summary = "Spring stuff",
        articles = listOf("a1"), sources = listOf("rss"),
        totalEngagement = 80.0, createdAt = fixedNow,
    )
    val repo = stubRepo(listOf(article))
    val clusterRepo = stubClusterRepo(listOf(cluster))

    OutgressWorkflow(repo, tempDir, clock = clock, clusterRepository = clusterRepo).execute()

    val jsonFile = tempDir.resolve("daily-2026-03-15.json")
    assertTrue(jsonFile.toFile().exists(), "daily JSON file should be created")
    val json = jsonFile.readText()
    assertContains(json, "\"date\":\"2026-03-15\"")
    assertContains(json, "Spring News")
    assertContains(json, "Spring Boot 4.0 Released")
    assertContains(json, "https://example.com/spring")
}

@Test
fun `unclustered articles appear in unclustered list`(@TempDir tempDir: Path) = runTest {
    val fixedNow = Instant.parse("2026-03-15T07:00:00Z")
    val clock = object : Clock { override fun now() = fixedNow }

    val clustered = processedArticle("a1", "Clustered", "https://example.com/1", listOf("spring"), 80.0, fixedNow)
    val unclustered = processedArticle("a2", "Standalone", "https://example.com/2", listOf("misc"), 20.0, fixedNow)
    val cluster = ArticleCluster(
        id = "c1", title = "Spring", summary = "Spring stuff",
        articles = listOf("a1"), sources = listOf("rss"),
        totalEngagement = 80.0, createdAt = fixedNow,
    )
    val repo = object : ProcessedArticleRepository {
        override fun save(article: ProcessedArticle) {}
        override fun saveAll(articles: List<ProcessedArticle>) {}
        override fun findAll() = listOf(clustered, unclustered)
        override fun findByDateRange(startDate: Instant, endDate: Instant) = listOf(clustered, unclustered)
        override fun findByIngestedAtRange(start: Instant, end: Instant) = listOf(clustered, unclustered)
        override fun findByIds(ids: List<String>) = listOf(clustered)
        override fun findFailedSince(since: Instant) = emptyList<ProcessedArticle>()
        override fun findFailedRawArticleIds(since: Instant, limit: Int) = emptyList<String>()
        override fun findFailedByIds(ids: List<String>) = emptyList<ProcessedArticle>()
        override fun findInspectionCandidates(since: Instant, limit: Int, minWarnings: Int) = emptyList<ProcessedArticle>()
        override fun findUnprocessedRawArticles(since: Instant) = emptyList<String>()
        override fun existsById(id: String) = false
        override fun count() = 2L
    }
    val clusterRepo = stubClusterRepo(listOf(cluster))

    OutgressWorkflow(repo, tempDir, clock = clock, clusterRepository = clusterRepo).execute()

    val json = tempDir.resolve("daily-2026-03-15.json").readText()
    assertContains(json, "Standalone")
    assertContains(json, "\"unclustered\"")
}

@Test
fun `empty clusters produces clusters=[] with all articles in unclustered`(@TempDir tempDir: Path) = runTest {
    val fixedNow = Instant.parse("2026-03-15T07:00:00Z")
    val clock = object : Clock { override fun now() = fixedNow }
    val article = processedArticle("a1", "Solo Article", "https://example.com", listOf("misc"), 30.0, fixedNow)
    val repo = stubRepo(listOf(article))
    val clusterRepo = stubClusterRepo(emptyList())

    OutgressWorkflow(repo, tempDir, clock = clock, clusterRepository = clusterRepo).execute()

    val json = tempDir.resolve("daily-2026-03-15.json").readText()
    assertContains(json, "\"clusters\":[]")
    assertContains(json, "Solo Article")
}

@Test
fun `without clusterRepository only md file is written`(@TempDir tempDir: Path) = runTest {
    val fixedNow = Instant.parse("2026-03-15T07:00:00Z")
    val clock = object : Clock { override fun now() = fixedNow }
    val article = processedArticle("a1", "Some Article", "https://example.com", listOf("misc"), 30.0, fixedNow)

    OutgressWorkflow(stubRepo(listOf(article)), tempDir, clock = clock).execute()

    assertFalse(tempDir.resolve("daily-2026-03-15.json").toFile().exists())
    assertTrue(tempDir.resolve("jvm-daily-2026-03-15.md").toFile().exists())
}

@Test
fun `totalArticles equals clusteredCount plus unclusteredCount`(@TempDir tempDir: Path) = runTest {
    val fixedNow = Instant.parse("2026-03-15T07:00:00Z")
    val clock = object : Clock { override fun now() = fixedNow }
    val a1 = processedArticle("a1", "A1", "https://a1.com", listOf("spring"), 80.0, fixedNow)
    val a2 = processedArticle("a2", "A2", "https://a2.com", listOf("misc"), 20.0, fixedNow)
    val cluster = ArticleCluster("c1", "C1", "summary", listOf("a1"), listOf("rss"), 80.0, fixedNow)
    val repo = object : ProcessedArticleRepository {
        override fun save(article: ProcessedArticle) {}
        override fun saveAll(articles: List<ProcessedArticle>) {}
        override fun findAll() = listOf(a1, a2)
        override fun findByDateRange(s: Instant, e: Instant) = listOf(a1, a2)
        override fun findByIngestedAtRange(s: Instant, e: Instant) = listOf(a1, a2)
        override fun findByIds(ids: List<String>) = listOf(a1)
        override fun findFailedSince(since: Instant) = emptyList<ProcessedArticle>()
        override fun findFailedRawArticleIds(since: Instant, limit: Int) = emptyList<String>()
        override fun findFailedByIds(ids: List<String>) = emptyList<ProcessedArticle>()
        override fun findInspectionCandidates(since: Instant, limit: Int, minWarnings: Int) = emptyList<ProcessedArticle>()
        override fun findUnprocessedRawArticles(since: Instant) = emptyList<String>()
        override fun existsById(id: String) = false
        override fun count() = 2L
    }

    OutgressWorkflow(repo, tempDir, clock = clock, clusterRepository = stubClusterRepo(listOf(cluster))).execute()

    val json = tempDir.resolve("daily-2026-03-15.json").readText()
    assertContains(json, "\"totalArticles\":2")
}

private fun stubClusterRepo(clusters: List<ArticleCluster>) = object : ClusterRepository {
    override fun save(cluster: ArticleCluster) {}
    override fun saveAll(cs: List<ArticleCluster>) {}
    override fun findByDateRange(start: Instant, end: Instant) = clusters
    override fun deleteByDateRange(start: Instant, end: Instant) {}
}
```

Also add these imports to `OutgressWorkflowTest.kt`:
```kotlin
import jvm.daily.model.ArticleCluster
import jvm.daily.storage.ClusterRepository
```

- [ ] **Run tests to verify they fail** (OutgressWorkflow doesn't have clusterRepository yet)

```bash
./gradlew test --tests "jvm.daily.workflow.OutgressWorkflowTest" 2>&1 | tail -10
```

- [ ] **Implement PATH B in `OutgressWorkflow.kt`**

Add import:
```kotlin
import jvm.daily.storage.ClusterRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.hours
```

Update constructor:
```kotlin
class OutgressWorkflow(
    private val processedArticleRepository: ProcessedArticleRepository,
    private val outputDir: Path,
    private val outgressDays: Int = 1,
    private val clock: Clock = Clock.System,
    private val clusterRepository: ClusterRepository? = null,
) : Workflow {
```

At the end of `execute()`, after the existing `.md` loop, add:

```kotlin
if (clusterRepository != null) {
    writeDigestJson(now, clusterRepository)
}
```

Add the private method:

```kotlin
private suspend fun writeDigestJson(now: kotlinx.datetime.Instant, clusterRepository: ClusterRepository) {
    val windowStart = now.minus(24.hours)
    val clusters = clusterRepository.findByDateRange(windowStart, now)
    val allClusterArticleIds = clusters.flatMap { it.articles }.toSet()

    val clusterArticlesById = processedArticleRepository
        .findByIds(allClusterArticleIds.toList())
        .associateBy { it.id }

    val allIngested = processedArticleRepository.findByIngestedAtRange(windowStart, now)
    val unclusteredArticles = allIngested.filter { it.id !in allClusterArticleIds }

    val totalArticles = allClusterArticleIds.size + unclusteredArticles.size

    val digestClusters = clusters.map { cluster ->
        DigestCluster(
            id = cluster.id,
            title = cluster.title,
            summary = cluster.summary,
            engagementScore = cluster.totalEngagement,
            articles = cluster.articles
                .mapNotNull { clusterArticlesById[it] }
                .sortedByDescending { it.engagementScore }
                .map { it.toDigestArticle() },
        )
    }.sortedByDescending { it.engagementScore }

    val digest = DigestJson(
        date = now.toLocalDateTime(TimeZone.UTC).date.toString(),
        generatedAt = now.toString(),
        windowHours = 24,
        totalArticles = totalArticles,
        clusters = digestClusters,
        unclustered = unclusteredArticles
            .sortedByDescending { it.engagementScore }
            .map { it.toDigestArticle() },
    )

    outputDir.createDirectories()
    val date = now.toLocalDateTime(TimeZone.UTC).date
    val jsonFile = outputDir.resolve("daily-$date.json")
    jsonFile.writeText(Json.encodeToString(digest))
    println("[outgress] Wrote digest JSON to $jsonFile (${digestClusters.size} clusters, ${unclusteredArticles.size} unclustered)")
}

private fun jvm.daily.model.ProcessedArticle.toDigestArticle() = DigestArticle(
    id = id,
    title = originalTitle,
    url = url,
    summary = summary,
    topics = topics,
    entities = entities,
    engagementScore = engagementScore,
    publishedAt = publishedAt,
    ingestedAt = ingestedAt,
    sourceType = sourceType,
)
```

- [ ] **Run tests**

```bash
./gradlew test --tests "jvm.daily.workflow.OutgressWorkflowTest"
```
Expected: all tests pass

- [ ] **Run full test suite**

```bash
./gradlew test
```
Expected: BUILD SUCCESSFUL

- [ ] **Commit**

```bash
git add app/src/main/kotlin/jvm/daily/workflow/OutgressWorkflow.kt \
        app/src/test/kotlin/jvm/daily/workflow/OutgressWorkflowTest.kt
git commit -m "feat: OutgressWorkflow writes daily-YYYY-MM-DD.json digest with clusters"
```

---

## Chunk 3: App.kt wiring

### Files
- Modify: `app/src/main/kotlin/jvm/daily/App.kt`

### Task 8: Wire ClusterRepository into runClustering and runOutgress

No new tests needed here — App.kt is integration glue covered by the existing `ProcessingPipelineIntegrationTest` and smoke test of the build.

- [ ] **Update `runClustering` in `App.kt`**

Replace the body of `runClustering`:
```kotlin
internal fun runClustering(dbPath: String) {
    val llmProvider = System.getenv("LLM_PROVIDER") ?: "mock"
    val llmApiKey   = System.getenv("LLM_API_KEY")
    val llmModel    = System.getenv("LLM_MODEL") ?: "gpt-4"

    if (llmProvider != "mock" && llmApiKey == null) {
        error("LLM_API_KEY required for provider '$llmProvider'")
    }

    DuckDbConnectionFactory.persistent(dbPath).use { connection ->
        val clusterRepository = DuckDbClusterRepository(connection)
        val processedRepo = DuckDbProcessedArticleRepository(connection)
        runBlocking {
            ClusteringWorkflow(processedRepo, createLLMClient(llmProvider, llmApiKey, llmModel), clusterRepository).execute()
        }
    }
}
```

- [ ] **Update `runOutgress` in `App.kt`**

Replace the body of `runOutgress`:
```kotlin
internal fun runOutgress(dbPath: String) {
    val outputDirPath = System.getenv("OUTPUT_DIR")    ?: "output"
    val outgressDays  = System.getenv("OUTGRESS_DAYS")?.toIntOrNull() ?: 30
    val outputDir     = Path.of(outputDirPath)
    outputDir.createDirectories()

    println("Output dir: $outputDir  |  Days: $outgressDays")

    DuckDbConnectionFactory.persistent(dbPath).use { connection ->
        val clusterRepository = DuckDbClusterRepository(connection)
        val processedRepo = DuckDbProcessedArticleRepository(connection)
        runBlocking {
            OutgressWorkflow(
                processedRepo,
                outputDir,
                outgressDays = outgressDays,
                clusterRepository = clusterRepository,
            ).execute()
        }
    }
}
```

Add imports at top of `App.kt` if not already present:
```kotlin
import jvm.daily.storage.DuckDbClusterRepository
```

- [ ] **Run full build**

```bash
./gradlew build
```
Expected: BUILD SUCCESSFUL

- [ ] **Commit**

```bash
git add app/src/main/kotlin/jvm/daily/App.kt
git commit -m "feat: wire DuckDbClusterRepository into clustering and outgress pipeline"
```

---

## Chunk 4: serve.py + frontend viewer

### Files
- Modify: `viewer/serve.py`

### Task 9: serve.py — add /api/daily/<date> endpoint

- [ ] **Update `serve.py`** — add regex import and new endpoint

At the top of `serve.py`, add `import re` alongside the existing imports.

Add `DATE_PATTERN` constant after the existing constants near the top:
```python
DATE_PATTERN = re.compile(r'^\d{4}-\d{2}-\d{2}$')
```

In `do_GET`, add the new branch after the `elif p == "/api/files":` block and before `elif p.startswith("/output/"):`:

```python
elif p.startswith("/api/daily/"):
    date_seg = p[len("/api/daily/"):]
    if not DATE_PATTERN.match(date_seg):
        self.send_bytes(400, "application/json", json.dumps({"error": "invalid date"}))
        return
    path = OUTPUT_DIR / f"daily-{date_seg}.json"
    if not path.exists() or not str(path.resolve()).startswith(str(OUTPUT_DIR.resolve())):
        self.send_bytes(404, "application/json", json.dumps({"error": "not found"}))
        return
    self.send_bytes(200, "application/json", path.read_bytes())
```

- [ ] **Test manually** (optional quick smoke test)

```bash
cd /path/to/project
python3 viewer/serve.py 8889 &
curl -s http://localhost:8889/api/daily/2026-02-27  # expect 404 (no JSON file yet)
curl -s http://localhost:8889/api/daily/invalid     # expect 400
kill %1
```

- [ ] **Commit**

```bash
git add viewer/serve.py
git commit -m "feat: serve.py adds /api/daily/<date> JSON endpoint"
```

---

### Task 10: Frontend — cluster rendering

- [ ] **Update the `HTML` constant in `serve.py`**

**Add CSS:** In the `<style>` block, after the `.offline-msg` and `.refresh-btn` rules (just before the closing `</style>`), add:

```css
    /* ── Cluster digest view ── */
    .cluster { margin-bottom: 36px; }
    .cluster-title { color: #e6edf3; font-size: 1.1rem; margin-bottom: 8px;
                     display: flex; align-items: center; gap: 8px;
                     border-bottom: 1px solid #21262d; padding-bottom: 6px; }
    .cluster-count { font-size: 0.72rem; background: #21262d; color: #8b949e;
                     padding: 2px 7px; border-radius: 10px; font-weight: normal; }
    .cluster-synthesis { color: #8b949e; line-height: 1.7; margin-bottom: 14px; font-size: 0.9rem; }
    .article-list { display: flex; flex-direction: column; gap: 10px; }
    .article-row { background: #161b22; border: 1px solid #30363d; border-radius: 8px;
                   padding: 12px 16px; }
    .article-title { color: #58a6ff; font-size: 0.95rem; text-decoration: none; font-weight: 600; display: block; margin-bottom: 4px; }
    .article-title:hover { text-decoration: underline; }
    .chips { display: flex; gap: 6px; flex-wrap: wrap; margin: 4px 0 6px; }
    .chip { background: #1f3a5f; color: #58a6ff; font-size: 0.7rem;
            padding: 2px 8px; border-radius: 10px; }
    .article-summary { color: #8b949e; font-size: 0.85rem; line-height: 1.5; margin: 0;
                       overflow: hidden; display: -webkit-box;
                       -webkit-line-clamp: 2; -webkit-box-orient: vertical; }
```

**Replace `initArticles` and `loadArticle` functions** in the `<script>` block with:

```javascript
  // ── Articles ──────────────────────────────────────────────────────────────
  function esc(s) {
    return String(s || '').replace(/&/g,'&amp;').replace(/</g,'&lt;')
      .replace(/>/g,'&gt;').replace(/"/g,'&quot;');
  }

  async function loadDate(date, btn) {
    document.querySelectorAll('.date-btn').forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
    document.getElementById('no-files').classList.add('hidden');

    const jsonRes = await fetch('/api/daily/' + date);
    if (jsonRes.ok) {
      const data = await jsonRes.json();
      renderClusters(data);
      document.getElementById('meta').textContent = data.totalArticles + ' articles';
    } else {
      const mdRes = await fetch('/output/jvm-daily-' + date + '.md');
      if (!mdRes.ok) { document.getElementById('md').innerHTML = ''; return; }
      const md = await mdRes.text();
      document.getElementById('md').innerHTML = marked.parse(md);
      const m = md.match(/Articles: (\d+)/);
      document.getElementById('meta').textContent = m ? m[1] + ' articles' : '';
    }
  }

  function renderClusters(data) {
    const clusters = [...data.clusters].sort((a, b) => b.engagementScore - a.engagementScore);
    let html = '';

    function articleHtml(a) {
      const chips = (a.topics || []).map(t => `<span class="chip">${esc(t)}</span>`).join('');
      return `<div class="article-row">
        <a class="article-title" href="${esc(a.url || '#')}" target="_blank" rel="noopener">${esc(a.title)} ↗</a>
        <div class="chips">${chips}</div>
        <p class="article-summary">${esc(a.summary)}</p>
      </div>`;
    }

    for (const cluster of clusters) {
      const arts = [...cluster.articles].sort((a, b) => b.engagementScore - a.engagementScore);
      html += `<div class="cluster">
        <h2 class="cluster-title">${esc(cluster.title)}
          <span class="cluster-count">${arts.length}</span></h2>
        <p class="cluster-synthesis">${esc(cluster.summary)}</p>
        <div class="article-list">${arts.map(articleHtml).join('')}</div>
      </div>`;
    }

    if (data.unclustered && data.unclustered.length > 0) {
      const arts = [...data.unclustered].sort((a, b) => b.engagementScore - a.engagementScore);
      html += `<div class="cluster">
        <h2 class="cluster-title">Other <span class="cluster-count">${arts.length}</span></h2>
        <div class="article-list">${arts.map(articleHtml).join('')}</div>
      </div>`;
    }

    document.getElementById('md').innerHTML = html;
  }

  async function initArticles() {
    const files = await fetch('/api/files').then(r => r.json());
    const sidebar = document.getElementById('date-sidebar');
    if (!files.length) { document.getElementById('no-files').classList.remove('hidden'); return; }
    files.forEach((f, i) => {
      const date = f.replace('jvm-daily-', '').replace('.md', '');
      const btn  = document.createElement('button');
      btn.className = 'date-btn';
      btn.textContent = date;
      btn.onclick = () => loadDate(date, btn);
      sidebar.appendChild(btn);
      if (i === 0) loadDate(date, btn);
    });
  }
```

- [ ] **Run full build to confirm nothing broken**

```bash
./gradlew build
```
Expected: BUILD SUCCESSFUL

- [ ] **Smoke-test the viewer manually**

```bash
python3 viewer/serve.py 8888 &
open http://localhost:8888
# Verify: dates load, no JS errors in console
kill %1
```

- [ ] **Commit**

```bash
git add viewer/serve.py
git commit -m "feat: viewer renders Latent Space-style cluster digest with JSON fallback to markdown"
```

---

## Final verification

- [ ] **Run full test suite one last time**

```bash
./gradlew build
```
Expected: BUILD SUCCESSFUL, all tests green

- [ ] **Push and open PR**

```bash
git push -u origin feat/daily-digest-viewer
gh pr create \
  --title "feat: daily digest viewer with Latent Space-style cluster rendering" \
  --body "$(cat <<'EOF'
## Summary
- Adds `ClusterRepository` + `DuckDbClusterRepository` to persist article clusters to DuckDB
- Fixes `ClusteringWorkflow` TODO: clusters are now saved after each run
- `OutgressWorkflow` writes `daily-YYYY-MM-DD.json` alongside existing `.md` — contains clusters + unclustered articles from last 24h
- `serve.py` adds `GET /api/daily/<date>` endpoint
- Frontend renders Latent Space AI News-style view: cluster title, synthesis, article cards with topic chips and 2-line summary; falls back to markdown for old dates

## Test plan
- [ ] `./gradlew build` passes
- [ ] Open viewer locally, verify cluster view renders for a date that has JSON
- [ ] Verify fallback to markdown for dates without JSON
EOF
)"
```
