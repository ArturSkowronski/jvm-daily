# Release Cluster Type Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Classify release-related clusters with `type="release"`, generate 5-bullet summaries for them, and render them as bullet list + source badges in the viewer instead of article cards.

**Architecture:** Extend `ClusteringWorkflow` grouping prompt with `RELEASE: YES` flag; replace `Triple` return type with `GroupResult` data class; generate bullet-point synthesis for release clusters; add `type`/`bullets` fields through the model → DB → outgress → viewer stack.

**Tech Stack:** Kotlin, Gradle, JUnit 5 + kotlin-test, DuckDB JDBC, Python viewer (serve.py)

**Spec:** `docs/superpowers/specs/2026-03-20-release-cluster-type-design.md`

---

## File Map

| File | Change |
|------|--------|
| `app/src/main/kotlin/jvm/daily/model/ProcessedArticle.kt` | Add `type: String = "topic"` and `bullets: List<String> = emptyList()` to `ArticleCluster` |
| `app/src/main/kotlin/jvm/daily/workflow/DigestJson.kt` | Same two fields on `DigestCluster` |
| `app/src/main/kotlin/jvm/daily/storage/DuckDbClusterRepository.kt` | ALTER TABLE migration; update `save()` INSERT; update `toArticleCluster()` read |
| `app/src/main/kotlin/jvm/daily/workflow/ClusteringWorkflow.kt` | `GroupResult` data class; `RELEASE:` in prompt+parser; bullet prompt in `createCluster` |
| `app/src/main/kotlin/jvm/daily/workflow/OutgressWorkflow.kt` | Pass `type` + `bullets` in `DigestCluster(...)` constructor |
| `viewer/serve.py` | Release cluster rendering: bullet list + per-article source badges |
| `app/src/test/kotlin/jvm/daily/storage/DuckDbClusterRepositoryTest.kt` | Add: type/bullets round-trip |
| `app/src/test/kotlin/jvm/daily/workflow/ClusteringWorkflowOrderingTest.kt` | Add: `llmWithRaw` helper; RELEASE parsing tests; bullet synthesis test |
| `app/src/test/kotlin/jvm/daily/workflow/OutgressWorkflowTest.kt` | Add: release cluster fields pass-through test |

---

## Task 1: Add `type` and `bullets` to `ArticleCluster` and `DigestCluster`

**Files:**
- Modify: `app/src/main/kotlin/jvm/daily/model/ProcessedArticle.kt`
- Modify: `app/src/main/kotlin/jvm/daily/workflow/DigestJson.kt`

- [ ] **Step 1: Write failing DB round-trip test** (it won't compile yet — that's expected)

Add to `app/src/test/kotlin/jvm/daily/storage/DuckDbClusterRepositoryTest.kt`, inside the class, after the existing tests:

```kotlin
@Test
fun `type and bullets round-trip through DB`() {
    val releaseCluster = cluster(
        id = "rc1",
        type = "release",
        bullets = listOf("Virtual threads are now default", "New @RestClientTest slice"),
    )
    repository.saveAll(listOf(releaseCluster))

    val results = repository.findByDateRange(
        start = Instant.parse("2026-03-15T00:00:00Z"),
        end = Instant.parse("2026-03-15T23:59:59Z"),
    )

    assertEquals(1, results.size)
    assertEquals("release", results[0].type)
    assertEquals(listOf("Virtual threads are now default", "New @RestClientTest slice"), results[0].bullets)
}

@Test
fun `topic cluster has default type and empty bullets`() {
    val topicCluster = cluster(id = "tc1")
    repository.saveAll(listOf(topicCluster))

    val results = repository.findByDateRange(
        start = Instant.parse("2026-03-15T00:00:00Z"),
        end = Instant.parse("2026-03-15T23:59:59Z"),
    )

    assertEquals("topic", results[0].type)
    assertEquals(emptyList<String>(), results[0].bullets)
}
```

Also update the `cluster()` helper at the bottom of `DuckDbClusterRepositoryTest` to accept `type` and `bullets`:

