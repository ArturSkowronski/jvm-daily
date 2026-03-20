# Release Cluster Type — Design

**Date:** 2026-03-20
**Branch:** feat/compact-social-card-in-clusters
**Status:** Approved

## Problem

When multiple articles cover the same software release (e.g. a GitHub release note + a blog announcement + Bluesky shares), they currently appear as individual article cards inside a topic cluster. This is redundant — the reader sees the same event described three times. Releases need a distinct rendering: a concise bullet-point summary with all sources shown as compact badges below.

## Approach

Extend `ClusteringWorkflow`'s LLM prompt to classify clusters as `type: "release" | "topic"` and generate bullet-point summaries for release clusters. No separate workflow; classification happens at clustering time.

## Scope

- Any cluster whose primary subject is a software release gets `type = "release"` — including the generic "Releases" roundup and dedicated major-release clusters (e.g. "Spring Boot 4.1.0-M3", "Kotlin 2.3.20")
- Non-release clusters (discussions, tutorials, performance analysis) remain `type = "topic"` with unchanged prose synthesis
- The existing `isReleasesCluster` branch in `createCluster` is replaced by the new `isRelease` flag — there will be exactly two prompt branches: `isRelease` (bullet prompt) and not (topic prose prompt)

---

## Changes

### 1. `groupBySemantic` — grouping prompt and return type

**Return type change:**
`groupBySemantic` currently returns `List<Triple<String, List<ProcessedArticle>, Boolean>>` where the Boolean is `isMajor`. Adding `isRelease` requires a fourth value. Replace the Triple with a named data class:

```kotlin
private data class GroupResult(
    val name: String,
    val articles: List<ProcessedArticle>,
    val isMajor: Boolean,
    val isRelease: Boolean,
)
```

Update all callers: `groupBySemantic`, `clusterArticles`, `parseGroupResponse`.

**LLM prompt addition:**

```
**Release type rule**:
- Add `RELEASE: YES` for any cluster primarily about a software release:
  - The generic "Releases" roundup
  - Any dedicated release cluster (e.g. "Spring Boot 4.1.0", "Kotlin 2.3.20 Release")
- Omit for non-release clusters (discussions, tutorials, blog series, performance analysis)
```

**Output format (updated):**

```
GROUP: Spring Boot 4.1.0-M3
MAJOR: YES
RELEASE: YES
INDICES: 2, 7, 11
```

**`parseGroupResponse`:** Add `pendingRelease: Boolean` alongside `pendingMajor`. Parse `RELEASE:` lines:

```kotlin
line.startsWith("RELEASE:") -> {
    pendingRelease = line.substringAfter("RELEASE:").trim().uppercase().startsWith("YES")
}
```

**Fallback constructors:** All three sites that previously constructed `Triple(...)` must switch to `GroupResult(...)`:
- The main accumulation path in `parseGroupResponse`
- The unassigned-articles catch-all at the end of `parseGroupResponse` (`GroupResult("", unassigned, false, false)`)
- The empty-parse fallback in `groupBySemantic` (`GroupResult("", articles, false, false)`)

**`clusterArticles` sort:** The bottom-sort bucket (sinks the generic "Releases" roundup to the bottom of the digest) stays driven by title matching — `c.title.equals("Releases", ignoreCase = true)` — and is **not** replaced by `isRelease`. The `isRelease` flag is forwarded only to `createCluster` for prompt selection. Dedicated major-release clusters (e.g. "Spring Boot 4.1.0-M3") have `isRelease = true` but do NOT get sorted to the bottom.

### 2. `createCluster` — bullet prompt for release clusters

`createCluster` currently detects `isReleasesCluster` from the cluster name. Replace this with an `isRelease: Boolean` parameter forwarded from `GroupResult`.

**Signature change:**
```kotlin
private suspend fun createCluster(articles: List<ProcessedArticle>, clusterName: String = "", isRelease: Boolean = false): ArticleCluster
```

When `isRelease = true`, use the bullet prompt (replaces the existing `isReleasesCluster` prompt):

```
You are writing a concise release summary for a JVM ecosystem digest.

Release: [cluster name]
Articles:
[articleSummaries]

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
...
```

When `isRelease = false`, use the existing `CLUSTERING_SYSTEM_PROMPT` prose path (unchanged).

**`parseClusterResponse`:** Collect `BULLET:` lines into `List<String>`, hard-capped at 5:

```kotlin
val bullets = lines.filter { it.startsWith("BULLET:") }
    .map { it.substringAfter("BULLET:").trim() }
    .take(5)  // parser enforces max even if LLM over-generates
```

`ClusterSynthesis` grows a `bullets` field:

```kotlin
private data class ClusterSynthesis(val title: String, val summary: String, val bullets: List<String> = emptyList())
```

For release clusters, `summary` is an empty string and `bullets` carries the content. For topic clusters, `bullets` is empty.

### 3. `ArticleCluster` model (ProcessedArticle.kt)

Add two fields with defaults (backward-compatible with existing DB rows):

```kotlin
val type: String = "topic",            // "topic" | "release"
val bullets: List<String> = emptyList(),
```

### 4. `DigestCluster` model (DigestJson.kt)

```kotlin
val type: String = "topic",
val bullets: List<String> = emptyList(),
```

### 5. `DuckDbClusterRepository` — DB migration

The table is created with `CREATE TABLE IF NOT EXISTS`. New columns must be added via `ALTER TABLE` for existing databases. Add to `createTable()` after the `CREATE TABLE` statement:

