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