```kotlin
private fun cluster(
    id: String,
    title: String = "Cluster $id",
    summary: String = "Summary for $id",
    articles: List<String> = listOf("a1", "a2"),
    sources: List<String> = listOf("rss", "twitter"),
    totalEngagement: Double = 42.0,
    createdAt: Instant = Instant.parse("2026-03-15T10:00:00Z"),
    type: String = "topic",
    bullets: List<String> = emptyList(),
) = ArticleCluster(
    id = id, title = title, summary = summary, articles = articles, sources = sources,
    totalEngagement = totalEngagement, createdAt = createdAt, type = type, bullets = bullets,
)
```

- [ ] **Step 2: Run test — expect compile error**

```bash
./gradlew test --tests "jvm.daily.storage.DuckDbClusterRepositoryTest" 2>&1 | tail -20
```

Expected: compilation failure mentioning `type` or `bullets` not found on `ArticleCluster`.

- [ ] **Step 3: Add `type` and `bullets` to `ArticleCluster`**

In `app/src/main/kotlin/jvm/daily/model/ProcessedArticle.kt`, update `ArticleCluster`:

```kotlin
@Serializable
data class ArticleCluster(
    val id: String,
    val title: String,
    val summary: String,
    val articles: List<String>,
    val sources: List<String>,
    val totalEngagement: Double,
    val createdAt: Instant,
    val type: String = "topic",              // "topic" | "release"
    val bullets: List<String> = emptyList(), // up to 5 bullets for release clusters
)
```

- [ ] **Step 4: Add `type` and `bullets` to `DigestCluster`**

In `app/src/main/kotlin/jvm/daily/workflow/DigestJson.kt`, update `DigestCluster`:

```kotlin
@Serializable
data class DigestCluster(
    val id: String,
    val title: String,
    val summary: String,
    val engagementScore: Double,
    val articles: List<DigestArticle>,
    val type: String = "topic",
    val bullets: List<String> = emptyList(),
)
```

- [ ] **Step 5: Run tests — expect failure (DB not updated yet)**

```bash
./gradlew test --tests "jvm.daily.storage.DuckDbClusterRepositoryTest" 2>&1 | tail -30
```

Expected: compile passes, but `type and bullets round-trip` test FAILS (column does not exist).

- [ ] **Step 6: Commit model changes**

```bash
git add app/src/main/kotlin/jvm/daily/model/ProcessedArticle.kt \
        app/src/main/kotlin/jvm/daily/workflow/DigestJson.kt \
        app/src/test/kotlin/jvm/daily/storage/DuckDbClusterRepositoryTest.kt
git commit -m "feat: add type and bullets fields to ArticleCluster and DigestCluster models"
```

---

## Task 2: DB migration — `DuckDbClusterRepository`

**Files:**
- Modify: `app/src/main/kotlin/jvm/daily/storage/DuckDbClusterRepository.kt`

The test from Task 1 is already failing. Now make it pass.

- [ ] **Step 1: Add ALTER TABLE migration to `createTable()`**

In `DuckDbClusterRepository.kt`, update `createTable()`:

```kotlin
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
```

- [ ] **Step 2: Update `save()` to write `type` and `bullets`**

Replace the `save()` method:

```kotlin
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
```

- [ ] **Step 3: Update `toArticleCluster()` to read `type` and `bullets`**

Replace the `toArticleCluster()` extension function:

```kotlin
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
```

- [ ] **Step 4: Run DB tests — expect all pass**

```bash
./gradlew test --tests "jvm.daily.storage.DuckDbClusterRepositoryTest" 2>&1 | tail -20
```

Expected: all tests PASS (including the two new round-trip tests from Task 1).

- [ ] **Step 5: Run full build**

```bash
./gradlew build 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/jvm/daily/storage/DuckDbClusterRepository.kt
git commit -m "feat: add type/bullets columns to article_clusters with ALTER TABLE migration"
```

---

