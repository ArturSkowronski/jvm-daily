# Vived Engine

Generic news aggregation engine powering daily developer digests. Domain-agnostic — configure it for any ecosystem (JVM, AI, Rust, DevOps) by providing config files, no code changes needed.

## What it does

Vived Engine runs a four-stage pipeline:

```
Ingress → Enrichment → Clustering → Outgress
```

1. **Ingress** — collects articles from RSS, Reddit, Bluesky, GitHub Trending/Releases, and custom sources
2. **Enrichment** — LLM-powered summarization, entity extraction, topic tagging, relevance gating, and taxonomy classification
3. **Clustering** — groups related articles into thematic clusters with LLM-generated synthesis
4. **Outgress** — writes daily digest as JSON + Markdown

## Architecture

```
vived-engine/src/main/kotlin/jvm/daily/
├── ai/          LLMClient interface + OpenAI-compatible implementation
├── api/         Ktor REST API (serves digests, accepts ingest, serves SPA)
├── config/      DomainProfile + SourcesConfig (domain-specific text injection)
├── model/       Article, ProcessedArticle, ArticleCluster, FeedIngestResult
├── source/      Source interface + 7 built-in connectors
├── storage/     Repository interfaces + DuckDB implementations
└── workflow/    Workflow interface + 4 pipeline stages + TaxonomyClassifier
```

### Built-in sources

| Source | What it fetches |
|--------|----------------|
| `RssSource` | RSS/Atom feeds with optional roundup splitting |
| `RedditSource` | Top posts from subreddits |
| `BlueskySource` | Posts from Bluesky accounts |
| `GitHubTrendingSource` | Trending repos by language |
| `GitHubReleasesSource` | New releases from tracked repos |
| `MarkdownFileSource` | Local `.md` files (testing/manual input) |
| `RoundupSplitter` | LLM-based extraction of sub-articles from digest feeds |

### Storage

DuckDB via plain JDBC. Repository interfaces (`ArticleRepository`, `ProcessedArticleRepository`, `ClusterRepository`) allow swapping to other backends.

### LLM

`LLMClient` interface with a single method: `suspend fun chat(prompt: String): String`. Ships with `OpenAiCompatibleLLMClient` supporting OpenAI, Gemini, Groq, and any OpenAI-compatible API.

## Usage

Add as a Gradle dependency:

```kotlin
// build.gradle.kts
dependencies {
    implementation(project(":vived-engine"))
}
```

### Minimal example

```kotlin
val domain = DomainProfile.load(Path.of("domains/my-daily/domain.json"))
val config = SourcesConfig.load(Path.of("domains/my-daily/sources.yml"))

DuckDbConnectionFactory.persistent("my-daily.duckdb").use { conn ->
    val articleRepo = DuckDbArticleRepository(conn)
    val processedRepo = DuckDbProcessedArticleRepository(conn)
    val clusterRepo = DuckDbClusterRepository(conn)
    val llm = OpenAiCompatibleLLMClient(apiKey, model, baseUrl)

    val registry = SourceRegistry().apply {
        register(RssSource(config.rss))
        register(RedditSource(config.reddit))
        config.bluesky?.let { register(BlueskySource(it)) }
        config.githubTrending?.let { register(GitHubTrendingSource(it)) }
        config.githubReleases?.let { register(GitHubReleasesSource(it)) }
    }

    runBlocking {
        IngressWorkflow(registry, articleRepo).execute()
        EnrichmentWorkflow(articleRepo, processedRepo, llm, domainProfile = domain).execute()
        ClusteringWorkflow(processedRepo, clusterRepo, llm, domainProfile = domain).execute()
        OutgressWorkflow(processedRepo, Path.of("output"), domainProfile = domain).execute()
    }
}
```

### Custom sources

Implement the `Source` interface and register:

```kotlin
class HuggingFaceSource(...) : Source {
    override val sourceType = "huggingface"
    override suspend fun fetch(): List<Article> { /* ... */ }
    override suspend fun fetchOutcomes(): List<SourceFetchOutcome> { /* ... */ }
}

registry.register(HuggingFaceSource(...))
```

## Configuration

Three files define a domain:

| File | Purpose |
|------|---------|
| `domain.json` | LLM prompts, audience, relevance gate, branding |
| `taxonomy.json` | Hierarchical classification tree (areas, sub-areas, impact tags) |
| `sources.yml` | RSS feeds, subreddits, Bluesky accounts, GitHub repos |

See `domains/_template/` for blank templates.

## REST API

`startRestApi()` launches a Ktor CIO server:

| Endpoint | Description |
|----------|-------------|
| `GET /api/dates` | List available digest dates |
| `GET /api/daily/{date}` | Fetch digest JSON for a date |
| `POST /api/ingest` | Push articles from external sources |
| `GET /` | Serve SvelteKit viewer (if built) |

## Tests

```bash
./gradlew vived-engine:test           # 175 tests
./gradlew vived-engine:test -Pintegration  # include network-dependent tests
```
