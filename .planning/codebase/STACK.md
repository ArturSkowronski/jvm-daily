# Technology Stack

**Analysis Date:** 2026-02-27

## Languages

**Primary:**
- Kotlin 2.2.0 - Application and test code in `app/src/main/kotlin` and `app/src/test/kotlin`

**Secondary:**
- Python 3.x - Airflow DAG in `airflow/dags/jvm_daily_pipeline.py`, simple viewer in `viewer/serve.py`
- Bash - Operational scripts like `run-ingress.sh` and `entrypoint.sh`
- SQL - Embedded SQL strings in repositories under `app/src/main/kotlin/jvm/daily/storage`

## Runtime

**Environment:**
- JVM 21 (toolchain enforced in `app/build.gradle.kts`)
- Airflow 2.8.1 + Python 3.11 container for orchestration (`airflow/docker-compose.yml`)

**Package Manager / Build:**
- Gradle 8.11 wrapper (`gradlew`, `gradle/wrapper/gradle-wrapper.properties`)
- Version catalog in `gradle/libs.versions.toml`

## Frameworks

**Core:**
- Koog Agents `0.6.1` (`ai.koog:koog-agents-jvm`) for LLM abstraction
- DuckDB JDBC `1.1.3` for local embedded analytics storage
- Rome `2.1.0` for RSS parsing
- kotlinx.serialization + Kaml for YAML config loading
- JobRunr `7.3.2` + H2 `2.2.224` for recurring daemon scheduling

**Testing:**
- JUnit 5 (`org.junit.jupiter`) + Kotlin test assertions
- kotlinx-coroutines-test for suspend workflow tests

## Key Dependencies

**Critical:**
- `org.duckdb:duckdb_jdbc` - persistence for raw and processed articles
- `com.rometools:rome` - RSS ingestion parsing pipeline
- `ai.koog:koog-agents-jvm` - LLM provider abstraction boundary
- `org.jobrunr:jobrunr` - in-process recurring pipeline daemon mode
- `com.charleskorn.kaml:kaml` - `config/sources.yml` parsing

## Configuration

**Environment variables used by runtime:**
- `DUCKDB_PATH`, `CONFIG_PATH`, `SOURCES_DIR`
- `LLM_PROVIDER`, `LLM_API_KEY`, `LLM_MODEL`
- `OUTPUT_DIR`, `OUTGRESS_DAYS`
- `PIPELINE_CRON`, `DASHBOARD_PORT`, `JOBRUNR_STORE`

**Build/config files:**
- `settings.gradle.kts`, `app/build.gradle.kts`
- `gradle/libs.versions.toml`
- `config/sources.yml`

## Platform Requirements

**Development:**
- JDK 21 and Gradle wrapper
- Optional Docker/Podman for Airflow local orchestration

**Production/ops shape currently present:**
- Docker image (`Dockerfile`) deploy target includes JVM app + Python viewer
- Optional Airflow stack via `airflow/docker-compose.yml`

---
*Stack analysis: 2026-02-27*
*Update after major dependency/runtime changes*