## Task 3: `ClusteringWorkflow` — `GroupResult`, `RELEASE:` parsing, bullet prompt

**Files:**
- Modify: `app/src/main/kotlin/jvm/daily/workflow/ClusteringWorkflow.kt`
- Modify: `app/src/test/kotlin/jvm/daily/workflow/ClusteringWorkflowOrderingTest.kt`

- [ ] **Step 1: Add `llmWithRaw` helper and new tests to `ClusteringWorkflowOrderingTest`**

In `ClusteringWorkflowOrderingTest.kt`:

1. Add `llmWithRaw` helper alongside `llmWith` (do NOT remove `llmWith` — existing tests use it):

```kotlin
/**
 * LLM mock with explicit raw synthesis responses.
 * Call 0 = grouping; calls 1+ = synthesis responses in GROUP order.
 */
private fun llmWithRaw(groupResponse: String, synthesisResponses: List<String>) = object : LLMClient {
    private var callCount = 0
    override suspend fun chat(prompt: String): String {
        return if (callCount == 0) {
            callCount++
            groupResponse
        } else {
            val r = synthesisResponses.getOrElse(callCount - 1) { "TITLE: Cluster\nSYNTHESIS: ..." }
            callCount++
            r
        }
    }
}
```

2. Add a new `captureSavedRaw` helper that uses `llmWithRaw`:

```kotlin
private suspend fun captureSavedRaw(
    articles: List<ProcessedArticle>,
    groupResponse: String,
    synthesisResponses: List<String>,
): List<ArticleCluster> {
    val saved = mutableListOf<ArticleCluster>()
    val clusterRepo = object : ClusterRepository {
        override fun save(c: ArticleCluster) {}
        override fun saveAll(clusters: List<ArticleCluster>) { saved.addAll(clusters) }
        override fun findByDateRange(start: Instant, end: Instant): List<ArticleCluster> = emptyList()
        override fun deleteByDateRange(start: Instant, end: Instant) {}
    }
    val articleRepo = object : ProcessedArticleRepository {
        override fun save(a: ProcessedArticle) {}
        override fun saveAll(a: List<ProcessedArticle>) {}
        override fun findAll(): List<ProcessedArticle> = articles
        override fun findByDateRange(s: Instant, e: Instant): List<ProcessedArticle> = articles
        override fun findFailedSince(since: Instant): List<ProcessedArticle> = emptyList()
        override fun findFailedRawArticleIds(since: Instant, limit: Int): List<String> = emptyList()
        override fun findFailedByIds(ids: List<String>): List<ProcessedArticle> = emptyList()
        override fun findInspectionCandidates(since: Instant, limit: Int, minWarnings: Int): List<ProcessedArticle> = emptyList()
        override fun findByIds(ids: List<String>): List<ProcessedArticle> = articles.filter { it.id in ids }
        override fun findByIngestedAtRange(s: Instant, e: Instant): List<ProcessedArticle> = articles
        override fun findUnprocessedRawArticles(since: Instant): List<String> = emptyList()
        override fun existsById(id: String): Boolean = false
        override fun count(): Long = articles.size.toLong()
        override fun deleteByProcessedAtSince(since: Instant): Int = 0
    }
    ClusteringWorkflow(articleRepo, clusterRepo, llmWithRaw(groupResponse, synthesisResponses), clock).execute()
    assertTrue(saved.isNotEmpty(), "ClusteringWorkflow should produce at least one cluster")
    return saved
}
```

3. Add the four new tests:

