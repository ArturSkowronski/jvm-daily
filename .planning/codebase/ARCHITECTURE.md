# Architecture

**Analysis Date:** 2026-02-27

## Pattern Overview

**Overall:** Monolithic Kotlin batch pipeline with stage-based workflows.

**Key Characteristics:**
- Single executable entry (`jvm.daily.AppKt`) with command-driven stage execution
- Plugin-like ingestion boundary via `Source` interface and `SourceRegistry`
- Repository abstractions over embedded DuckDB tables
- Optional external orchestration via Airflow; optional internal orchestration via JobRunr daemon mode

## Layers

**Entry/Orchestration Layer:**
- Purpose: Parse command, wire dependencies, execute one or more workflows
- Contains: `App.kt`, `PipelineService.kt`, Airflow DAG
- Depends on: workflow and infrastructure layers

**Workflow Layer:**
- Purpose: Encapsulate pipeline stages (`ingress`, `enrichment`, `clustering`, `outgress`)
- Contains: classes in `app/src/main/kotlin/jvm/daily/workflow`
- Depends on: source/repository abstractions and LLM client

**Source Layer:**
- Purpose: Fetch raw articles from heterogeneous inputs
- Contains: `Source`, `RssSource`, `MarkdownFileSource`, `SourceRegistry`
- Depends on: model layer + external libs (Rome, filesystem)

**Storage Layer:**
- Purpose: Persist/retrieve raw and processed article entities
- Contains: `ArticleRepository`, `ProcessedArticleRepository`, DuckDB implementations
- Depends on: JDBC connection + serialization

**Model/Config Layer:**
- Purpose: DTOs and strongly typed config parsing
- Contains: `model/*`, `config/SourcesConfig.kt`

## Enforced Boundaries

Architecture boundaries are now executable, not advisory. The following tests
guard dependency direction and layer isolation:

- `app/src/test/kotlin/jvm/daily/architecture/WorkflowBoundaryTest.kt`
- `app/src/test/kotlin/jvm/daily/architecture/LayerDependencyTest.kt`

### Boundary Rules

- Workflow package must not import concrete source implementations.
- Workflow package must not import concrete DuckDB repositories directly.
- Source package must not depend on workflow package.
- Storage package must not depend on workflow package or concrete source implementations.

These checks run in the default `./gradlew test` path.

## Data Flow

**Pipeline run (batch):**
1. Entry command selects stage (`pipeline|ingress|enrichment|clustering|outgress`)
2. Ingress reads sources and writes deduplicated rows to `articles`
3. Enrichment selects unprocessed raw articles, calls LLM, writes to `processed_articles`
4. Clustering reads recent processed rows, builds in-memory clusters (not persisted yet)
5. Outgress reads processed rows and writes markdown digest files

**State Management:**
- Persistent state in DuckDB tables and output markdown files
- No long-lived domain state in memory across process restarts

## Key Abstractions

**`Workflow`:**
- Purpose: Stage contract with `name` and suspend `execute()`
- Examples: `IngressWorkflow`, `EnrichmentWorkflow`, `ClusteringWorkflow`, `OutgressWorkflow`

**`Source`:**
- Purpose: Input plugin contract for article acquisition
- Pattern: interface + registry composition

**Repository Interfaces:**
- Purpose: Persistence boundary for raw/processed article lifecycle
- Pattern: interface + concrete DuckDB implementation

## Entry Points

- `app/src/main/kotlin/jvm/daily/App.kt`: main executable routing and dependency wiring
- `app/src/main/kotlin/jvm/daily/PipelineService.kt`: reusable full pipeline invocation
- `airflow/dags/jvm_daily_pipeline.py`: scheduled orchestration and conditional branching

## Error Handling

**Strategy:**
- Mostly local `try/catch` inside long loops for per-item resilience
- Fail-fast for invalid startup configuration (e.g., missing API key for non-mock provider)

**Patterns:**
- RSS fetch failures are logged and converted to empty list (source isolation)
- Enrichment per-article failures are counted and do not stop the whole stage

## Cross-Cutting Concerns

**Logging:**
- Console logging with stage prefixes (`[ingress]`, `[enrichment]`, etc.)

**Validation:**
- Config parsing and null/empty checks at boundaries

**Scheduling:**
- External (Airflow) and internal (JobRunr+H2) options coexist

---
*Architecture analysis: 2026-02-27*
*Update when major patterns or stage boundaries change*
