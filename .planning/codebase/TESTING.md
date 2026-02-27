# Testing Patterns

**Analysis Date:** 2026-02-27

## Test Framework

**Runner:**
- JUnit 5 platform (`useJUnitPlatform()` in `app/build.gradle.kts`)
- Kotlin test assertions (`kotlin-test-junit5`)

**Async support:**
- `kotlinx-coroutines-test` with `runTest` for suspend functions

**Run Commands:**
```bash
./gradlew test                                    # Run configured test suite
./gradlew test --tests '*IngressWorkflowTest*'    # Single test class
./gradlew test --tests '*ProcessingPipelineIntegrationTest*'  # Focused integration-style class
```

## Test File Organization

- Tests are colocated by package mirror under `app/src/test/kotlin/jvm/daily/...`
- Naming convention is `<ClassOrFeature>Test.kt`
- Integration-style scenarios are mixed into same tree and may use `@Tag("integration")`

## Test Structure

**Observed patterns:**
- Arrange/act/assert flow with explicit fixture setup
- `@BeforeEach` / `@AfterEach` for DB connection lifecycle in repository/integration tests
- Helper factory methods per test file for readable fixture creation

## Mocking

- Lightweight handwritten stubs over framework-heavy mocking
- Example: anonymous `LLMClient` implementations in workflow tests
- Example: in-memory fake `ArticleRepository` for ingress unit tests

## Fixtures and Factories

- Inline factory helpers (`article(...)`, `createArticle(...)`) are standard
- Temporary filesystem fixtures for source tests (markdown source behavior)
- In-memory DuckDB used for persistence-focused tests

## Coverage and Gaps

**Covered well:**
- Source ingestion behavior (`RssSource`, `MarkdownFileSource`, registry)
- Workflow stage logic (ingress, enrichment, outgress, runner)
- Repository CRUD and dedup behavior

**Gaps observed:**
- `integrationTest` task is mentioned in docs/tests comments but not defined in Gradle build script
- No explicit coverage task/reporting gate in Gradle config
- Airflow DAG behavior not covered by automated Python tests in repo

## Common Patterns

- Prefer deterministic stubs and local in-memory DB for fast feedback
- Assert both functional outcomes and aggregate counts
- Use coroutine test utilities whenever stage executes suspend code

---
*Testing analysis: 2026-02-27*
*Update when test tasking, frameworks, or conventions change*
