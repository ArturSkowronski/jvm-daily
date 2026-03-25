# JVM Daily

[![Build](https://github.com/ArturSkowronski/jvm-daily/actions/workflows/ci.yml/badge.svg)](https://github.com/ArturSkowronski/jvm-daily/actions/workflows/ci.yml)
[![Deploy](https://github.com/ArturSkowronski/jvm-daily/actions/workflows/deploy.yml/badge.svg)](https://github.com/ArturSkowronski/jvm-daily/actions/workflows/deploy.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

> Automated daily news aggregator for the JVM ecosystem — inspired by [Latent Space AI News](https://news.smol.ai)

**JVM Daily** collects articles from RSS feeds, Reddit, GitHub, Bluesky, and OpenJDK mailing lists; enriches them with an LLM; clusters them into themes; and publishes a daily digest via a headless REST API + SPA viewer.

Live: **https://jvm-daily.fly.dev**

## How It Works

```
Sources → Ingress → Enrichment (LLM) → Clustering (LLM) → Outgress → REST API → Viewer
```

| Stage | What happens |
|---|---|
| **Ingress** | Fetch articles from all configured sources, deduplicate, store in DuckDB |
| **Enrichment** | LLM generates a summary, extracts entities (frameworks, versions) and topics |
| **Clustering** | Group articles into thematic clusters with AI-generated synthesis |
| **Outgress** | Write daily JSON digests to `output/` |
| **REST API** | Serve digests, pipeline status, and ingest endpoint via Ktor |
| **Viewer** | Standalone SPA consuming the API |

### Sources

| Source | What's collected |
|---|---|
| RSS (17 feeds) | inside.java, Spring, Kotlin blog, Baeldung, InfoQ, Quarkus, Foojay, DZone, HN, GraalVM, ... |
| Reddit | r/java, r/Kotlin, r/scala (via Raspberry Pi for server IP bypass) |
| GitHub Trending | Top Java/Kotlin/Scala repos by stars |
| GitHub Releases | New releases from 20 core JVM projects (Spring Boot, Kotlin, Quarkus, ...) |
| Bluesky | Posts from 42 JVM community members (OpenJDK team, framework leads, orgs) |
| OpenJDK Mail | 23 mailing lists (jdk-dev, amber-dev, loom-dev, hotspot-dev, ...) |
| JEP Tracking | JDK Enhancement Proposals (Draft, Candidate, Proposed, Targeted, Integrated) |

## Quick Start

**Prerequisites:** JDK 21+, an LLM API key (Gemini, OpenAI, or Groq)

```bash
# Run the full pipeline once
LLM_PROVIDER=gemini GEMINI_API_KEY=<key> ./gradlew run --args="pipeline"

# Start the daemon (scheduler + REST API + viewer on :8888)
LLM_PROVIDER=gemini GEMINI_API_KEY=<key> ./gradlew run
```

Or build and run the distribution:

```bash
./gradlew installDist
LLM_PROVIDER=gemini GEMINI_API_KEY=<key> ./app/build/install/app/bin/app pipeline
```

## REST API

The daemon serves a headless REST API on port 8888 (configurable via `VIEWER_PORT`).

| Endpoint | Method | Description |
|---|---|---|
| `/api/dates` | GET | Available digest dates (sorted desc) |
| `/api/daily/{date}` | GET | Full digest JSON for a date (clusters, articles, debug) |
| `/api/pipeline` | GET | Pipeline status (stats, recent jobs) |
| `/api/ingest` | POST | Push articles from external sources (Bearer auth) |
| `/api/files` | GET | Markdown filenames (backward compat) |
| `/` | GET | Viewer SPA |

See [OpenAPI spec](docs/openapi.yaml) for full schema.

## CLI Commands

```bash
app pipeline                    # Full pipeline (ingress → enrichment → clustering → outgress)
app ingress                     # Collect articles from all sources
app enrichment                  # LLM-process unprocessed articles
app clustering                  # Group articles into thematic clusters
app outgress                    # Write JSON digests
app ingress-push                # Fetch Reddit → push to remote ingest API (for Pi)
app reprocess [--since-hours N] # Clear and re-enrich recent articles
app enrichment-replay [opts]    # Replay only failed enrichment items
app quality-report [opts]       # Quality metrics (duplicates, failures, thresholds)
app inspect-quality [opts]      # Inspect low-quality / failed articles
app validate-raw-ids [--apply]  # Check/fix article ID collisions
# (no args)                     # Start daemon (scheduler + REST API + viewer)
```

## Deployment

### Architecture

```
┌─────────────┐  5:30 AM   ┌──────────────────────────────────┐
│ Raspberry Pi │ ─────────► │ Fly.io (Frankfurt)               │
│ (Tailscale)  │  POST      │                                  │
│ Reddit only  │  /api/     │  6:00 AM: enrichment → clustering│
└─────────────┘  ingest     │  REST API + Viewer on :8888      │
                            │  JobRunr dashboard on :8000      │
                            └──────────────────────────────────┘
```

- **Fly.io** — runs the full pipeline (minus Reddit), REST API, and viewer
- **Raspberry Pi** — fetches Reddit (residential IP, not blocked) and pushes via ingest API
- **GitHub Actions** — auto-deploys to both Fly.io and Pi on push to `main`

### Fly.io

```bash
fly auth login
fly volumes create jvm_daily_data --size 5 --region fra

fly secrets set \
  LLM_PROVIDER=gemini \
  GEMINI_API_KEY=<key> \
  GITHUB_TOKEN=<token> \
  INGEST_API_KEY=<secret> \
  REDDIT_ENABLED=false

fly deploy
```

### Raspberry Pi

```bash
./scripts/deploy-pi.sh    # builds locally, rsyncs to Pi via Tailscale
```

Pi runs `ingress-push` via cron at 5:30 AM Warsaw time, pushing Reddit articles to Fly.io before the 6:00 AM pipeline runs.

### Environment Variables

| Variable | Default | Description |
|---|---|---|
| `LLM_PROVIDER` | `mock` | `mock` / `gemini` / `openai` / `groq` / `openai-compatible` |
| `GEMINI_API_KEY` | — | Required when `LLM_PROVIDER=gemini` |
| `LLM_API_KEY` | — | Required for `openai` / `groq` / `openai-compatible` |
| `LLM_MODEL` | `gpt-4` | Model name passed to the provider |
| `DUCKDB_PATH` | `~/.jvm-daily/jvm-daily.duckdb` | Database file path |
| `CONFIG_PATH` | `config/sources.yml` | Sources configuration |
| `OUTPUT_DIR` | `output` | Directory for generated digests |
| `PIPELINE_CRON` | `0 7 * * *` | Cron schedule in daemon mode |
| `VIEWER_PORT` | `8888` | REST API + viewer port |
| `DASHBOARD_PORT` | `8000` | JobRunr dashboard port |
| `GITHUB_TOKEN` | — | Required for GitHub sources |
| `INGEST_API_KEY` | — | Bearer token for POST /api/ingest |
| `REDDIT_ENABLED` | `true` | Set `false` to skip Reddit in cloud ingress |
| `INGEST_TARGET_URL` | — | Remote API URL for `ingress-push` command |

## Viewer

The SPA viewer (`viewer/index.html`) is served by the Ktor REST API at `/`.

### Features

- **Digest view** — clustered articles with AI synthesis, release cards, social posts
- **Article merging** — duplicate Bluesky shares merged into single card with all handles
- **Pipeline view** — JobRunr run history and stats
- **ROTS (Rest of the Story)** — bookmark clusters for later reading:
  - Click ★ on any cluster to bookmark
  - ROTS tab collects bookmarks across dates with Copy as Markdown and Share URL
- **Dismiss/Archive** — click ✓ to archive reviewed clusters
- **Persistence** — bookmarks and dismissals stored in localStorage

## Architecture

```
app/src/main/kotlin/jvm/daily/
├── api/         # RestApi (Ktor REST server)
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
viewer/          # index.html (SPA), Playwright tests, test fixtures
```

### Core Abstractions

- **`Source`** — Plugin interface; each data source implements it
- **`SourceRegistry`** — Registers and manages source plugins
- **`Workflow`** — Pipeline stage interface
- **`RestApi`** — Ktor HTTP server (headless API + SPA serving)
- **`ArticleRepository`** / **`ProcessedArticleRepository`** — Storage abstraction (DuckDB via JDBC)

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 2.2 |
| JVM | 21 |
| Build | Gradle (Kotlin DSL) |
| Database | DuckDB (JDBC) |
| REST API | Ktor (CIO) |
| Scheduling | JobRunr |
| HTTP Client | java.net.HttpURLConnection |
| RSS/Atom | Rome |
| Config | kaml (YAML) |
| AI / LLM | OpenAI-compatible API (Gemini, OpenAI, Groq) |
| Serialization | kotlinx.serialization |
| Testing | JUnit 5 + kotlin-test + Playwright |

## Testing

```bash
./gradlew build                                           # compile + unit tests
npx playwright test --config viewer/playwright.config.ts  # 25 viewer/API integration tests
```

Playwright tests use mock fixtures (`viewer/test-fixtures/`) with a lightweight Node.js test server — no running backend needed.

## Using the Engine for a Different Domain

The engine is domain-agnostic — JVM-specific text lives entirely in three config files. To create "AI Daily", "Rust Daily", or any other news aggregator:

### 1. Create a domain profile

Copy `config/domain.json` and edit for your domain:

```json
{
  "name": "AI Daily",
  "slug": "ai-daily",
  "description": "AI/ML ecosystem news aggregator",
  "audience": "ML engineers and AI researchers",
  "relevanceGate": {
    "include": [
      "Machine learning frameworks (PyTorch, JAX, TensorFlow)",
      "LLM developments (GPT, Claude, Gemini, Llama)",
      "AI infrastructure (training, inference, deployment)",
      "Research papers with practical engineering impact"
    ],
    "exclude": [
      "General software engineering not AI-specific",
      "AI hype without technical substance"
    ]
  },
  "enrichment": {
    "systemPrompt": "You are an AI/ML ecosystem news analyst...",
    "entityExamples": ["PyTorch 2.5", "GPT-5", "CUDA 13", "vLLM 0.8"],
    "topicExamples": ["fine-tuning", "inference-optimization", "rlhf", "multimodal"]
  },
  "clustering": {
    "systemPrompt": "You are an expert AI news curator...",
    "audienceDescription": "ML engineers who want signal, not hype",
    "exampleClusters": ["PyTorch 2.5 Release", "Llama 4 Benchmarks", "vLLM Inference Optimization"],
    "majorReleaseRules": ["PyTorch major releases", "New foundation model families", "CUDA major versions"]
  }
}
```

### 2. Create a taxonomy

Copy `config/taxonomy.json` and define your domain's areas:

```
ai-fundamentals (transformers, attention, optimization)
llms (fine-tuning, rlhf, inference, agents)
computer-vision (detection, segmentation, generation)
frameworks (pytorch, jax, tensorflow, huggingface)
infrastructure (training-clusters, serving, mlops)
```

### 3. Configure sources

Copy `config/sources.yml` with your domain's feeds:

```yaml
rss:
  - url: "https://arxiv.org/rss/cs.AI"
  - url: "https://huggingface.co/blog/feed.xml"
reddit:
  - subreddit: "MachineLearning"
  - subreddit: "LocalLLaMA"
bluesky:
  accounts: ["karpathy.ai", "ylecun.bsky.social"]
```

### 4. Run

```bash
DOMAIN_CONFIG_PATH=config/domain-ai.json \
CONFIG_PATH=config/sources-ai.yml \
LLM_PROVIDER=gemini GEMINI_API_KEY=<key> \
./app/build/install/app/bin/app pipeline
```

No code changes needed. The same binary, different configs.

### What's generic vs domain-specific

| Generic (no changes needed) | Domain-specific (config only) |
|---|---|
| All 6 source adapters (RSS, Reddit, Bluesky, GitHub, RoundupSplitter) | `config/domain.json` — prompts, audience, examples |
| Ingress, enrichment, clustering, outgress pipeline | `config/taxonomy.json` — areas, sub-areas, tags |
| DuckDB storage, REST API, SvelteKit viewer | `config/sources.yml` — feeds, accounts, repos |
| Taxonomy classifier framework | |
| Deployment (Fly.io, Pi, GitHub Actions) | |

## Other Docs

- [`QUICKSTART.md`](QUICKSTART.md) — detailed local setup walkthrough
- [`EXPLORER.md`](EXPLORER.md) — interactive database explorer CLI
- [`Findings.md`](Findings.md) — technical discoveries and gotchas
- [`docs/openapi.yaml`](docs/openapi.yaml) — REST API specification

## License

MIT — inspired by [Latent Space AI News](https://news.smol.ai) by [@swyx](https://twitter.com/swyx)
