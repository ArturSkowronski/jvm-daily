# Codebase Structure

**Analysis Date:** 2026-02-27

## Directory Layout

```
jvm-daily/
├── app/                    # Kotlin application module (main code + tests)
├── airflow/                # Airflow deployment and DAG orchestration files
├── config/                 # Runtime source configuration (RSS feed list)
├── gradle/                 # Gradle wrapper and version catalog support
├── sources/                # Local markdown sources for ingestion
├── viewer/                 # Lightweight Python viewer utility
├── output/                 # Generated digest markdown output
├── .github/workflows/      # CI/CD automation
├── Dockerfile              # Container build/runtime definition
├── run-ingress.sh          # Cron-friendly local ingress runner
└── README.md               # Project overview and run instructions
```

## Directory Purposes

**`app/src/main/kotlin/jvm/daily/`:**
- Purpose: Main application runtime code
- Key subdirs: `workflow/`, `storage/`, `source/`, `config/`, `model/`, `ai/`
- Entry files: `App.kt`, `PipelineService.kt`, `ExploreDb.kt`

**`app/src/test/kotlin/jvm/daily/`:**
- Purpose: Unit and integration tests
- Key subdirs mirror main modules (`workflow`, `storage`, `source`, `config`)

**`airflow/`:**
- Purpose: Local Airflow stack and DAG
- Key files: `airflow/dags/jvm_daily_pipeline.py`, `airflow/docker-compose.yml`, `airflow/README.md`

## Key File Locations

**Entry Points:**
- `app/src/main/kotlin/jvm/daily/App.kt` - CLI and daemon start point
- `airflow/dags/jvm_daily_pipeline.py` - scheduled task graph

**Configuration:**
- `config/sources.yml` - RSS source list
- `app/build.gradle.kts` - module build and dependency config
- `gradle/libs.versions.toml` - version catalog

**Core Logic:**
- `app/src/main/kotlin/jvm/daily/workflow/*.kt` - stage implementations
- `app/src/main/kotlin/jvm/daily/storage/*.kt` - persistence and table lifecycle
- `app/src/main/kotlin/jvm/daily/source/*.kt` - source plugins

**Testing:**
- `app/src/test/kotlin/jvm/daily/workflow/*`
- `app/src/test/kotlin/jvm/daily/storage/*`

## Naming Conventions

**Files:**
- Kotlin source uses `PascalCase.kt` for classes (`IngressWorkflow.kt`, `DuckDbArticleRepository.kt`)
- Test files suffix with `Test.kt`

**Directories:**
- Lowercase package paths under `jvm/daily/...`
- Feature-by-responsibility grouping (`workflow`, `storage`, `source`)

## Where to Add New Code

- New source plugin: `app/src/main/kotlin/jvm/daily/source/` + tests in matching test package
- New workflow stage: `app/src/main/kotlin/jvm/daily/workflow/` + command wiring in `App.kt`
- New persistence model/repo: `app/src/main/kotlin/jvm/daily/storage/` and `model/`
- New operational DAG steps: `airflow/dags/jvm_daily_pipeline.py`

## Special Directories

- `output/`: generated markdown artifacts (runtime output)
- `airflow/logs/`: runtime logs when local Airflow runs
- `.planning/codebase/`: generated mapping docs for GSD workflows

---
*Structure analysis: 2026-02-27*
*Update when directory layout changes*
