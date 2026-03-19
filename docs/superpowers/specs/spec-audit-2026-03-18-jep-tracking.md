# Spec Audit: JEP Tracking Source
**Spec:** `2026-03-18-jep-tracking-design.md`
**Audit date:** 2026-03-18
**Auditor:** spec-auditor agent

---

## Summary

The spec is mostly implementable as written and follows project conventions
correctly. However, six issues need resolution before implementation begins —
two are blockers, the rest are clarifications that will affect code written on
the first pass.

**Compliance status: Non-Compliant (blocked by two Critical issues)**

---

## Critical Issues

### C1 — `Article` model has no `topics` field — spec claims source sets it

**Spec reference (Step "Article generation"):**
> `topics = ["jep"]` — always set by the source, not left to LLM

**Evidence:**
`app/src/main/kotlin/jvm/daily/model/Article.kt` — the `Article` data class
(lines 5–15) has no `topics` field whatsoever:

```
data class Article(
    val id: String,
    val title: String,
    val content: String,
    val sourceType: String,
    val sourceId: String,
    val url: String? = null,
    val author: String? = null,
    val comments: String? = null,
    val ingestedAt: Instant,
)
```

`topics` exist only on `ProcessedArticle` (produced by `EnrichmentWorkflow`),
not on the raw `Article`. The statement "topics pre-populated by source,
enrichment adds more" in the review prompt does not match the actual model:
`ProcessedArticle.topics` is written once by `EnrichmentContract.parse()`
(EnrichmentWorkflow.kt line 143) and is not merged with any source-provided
list.

**Impact:** The spec requires a field that does not exist. Three outcomes are
possible — each changes implementation significantly:

- Option A: add `topics: List<String> = emptyList()` to `Article`, and merge
  it into `ProcessedArticle.topics` inside `EnrichmentWorkflow`. This is the
  largest change and affects every source and the enrichment contract test
  suite.
- Option B: store the `"jep"` topic seed in the article `content` or a
  dedicated convention field, and rely on the LLM prompt or
  `EnrichmentContract` post-processing to preserve it. Requires a prompt
  change and a new contract rule.
- Option C: do nothing in the source; the LLM will reliably produce a `"jep"`
  topic from the article content anyway. The spec sentence is aspirational
  commentary, not a code requirement.

**Which option is intended?** This must be decided before writing
`JepSource.fetch()`.

**Severity: Critical** — the spec asserts a code contract (`topics` is set by
source) against a model that has no such field.

---

### C2 — No `Article.id` construction specified for JEP articles

**Spec reference (Section "Article generation"):**
> `url = "https://openjdk.org/jeps/{number}"`

The spec lists every Article field except `id` and `sourceId`.

**Evidence:**
`CanonicalArticleId.from()` (CanonicalArticleId.kt lines 6–18) selects the
canonical id by URL when a URL is provided:

```
normalizeUrl(url)?.let { return it }
```

The URL `https://openjdk.org/jeps/491` normalizes to
`https://openjdk.org/jeps/491` (fragment stripped, trailing slash trimmed)
which then produces a stable canonical id.

**However, this produces the same `id` for every article about JEP 491 — a
status change today and a content update tomorrow would collide**, because
both use the same URL. The `INSERT OR REPLACE` upsert in
`DuckDbArticleRepository.save()` would silently overwrite the earlier article
rather than creating two separate entries in the digest.

This is a fundamental design question for the spec: should repeated changes to
the same JEP within a short window produce multiple articles (one per change
event), or should the latest state replace the previous one? If the former, a
different `id` strategy is needed (e.g. including a timestamp or change-type
discriminator in the id). If the latter, that behavior needs to be documented
explicitly.

**Severity: Critical** — silent data loss (overwrite) or duplicate id
collisions depending on intent. Neither path is specified.

---

## High Issues

### H1 — `sourceId` not specified for JEP articles

**Spec reference (Section "Article generation"):**
The field list in the Article generation section does not include `sourceId`,
but `Article.sourceId` is a required non-null field (Article.kt line 10) and
the Source contract test asserts it is non-blank
(SourceContractTest.kt line 41).

**Evidence:** Every existing source sets `sourceId` to a meaningful value
(feed URL for RSS, list name for mail, `{handle}/{rkey}` for Bluesky). For
`JepSource`, what should `sourceId` be? Candidates: the JEP number as a
string, `"openjdk.org/jeps"` (the list page), or the full JEP URL. The choice
affects the `FeedIngestResult.sourceId` reported in ingest run logs (which the
spec does not mention at all — see H3).

