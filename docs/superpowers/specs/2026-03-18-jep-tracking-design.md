# JEP Tracking Source — Design Spec

**Date:** 2026-03-18
**Status:** Approved

## Goal

Track all changes to Java Enhancement Proposals (JEPs) — new JEPs, status transitions, target release assignments, title changes, and content updates — and surface them as articles in the daily JVM digest with LLM-generated context.

---

## Storage

New table `jep_snapshots` in the existing DuckDB instance:

```sql
CREATE TABLE IF NOT EXISTS jep_snapshots (
    jep_number     INTEGER PRIMARY KEY,
    title          VARCHAR NOT NULL,
    status         VARCHAR NOT NULL,
    target_release VARCHAR,          -- e.g. "JDK 26", null if unassigned
    updated_date   VARCHAR,          -- "YYYY/MM/DD" from individual JEP page
    summary        VARCHAR,          -- first paragraph of JEP description
    last_seen_at   VARCHAR NOT NULL  -- ISO-8601 timestamp of last check
)
```

Interface `JepSnapshotRepository`:
- `findAll(): List<JepSnapshot>`
- `upsert(jep: JepSnapshot)`

Follows the same pattern as all existing repositories (plain JDBC, `INSERT OR REPLACE`).

---

## Article model

`Article` has no `topics` field. The `"jep"` topic is guaranteed via two layers:

1. **Prompt hint** — `content` starts with `[JEP TRACKING]\ntopics: jep`. The enrichment LLM prompt is updated with: *"If the article content starts with `[JEP TRACKING]`, always include `jep` in topics."*

2. **Code enforcement** — in `EnrichmentWorkflow.enrichArticle()`, after `EnrichmentContract.parse()` returns the enriched article, if the original article content starts with `[JEP TRACKING]` and `"jep"` is absent from the returned topics, inject it:
   ```kotlin
   val topics = if (article.content.startsWith("[JEP TRACKING]") && "jep" !in result.topics)
       result.topics + "jep" else result.topics
   ```

No changes to `Article` data class or `ProcessedArticle`.

---

## Article identity

`CanonicalArticleId.from()` uses the URL as a global key when a URL is present — meaning
`https://openjdk.org/jeps/491` would produce the same id every run, silently overwriting
previous change events for the same JEP.

**Solution:** do not pass `url` to `CanonicalArticleId`. Instead pass `sourceNativeId`:

```kotlin
CanonicalArticleId.from(
    namespace  = "jep",
    sourceId   = "jep",
    title      = article.title,
    url        = null,                       // not used for ID
    sourceNativeId = "jep-$number-$updatedDate-$changeType",  // unique per event
)
```

The `url` field on `Article` is still set to `https://openjdk.org/jeps/{number}` for display.
This allows multiple change events for the same JEP across different runs.

`changeType` values: `new`, `status`, `content`, `title`, `release`.
If multiple fields change in one run: one combined article, `changeType = "multi"`.

---

## JepSource

Implements `Source`. `sourceType = "jep"`. `sourceId = "openjdk.org/jeps"`.

Constructor: `JepSource(repository: JepSnapshotRepository, config: JepConfig, clock: Clock)`.
This is the first source with a storage dependency; wired in `App.kt` alongside repository
initialization, following the same pattern as `ClusteringWorkflow(clusterRepository, ...)`.

### Fetch logic

**Step 1 — List page scrape**
Single HTTP GET `https://openjdk.org/jeps/`. Timeout: 10 s (consistent with other sources).
Parse HTML table: JEP number, title, status, target release.

**Step 2 — Individual page scrape (active JEPs only)**
For each JEP whose status is in `config.activeStatuses`:
HTTP GET `https://openjdk.org/jeps/{number}`. Timeout: 10 s per request.
Parse `Updated: YYYY/MM/DD` and first paragraph of summary.
Delay 200 ms between requests to avoid hammering openjdk.org.

Active statuses (configurable): `Draft`, `Candidate`, `Proposed to Target`, `Targeted`, `Integrated`.
Closed statuses (`Closed/Delivered`, `Closed/Withdrawn`, `Closed/Rejected`): list page data only.

Typically 30–80 active JEPs → ~30–80 individual fetches per day.

