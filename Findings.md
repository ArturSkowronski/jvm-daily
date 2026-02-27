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