**Severity: High** — required field with no specified value; implementer will
guess.

---

### H2 — Rate limiting / sequencing of 30–80 individual HTTP requests is unspecified

**Spec reference (Section "Step 2 — Individual page scrape"):**
> Typically 30–80 JEPs → 30–80 extra HTTP requests per day.

**Evidence:**
Every existing source that makes multiple requests (`RssSource`,
`OpenJdkMailSource`, `BlueskySource`) makes them sequentially with no delay.
The spec does not state whether individual JEP page fetches should be
sequential or concurrent, or whether any delay or timeout should apply between
requests. The openjdk.org servers are run by volunteers; 80 rapid-fire
requests could trigger rate limiting or a soft ban.

There is also no timeout value specified for individual JEP page fetches, while
other sources hardcode `connectTimeout=15_000` / `readTimeout=15_000` (or
`30_000` for mail). The spec says to use `HttpURLConnection` (consistent with
other sources) but omits all of these operational parameters.

**Severity: High** — a production incident risk, and the absence of spec
guidance means the implementation will make an arbitrary choice.

---

### H3 — `fetchOutcomes()` / `FeedIngestResult` contract not addressed

**Spec reference (Section "Testing"):**
The spec lists `JepSnapshotRepositoryTest` and `JepSourceTest` but does not
mention `fetchOutcomes()` behaviour.

**Evidence:**
`Source.fetchOutcomes()` (Source.kt lines 33–64) is used by `IngressWorkflow`
for reliability reporting. The default implementation wraps `fetch()` as a
single logical feed. For `JepSource` this is probably correct, but the spec
does not say whether `JepSource` should override `fetchOutcomes()` (like
`RssSource` and `BlueskySource` do to report per-feed outcomes) or rely on
the default.

More concretely: if the list-page fetch fails, should the source throw (which
the default wrapper catches as FAILED), or return an empty list silently?
If an individual JEP page fetch fails, should the JEP be skipped with a
warning, or should the whole source fail? The spec says nothing about
partial-fetch error handling.

The connector dry-run contract test (`ConnectorDryRunContractTest.kt`) and the
reliability tests will run against `JepSource` if it is registered; the spec
does not mention making those pass.

**Severity: High** — partial failure semantics are unspecified; divergent
implementations will behave inconsistently with the rest of the pipeline.

---

## Medium Issues

### M1 — "Completed / Withdrawn JEPs" set is not defined

**Spec reference (Section "Step 2"):**
> Completed / Withdrawn JEPs: only list page data tracked (no individual fetch).

**Evidence:**
The spec defines `activeStatuses` (the inclusion list for individual fetches)
but does not define what "Completed" or "Withdrawn" statuses are called on the
openjdk.org JEP list page. The actual status strings on the page include
"Closed/Delivered", "Closed/Withdrawn", "Closed/Rejected" — none of which are
labelled simply "Completed" or "Withdrawn".

The implementation of "fetch individual page only if status is in
`activeStatuses`" is straightforward (anything NOT in `activeStatuses` skips
the individual fetch), but the spec's prose description uses vocabulary that
does not match the real page. A developer reading the spec without checking the
site might misunderstand the model.

**Severity: Medium** — spec language does not match the real data source;
could cause confusion during implementation or code review, but the
`activeStatuses` whitelist approach makes it implementable without ambiguity.

---

### M2 — Snapshot update after article emission is not specified

**Spec reference (Section "Change detection"):**
> A JEP is considered changed if any of these differ from the snapshot...

**Evidence:**
The spec describes detecting changes by comparing current state against the
snapshot. It does not specify *when* `JepSnapshotRepository.upsert()` should
be called relative to article emission — before or after `fetch()` returns?

This matters because:
- If the snapshot is updated before articles are emitted and the pipeline
  crashes mid-run, the change is lost forever (snapshot updated, article never
  stored).
- If the snapshot is updated after articles are emitted, a re-run produces
  duplicate articles until the snapshot catches up.

The pattern used for the `initialSeed` case (populate snapshot, emit nothing)
implies snapshot writes happen inside `fetch()`, but the ordering relative to
article list construction is never stated.

**Severity: Medium** — ordering determines crash-recovery behavior; the
implementer will pick arbitrarily.

---

### M3 — `JepSnapshotRepository` connection lifecycle not fully addressed

