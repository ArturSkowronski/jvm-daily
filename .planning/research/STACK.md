# Stack Research

**Domain:** JVM ecosystem newsletter ingestion and summarization platform
**Researched:** 2026-02-27
**Confidence:** HIGH

## Recommended Stack

### Core Technologies

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Kotlin + JVM | Kotlin 2.2 / Java 21 | Core pipeline implementation | Strong fit with existing brownfield codebase and JVM ecosystem domain |
| DuckDB | 1.1.x+ | Embedded analytical and operational store | Fast local analytics, simple ops footprint, already integrated |
| Gradle (Kotlin DSL) | 8.x | Build/test/packaging | Standard for Kotlin/JVM projects, already in use |
| Airflow (optional orchestrator) | 2.8.x+ | Scheduled DAG execution and retries | Good visibility and retry semantics for daily batch workflows |

### Supporting Libraries

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Rome | 2.1.x | RSS parsing | Required for robust feed ingestion |
| kotlinx.serialization + Kaml | 1.7.x / 0.67.x | Config parsing and typed data | Keep source configuration explicit and auditable |
| Coroutines | 1.9.x | Async stage execution | For controlled concurrency in fetch/enrichment stages |
| JobRunr + H2 | 7.x / 2.2.x | Lightweight in-process scheduling mode | Local daemon mode outside Airflow |

### Development Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| JUnit5 + kotlin-test | Unit/integration tests | Keep contract tests around source adapters and dedup |
| GitHub Actions | CI for test/build | Existing pipeline should gate merge on tests |
| Docker | Reproducible runtime | Useful for Airflow and deployment alignment |

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| DuckDB | PostgreSQL | When multi-user concurrent writes and external service availability are primary goals |
| Airflow + CLI stages | Pure cron + shell scripts | For minimal operations when observability requirements are very low |
| Kotlin monolith pipeline | Python-first ETL stack | If team skills and ecosystem are primarily Python-based |

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| Premature microservices for each stage | High ops overhead for single-user v1 | Keep modular monolith with strong interfaces |
| Source-specific logic inside orchestration layer | Makes adding connectors expensive | Isolate in `Source` adapter boundary |
| “One-shot” no-metrics daily jobs | Failures stay silent | Add per-stage counters, logs, and quality reports |

## Version Compatibility

| Package A | Compatible With | Notes |
|-----------|-----------------|-------|
| Kotlin 2.2.0 | Java 21 + Gradle 8.x | Current baseline in codebase |
| DuckDB JDBC 1.1.x | Java 21 | Works in existing repositories and tests |
| Rome 2.1.x | Kotlin/JVM pipeline | Stable RSS parser for current ingestion flow |

## Sources

- Existing project implementation (`app/src/main/kotlin/jvm/daily/*`) — high confidence
- Existing operational docs (`README.md`, `airflow/README.md`) — high confidence
- Existing codebase map in `.planning/codebase/*` — high confidence

---
*Stack research for: JVM Weekly ingestion platform*
*Researched: 2026-02-27*