```sql
ALTER TABLE article_clusters ADD COLUMN IF NOT EXISTS type VARCHAR DEFAULT 'topic';
ALTER TABLE article_clusters ADD COLUMN IF NOT EXISTS bullets VARCHAR DEFAULT '[]';
```

`bullets` is stored as a JSON-encoded string using `kotlinx.serialization.encodeToString(listOf(...))`, consistent with how `article_ids` and `sources` are stored today.

**Updated `save()` INSERT:**
```kotlin
connection.prepareStatement(
    """
    INSERT OR REPLACE INTO article_clusters
    (id, title, summary, article_ids, sources, total_engagement, created_at, type, bullets)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
    """.trimIndent()
).use { stmt ->
    // ... existing bindings (1–7) unchanged ...
    stmt.setString(8, cluster.type)
    stmt.setString(9, Json.encodeToString(cluster.bullets))
    stmt.executeUpdate()
}
```

**Updated `toArticleCluster()`:**
```kotlin
ArticleCluster(
    id = getString("id"),
    title = getString("title"),
    summary = getString("summary"),
    articles = Json.decodeFromString(getString("article_ids")),
    sources = Json.decodeFromString(getString("sources")),
    totalEngagement = getDouble("total_engagement"),
    createdAt = Instant.parse(getString("created_at")),
    type = getString("type") ?: "topic",         // ← new
    bullets = Json.decodeFromString(getString("bullets") ?: "[]"),  // ← new
)
```

### 6. `OutgressWorkflow` — constructor update

Update the explicit `DigestCluster(...)` constructor call in `writeDigestJson` to pass through `type` and `bullets`:

```kotlin
DigestCluster(
    id = cluster.id, title = cluster.title, summary = cluster.summary,
    engagementScore = cluster.totalEngagement,
    type = cluster.type,          // ← new
    bullets = cluster.bullets,    // ← new
    articles = cluster.articles
        .mapNotNull { clusterArticlesById[it] }
        .sortedByDescending { it.engagementScore }
        .map { it.toDigestArticle(socialByUrl) },
)
```

No other logic changes in `OutgressWorkflow`.

### 7. Viewer (`serve.py` template)

For `cluster.type === "release"`:
- Render cluster title as heading
- If `bullets` is non-empty: render as an unordered list (up to 5 items)
- If `bullets` is empty (LLM parse failure fallback): render `cluster.summary` as prose (same as topic cluster)
- Render all `articles` as compact source badges using the existing `socialLinksHtml()` pattern: build a `<div class="social-links">` containing one `<a class="social-link social-link-{source}">` per article, linking to `article.url`, labelled by `article.sourceType` and `article.handle` (same DOM structure as `socialLinksHtml(article.socialLinks)` today)
- No individual article cards

For `cluster.type === "topic"`: unchanged behaviour.

---

## Data Flow

```
ClusteringWorkflow
  groupBySemantic()
    → LLM outputs RELEASE: YES per group
    → parseGroupResponse returns List<GroupResult> (name, articles, isMajor, isRelease)
  createCluster(articles, name, isRelease)
    → isRelease=true: LLM outputs BULLET: lines; summary = "", bullets = List<String>
    → isRelease=false: LLM outputs SYNTHESIS:; summary = "...", bullets = []
  ArticleCluster(type="release"|"topic", bullets=[...])

DuckDbClusterRepository
  type stored as VARCHAR, bullets stored as JSON string

OutgressWorkflow
  DigestCluster(type=cluster.type, bullets=cluster.bullets, ...)

Viewer
  cluster.type === "release"  → bullets list + all articles as badges
  cluster.type === "topic"    → prose synthesis + article cards
```

---

## Error Handling

- If the LLM omits `RELEASE:`, `isRelease` defaults to `false` → safe fallback to topic rendering
- If fewer than 5 bullets are generated, render only what exists (no padding)
- If `bullets` is empty on a release cluster (LLM parse failure), fall back to rendering the `summary` field as prose
- `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` is idempotent — safe to run on every app start

---

## Testing

- Unit test `parseGroupResponse`: `RELEASE: YES` parsed → `isRelease = true` in `GroupResult`; `MAJOR: YES` still parsed independently
- Unit test `parseClusterResponse`: `BULLET:` lines collected; exactly 5 returned when LLM returns 7; fewer than 5 returned correctly when LLM returns 3
- Unit test `DuckDbClusterRepository`: `type` and `bullets` round-trip through DB serialization (in-memory DuckDB)
- Unit test `OutgressWorkflow`: release cluster passes `type = "release"` and non-empty `bullets` to `DigestCluster`

---

## Files Changed

| File | Change |
|------|--------|
| `ClusteringWorkflow.kt` | `GroupResult` data class; `RELEASE:` in prompt and parser; `isRelease` param in `createCluster`; bullet prompt replaces `isReleasesCluster` branch |
| `ProcessedArticle.kt` | `ArticleCluster.type`, `ArticleCluster.bullets` |
| `DigestJson.kt` | `DigestCluster.type`, `DigestCluster.bullets` |
| `DuckDbClusterRepository.kt` | `ALTER TABLE` migration; serialize/deserialize `type` + `bullets` |
| `OutgressWorkflow.kt` | `DigestCluster` constructor: add `type`, `bullets` |
| `viewer/serve.py` (template) | Release cluster rendering: bullets + badges |
