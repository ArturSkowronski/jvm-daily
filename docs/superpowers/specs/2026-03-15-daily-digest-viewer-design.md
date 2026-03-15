# Design: Daily Digest Viewer (Latent Space Style)

**Date:** 2026-03-15
**Status:** Approved

## Overview

Enhance JVM Daily with a Latent Space AI News-style morning digest viewer. Each day, the pipeline produces `daily-YYYY-MM-DD.json` grouping articles into thematic clusters. The viewer renders clusters with synthesis summaries and article links, filtered to the last 24 hours of ingested content.

## Goals

- Display last 24 hours of articles grouped by thematic cluster
- Latent Space AI News style: cluster title + cross-source synthesis + article list
- Read from static outgress files (no DuckDB at viewer runtime)
- Deployed on Fly — pipeline runs each morning, viewer serves static files

## Out of Scope

- Reddit/HN discussion links (future milestone)
- Real-time updates

---

## Data Flow

```
ClusteringWorkflow.execute():
  articles = processedArticleRepository.findByIngestedAtRange(now-24h, now)  ← changed
  clusters = clusterArticles(articles)
  clusterRepository.saveAll(clusters)  ← replaces TODO

OutgressWorkflow.execute() — two independent output paths:

  PATH A: Markdown (unchanged)
    articles = processedArticleRepository.findByDateRange(since, now)  ← processedAt, unchanged
    write jvm-daily-YYYY-MM-DD.md

  PATH B: JSON (new, when clusterRepository != null)
    windowStart = now - 24h
    clusters = clusterRepository.findByDateRange(windowStart, now)
    allClusterArticleIds = clusters.flatMap { it.articles }.toSet()
    clusterArticles = processedArticleRepository.findByIds(allClusterArticleIds.toList())
    allIngestedArticles = processedArticleRepository.findByIngestedAtRange(windowStart, now)
    unclusteredArticles = allIngestedArticles.filter { it.id !in allClusterArticleIds }
    write daily-YYYY-MM-DD.json

  PATH B (when clusterRepository == null): skip JSON entirely

serve.py:
  GET /api/daily/<date> → validate date as YYYY-MM-DD → read output/daily-<date>.json → return JSON

Frontend:
  loadDate(date):
    1. GET /api/daily/<date>
    2. If 200 → renderClusters(json)
    3. If 404 → renderMarkdown(GET /output/jvm-daily-<date>.md)
```

---

## Section 1: JSON Output Format

File: `output/daily-YYYY-MM-DD.json`

Note on naming: the existing markdown uses `jvm-daily-YYYY-MM-DD.md`. The JSON uses `daily-YYYY-MM-DD.json` (no `jvm-` prefix) — intentional to distinguish formats.

```json
{
  "date": "2026-03-15",
  "generatedAt": "2026-03-15T07:00:00Z",
  "windowHours": 24,
  "totalArticles": 47,
  "clusters": [
    {
      "id": "cluster-001",
      "title": "Spring Boot 4.0 RC1 Sparks Migration Discussion",
      "summary": "Cross-source synthesis paragraph (150-200 words)...",
      "engagementScore": 87.3,
      "articles": [
        {
          "id": "art-001",
          "title": "Spring Boot 4.0 RC1 is here",
          "url": "https://spring.io/blog/...",
          "summary": "LLM-generated executive summary (max 200 words)...",
          "topics": ["spring", "releases"],
          "entities": ["Spring Boot 4.0", "JDK 21"],
          "engagementScore": 92.0,
          "publishedAt": "2026-03-15T06:00:00Z",
          "ingestedAt": "2026-03-15T07:00:00Z",
          "sourceType": "rss"
        }
      ]
    }
  ],
  "unclustered": [
    {
      "id": "art-099",
      "title": "Some standalone article",
      "url": "...",
      "summary": "...",
      "topics": ["misc"],
      "entities": [],
      "engagementScore": 12.0,
      "publishedAt": "2026-03-15T05:00:00Z",
      "ingestedAt": "2026-03-15T07:00:00Z",
      "sourceType": "rss"
    }
  ]
}
```