```kotlin
@Test
fun `RELEASE YES produces cluster with type release`() = runTest {
    val articles = articles("a1" to "Spring Boot 4.1.0-M3 Announced", "a2" to "Kotlin 2.3.20")

    val saved = captureSavedRaw(
        articles,
        groupResponse = """
            GROUP: Spring Boot 4.1.0-M3
            RELEASE: YES
            INDICES: 0
            GROUP: Kotlin 2.3.20
            RELEASE: YES
            INDICES: 1
        """.trimIndent(),
        synthesisResponses = listOf(
            "TITLE: Spring Boot 4.1.0-M3\nBULLET: Virtual threads on by default\nBULLET: New @RestClientTest slice",
            "TITLE: Kotlin 2.3.20\nBULLET: Context parameters preview\nBULLET: K2 compiler stabilized",
        ),
    )

    assertEquals(2, saved.size)
    val springCluster = saved.first { it.title == "Spring Boot 4.1.0-M3" }
    assertEquals("release", springCluster.type)
    assertEquals(listOf("Virtual threads on by default", "New @RestClientTest slice"), springCluster.bullets)
    assertEquals("", springCluster.summary, "Release clusters have empty summary")
}

@Test
fun `RELEASE NO is treated same as omitting RELEASE line — type is topic`() = runTest {
    val articles = articles("a1" to "Spring Security Discussion")

    val saved = captureSavedRaw(
        articles,
        groupResponse = "GROUP: Spring Security\nRELEASE: NO\nINDICES: 0",
        synthesisResponses = listOf("TITLE: Spring Security\nSYNTHESIS: Spring security discussion."),
    )

    assertEquals(1, saved.size)
    assertEquals("topic", saved.first().type)
    assertEquals(emptyList<String>(), saved.first().bullets)
}

@Test
fun `bullet synthesis caps at 5 even if LLM returns more`() = runTest {
    val articles = articles("a1" to "Spring Boot 4.1.0-M3")

    val saved = captureSavedRaw(
        articles,
        groupResponse = "GROUP: Spring Boot 4.1.0-M3\nRELEASE: YES\nINDICES: 0",
        synthesisResponses = listOf(
            "TITLE: Spring Boot 4.1.0-M3\n" +
            "BULLET: One\nBULLET: Two\nBULLET: Three\nBULLET: Four\nBULLET: Five\nBULLET: Six\nBULLET: Seven",
        ),
    )

    assertEquals(1, saved.size)
    assertEquals(5, saved.first().bullets.size, "bullets must be capped at 5")
}

@Test
fun `RELEASE YES cluster does not sink to bottom — sort stays title-based`() = runTest {
    // "Releases" roundup sinks to bottom; dedicated release cluster stays in normal position
    val articles = articles(
        "a1" to "Spring Boot 4.1.0-M3",  // score=10, isRelease=true
        "a2" to "Hibernate 7.3.0",        // score=20, in generic Releases roundup
        "a3" to "JVM Roadmap Discussion", // score=30, topic cluster
    )

    val saved = captureSavedRaw(
        articles,
        groupResponse = """
            GROUP: Spring Boot 4.1.0-M3
            RELEASE: YES
            INDICES: 0
            GROUP: JVM Roadmap Discussion
            INDICES: 2
            GROUP: Releases
            RELEASE: YES
            INDICES: 1
        """.trimIndent(),
        synthesisResponses = listOf(
            "TITLE: Spring Boot 4.1.0-M3\nBULLET: Something new",
            "TITLE: JVM Roadmap Discussion\nSYNTHESIS: Roadmap stuff.",
            "TITLE: Releases\nBULLET: Hibernate 7.3.0 released",
        ),
    )

    assertEquals(3, saved.size)
    assertEquals("Releases", saved.last().title, "Generic Releases roundup must be last (title-based sort)")
    assertNotEquals("Spring Boot 4.1.0-M3", saved.last().title, "Dedicated release cluster must NOT be sorted to bottom")
}
```

- [ ] **Step 2: Run new tests — expect failures**

```bash
./gradlew test --tests "jvm.daily.workflow.ClusteringWorkflowOrderingTest" 2>&1 | tail -30
```

Expected: The 4 new tests FAIL. Existing tests still pass.

- [ ] **Step 3: Replace `Triple` with `GroupResult` data class in `ClusteringWorkflow`**

At the top of the companion object (or as a private top-level data class — put it inside the `ClusteringWorkflow` class before the companion object):

```kotlin
private data class GroupResult(
    val name: String,
    val articles: List<ProcessedArticle>,
    val isMajor: Boolean,
    val isRelease: Boolean,
)
```

