# JVM Daily

[![Build](https://github.com/ArturSkowronski/jvm-daily/actions/workflows/gradle.yml/badge.svg)](https://github.com/ArturSkowronski/jvm-daily/actions/workflows/gradle.yml)
[![RSS Feed Check](https://github.com/ArturSkowronski/jvm-daily/actions/workflows/rss-feed-check.yml/badge.svg)](https://github.com/ArturSkowronski/jvm-daily/actions/workflows/rss-feed-check.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

> Automated daily news aggregator for the JVM ecosystem — inspired by [Latent Space AI News](https://news.smol.ai)

**JVM Daily** aggregates news, blog posts, and articles from 17+ RSS feeds covering Java, Kotlin, Spring, Quarkus, GraalVM, and the broader JVM ecosystem. Built with a plugin-based architecture supporting multi-stage AI workflows powered by Koog Agents.

## Features

- **Plugin-based source engine** — easily add new data sources (RSS, Twitter, Reddit, etc.)
- **Deduplication** — skip already-ingested articles automatically
- **Cron-ready** — designed for scheduled execution with timestamped logging
- **DuckDB storage** — lightweight, embedded analytics database
- **Multi-stage workflows** — ingress → processing → publishing pipeline (ingress implemented)

## Quick Start

> 📖 **Detailed guides:** [`QUICKSTART.md`](QUICKSTART.md) | [`airflow/README.md`](airflow/README.md)

### Option 1: Run Workflows Locally (No Airflow)

**Prerequisites:** JDK 21+, Gradle 8.x (wrapper included)

```bash
# Step 1: Collect articles from RSS feeds
./gradlew run --args="ingress"

# Step 2: Enrich with LLM (summaries, entities, topics)
./gradlew run --args="enrichment"

# Step 3: Group into thematic clusters
./gradlew run --args="clustering"

# Explore database
./gradlew explore
```

**Ingress reliability semantics:**
- `SUCCESS` — run completed and no feed-level failures blocked coverage (including valid zero-new-items days)
- `SUCCESS_WITH_WARNINGS` — run completed with partial feed failures or malformed-entry warnings
- `FAIL` — all feeds failed (ingest reliability failure)

Ingress output now includes a per-feed summary with status, fetched/new/duplicate counts, and error reasons.

**Raw article ID validation/backfill (Phase 3):**
```bash
# Dry-run: report mismatches/collisions without mutating rows
./gradlew run --args="validate-raw-ids"

# Apply mode: update mismatched IDs when collision-free
./gradlew run --args="validate-raw-ids --apply"
```
Recommended operator flow:
1. Run dry-run and check mismatch/collision counters.
2. Only run `--apply` when collisions are `0`.
3. Re-run dry-run to confirm mismatches are cleared.

**Summarization core contract (Phase 4):**
- Enrichment expects strict JSON from the LLM (`summary`, `entities`, `topics`).
- Invalid JSON or blank summary produces explicit failed outcomes in processed storage.
- Transport/provider failures retry up to 3 attempts (2 retries, fixed 2s backoff).
- Pipeline continues on partial failures and reports warning status instead of aborting the entire run.

**Recoverability controls (Phase 5):**
```bash
# Preview failed enrichment candidates from the last 7 days (default selector)
./gradlew run --args="enrichment-replay --dry-run"

# Replay last N failed items from a time window
./gradlew run --args="enrichment-replay --since-hours 48 --limit 20"

# Replay explicit failed IDs only
./gradlew run --args="enrichment-replay --ids a1,a2,a3"
```

Replay selector rules:
1. Use either `--ids` OR the range selector (`--since-hours` + `--limit`), not both.
2. Start with `--dry-run` before mutating runs.
3. Replay targets failed enrichment records only; no ingest rerun is required.

### Recoverability Runbook (Phase 5)

1. Preview candidates:
```bash
./gradlew run --args="enrichment-replay --dry-run --since-hours 24 --limit 20"
```
2. Validate candidate IDs and failure reasons in DB explorer/logs.
3. Execute replay:
```bash
./gradlew run --args="enrichment-replay --since-hours 24 --limit 20"
```
4. Verify outcomes:
   - rerun dry-run and confirm failed candidate count dropped
   - inspect `still-failed` output from replay command
5. If failures remain, rerun with focused IDs:
```bash
./gradlew run --args="enrichment-replay --ids <id1,id2,...>"
```

**Daily automation telemetry (Phase 6):**
- `PipelineService` emits structured stage telemetry lines:
  - `run_id`, `stage`, `status`, `started_at`, `ended_at`, `duration_ms`, optional `error`
- Example log prefix: `[pipeline][telemetry] ...`
- Stage status is emitted on both success and failure paths for quick diagnosis.

**Environment Variables:**

| Variable | Default | Description |
|----------|---------|-------------|
| `DUCKDB_PATH` | `jvm-daily.duckdb` | Database file path |
| `CONFIG_PATH` | `config/sources.yml` | RSS feed configuration |
| `LLM_PROVIDER` | `mock` | LLM provider (`mock`, `openai`, `anthropic`) |
| `LLM_API_KEY` | - | API key for LLM provider |
| `LLM_MODEL` | `gpt-4` | Model to use |

### Option 2: Run with Airflow Orchestration

**Prerequisites:** Docker or Podman, Docker Compose

```bash
# 1. Setup Podman machine (if using Podman)
podman machine init --cpus 2 --memory 4096 --disk-size 20
podman machine start

# 2. Start Airflow
cd airflow
cp .env.example .env
docker-compose up airflow-init
docker-compose up -d

# 3. Access UI
open http://localhost:8080
# Login: airflow / airflow

# 4. Configure LLM in UI (Admin → Variables)
#    - llm_provider: mock (or openai, anthropic)
#    - llm_api_key: your-key
#    - llm_model: gpt-4

# 5. Enable and trigger DAG 'jvm_daily_pipeline'
```

**Airflow Features:**
- 📅 Daily schedule at 7am UTC
- 🔀 Conditional execution (skip if no new articles)
- 🔄 Retry logic (2 retries, 5min delay)
- ⏱️ Timeouts (30min enrichment, 20min clustering)
- 📊 Web UI for monitoring

**Resource Requirements:** 2 CPUs, 4GB RAM, 20GB disk ([details](airflow/RESOURCES.md))

## RSS Sources

Currently aggregating from 17 feeds:

| Source | URL |
|--------|-----|
| Inside Java | https://inside.java/feed.xml |
| Spring Blog | https://spring.io/blog.atom |
| Kotlin Blog | https://blog.jetbrains.com/kotlin/feed/ |
| Baeldung | https://feeds.feedblitz.com/baeldung |
| InfoQ Java | https://feed.infoq.com/java |
| Quarkus | https://quarkus.io/feed.xml |
| Micronaut | https://micronaut.io/feed/ |
| Foojay | https://foojay.io/feed/ |
| Gradle Blog | https://feed.gradle.org/blog.atom |
| JetBrains Blog | https://blog.jetbrains.com/feed/ |
| Vlad Mihalcea | https://vladmihalcea.com/feed/ |
| Thorben Janssen | https://thorben-janssen.com/feed/ |
| Marco Behler | https://dev.to/feed/marcobehler |
| Adam Bien | https://adambien.blog/roller/abien/feed/entries/atom |
| DZone Java | https://feeds.dzone.com/java |
| Hacker News (JVM) | https://hnrss.org/newest?q=java+OR+kotlin+OR+jvm+OR+spring+OR+graalvm |
| GraalVM Medium | https://medium.com/feed/graalvm |

## Architecture

```
jvm-daily/
├── app/src/main/kotlin/jvm/daily/
│   ├── model/          # Data models (Article)
│   ├── source/         # Plugin sources (RssSource, MarkdownFileSource)
│   ├── storage/        # DuckDB repository & connection factory
│   ├── workflow/       # Multi-stage workflows (IngressWorkflow)
│   └── config/         # Configuration models (SourcesConfig)
├── config/
│   └── sources.yml     # RSS feed configuration
├── run-ingress.sh      # Cron-friendly wrapper script
└── PLAN.md             # Detailed project roadmap
```

### Core Abstractions

- **`Source`** — Plugin interface for data sources (RSS, Twitter, Reddit, etc.)
- **`SourceRegistry`** — Registry for managing source plugins
- **`Workflow`** — Pipeline stage interface (ingress, processing, publishing)
- **`ArticleRepository`** — Storage abstraction (DuckDB via JDBC)

### Architecture Guardrails

- Source adapters must implement `Source` and return normalized `Article` records.
- Workflow layer should not import concrete source implementations or concrete DuckDB repositories.
- Source and storage layers should stay independent from workflow package internals.
- Guard tests in `app/src/test/kotlin/jvm/daily/architecture/` enforce these boundaries on every `./gradlew test`.

## Tech Stack

- **Language:** Kotlin 2.2
- **JVM:** 21
- **Build:** Gradle (Kotlin DSL)
- **Database:** DuckDB 1.1.3 (via JDBC)
- **Orchestration:** Apache Airflow 2.8.1 (Docker/Podman)
- **RSS:** Rome 2.1.0
- **Config:** kaml 0.67.0 (YAML + kotlinx.serialization)
- **AI:** Koog 0.6.1 (LLM abstraction layer)
- **Testing:** JUnit 5 + kotlin-test

## Development

### Build

```bash
./gradlew build
```

### Run Tests

```bash
# Unit tests only
./gradlew test

# Integration tests (requires network)
./gradlew integrationTest
```

### Project Conventions

- **Minimal implementation** — no over-engineering
- **Unit tests required** — every component must have tests
- **Branch workflow** — feature branches → PR → main
- **Findings** — document discoveries in `Findings.md`
- **Always run build** — `./gradlew build` after every change

See [`CLAUDE.md`](CLAUDE.md) and [`Agent.md`](Agent.md) for detailed conventions.

## CI/CD

- **Build CI:** Runs on every push/PR to `main` (`.github/workflows/gradle.yml`)
- **RSS Feed Verification:** Daily at 07:00 UTC (`.github/workflows/rss-feed-check.yml`)

## Roadmap

- [x] Plugin-based source engine
- [x] DuckDB storage with deduplication
- [x] RSS source with 17 feeds
- [x] Ingress workflow
- [x] Enrichment workflow (LLM summaries, entities, topics)
- [x] Clustering workflow (thematic grouping)
- [x] Apache Airflow orchestration
- [x] Command-line workflow execution
- [ ] Real LLM integration (OpenAI, Anthropic, Koog Agents)
- [ ] Cluster persistence (save to DuckDB)
- [ ] Compilation workflow (newsletter generation)
- [ ] Publishing workflow (static site, email)
- [ ] Twitter/X source plugin
- [ ] Reddit source plugin
- [ ] Discord source plugin
- [ ] GitHub Trending source plugin

See [`PLAN.md`](PLAN.md) for the full roadmap.

## License

MIT

## Credits

Inspired by [Latent Space AI News](https://news.smol.ai) by [@swyx](https://twitter.com/swyx)