**Field definitions:**

- `date`: UTC calendar date of `clock.now()` at outgress run time, formatted as `YYYY-MM-DD`. If the pipeline runs at 01:00 UTC, this may be a different calendar date than the articles' `ingestedAt` dates — it represents the run date, not the ingest window.
- `generatedAt`: `clock.now()` at the start of `OutgressWorkflow.execute()`, ISO 8601 UTC.
- `windowHours`: constant `24`. Not configurable — distinct from `outgressDays` which controls the `.md` window. The JSON path always uses a 24-hour window regardless of `outgressDays`.
- `totalArticles`: distinct article ID count. Computed as `(allClusterArticleIds + unclusteredIds).size`. If an article appears in multiple clusters (inconsistent data), it is counted once.
- Cluster `summary`: mapped from `ArticleCluster.summary` (the cross-source synthesis field). JSON key is `summary`.
- Cluster `engagementScore`: mapped from `ArticleCluster.totalEngagement`. JSON key is `engagementScore` (normalized for the viewer).
- `unclustered`: articles where `ingestedAt in [now-24h, now]` but ID is not in any cluster's `articles` list. Same article object shape as cluster articles.
- **Empty clusters case**: if `clusterRepository.findByDateRange` returns empty, write `"clusters": []` and put all `findByIngestedAtRange` articles in `"unclustered"`. Always write a valid JSON file.

---

## Section 2: Kotlin Pipeline Changes

### 2a. ClusterRepository (new interface + implementation)

**Package:** `ClusterRepository` interface → `jvm.daily.storage`; `DuckDbClusterRepository` → `jvm.daily.storage`. `OutgressWorkflow` accepts the interface type, consistent with how it accepts `ProcessedArticleRepository` — no DuckDb* import in workflow files (WorkflowBoundaryTest constraint).

```kotlin
interface ClusterRepository {
    fun save(cluster: ArticleCluster)
    fun saveAll(clusters: List<ArticleCluster>)
    fun findByDateRange(start: Instant, end: Instant): List<ArticleCluster>  // filters on createdAt
    fun deleteByDateRange(start: Instant, end: Instant)  // infrastructure for future cleanup; no caller in this spec
}
```

`DuckDbClusterRepository` implementation. DuckDB table DDL:

```sql
CREATE TABLE IF NOT EXISTS article_clusters (
    id               VARCHAR PRIMARY KEY,
    title            VARCHAR NOT NULL,
    summary          VARCHAR NOT NULL,
    article_ids      VARCHAR NOT NULL,   -- JSON-serialized List<String> (ArticleCluster.articles)
    sources          VARCHAR NOT NULL,   -- JSON-serialized List<String> (ArticleCluster.sources)
    total_engagement DOUBLE  NOT NULL,   -- ArticleCluster.totalEngagement
    created_at       VARCHAR NOT NULL    -- ArticleCluster.createdAt, ISO 8601 UTC as VARCHAR
)
```

Column-to-field mapping:

| DB column         | Kotlin field               | Notes |
|-------------------|----------------------------|-------|
| `id`              | `ArticleCluster.id`        | VARCHAR PK |
| `title`           | `ArticleCluster.title`     | |
| `summary`         | `ArticleCluster.summary`   | |
| `article_ids`     | `ArticleCluster.articles`  | `Json.encodeToString<List<String>>(articles)` |
| `sources`         | `ArticleCluster.sources`   | `Json.encodeToString<List<String>>(sources)` |
| `total_engagement`| `ArticleCluster.totalEngagement` | |
| `created_at`      | `ArticleCluster.createdAt` | `.toString()` / `Instant.parse()` |