**Spec reference (Section "Integration"):**
> `JepSnapshotRepository` uses the existing DuckDB connection, table
> initialized in `init {}`.

**Evidence:**
`DuckDbArticleRepository`, `DuckDbClusterRepository`, and
`DuckDbProcessedArticleRepository` all receive a `Connection` as a constructor
parameter. The spec says to follow the same pattern. In `runIngress()` (App.kt
line 124), the `DuckDbConnectionFactory.persistent(dbPath)` connection is used
and then `DuckDbArticleRepository` is the only repository constructed from it.

The spec does not say where `DuckDbJepSnapshotRepository` is constructed in
`App.kt` / `runIngress()`, nor whether `JepSource` receives the repository as
a constructor parameter (the clean testable approach) or constructs it
internally (which would make unit testing harder and contradict how all other
sources are wired). All existing sources receive their config via constructor
and have no storage dependency; `JepSource` would be the first source that
directly depends on a repository.

**Severity: Medium** — the wiring pattern is ambiguous and affects
testability. The spec implies clean injection but never states it.

---

## Low Issues

### L1 — `initialSeed` reset is a manual operator step with no guard

**Spec reference (Section "Initial seed handling"):**
> Operator sets `initialSeed: true` for first run, then removes it.

**Evidence:**
There is no mention of what happens if an operator forgets to remove
`initialSeed: true`. Subsequent runs would silently update snapshots without
emitting articles, leading to missed JEP changes with no error or warning. A
simple guard (log a prominent warning if `initialSeed: true` and
`jep_snapshots` is already populated) would prevent silent data loss but is
not specified.

**Severity: Low** — operational concern; survivable with good documentation
but worth adding a warning log.

---

### L2 — Title format examples are inconsistent in completeness

**Spec reference (Section "Article generation"):**
> `"JEP 491: Null-Restricted Value Class Types — status: Candidate → Targeted (JDK 26)"`
> `"New JEP 502: [title] — status: Draft"`
> `"JEP 480: content updated (Updated: 2026/03/18)"`

The first example includes both old and new status and a target release. The
third example includes only the updated date. But the spec does not cover
*combined* changes: what is the title format when a JEP has a simultaneous
status change AND an `updated_date` change AND a `target_release` change?
Should that produce one article with a combined title, or multiple articles
(one per change dimension)?

**Severity: Low** — edge case; the most natural reading is one article per
changed JEP with a combined title. Worth confirming.

---

## Clarification Questions

1. **`topics` on `Article`** (blocker for C1): Should `topics: List<String>`
   be added to the `Article` model, with `EnrichmentWorkflow` merging
   source-supplied topics with LLM-generated ones? Or is the intent that the
   LLM will reliably produce `"jep"` and the spec line is non-normative?

2. **Article `id` stability for repeated JEP changes** (blocker for C2):
   Should two consecutive changes to JEP 491 (e.g., status change today,
   content update tomorrow) produce two separate `Article` records in the DB,
   or should the latest replace the earlier? If separate, what discriminator
   makes their ids distinct?

3. **`sourceId` value**: What should `Article.sourceId` be for JEP articles
   — the JEP number (e.g. `"491"`), the list URL, or something else?

4. **Individual page fetch failures**: If fetching
   `https://openjdk.org/jeps/491` fails, should the whole `JepSource.fetch()`
   fail, or should that JEP be skipped and the remaining JEPs processed?

5. **Snapshot write timing**: Should the snapshot upsert happen before or
   after the article is appended to the result list? (Affects crash-recovery
   semantics.)

6. **Combined title format**: If a JEP changes status AND has a new
   `updated_date` in the same run, what does the article title look like?

---

## Extra / Out-of-Scope Observations (not findings)

- The spec correctly uses `HttpURLConnection` (consistent with `RssSource` and
  `OpenJdkMailSource`), not Ktor Client (which is listed in the tech stack but
  not used by any source in practice).
- The `INSERT OR REPLACE` directive for `jep_snapshots` is correct and
  consistent with the project's DuckDB pattern.
- The test coverage list in Section "Testing" is good but misses
  `fetchOutcomes()` success and failure cases, which the connector
  certification test (`ConnectorDryRunContractTest`) will exercise at runtime.
- The spec correctly omits any mention of `topics` on `ProcessedArticle` being
  seeded from the raw article — but then contradicts itself by asserting
  `topics = ["jep"]` at the `Article` level. These two statements cannot both
  be true without a model change (see C1).
