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

## JepSource

Implements `Source`. `sourceType = "jep"`.

### Fetch logic

**Step 1 — List page scrape**
Fetch `https://openjdk.org/jeps/` (single HTTP GET, stdlib `HttpURLConnection`).
Parse HTML table: JEP number, title, status, target release.

**Step 2 — Individual page scrape (active JEPs only)**
For each JEP whose status is in `activeStatuses` (configurable: `Draft`, `Candidate`, `Proposed to Target`, `Targeted`, `Integrated`):
Fetch `https://openjdk.org/jeps/{number}`, parse `Updated: YYYY/MM/DD` and first paragraph of summary.
Typically 30–80 JEPs → 30–80 extra HTTP requests per day.

Completed / Withdrawn JEPs: only list page data tracked (no individual fetch).

### Change detection

A JEP is considered changed if any of these differ from the snapshot:
- `status`
- `title`
- `target_release`
- `updated_date` (newer date = content change)

A JEP is considered new if absent from `jep_snapshots`.

### Article generation

One `Article` per changed/new JEP:
- `sourceType = "jep"`
- `url = "https://openjdk.org/jeps/{number}"`
- `title` = change description, e.g.:
  - `"JEP 491: Null-Restricted Value Class Types — status: Candidate → Targeted (JDK 26)"`
  - `"New JEP 502: [title] — status: Draft"`
  - `"JEP 480: content updated (Updated: 2026/03/18)"`
- `content` = structured diff (old values → new values) + current JEP summary — used as input to LLM enrichment
- `topics = ["jep"]` — always set by the source, not left to LLM

### Initial seed handling

Config flag `initialSeed: true` (default `false`).
When `true`: populate `jep_snapshots` from current state but emit **no articles**.
Purpose: prevent ~50–80 "new JEP" articles flooding the first digest after deployment.
Operator sets `initialSeed: true` for first run, then removes it.

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

---

## Integration

No changes to the existing pipeline. `JepSource` registers in `SourceRegistry` like any other source. Articles flow through:

```
JepSource → IngressWorkflow → EnrichmentWorkflow (LLM context) → ClusteringWorkflow → OutgressWorkflow
```

JEP articles will naturally cluster with related JDK/OpenJDK news in the digest.

`JepSnapshotRepository` uses the existing DuckDB connection, table initialized in `init {}`.

---

## Testing

- `JepSnapshotRepositoryTest` — upsert, findAll, idempotency
- `JepSourceTest` — unit tests with stubbed HTTP responses:
  - New JEP emits article
  - Status change emits article
  - `updated_date` change emits article
  - No change → no article
  - `initialSeed=true` → no articles emitted, snapshot populated
  - Completed JEPs not individually fetched