`findByDateRange` queries `WHERE created_at >= ? AND created_at <= ?` (lexicographic comparison on ISO 8601 UTC VARCHAR is correct).

`saveAll` uses `INSERT OR REPLACE` (same upsert pattern as `DuckDbArticleRepository`).

### 2b. ProcessedArticleRepository — two new methods

Both methods are added to the **interface** (not just `DuckDbProcessedArticleRepository`). All existing anonymous-object stubs of `ProcessedArticleRepository` in tests (e.g., `OutgressWorkflowTest`) must add stub implementations of these two new methods — they will fail to compile otherwise. Add them returning empty lists as the default stub behaviour.

`kotlinx-serialization-json` is confirmed as a transitive dependency (already used by `DuckDbProcessedArticleRepository` via `Json.encodeToString` for topics/entities/warnings). No new dependency needed.

```kotlin
// Add to ProcessedArticleRepository interface and DuckDbProcessedArticleRepository:

// Fetches articles matching the given IDs, filtered to outcome_status = SUCCESS only.
// FAILED articles have empty/stub fields that would render badly in the digest viewer.
// Returns empty list without DB query when ids is empty.
fun findByIds(ids: List<String>): List<ProcessedArticle>

// Filters on ingested_at column (not processed_at).
fun findByIngestedAtRange(start: Instant, end: Instant): List<ProcessedArticle>
```

Also add to `DuckDbProcessedArticleRepository` schema init:
```sql
CREATE INDEX IF NOT EXISTS idx_ingested_at ON processed_articles(ingested_at)
```
Consistent with the existing `idx_processed_at` index.

Note: the existing `findByDateRange` filters on `processed_at` and is **not changed**. Both methods coexist.

### 2c. ClusteringWorkflow changes

Constructor gains `ClusterRepository` parameter:

```kotlin
class ClusteringWorkflow(
    private val processedArticleRepository: ProcessedArticleRepository,
    private val llmClient: LLMClient,
    private val clusterRepository: ClusterRepository,
    private val clock: Clock = Clock.System,
)
```

Two changes to `execute()`:
1. Replace `findByDateRange(yesterday, now)` with `findByIngestedAtRange(yesterday, now)`
2. Replace `// TODO: Save clusters to database` with `clusterRepository.saveAll(clusters)`

### 2d. OutgressWorkflow changes

Gains nullable `clusterRepository` parameter (default `null`):

```kotlin
class OutgressWorkflow(
    private val processedArticleRepository: ProcessedArticleRepository,
    private val outputDir: Path,
    private val outgressDays: Int = 1,
    private val clock: Clock = Clock.System,
    private val clusterRepository: ClusterRepository? = null,
)
```

After the existing `.md` write, when `clusterRepository != null`, execute PATH B (see Data Flow). Both paths use the **same `clock.now()`** value captured at the top of `execute()`. PATH B calls `outputDir.createDirectories()` before writing the JSON file (same guard as PATH A — do not assume the directory pre-exists).

JSON serialization: define a `@Serializable` data class `DigestJson` in the `workflow` package, serialize with `kotlinx.serialization` `Json.encodeToString(digest)`. `Instant` fields serialize as ISO 8601 strings (standard `kotlinx-datetime` behaviour — same format used throughout the project).

`totalArticles` is computed as `(allClusterArticleIds + unclusteredArticles.map { it.id }).toSet().size` — a defensive distinct count even though the current clustering logic assigns each article to exactly one group.

### 2e. App.kt wiring

Both `runClustering` and `runOutgress` already open a single `DuckDbConnectionFactory.persistent(dbPath).use {}` block. Construct `DuckDbClusterRepository(connection)` inside that block and pass it to the workflow. No new connection needed.

`runOutgress` **always** passes a non-null `DuckDbClusterRepository` — PATH B (JSON generation) is unconditionally active once this change ships. No feature flag is needed; the JSON file is additive and does not remove the existing `.md` output.

