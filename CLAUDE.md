# JVM Daily — Project Conventions

## Development Rules

1. **Minimal implementation** — always implement the minimum viable version first. No over-engineering.
2. **Unit tests required** — every new component must have unit tests. No exceptions.
3. **Always run build** — run `./gradlew build` after every change to verify compilation and tests pass.
4. **Branch workflow** — always work on feature branches, never commit directly to `main`.
5. **Findings** — save all interesting discoveries, gotchas, and learnings to `Findings.md`.
6. **PR workflow** — push to GitHub and create a PR when work is complete.

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