### Failure handling

- **List page fails** → `fetchOutcomes()` returns a single `FAILED` outcome with `sourceId = "openjdk.org/jeps"`, no snapshots updated.
- **Individual JEP page fails** → log warning, skip that JEP's content diff for this run (status/title diff from list page still applies). Never fail the whole source over a single JEP page. For `sourceNativeId`, substitute `updatedDate` with the last snapshot's `updated_date` if available, otherwise `"unknown"`.

Overrides `fetchOutcomes()` directly (does not use the default `fetch()` wrapper).

### Change detection

Compare fetched state against `JepSnapshotRepository.findAll()` (loaded once into a map by jep_number).

A change is detected when any of these differ from the snapshot:
- `status`
- `title`
- `target_release`
- `updated_date` (newer date = content change)

A JEP is new if absent from the snapshot map.

Multiple fields changed in one run → one combined article with `changeType = "multi"`,
title describes all changes: `"JEP 491: status Candidate→Targeted (JDK 26), content updated"`.

### Article generation

One `Article` per changed/new JEP:

```
sourceType     = "jep"
sourceId       = "openjdk.org/jeps"
url            = "https://openjdk.org/jeps/{number}"   (display only, not used for ID)
sourceNativeId = "jep-{number}-{updatedDate}-{changeType}"
title          = e.g. "JEP 491: Null-Restricted Value Class Types — Candidate → Targeted (JDK 26)"
content        = """
[JEP TRACKING]
topics: jep
change: {changeType}
old_status: {old} → new_status: {new}
old_release: {old} → new_release: {new}
old_updated: {old} → new_updated: {new}
summary: {current JEP summary paragraph}
"""
```

### Snapshot write ordering

Snapshots are written **after** articles are emitted (update-after-emit).
Rationale: on crash between emit and upsert, the next run re-detects the same changes and
emits duplicate articles — acceptable. The alternative (update-before-emit) risks silent loss.

### Initial seed handling

Config flag `initialSeed: false` (default).
When `true` and snapshot table is **empty**: populate `jep_snapshots` from current state, emit **no articles**.
When `true` and snapshot table is **already non-empty**: log warning
`"[jep] initialSeed=true ignored — snapshot table already has {n} rows, running change detection normally"`,
then run normal change-detection and emit articles as usual (the seed already happened).

---

## Configuration (`sources.yml`)

```yaml
jep:
  enabled: true
  initialSeed: false
  activeStatuses:
    - Draft
    - Candidate
    - "Proposed to Target"
    - Targeted
    - Integrated
```

Kotlin config class (added to `SourcesConfig.kt`):

```kotlin
data class JepConfig(
    val enabled: Boolean = false,
    val initialSeed: Boolean = false,
    val activeStatuses: List<String> = listOf(
        "Draft", "Candidate", "Proposed to Target", "Targeted", "Integrated"
    ),
)

// In SourcesConfig:
data class SourcesConfig(
    // ... existing fields ...
    val jep: JepConfig? = null,
)
```

---

## Integration

No changes to the existing pipeline. `JepSource` registers in `SourceRegistry` like any other source. Articles flow through:

```
JepSource → IngressWorkflow → EnrichmentWorkflow (LLM + "jep" topic) → ClusteringWorkflow → OutgressWorkflow
```

`JepSnapshotRepository` uses the existing DuckDB connection, table initialized in `init {}`.

`EnrichmentWorkflow` prompt updated with one line to preserve `jep` topic for `[JEP TRACKING]` articles.

---

## Testing

- `JepSnapshotRepositoryTest` — upsert, findAll, idempotency
- `JepSourceTest` — unit tests with stubbed HTTP responses and stubbed repository:
  - New JEP → article emitted, snapshot upserted after
  - Status change → article with `changeType=status`
  - `updated_date` change → article with `changeType=content`
  - Multiple fields changed → one combined article, `changeType=multi`
  - No change → no article emitted
  - `initialSeed=true`, empty table → no articles, snapshot populated
  - `initialSeed=true`, non-empty table → warning logged, normal processing
  - Individual JEP page fetch failure → source does not fail, status diff still works
  - List page fetch failure → `FAILED` outcome returned