```kotlin
// In runClustering:
DuckDbConnectionFactory.persistent(dbPath).use { connection ->
    val clusterRepository = DuckDbClusterRepository(connection)
    val processedRepo = DuckDbProcessedArticleRepository(connection)
    runBlocking { ClusteringWorkflow(processedRepo, createLLMClient(...), clusterRepository).execute() }
}

// In runOutgress:
DuckDbConnectionFactory.persistent(dbPath).use { connection ->
    val clusterRepository = DuckDbClusterRepository(connection)
    val processedRepo = DuckDbProcessedArticleRepository(connection)
    runBlocking { OutgressWorkflow(processedRepo, outputDir, outgressDays, clusterRepository = clusterRepository).execute() }
}
```

---

## Section 3: Viewer Changes

### 3a. serve.py — new endpoint

```python
import re
DATE_PATTERN = re.compile(r'^\d{4}-\d{2}-\d{2}$')

# In do_GET, add before the final else:
elif p.startswith("/api/daily/"):
    date_seg = p[len("/api/daily/"):]
    if not DATE_PATTERN.match(date_seg):
        self.send_bytes(400, "application/json", json.dumps({"error": "invalid date"}))
        return
    path = OUTPUT_DIR / f"daily-{date_seg}.json"
    # Path traversal guard (same pattern as /output/ handler):
    if not path.exists() or not str(path.resolve()).startswith(str(OUTPUT_DIR.resolve())):
        self.send_bytes(404, "application/json", json.dumps({"error": "not found"}))
        return
    self.send_bytes(200, "application/json", path.read_bytes())
```

### 3b. Frontend — cluster rendering

The existing Articles tab (`#article-view`) displays one date at a time. Replace `loadArticle(filename, btn)` with `loadDate(date, btn)` that tries JSON first, falls back to markdown.

Add a `renderClusters(data)` function that builds the cluster HTML into `#md` (reusing existing container):

```javascript
async function loadDate(date, btn) {
  document.querySelectorAll('.date-btn').forEach(b => b.classList.remove('active'));
  btn.classList.add('active');

  const jsonRes = await fetch('/api/daily/' + date);
  if (jsonRes.ok) {
    const data = await jsonRes.json();
    renderClusters(data);
    document.getElementById('meta').textContent = data.totalArticles + ' articles';
  } else {
    const md = await fetch('/output/jvm-daily-' + date + '.md').then(r => r.text());
    document.getElementById('md').innerHTML = marked.parse(md);
    const m = md.match(/Articles: (\d+)/);
    document.getElementById('meta').textContent = m ? m[1] + ' articles' : '';
  }
}

function renderClusters(data) {
  const clusters = [...data.clusters].sort((a, b) => b.engagementScore - a.engagementScore);
  let html = '';
  for (const cluster of clusters) {
    const sortedArticles = [...cluster.articles].sort((a, b) => b.engagementScore - a.engagementScore);
    html += `<div class="cluster">
      <h2 class="cluster-title">${esc(cluster.title)}
        <span class="cluster-count">${sortedArticles.length}</span></h2>
      <p class="cluster-synthesis">${esc(cluster.summary)}</p>
      <div class="article-list">`;
    for (const a of sortedArticles) {
      const chips = a.topics.map(t => `<span class="chip">${esc(t)}</span>`).join('');
      html += `<div class="article-row">
        <a class="article-title" href="${esc(a.url || '#')}" target="_blank"
           rel="noopener">${esc(a.title)} ↗</a>
        <div class="chips">${chips}</div>
        <p class="article-summary">${esc(a.summary)}</p>
      </div>`;
    }
    html += '</div></div>';
  }
  if (data.unclustered && data.unclustered.length > 0) {
    // render unclustered as a plain list under "Other" heading
    html += `<div class="cluster"><h2 class="cluster-title">Other
      <span class="cluster-count">${data.unclustered.length}</span></h2>
      <div class="article-list">`;
    for (const a of data.unclustered) {
      const chips = a.topics.map(t => `<span class="chip">${esc(t)}</span>`).join('');
      html += `<div class="article-row">
        <a class="article-title" href="${esc(a.url || '#')}" target="_blank"
           rel="noopener">${esc(a.title)} ↗</a>
        <div class="chips">${chips}</div>
        <p class="article-summary">${esc(a.summary)}</p>
      </div>`;
    }
    html += '</div></div>';
  }
  document.getElementById('md').innerHTML = html;
}

function esc(s) {
  return String(s || '').replace(/&/g,'&amp;').replace(/</g,'&lt;')
    .replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}
```

