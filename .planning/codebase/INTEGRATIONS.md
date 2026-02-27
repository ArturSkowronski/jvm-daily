# External Integrations

**Analysis Date:** 2026-02-27

## APIs & External Services

**RSS feeds (primary external input):**
- Multiple public feeds configured in `config/sources.yml` (Inside Java, Spring, Kotlin Blog, InfoQ, Quarkus, etc.)
  - Client: Rome parser (`com.rometools:rome`) in `RssSource`
  - Auth: None
  - Transport: HTTP(S) with explicit user-agent and timeouts in `app/src/main/kotlin/jvm/daily/source/RssSource.kt`

**LLM provider abstraction (planned real providers):**
- Contract exposed via `app/src/main/kotlin/jvm/daily/ai/LLMClient.kt`
  - Current implementation in `App.kt` supports only `mock`
  - Env config: `LLM_PROVIDER`, `LLM_API_KEY`, `LLM_MODEL`
  - Risk: non-mock providers error out at runtime (not implemented yet)

## Data Storage

**Databases:**
- DuckDB file DB (`jdbc:duckdb:<path>`) via `DuckDbConnectionFactory`
  - Tables created on startup by repositories:
  - `articles` in `DuckDbArticleRepository`
  - `processed_articles` + index `idx_processed_at` in `DuckDbProcessedArticleRepository`

- H2 file DB for JobRunr metadata (`jdbc:h2:file:...`) in daemon mode
  - Configured inside `startDaemon` in `app/src/main/kotlin/jvm/daily/App.kt`

**File storage/output:**
- Markdown output files emitted by outgress workflow into `output/` (or `OUTPUT_DIR`)

## Authentication & Identity

- No end-user auth flows in application code
- External credential surface is env-based API keys for future LLM providers

## Monitoring & Observability

- Logging is stdout/stderr prints from workflows and scripts
- Airflow task logs available under `airflow/logs/` when orchestrated
- No centralized error tracking (Sentry/Datadog/etc.) integrated

## CI/CD & Deployment

**CI:**
- GitHub Actions workflow `/.github/workflows/gradle.yml`
  - Runs `./gradlew test` on PR/push

**Container/deploy:**
- GHCR image publish + Fly.io deployment in same workflow (`deploy` job)
  - Requires `FLY_API_TOKEN` repo secret

## Environment Configuration

**Development:**
- Local `.duckdb` files in repo root by default
- Airflow variable store used for LLM settings in DAG (`{{ var.value.llm_* }}`)

**Production/runtime:**
- Container envs set in `Dockerfile` defaults (`DUCKDB_PATH=/data/jvm-daily.duckdb`, etc.)

## Webhooks & Callbacks

- No inbound or outbound webhook handlers present in Kotlin app

---
*Integration audit: 2026-02-27*
*Update when adding/removing external systems*
