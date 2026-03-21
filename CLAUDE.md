# JVM Daily — Project Conventions

## Development Rules

1. **Minimal implementation** — always implement the minimum viable version first. No over-engineering.
2. **Unit tests required** — every new component must have unit tests. No exceptions.
3. **Always run build** — run `./gradlew build` after every change to verify compilation and tests pass.
4. **Branch workflow** — always work on feature branches, never commit directly to `main`.
5. **Findings** — save all interesting discoveries, gotchas, and learnings to `Findings.md`.
6. **PR workflow** — push to GitHub and create a PR when work is complete.
7. **Verify before claiming** — before saying something is deployed or visible (viewer, server, etc.), verify it actually is (e.g. `curl`, `pgrep`, check response). Never announce a change as live without confirming.

## Quick Commands

### Build & Test

```bash
./gradlew build                # Compile + all unit tests
./gradlew test                 # Unit tests only
./gradlew integrationTest      # Integration tests (require network)
node --test viewer/rots.test.js  # Viewer JS tests (ROTS logic)
```

### Pipeline CLI

Run via `./gradlew run --args="COMMAND"` or `./app/build/install/app/bin/app COMMAND`:

```bash
pipeline                         # Full pipeline (ingress → enrichment → clustering → outgress)
ingress                          # Collect articles from all sources
enrichment                       # LLM-process unprocessed articles
clustering [--since-hours 24]    # Group articles into clusters
outgress                         # Write markdown + JSON digests
reprocess [--since-hours 2]      # Clear and re-enrich recent articles
enrichment-replay --dry-run      # Preview failed enrichment candidates
enrichment-replay --since-hours 48 --limit 20  # Replay failed items
quality-report --fail-on-threshold  # Quality metrics with exit code
inspect-quality --since-hours 24    # Inspect low-quality articles
validate-raw-ids [--apply]       # Check/fix article ID collisions
```

No arguments = start JobRunr daemon + scheduler (dashboard at `:8000`).

### Viewer

```bash
python3 viewer/serve.py          # Start viewer at http://localhost:8888
python3 viewer/serve.py 9000     # Custom port
lsof -i :8888 -t | xargs kill   # Kill viewer (restart to pick up changes)
```

### Dev Mode

```bash
./scripts/dev.sh                 # Auto-reload on .kt and .py changes (Ctrl-C to stop)
./gradlew explore                # Interactive DuckDB explorer (count, sources, recent, find <term>)
```

### Environment Variables

| Variable | Default | Required for |
|---|---|---|
| `LLM_PROVIDER` | `mock` | Always — set to `gemini`/`openai`/`groq` for real LLM |
| `GEMINI_API_KEY` | — | `LLM_PROVIDER=gemini` |
| `LLM_API_KEY` | — | `openai`, `groq`, `openai-compatible` |
| `GITHUB_TOKEN` | — | GitHub Trending + Releases sources |
| `DUCKDB_PATH` | `~/.jvm-daily/jvm-daily.duckdb` | |
| `OUTPUT_DIR` | `~/.jvm-daily/output` | |
| `CONFIG_PATH` | `config/sources.yml` | |

### Ports

| Service | Port | URL |
|---|---|---|
| Viewer | 8888 | `http://localhost:8888` |
| JobRunr Dashboard | 8000 | `http://localhost:8000/dashboard` |
| Fly.io Production | 443 | `https://jvm-daily.fly.dev` |

### Deploy

```bash
git push origin main             # Auto-deploys via GitHub Actions + Fly.io
fly logs --app jvm-daily         # Monitor
fly ssh console -C "/app/bin/app pipeline"  # Manual run on prod
```

## Tech Stack

- **Language:** Kotlin (latest stable)
- **Build:** Gradle (Kotlin DSL)
- **JVM:** 21
- **AI Agents:** Koog Agents (`ai.koog:koog-agents-jvm`)
- **Database:** DuckDB (via JDBC)
- **HTTP:** Ktor Client
- **Serialization:** kotlinx.serialization
- **Testing:** JUnit 5 + kotlin-test

## Architecture

- **Plugin-based source engine** — each data source is a plugin implementing `Source` interface
- **Multi-stage workflows** — ingress → processing → publishing (pipeline pattern)
- **Agent-based processing** — Koog agents handle per-article multi-stage workflows