New CSS classes (append to existing `<style>`):

```css
.cluster { margin-bottom: 36px; }
.cluster-title { color: #e6edf3; font-size: 1.1rem; margin-bottom: 8px;
                 display: flex; align-items: center; gap: 8px; }
.cluster-count { font-size: 0.72rem; background: #21262d; color: #8b949e;
                 padding: 2px 7px; border-radius: 10px; font-weight: normal; }
.cluster-synthesis { color: #8b949e; line-height: 1.7; margin-bottom: 14px; font-size: 0.9rem; }
.article-list { display: flex; flex-direction: column; gap: 12px; }
.article-row { background: #161b22; border: 1px solid #30363d; border-radius: 8px;
               padding: 12px 16px; }
.article-title { color: #58a6ff; font-size: 0.95rem; text-decoration: none; font-weight: 600; }
.article-title:hover { text-decoration: underline; }
.chips { display: flex; gap: 6px; flex-wrap: wrap; margin: 6px 0; }
.chip { background: #1f3a5f; color: #58a6ff; font-size: 0.7rem;
        padding: 2px 8px; border-radius: 10px; }
.article-summary { color: #8b949e; font-size: 0.85rem; line-height: 1.5; margin: 0;
                   overflow: hidden; display: -webkit-box;
                   -webkit-line-clamp: 2; -webkit-box-orient: vertical; }
```

---

## Testing

| Test | Covers |
|------|--------|
| `DuckDbClusterRepository` — saveAll then findByDateRange returns clusters in range | Persistence + date filter |
| `DuckDbClusterRepository` — saveAll idempotency: saving same cluster twice does not duplicate (INSERT OR REPLACE) | Upsert correctness |
| `DuckDbClusterRepository` — findByDateRange with empty range returns empty list | Edge case |
| `DuckDbClusterRepository` — deleteByDateRange removes clusters in range, leaves others intact | `deleteByDateRange` correctness |
| `ProcessedArticleRepository.findByIds` — returns SUCCESS-only articles; FAILED article excluded; empty input returns empty without DB query | New method + status filter |
| `ProcessedArticleRepository.findByIngestedAtRange` — boundary: article at now-25h excluded, now-23h included | 24h filter on ingestedAt |
| `OutgressWorkflow` with clusterRepository — JSON file written with correct cluster structure and article objects | Happy path |
| `OutgressWorkflow` — unclustered articles (not in any cluster) appear in `unclustered` list | Unclustered path |
| `OutgressWorkflow` — empty clusters produces `"clusters":[], "unclustered":[all ingestedAt articles]` | No-cluster fallback |
| `OutgressWorkflow` without clusterRepository — only .md written, no JSON file created | Backward compat |
| `OutgressWorkflow` — `totalArticles` equals clusteredCount + unclusteredCount (unclustered is already disjoint from cluster IDs by construction; `toSet()` is a defensive guard) | Count correctness |
| `ClusteringWorkflow` — `saveAll` called with clusters produced by `clusterArticles`. Use anonymous `object : LLMClient` in the test returning `"TITLE: Test\nSYNTHESIS: body"`. Do NOT import `MockLLMClient` from `App.kt`. Capturing `ClusterRepository` stub records the argument passed to `saveAll`. | Wiring test |