- [ ] **Step 4: Update `groupBySemantic` return type and fallback**

Change signature:
```kotlin
private suspend fun groupBySemantic(articles: List<ProcessedArticle>): List<GroupResult> {
```

Change the fallback at the end of the function (was `listOf(Triple("", articles, false))`):
```kotlin
return listOf(GroupResult("", articles, false, false))
```

- [ ] **Step 5: Add `RELEASE: YES` rule to the grouping prompt**

After the `**Priority rule**` block in the `groupBySemantic` prompt, add:

```
**Release type rule**:
- Add `RELEASE: YES` for any cluster primarily about a software release:
  - The generic "Releases" roundup
  - Any dedicated release cluster (e.g. "Spring Boot 4.1.0", "Kotlin 2.3.20 Release")
- Omit for non-release clusters (discussions, tutorials, blog series, performance analysis)
```

Also update the output format comment in the prompt to show the new field:
```
GROUP: [cluster name]
MAJOR: YES   ← only when applicable
RELEASE: YES ← only when applicable
INDICES: [comma-separated indices]
```

- [ ] **Step 6: Update `parseGroupResponse` to return `List<GroupResult>` and parse `RELEASE:`**

Change signature:
```kotlin
private fun parseGroupResponse(
    response: String,
    articles: List<ProcessedArticle>,
): List<GroupResult> {
```

Add `pendingRelease` tracking alongside `pendingMajor`:
```kotlin
var pendingRelease = false
```

Add the `RELEASE:` case in the `when` block (after the `MAJOR:` case):
```kotlin
line.startsWith("RELEASE:") -> {
    pendingRelease = line.substringAfter("RELEASE:").trim().uppercase().startsWith("YES")
}
```

Change every `Triple(` constructor to `GroupResult(`:
- In the flush block inside the loop: `groups.add(GroupResult(pendingName, group, pendingMajor, pendingRelease)); pendingRelease = false`
- In the final flush block: same pattern
- In the catch-all unassigned block: `groups.add(GroupResult("", unassigned, false, false))`

Also reset `pendingRelease = false` when flushing (when a new `GROUP:` line is encountered).

- [ ] **Step 7: Update `clusterArticles` to use `GroupResult`**

Change the destructuring and `createCluster` call:

```kotlin
private suspend fun clusterArticles(articles: List<ProcessedArticle>): List<ArticleCluster> {
    val groups = groupBySemantic(articles)
    val clusters = groups
        .map { group -> Pair(createCluster(group.articles, group.name, group.isRelease), group.isMajor) }
    // Sort: MAJOR first → normal by engagement → "Releases" last (title-based, NOT isRelease-based)
    val major    = clusters.filter { (_, m) -> m }.sortedByDescending { (c, _) -> c.totalEngagement }.map { it.first }
    val releases = clusters.filter { (c, _) -> c.title.equals("Releases", ignoreCase = true) }.map { it.first }
    val normal   = clusters.filter { (c, m) -> !m && !c.title.equals("Releases", ignoreCase = true) }
                           .sortedByDescending { (c, _) -> c.totalEngagement }.map { it.first }
    return major + normal + releases
}
```

- [ ] **Step 8: Update `createCluster` — add `isRelease` param and bullet prompt**

Change signature:
```kotlin
private suspend fun createCluster(
    articles: List<ProcessedArticle>,
    clusterName: String = "",
    isRelease: Boolean = false,
): ArticleCluster {
```

Remove the `isReleasesCluster` variable. Replace the prompt branching with `isRelease`:

