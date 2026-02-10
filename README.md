# JVM Daily

Automated daily news aggregator for the JVM ecosystem — inspired by [Latent Space AI News](https://news.smol.ai).

Collects articles from RSS feeds, blogs, and community sources across the Java, Kotlin, Scala, and broader JVM world, then processes and summarizes them using AI agents.

## Architecture

```
Source (plugin) → SourceRegistry → IngressWorkflow → DuckDB
```

**Plugin-based source engine** — each data source implements the `Source` interface and is registered in `SourceRegistry`. The system is designed for easy addition of new sources (Twitter, Reddit, Discord, GitHub Trending — see [PLAN.md](PLAN.md)).

**Multi-stage workflow pipeline** — workflows are independent stages:
1. **Ingress** — collect raw articles from all sources, persist to DuckDB *(implemented)*
2. **Processing** — AI summarization & clustering via Koog agents *(planned)*
3. **Publishing** — newsletter generation & distribution *(planned)*

## Current Sources

17 verified RSS/Atom feeds:

| Source | Feed |
|--------|------|
| Inside Java | `inside.java/feed.xml` |
| Spring Blog | `spring.io/blog.atom` |
| Kotlin Blog | `blog.jetbrains.com/kotlin/feed/` |
| Baeldung | `feeds.feedblitz.com/baeldung` |
| InfoQ Java | `feed.infoq.com/java` |
| Quarkus Blog | `quarkus.io/feed.xml` |
| Micronaut Blog | `micronaut.io/feed/` |
| foojay.io | `foojay.io/feed/` |
| Gradle Blog | `feed.gradle.org/blog.atom` |
| JetBrains Blog | `blog.jetbrains.com/feed/` |
| Vlad Mihalcea | `vladmihalcea.com/feed/` |
| Thorben Janssen | `thorben-janssen.com/feed/` |
| Marco Behler | `dev.to/feed/marcobehler` |
| Adam Bien | `adambien.blog/roller/abien/feed/entries/atom` |
| DZone Java | `feeds.dzone.com/java` |
| Hacker News (JVM) | `hnrss.org/newest?q=java+OR+kotlin+OR+jvm+OR+spring+OR+graalvm` |
| GraalVM Blog | `medium.com/feed/graalvm` |

Feeds are configured in [`config/sources.yml`](config/sources.yml). Markdown files from the `sources/` directory are also ingested.

## Tech Stack

- **Kotlin** 2.2 on JVM 21
- **Gradle** (Kotlin DSL)
- **DuckDB** — embedded analytical database (via JDBC)
- **Rome** — RSS/Atom feed parsing
- **kaml** + kotlinx.serialization — YAML config
- **Koog Agents** — AI agent framework (for future processing workflow)

## Getting Started

```bash
# Build and run tests
./gradlew build

# Run the ingress pipeline
./gradlew run

# Run integration tests (verifies all 17 RSS feeds)
./gradlew integrationTest
```

### Configuration

| Environment Variable | Default | Description |
|---------------------|---------|-------------|
| `DUCKDB_PATH` | `jvm-daily.duckdb` | Path to DuckDB database file |
| `SOURCES_DIR` | `sources` | Directory with markdown source files |
| `CONFIG_PATH` | `config/sources.yml` | Path to YAML feed config |

## Project Structure

```
app/src/main/kotlin/jvm/daily/
├── config/SourcesConfig.kt          # YAML config parsing
├── model/Article.kt                 # Article data model
├── source/
│   ├── Source.kt                    # Source plugin interface
│   ├── SourceRegistry.kt           # Plugin registry
│   ├── MarkdownFileSource.kt       # Reads .md files from directory
│   └── RssSource.kt                # Fetches RSS/Atom feeds
├── storage/
│   ├── ArticleRepository.kt        # Storage interface
│   ├── DuckDbArticleRepository.kt  # DuckDB JDBC implementation
│   └── DuckDbConnectionFactory.kt  # Connection helper
├── workflow/
│   ├── Workflow.kt                  # Workflow interface
│   ├── IngressWorkflow.kt          # Collect → persist pipeline
│   └── WorkflowRunner.kt           # Workflow orchestrator
└── App.kt                          # Entry point
```

## CI/CD

- **`build`** — runs on push/PR to `main` (`./gradlew build`)
- **`RSS Feed Check`** — daily at 07:00 UTC, verifies all feeds are still parseable (`./gradlew integrationTest`)
