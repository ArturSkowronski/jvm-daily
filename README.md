# JVM Daily

> Automated daily news aggregator for the JVM ecosystem — inspired by [Latent Space AI News](https://news.smol.ai)

**JVM Daily** aggregates news, blog posts, and articles from 17+ RSS feeds covering Java, Kotlin, Spring, Quarkus, GraalVM, and the broader JVM ecosystem. Built with a plugin-based architecture supporting multi-stage AI workflows powered by Koog Agents.

## Features

- **Plugin-based source engine** — easily add new data sources (RSS, Twitter, Reddit, etc.)
- **Deduplication** — skip already-ingested articles automatically
- **Cron-ready** — designed for scheduled execution with timestamped logging
- **DuckDB storage** — lightweight, embedded analytics database
- **Multi-stage workflows** — ingress → processing → publishing pipeline (ingress implemented)

## Quick Start

### Prerequisites

- JDK 21+
- Gradle 8.x (wrapper included)

### Run Ingress

```bash
# Run once
./gradlew run

# Via cron (daily at 7am)
0 7 * * * /path/to/jvm-daily/run-ingress.sh
```

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `DUCKDB_PATH` | `jvm-daily.duckdb` | Database file path |
| `CONFIG_PATH` | `config/sources.yml` | RSS feed configuration |
| `SOURCES_DIR` | `sources` | Directory for markdown sources |
| `LOG_DIR` | `logs` | Log output directory |

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

## Tech Stack

- **Language:** Kotlin 2.2
- **JVM:** 21
- **Build:** Gradle (Kotlin DSL)
- **Database:** DuckDB 1.1.3 (via JDBC)
- **RSS:** Rome 2.1.0
- **Config:** kaml 0.67.0 (YAML + kotlinx.serialization)
- **AI Agents:** Koog 0.6.1 (planned for processing workflow)
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
- [x] Cron-ready run script
- [ ] Processing workflow (AI summarization, clustering)
- [ ] Publishing workflow (newsletter, static site)
- [ ] Twitter/X source plugin
- [ ] Reddit source plugin
- [ ] Discord source plugin
- [ ] GitHub Trending source plugin

See [`PLAN.md`](PLAN.md) for the full roadmap.

## License

MIT

## Credits

Inspired by [Latent Space AI News](https://news.smol.ai) by [@swyx](https://twitter.com/swyx)