```kotlin
val prompt = if (isRelease) """
You are writing a concise release summary for a JVM ecosystem digest.

Release: $clusterName
Articles:
$articleSummaries
${if (articles.size > 10) "\n[... and ${articles.size - 10} more releases]" else ""}

Provide:
1. TITLE: The specific release name (e.g. "Spring Boot 4.1.0-M3", "Releases")
2. Up to 5 BULLET lines, each 1–2 sentences, covering the key highlights:
   - New features or APIs
   - Breaking changes or deprecations
   - Performance or compatibility improvements
   - Notable community reaction

Format:
TITLE: [title]
BULLET: [highlight 1]
BULLET: [highlight 2]
    """.trimIndent() else """
$CLUSTERING_SYSTEM_PROMPT
... (existing topic prompt unchanged)
    """.trimIndent()
```

- [ ] **Step 9: Update `parseClusterResponse` to collect `BULLET:` lines**

Update `ClusterSynthesis`:
```kotlin
private data class ClusterSynthesis(
    val title: String,
    val summary: String,
    val bullets: List<String> = emptyList(),
)
```

Add bullet collection in `parseClusterResponse`:
```kotlin
private fun parseClusterResponse(response: String): ClusterSynthesis {
    val lines = response.lines().map { it.trim().let { l ->
        l.removePrefix("**").let { if (it.contains("**")) it.substringBefore("**") + it.substringAfter("**") else it }
    } }
    val title = lines.find { it.startsWith("TITLE:") }
        ?.substringAfter("TITLE:")?.trim()
        ?: "Untitled Cluster"

    val bullets = lines.filter { it.startsWith("BULLET:") }
        .map { it.substringAfter("BULLET:").trim() }
        .take(5)

    val synthesisStart = lines.indexOfFirst { it.startsWith("SYNTHESIS:") }
    val summary = if (synthesisStart != -1) {
        lines.drop(synthesisStart).joinToString("\n").substringAfter("SYNTHESIS:").trim()
    } else {
        ""
    }

    return ClusterSynthesis(title, summary, bullets)
}
```

- [ ] **Step 10: Pass `type` and `bullets` through to `ArticleCluster` in `createCluster`**

At the end of `createCluster`, update the `ArticleCluster(...)` constructor call:

```kotlin
return ArticleCluster(
    id = UUID.randomUUID().toString(),
    title = synthesis.title,
    summary = synthesis.summary,
    articles = articles.map { it.id },
    sources = articles.map { it.sourceType }.toSet().toList(),
    totalEngagement = articles.sumOf { it.engagementScore },
    createdAt = clock.now(),
    type = if (isRelease) "release" else "topic",
    bullets = synthesis.bullets,
)
```

- [ ] **Step 11: Run clustering tests — expect all pass**

```bash
./gradlew test --tests "jvm.daily.workflow.ClusteringWorkflowOrderingTest" \
               --tests "jvm.daily.workflow.ClusteringWorkflowPersistenceTest" 2>&1 | tail -30
```

Expected: all tests PASS.

- [ ] **Step 12: Run full build**

```bash
./gradlew build 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 13: Commit**

```bash
git add app/src/main/kotlin/jvm/daily/workflow/ClusteringWorkflow.kt \
        app/src/test/kotlin/jvm/daily/workflow/ClusteringWorkflowOrderingTest.kt
