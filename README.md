# JVM Daily

[![Build](https://github.com/ArturSkowronski/jvm-daily/actions/workflows/gradle.yml/badge.svg)](https://github.com/ArturSkowronski/jvm-daily/actions/workflows/gradle.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

> Automated daily news aggregator for the JVM ecosystem — inspired by [Latent Space AI News](https://news.smol.ai)

**JVM Daily** collects articles from RSS feeds, Reddit, GitHub, Bluesky, and OpenJDK mailing lists; enriches them with an LLM; clusters them into themes; and publishes a daily markdown digest.

Live: **https://jvm-daily.fly.dev**

## How It Works

```
Sources → Ingress → Enrichment → Clustering → Outgress
```

| Stage | What happens |
|---|---|
| **Ingress** | Fetch articles from all configured sources, deduplicate, store in DuckDB |
| **Enrichment** | LLM generates a summary, extracts entities (frameworks, versions) and topics |
| **Clustering** | Group articles into thematic clusters by shared topics |
| **Outgress** | Write daily markdown digest + JSON to `output/` |

### Sources

| Source | What's collected |
|---|---|
| RSS (17 feeds) | inside.java, Spring, Kotlin blog, Baeldung, InfoQ, Quarkus, Foojay, DZone, HN, GraalVM, … |
| Reddit | r/java, r/Kotlin, r/scala |
| GitHub Trending | Top Java/Kotlin/Scala repos by stars |
| GitHub Releases | New releases from 20 core JVM projects (Spring Boot, Kotlin, Quarkus, …) |
| Bluesky | Posts from 42 JVM community members (OpenJDK team, framework leads, orgs) |
| OpenJDK Mail | 23 mailing lists (jdk-dev, amber-dev, loom-dev, hotspot-dev, …) |

## Quick Start

**Prerequisites:** JDK 21+, an LLM API key (Gemini, OpenAI, or Groq)

```bash
# Run the full pipeline once
LLM_PROVIDER=gemini GEMINI_API_KEY=<key> ./gradlew run --args="pipeline"

# View output
cat output/jvm-daily-$(date +%Y-%m-%d).md
```

Or build and run the distribution:

```bash
./gradlew installDist
LLM_PROVIDER=gemini GEMINI_API_KEY=<key> ./app/build/install/app/bin/app pipeline
```

## CLI Commands

```bash
app pipeline                    # Full pipeline (ingress → enrichment → clustering → outgress)
app ingress                     # Collect articles from all sources
app enrichment                  # LLM-process unprocessed articles
app clustering                  # Group articles into thematic clusters
app outgress                    # Write markdown + JSON digests
app reprocess [--since-hours N] # Clear and re-enrich recent articles (default: 2h)
app enrichment-replay [opts]    # Replay only failed enrichment items
app quality-report [opts]       # Quality metrics (duplicates, failures, thresholds)
app inspect-quality [opts]      # Inspect low-quality / failed articles
app validate-raw-ids [--apply]  # Check/fix article ID collisions
# (no args)                     # Start JobRunr daemon + scheduler (dashboard at :8000)
```

### reprocess

Useful when enrichment ran with the mock LLM and you want to redo it with a real provider:

```bash
# Re-enrich + re-cluster articles from the last 2 hours (default)
LLM_PROVIDER=gemini GEMINI_API_KEY=<key> app reprocess

# Custom window
app reprocess --since-hours 6
```

### enrichment-replay

Replay only *failed* enrichment items (not mock-processed ones — use `reprocess` for that):

```bash
app enrichment-replay --dry-run                       # preview candidates
app enrichment-replay --since-hours 48 --limit 20     # replay by time window
app enrichment-replay --ids id1,id2,id3               # replay specific IDs
```

## Configuration

### Environment Variables

| Variable | Default | Description |
|---|---|---|
| `LLM_PROVIDER` | `mock` | `mock` / `gemini` / `openai` / `groq` / `openai-compatible` |
| `GEMINI_API_KEY` | — | Required when `LLM_PROVIDER=gemini` |
| `LLM_API_KEY` | — | Required for `openai` / `groq` / `openai-compatible` |
| `LLM_MODEL` | `gpt-4` | Model name passed to the provider |
| `DUCKDB_PATH` | `jvm-daily.duckdb` | Database file path |
| `CONFIG_PATH` | `config/sources.yml` | Sources configuration |
| `ENRICHMENT_SINCE_DAYS` | `1` | How many days back to enrich |
| `OUTPUT_DIR` | `output` | Directory for generated digests |
| `OUTGRESS_DAYS` | `30` | Digest window (days back to include) |
| `PIPELINE_CRON` | `0 7 * * *` | Cron schedule in daemon mode (7am UTC) |
| `GITHUB_TOKEN` | — | Required for GitHub Trending + Releases sources |

### sources.yml

`config/sources.yml` controls all data sources:

```yaml
rss:
  - url: "https://inside.java/feed.xml"

reddit:
  - subreddit: "java"
    timeWindow: "day"   # hour | day | week | month | year | all

githubTrending:
  languages: ["java", "kotlin", "scala"]
  sinceDays: 1
  minStars: 10

githubReleases:
  sinceDays: 1
  repos:
    - "spring-projects/spring-boot"

bluesky:
  sinceDays: 1
  accounts:
    - "nipafx.dev"

openjdkMail:
  - list: "jdk-dev"
    minReplies: 3
```

## Fly.io Deployment

```bash
fly auth login
fly app create jvm-daily
fly volumes create jvm_daily_data --size 5 --region fra

fly secrets set \
  LLM_PROVIDER=gemini \
  GEMINI_API_KEY=<key> \
  GITHUB_TOKEN=<token>

fly deploy
```

Monitor:

```bash
fly logs --app jvm-daily
fly ssh console --app jvm-daily -C "/app/bin/app pipeline"   # manual run
fly proxy 8000                                                # JobRunr dashboard
```

The app starts a JobRunr daemon that runs the pipeline on a cron schedule (default 7am UTC). The article viewer is served on port 8888.

## Viewer

The built-in viewer (`viewer/serve.py`) serves the daily digest at `http://localhost:8888`.

### Features

- **Digest view** — clustered articles with topic synthesis, release cards, social posts
- **Pipeline view** — JobRunr run history and stats
- **ROTS (Rest of the Story)** — bookmark clusters for the monthly "Rest of the Story" newsletter edition:
  - Click ★ on any cluster to bookmark it for ROTS
  - Bookmarked clusters move to a dedicated "★ Rest of the Story" section in the digest (above archived items)
  - The ROTS tab collects bookmarks across all dates with Copy as Markdown, Share URL, and Clear all
  - Shared URLs encode cluster indices so recipients see the exact selection without needing localStorage
- **Dismiss/Archive** — click ✓ to archive clusters you've reviewed; they move to a grayed-out "Archive" section
- **Persistence** — bookmarks and dismissals are stored in localStorage

**Auto-deploy via GitHub Actions:** set `FLY_API_TOKEN` as a repository secret — pushing to `main` deploys automatically.

## Architecture

```
app/src/main/kotlin/jvm/daily/
├── model/       # Article, ProcessedArticle, ArticleCluster
├── source/      # RssSource, RedditSource, GitHubTrendingSource,
│                #   BlueskySource, OpenJdkMailSource, GitHubReleasesSource
├── storage/     # DuckDbArticleRepository, DuckDbProcessedArticleRepository,
│                #   DuckDbClusterRepository
├── workflow/    # IngressWorkflow, EnrichmentWorkflow,
│                #   ClusteringWorkflow, OutgressWorkflow
├── ai/          # LLMClient, OpenAiCompatibleLLMClient
└── config/      # SourcesConfig (loads sources.yml)

config/          # sources.yml
output/          # generated digests (*.md + *.json)
viewer/          # Python HTTP server for the digest viewer
```

### Core Abstractions

- **`Source`** — Plugin interface; each data source implements it
- **`SourceRegistry`** — Registers and manages source plugins
- **`Workflow`** — Pipeline stage interface
- **`ArticleRepository`** / **`ProcessedArticleRepository`** — Storage abstraction (DuckDB via JDBC)

Architecture boundaries are enforced by guard tests in `app/src/test/kotlin/jvm/daily/architecture/`.

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 2.2 |
| JVM | 21 |
| Build | Gradle (Kotlin DSL) |
| Database | DuckDB (JDBC) |
| Scheduling | JobRunr |
| HTTP | Ktor Client |
| RSS/Atom | Rome |
| Config | kaml (YAML) |
| AI / LLM | Koog Agents |
| Serialization | kotlinx.serialization |
| Testing | JUnit 5 + kotlin-test |

## Development

```bash
./gradlew build        # compile + test
./gradlew test         # tests only
./gradlew explore      # interactive DB explorer
```

**Conventions:** minimal implementation, unit tests required, feature branches, document findings in `Findings.md`. See [`CLAUDE.md`](CLAUDE.md) for full rules.

## Other Docs

- [`QUICKSTART.md`](QUICKSTART.md) — detailed local setup walkthrough
- [`EXPLORER.md`](EXPLORER.md) — interactive database explorer CLI
- [`Findings.md`](Findings.md) — technical discoveries and gotchas
- [`airflow/README.md`](airflow/README.md) — Apache Airflow orchestration setup
- [`PLAN.md`](PLAN.md) — full system design and roadmap

## License

MIT — inspired by [Latent Space AI News](https://news.smol.ai) by [@swyx](https://twitter.com/swyx)
