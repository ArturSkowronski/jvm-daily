# Findings

## 2026-02-09 — Initial Setup

- Starting with plugin engine architecture for source ingestion
- DuckDB + plain JDBC as storage layer (Exposed doesn't support DuckDB — no built-in dialect)
- Koog Agents for AI workflow orchestration

## 2026-02-09 — DuckDB Timestamp Gotcha

- DuckDB's `TIMESTAMP` column type converts ISO 8601 strings (e.g. `2026-02-09T10:00:00Z`) into its own format (`2026-02-09 10:00:00`), stripping the `T` and `Z`
- `kotlinx.datetime.Instant.parse()` then fails on read because the format doesn't match ISO 8601
- **Fix:** Use `VARCHAR` column for `ingested_at` to preserve the ISO 8601 string as-is
- Alternative: use `java.sql.Timestamp` for proper JDBC timestamp handling, but VARCHAR is simpler for now

## 2026-02-09 — DuckDB JDBC

- Maven coordinate: `org.duckdb:duckdb_jdbc:1.1.3`
- In-memory: `jdbc:duckdb:` — great for tests
- Persistent: `jdbc:duckdb:path/to/file.duckdb`
- `INSERT OR REPLACE` works for upserts (SQLite-style syntax supported)

## 2026-02-09 — RSS Source Implementation

- **Rome** (`com.rometools:rome:2.1.0`) is the best RSS/Atom parser for JVM — handles both formats, extracts title, author, description, content, comments
- **kaml** (`com.charleskorn.kaml:kaml:0.67.0`) works well with kotlinx.serialization for YAML config parsing
- Rome's `XmlReader(URL)` constructor is deprecated — use `XmlReader(InputStream)` instead via `URL.openStream()`
- RSS entries without `<link>` or `<title>` are skipped (mapNotNull) — defensive parsing
- Test RSS feeds with local file:// URIs works perfectly for unit tests without network calls

## 2026-02-10 — Processing Pipeline Architecture

-**Multi-stage pipeline inspired by Latent Space AI News:**
  1. **Enrichment**: Raw articles → ProcessedArticles with LLM summaries, NER entities, topic tags
  2. **Clustering**: ProcessedArticles → ArticleClusters (cross-source thematic grouping, max 8/day)
  3. **Compilation**: ArticleClusters → NewsletterIssue (final markdown)

- **ProcessedArticle model** includes:
  - `normalizedTitle` for deduplication (lowercase, alphanumeric only)
  - `summary` (LLM-generated, 100-150 words)
  - `entities` (JDK versions, frameworks, companies, JEPs)
  - `topics` for clustering (language-updates, framework-releases, performance, etc.)
  - `engagementScore` (0-100) for prioritization

- **kotlinx.datetime Duration API:**
  - Use `Duration.Companion.days` instead of `DateTimeUnit.DAY.times()`
  - `clock.now().minus(7.days)` works correctly
  - Import: `kotlin.time.Duration.Companion.days`

- **LLMClient abstraction** simplifies AI integration - wraps Koog Agents or direct API calls
- Response parsing uses simple text format: `SUMMARY: ...\nENTITIES: ...\nTOPICS: ...`

## 2026-02-10 — Airflow 3 Integration

- **Apache Airflow for workflow orchestration:**
  - DAG: `jvm_daily_pipeline` runs daily at 7am UTC
  - Tasks: ingress → check_new_articles → [enrichment → clustering → compilation]
  - Conditional branching: skips processing if no new articles
  - Task groups for logical organization

- **Command-line workflow execution:**
  - `./gradlew run --args="ingress"` - collect articles
  - `./gradlew run --args="enrichment"` - LLM processing
  - `./gradlew run --args="clustering"` - thematic grouping
  - Each workflow is a separate Gradle task for Airflow

- **Environment-based configuration:**
  - `DUCKDB_PATH` - database file location
  - `LLM_PROVIDER` - mock/openai/anthropic/koog
  - `LLM_API_KEY` - API credentials
  - `LLM_MODEL` - model selection
  - Airflow Variables for sensitive data (API keys)

- **Docker Compose setup:**
  - LocalExecutor for simple deployment
  - PostgreSQL for Airflow metadata
  - Project mounted at `/jvm-daily` for Gradle access
  - Web UI at http://localhost:8080

- **Production considerations:**
  - Retry logic: 2 retries, 5min delay
  - Timeouts: 30min enrichment, 20min clustering
  - DuckDB file-level locking requires sequential task execution
  - Consider CeleryExecutor for parallel processing

## 2026-02-27 — Architecture Guardrails

- Added explicit source-adapter contract documentation in `Source`.
- Added registry guardrails: reject blank/duplicate `sourceType` at registration.
- Added architecture dependency tests in `app/src/test/kotlin/jvm/daily/architecture/` to enforce:
  - workflow does not import concrete source/storage implementations
  - source does not depend on workflow
  - storage does not depend on workflow or concrete source implementations
- Boundary checks run as part of default `./gradlew test`.

## 2026-02-27 — RSS Ingest Reliability

- Added explicit ingest outcome models:
  - `IngestRunStatus`: `SUCCESS`, `SUCCESS_WITH_WARNINGS`, `FAIL`
  - `FeedIngestResult`: per-feed status/count/error payload
- `Source` now supports `fetchOutcomes()` for feed-level reliability reporting while preserving legacy `fetch()`.
- `RssSource` now:
  - retries failed feed fetch attempts (bounded)
  - reports partial success when malformed entries are skipped
  - isolates failures per feed instead of failing whole RSS batch
- `IngressWorkflow` now:
  - aggregates per-feed results
  - classifies run status with explicit rules
  - emits per-feed summary table in logs
- Added reliability-focused tests:
  - `RssSourceReliabilityTest`
  - `IngressReliabilityTest`

## 2026-02-27 — Persistence and Idempotency (Phase 3)

- Added shared `CanonicalArticleId` utility to centralize deterministic ID generation.
- Migrated RSS and Markdown sources to use the same canonical ID derivation strategy.
- Added idempotency-focused tests for:
  - repository cardinality stability (`DuckDbArticleRepositoryIdempotencyTest`)
  - workflow rerun behavior (`IngressWorkflowIdempotencyTest`)
- Added `validate-raw-ids` CLI path and `ValidateRawArticleIds` tool with:
  - default dry-run mismatch/collision reporting
  - explicit `--apply` update mode
  - collision-safe behavior (no overwrite on conflicting target IDs)

## 2026-02-27 — Summarization Core (Phase 4)

- Enrichment parser migrated from tag-based text parsing to strict JSON contract validation.
- Added explicit enrichment outcome metadata in `ProcessedArticle`:
  - `outcomeStatus` (`SUCCESS` / `FAILED`)
  - `failureReason`
  - `lastAttemptAt`
  - `attemptCount`
  - `warnings`
- Enrichment workflow now persists failed outcomes instead of logging-and-dropping errors.
- Retry policy is explicit for transport/provider failures (max 3 attempts, fixed backoff).
- Added dedicated contract, reliability, and repository round-trip tests for summarization semantics.

## 2026-02-27 — Recoverability Controls (Phase 5)

- Added replay selectors to processed repository:
  - `findFailedRawArticleIds(since, limit)` for deterministic candidate preview.
  - `findFailedByIds(ids)` for targeted failed-item lookup in input order.
- `EnrichmentWorkflow` now supports targeted replay via explicit `replayRawArticleIds`.
- New CLI command: `enrichment-replay` with safe selector constraints:
  - either `--ids` or `--since-hours/--limit`
  - optional `--dry-run` preview mode before mutation.
- Recovery verification pattern now test-backed:
  1. create failures
  2. preview candidates
  3. replay subset
  4. verify success/failure counts post-replay.
- Practical gotcha: replay can only process IDs that still exist in raw article storage; missing IDs are skipped and logged.

## 2026-02-27 — Daily Automation and Telemetry (Phase 6)

- Scheduler contract aligned across JobRunr and Airflow:
  - both use `PIPELINE_CRON`
  - shared default cron is `0 7 * * *` (07:00 UTC).
- Pipeline now emits structured telemetry per stage with:
  - `run_id`, `stage`, `status`, `started_at`, `ended_at`, `duration_ms`, optional `error`.
- Failure visibility improved: failed stage telemetry is emitted before exception is rethrown (fail-fast behavior preserved).
- Added smoke-check guidance for:
  - local `pipeline` command telemetry inspection
  - Airflow trigger + logs verification path.

## 2026-02-27 — Quality Gates (Phase 7)

- Dedup quality gating is now anchored in canonical ID and cardinality stability tests, not manual validation.
- `quality-report` command emits daily counters for:
  - new items
  - duplicates
  - feed failures
  - summarization failures
- Feed failure/duplicate counters are derived from persisted `ingest_feed_runs` snapshots in DuckDB.
- Threshold evaluation (`max-duplicates`, `max-feed-failures`, `max-summarization-failures`) can optionally fail runs via `--fail-on-threshold`.
- Practical caveat: canonical URL normalization currently strips query punctuation characters (`?`, `=`), which is now explicitly regression-tested.

## 2026-03-17 — launchd Service / JOBRUNR_STORE absolute path gotcha

- `App.kt:startDaemon` builds the H2 JDBC URL as `"jdbc:h2:file:./$storePath"` (with `./` prefix).
- When `JOBRUNR_STORE` is an **absolute path** (e.g. `/Users/foo/.jvm-daily/jobrunr`), the URL becomes `jdbc:h2:file:.//Users/foo/.jvm-daily/jobrunr`.
- On POSIX systems `.//abs` resolves to `/abs`, so H2 should handle this correctly, but it is untested. If the daemon fails to start with H2 errors, the fix is to remove the `./` prefix in `App.kt` or change the URL to use `Path.of(storePath).toAbsolutePath()`.
- The `launchd` plist sets `JOBRUNR_STORE` to `$HOME/.jvm-daily/jobrunr` (absolute), so this path is exercised when using the local service.

## June 2–8 2026 digest gap — root cause & recovery (investigated 2026-06-16)

**Symptom:** Viewer on Fly had no digests for 2026-06-03..06-08 (last good `daily-2026-06-02.json`, next `daily-2026-06-09.json`).

**Initial wrong hypothesis:** OOM / dead daemon + catch-up not backfilling. Indirect signals (06:22 catch-up timestamp, `/proc/1` restart) pointed here but were misleading. Lesson: get direct evidence (DB) before concluding.

**Actual root cause:** External **LLM billing block**. The pipeline (ingress→enrichment→clustering→outgress) ran every day; ingest worked fine (`ingest_feed_runs` shows 107 feeds/day, articles ingested 06-02..06-08). But **every enrichment LLM call returned HTTP 403**:
`TRANSPORT: LLM API error 403: "Lightning dunning decision is deny for project: projects/381536636004", PERMISSION_DENIED` — a Google Cloud (Vertex/Gemini) dunning/unpaid-invoice block. All 190 processed_articles in the window are `outcome_status=FAILED`, `summary='[FAILED]'`. Zero successful summaries → no clusters created (clusters jump 06-01→06-10) → outgress wrote nothing. Resolved ~06-09 when billing cleared.

**Recoverability:**
- Raw `articles` for 06-02..06-08 are intact in the Fly DuckDB (full title+content+url, 190 rows: bluesky 118 / rss 46 / github_releases 16 / openjdk_mail 9 / trending 1). Exported to `recovery/articles-2026-06-*.json`.
- AI layer (summaries/clusters/digests) was never produced and is NOT in git, snapshots (5-day retention, oldest only ~06-12), or any local copy.
- Fly volume snapshots have 5-day retention → June 3-8 snapshots already expired; they hold the same raw DB anyway.

**Regeneration path (built-in tooling):** `enrichment-replay` re-runs only FAILED items (`App.kt:271`, selector by `--ids` or `--since-hours`). All 190 FAILED items are exactly this window (no other FAILED in DB), so it's cleanly targetable. Then `clustering` + `outgress` regenerate the digests. Requires LLM spend (~190 calls) and DuckDB is single-writer (daemon must release the prod DB).

**Prevention ideas:** billing/credit alert on the LLM project; alert when a day has 0 SUCCESS enrichments or no digest file; make outgress/quality-gate surface a hard failure instead of silently writing nothing.

### Recovery executed (2026-06-16) — outcome

1. **Raw articles** (190, ingested 06-02..08) exported to `recovery/articles-2026-06-*.json` straight from the live Fly DuckDB.
2. **Re-enrichment**: ran `enrichment-replay --since-hours 400 --limit 500` on Fly after the 403 billing block cleared → 190/190 processed (107 SUCCESS + 83 SKIPPED off-topic), still-failed=0. NOTE: the 512MB machine OOM-killed the first attempt (daemon JVM + replay JVM). Fix: temporarily `fly machine update --vm-memory 2048`, run, then restore to 512.
3. **Digest backfill**: existing CLI cannot emit historical-dated JSON digests (OutgressWorkflow.writeDigestJson is anchored to `clock.now()`, always writes `daily-<today>.json`). Added a `backfill-digest --from --to` command (App.kt) that runs ClusteringWorkflow + OutgressWorkflow with the clock pinned to each date at 12:00 UTC — the 24h window then lands exactly on that day's ~04:00 UTC ingestion batch (windows are driven by `ingested_at`, which re-enrichment leaves intact). Ran it locally against a pulled DB copy (so prod's cluster table stays untouched), generating `daily-2026-06-03..08.json` (+ .md), then sftp-uploaded them to `/data/output`.
4. **Verified live**: `/api/dates` now shows a continuous 06-01..06-16 run; `/api/daily/2026-06-05` serves 19 clusters.

**Gotcha for future backfills:** clustering/outgress windows are anchored to an injectable `clock` but the daily-pipeline CLI doesn't expose it — use `backfill-digest`. Digests window by `ingested_at` (not `published_at`); ingestion fires daily ~04:00 UTC, so anchoring the clock at 12:00 UTC isolates a single day cleanly.