git commit -m "feat: classify release clusters with RELEASE flag and generate bullet-point summaries"
```

---

## Task 4: `OutgressWorkflow` — pass `type` and `bullets` to `DigestCluster`

**Files:**
- Modify: `app/src/main/kotlin/jvm/daily/workflow/OutgressWorkflow.kt`
- Modify: `app/src/test/kotlin/jvm/daily/workflow/OutgressWorkflowTest.kt`

- [ ] **Step 1: Write failing test**

Add to `OutgressWorkflowTest.kt`, inside the class, after the existing tests:

```kotlin
@Test
fun `release cluster type and bullets are passed through to DigestCluster`(@TempDir tempDir: Path) = runTest {
    val fixedNow = Instant.parse("2026-03-15T12:00:00Z")
    val clock = object : Clock { override fun now() = fixedNow }

    val articles = listOf(
        processedArticle("a1", "Spring Boot 4.1.0-M3", "https://example.com/spring", listOf("spring"), 90.0, fixedNow),
    )
    val cluster = ArticleCluster(
        id = "c1", title = "Spring Boot 4.1.0-M3", summary = "",
        articles = listOf("a1"), sources = listOf("rss"), totalEngagement = 90.0,
        createdAt = fixedNow,
        type = "release",
        bullets = listOf("Virtual threads on by default", "New @RestClientTest slice"),
    )

    val repo = stubRepoWithData(articles)
    val clusterRepo = stubClusterRepo(listOf(cluster))

    OutgressWorkflow(repo, tempDir, clock = clock, clusterRepository = clusterRepo).execute()

    val json = tempDir.resolve("daily-2026-03-15.json").readText()
    val digest = Json.decodeFromString<DigestJson>(json)
    val digestCluster = digest.clusters.first()
    assertEquals("release", digestCluster.type)
    assertEquals(listOf("Virtual threads on by default", "New @RestClientTest slice"), digestCluster.bullets)
}
```

- [ ] **Step 2: Run test — expect failure**

```bash
./gradlew test --tests "jvm.daily.workflow.OutgressWorkflowTest.release cluster type and bullets are passed through to DigestCluster" 2>&1 | tail -20
```

Expected: FAIL — `type` is `"topic"` (default), not `"release"`.

- [ ] **Step 3: Update `OutgressWorkflow` — add `type` and `bullets` to constructor**

In `OutgressWorkflow.kt`, find the `DigestCluster(...)` constructor call inside `writeDigestJson` (the `digestClusters` mapping):

```kotlin
val digestClusters = clusters.map { cluster ->
    DigestCluster(
        id = cluster.id, title = cluster.title, summary = cluster.summary,
        engagementScore = cluster.totalEngagement,
        type = cluster.type,          // ← add
        bullets = cluster.bullets,    // ← add
        articles = cluster.articles
            .mapNotNull { clusterArticlesById[it] }
            .sortedByDescending { it.engagementScore }
            .map { it.toDigestArticle(socialByUrl) },
    )
}
```

- [ ] **Step 4: Run test — expect pass**

```bash
./gradlew test --tests "jvm.daily.workflow.OutgressWorkflowTest" 2>&1 | tail -20
```

Expected: all tests PASS.

- [ ] **Step 5: Run full build**

```bash
./gradlew build 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/jvm/daily/workflow/OutgressWorkflow.kt \
        app/src/test/kotlin/jvm/daily/workflow/OutgressWorkflowTest.kt
git commit -m "feat: pass release cluster type and bullets through to DigestCluster in outgress"
```

---

## Task 5: Viewer — release cluster rendering

**Files:**
- Modify: `viewer/serve.py`

No unit tests for viewer JS. Verify manually after implementing.

- [ ] **Step 1: Add `releaseBadgesHtml` function to the viewer JS**

In `viewer/serve.py`, find the `function socialLinksHtml(links)` function (around line 313). After it, add:

```javascript
  function releaseBadgesHtml(arts) {
    if (!arts || arts.length === 0) return '';
    const items = arts.map(a => {
      const label = a.sourceType === 'bluesky' ? '🦋 ' + esc(a.handle || 'Bluesky')
                  : a.sourceType === 'reddit'  ? '↗ ' + esc(a.handle || 'Reddit')
                  : a.sourceType === 'github-releases' ? '⬡ GitHub Release'
                  : a.sourceType === 'rss'     ? '↗ ' + esc(a.handle || 'Article')
                  : '↗ ' + esc(a.sourceType);
      const url = a.url || '#';
      return `<a class="social-link social-link-${esc(a.sourceType)}" href="${esc(url)}" target="_blank" rel="noopener">${label}</a>`;
    }).join('');
    return `<div class="social-links">${items}</div>`;
  }
