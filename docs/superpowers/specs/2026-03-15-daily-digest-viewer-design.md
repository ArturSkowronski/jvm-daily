# Design: Daily Digest Viewer (Latent Space Style)

**Date:** 2026-03-15
**Status:** Approved

## Overview

Enhance JVM Daily with a Latent Space AI News-style morning digest viewer. Each day, the pipeline produces a `daily-YYYY-MM-DD.json` file grouping articles into thematic clusters. The viewer renders these clusters with synthesis summaries and article links, filtered to the last 24 hours of ingested content.

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
ClusteringWorkflow
  → groups articles by topic similarity
  → LLM generates cluster title + synthesis
  → saves ArticleCluster to DuckDB (ClusterRepository) ← NEW

OutgressWorkflow
  → reads clusters from ClusterRepository ← NEW
  → joins cluster.articles (IDs) with full ProcessedArticle objects
  → filters to ingestedAt >= now - 24h
  → writes daily-YYYY-MM-DD.json ← NEW
  → writes daily-YYYY-MM-DD.md (unchanged)

serve.py
  → /api/daily/:date → reads daily-YYYY-MM-DD.json ← NEW
  → /api/files       → lists available dates (unchanged)
  → frontend renders clusters in Latent Space style ← NEW
```

---

## Section 1: JSON Output Format

File: `output/daily-YYYY-MM-DD.json`

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
      "synthesis": "Cross-source synthesis paragraph (150-200 words)...",
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
  "unclustered": []
}
```

**24h filter:** `ingestedAt >= now - 24h` (pipeline ingestion time, not author publish time — more predictable for morning digest scheduling).

**Unclustered:** articles that belong to no cluster (< 3 per topic group) are collected here with a generic "Miscellaneous" label.

---

## Section 2: Kotlin Pipeline Changes

### 2a. ClusterRepository (new)

New interface `ClusterRepository` with `DuckDbClusterRepository` implementation:

```
interface ClusterRepository {
    fun save(cluster: ArticleCluster)
    fun saveAll(clusters: List<ArticleCluster>)
    fun findByDateRange(start: Instant, end: Instant): List<ArticleCluster>
    fun deleteByDateRange(start: Instant, end: Instant)
}
```

DuckDB table `article_clusters`:
```sql
CREATE TABLE IF NOT EXISTS article_clusters (
    id VARCHAR PRIMARY KEY,
    title VARCHAR NOT NULL,
    summary VARCHAR NOT NULL,
    article_ids VARCHAR NOT NULL,  -- JSON array
    sources VARCHAR NOT NULL,       -- JSON array
    total_engagement DOUBLE NOT NULL,
    created_at VARCHAR NOT NULL
)
```

### 2b. ClusteringWorkflow fix

Replace `// TODO: Save clusters to database (future PR)` with:
```kotlin
clusterRepository.saveAll(clusters)
```

ClusteringWorkflow constructor gains `ClusterRepository` parameter.

### 2c. OutgressWorkflow additions

`OutgressWorkflow` gains a `ClusterRepository?` parameter (nullable for backward compatibility).

When `clusterRepository != null`:
1. Load clusters for today via `findByDateRange(now - 24h, now)`
2. Resolve article IDs to full `ProcessedArticle` objects via `ProcessedArticleRepository`
3. Apply `ingestedAt >= now - 24h` filter
4. Serialize to `daily-YYYY-MM-DD.json` via `kotlinx.serialization`

When `clusterRepository == null`: write `.md` only (existing behavior preserved).

App.kt wires up `ClusterRepository` to both workflows.

---

## Section 3: Viewer Changes

### 3a. serve.py — new endpoint

```
GET /api/daily/<date>  →  reads output/daily-YYYY-MM-DD.json, returns JSON
```

Falls back gracefully if JSON not found (404), so existing `.md`-only dates continue to work.

### 3b. Frontend layout

```
┌─────────────────────────────────────────────────────┐
│ ☕ JVM Daily    Articles  Pipeline        2026-03-15 │
├────────┬────────────────────────────────────────────┤
│ Dates  │  Spring Boot 4.0 RC1 Sparks Migration ...  │
│        │  ──────────────────────────────────────    │
│ Mar 15 │  Two paragraphs of cross-source synthesis. │
│ Mar 14 │  Highlights community sentiment...         │
│ Mar 13 │                                            │
│        │  · Spring Boot 4.0 RC1 is here ↗          │
│        │    [framework-releases] [migration]        │
│        │    LLM summary truncated to 2 lines...     │
│        │                                            │
│        │  · Migrating from Boot 3.x: a guide ↗     │
│        │    [migration] [spring]                    │
│        │    LLM summary...                          │
│        │                                            │
│        │  ─────────────────────────────────────     │
│        │  Virtual Threads Production Benchmarks     │
│        │  ...                                       │
└────────┴────────────────────────────────────────────┘
```

**Cluster section:**
- `<h2>` cluster title + article count badge
- Synthesis paragraph (full text, dark muted color)
- Divider
- Article list

**Article row:**
- Title as `<a href>` link (opens in new tab)
- Topic chips (small colored badges)
- Summary: 2-line clamp, no expand (keep it scannable)

**Fallback:** if `/api/daily/:date` returns 404, fall back to existing markdown rendering via `/output/jvm-daily-:date.md`

**Sort:** clusters by `engagementScore` descending; articles within cluster by `engagementScore` descending.

---

## Testing

- Unit test: `DuckDbClusterRepository` save/findByDateRange
- Unit test: `OutgressWorkflow` JSON output with mock cluster + articles
- Unit test: 24h filter boundary — article at `now - 25h` excluded, `now - 23h` included
- Unit test: `OutgressWorkflow` without ClusterRepository → only `.md` written (backward compat)