```

- [ ] **Step 2: Update `renderClusters` to branch on `cluster.type`**

Find the loop in `renderClusters` that calls `clusterHtml(...)` for each cluster (around line 376). Replace:

```javascript
    const arts = [...cluster.articles].sort((a, b) => b.engagementScore - a.engagementScore);
    if (arts.length === 1 && isSocialPost(arts[0])) {
      standaloneTweets.push(arts[0]);
      continue;
    }
    html += clusterHtml(
      cluster.title,
      `<div class="cluster-title">${esc(cluster.title)}<span class="cluster-count">${arts.length} articles</span></div>`,
      `<div class="cluster-synthesis">${marked.parse(cluster.summary)}</div>`,
      arts.map(a => articleHtml(a, arts.length)).join(''),
      ''
    );
```

With:

```javascript
    const arts = [...cluster.articles].sort((a, b) => b.engagementScore - a.engagementScore);
    if (arts.length === 1 && isSocialPost(arts[0])) {
      standaloneTweets.push(arts[0]);
      continue;
    }
    if (cluster.type === 'release') {
      const bulletsHtml = (cluster.bullets && cluster.bullets.length > 0)
        ? '<ul class="release-bullets">' + cluster.bullets.map(b => `<li>${esc(b)}</li>`).join('') + '</ul>'
        : `<div class="cluster-synthesis">${marked.parse(cluster.summary)}</div>`;
      html += clusterHtml(
        cluster.title,
        `<div class="cluster-title cluster-title-release">${esc(cluster.title)}<span class="cluster-count">${arts.length} sources</span></div>`,
        bulletsHtml,
        releaseBadgesHtml(arts),
        'cluster-release'
      );
    } else {
      html += clusterHtml(
        cluster.title,
        `<div class="cluster-title">${esc(cluster.title)}<span class="cluster-count">${arts.length} articles</span></div>`,
        `<div class="cluster-synthesis">${marked.parse(cluster.summary)}</div>`,
        arts.map(a => articleHtml(a, arts.length)).join(''),
        ''
      );
    }
```

- [ ] **Step 3: Add CSS for release cluster styling**

Find the CSS section in `serve.py` (the big `<style>` block). After the `.social-link-reddit:hover` rule (around line 140), add:

```css
    /* ── Release cluster ── */
    .cluster-release .cluster-head { background: #fffbf0; border-left: 3px solid #f59e0b; }
    .cluster-title-release { color: #92400e; }
    .release-bullets { margin: 8px 0 8px 18px; padding: 0; font-size: 0.9rem; line-height: 1.6; color: #374151; }
    .release-bullets li { margin-bottom: 4px; }
```

- [ ] **Step 4: Manual verification**

Restart the viewer and check a real digest JSON:

```bash
# Kill any running viewer
pkill -f "serve.py" 2>/dev/null || true
# Start fresh
cd /Users/askowronski/Priv/jvm-daily/viewer && python3 serve.py &
```

Open http://localhost:8888 and verify:
- Topic clusters: unchanged (prose synthesis + article cards)
- Release clusters (type="release"): amber left border, bullet list, source badges instead of cards
- If no release clusters in today's JSON, run clustering on fresh data

- [ ] **Step 5: Commit**

```bash
git add viewer/serve.py
git commit -m "feat: render release clusters as bullet list with source badges in viewer"
```

---

## Task 6: Final verification

- [ ] **Step 1: Run the full test suite**

```bash
./gradlew build 2>&1 | tail -30
```

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 2: Run clustering on today's data to produce a fresh digest with release clusters**

```bash
export $(cat ~/Library/LaunchAgents/com.local.jvm-daily.plist | grep -oP '(?<=<string>)[^<]+' | grep '=' | head -5)
# Or set manually:
# export GEMINI_API_KEY=... DUCKDB_PATH=~/.jvm-daily/jvm-daily.db OUTPUT_DIR=~/.jvm-daily/output/ LLM_PROVIDER=gemini

cd /Users/askowronski/Priv/jvm-daily && ./gradlew run --args="clustering outgress" 2>&1 | tail -30
```

Verify the output JSON has clusters with `type: "release"` and `bullets: [...]`.

- [ ] **Step 3: Check viewer renders correctly**

Open http://localhost:8888 — confirm at least one release-style cluster appears with bullets and badges.
