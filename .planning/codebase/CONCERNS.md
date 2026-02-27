# Codebase Concerns

**Analysis Date:** 2026-02-27

## Tech Debt

**LLM provider implementation mismatch:**
- Issue: Env/config/docs suggest `openai` and `anthropic`, but runtime currently only supports `mock`
- Why: Placeholder abstraction (`LLMClient`) exists before concrete provider integrations
- Impact: Non-mock deployment configuration fails at runtime
- Fix approach: Implement provider clients and wire them in `createLLMClient` in `App.kt`

**Clustering output is transient:**
- Issue: `ClusteringWorkflow` creates clusters in memory and logs them, but does not persist
- Why: Deferred with explicit TODO
- Impact: Outgress cannot use cluster artifacts; downstream features blocked
- Fix approach: Add cluster schema + repository and persist cluster objects

## Known Bugs / Operational Hazards

**Doc-command drift for integration tests:**
- Symptoms: Docs/comments suggest `./gradlew integrationTest`, but task appears undefined
- Trigger: Running command from README/test comment
- Workaround: Run targeted class via `./gradlew test --tests ...`
- Root cause: Build script lacks dedicated integration source set/task

**Potential resource handling in RSS source:**
- Symptoms: Input stream lifecycle depends on parser behavior; explicit close is not visible
- Trigger: Large/long-running ingestion workloads
- Workaround: Keep run frequency moderate and monitor memory/file handles
- Root cause: No explicit `use {}` around RSS input stream in `RssSource`

## Security Considerations

**Secrets via env variables only:**
- Risk: API keys may leak through shell history or misconfigured logs
- Current mitigation: No hardcoded secrets in repo; key loaded from env
- Recommendations: Add `.env.example`, secret-management guidance, and avoid echoing sensitive envs

**Third-party content ingestion:**
- Risk: Untrusted content from RSS could become problematic if rendered unsafely downstream
- Current mitigation: Stored as text and written to markdown without HTML sanitization controls
- Recommendations: Add sanitization/escaping policy before any web rendering pipeline

## Performance Bottlenecks

**Sequential enrichment and clustering:**
- Problem: Article processing loops are primarily sequential, including LLM calls
- Measurement: Not benchmarked in-repo; likely scales linearly with article volume
- Cause: Simplicity-first implementation
- Improvement path: Controlled concurrency + retry/backoff policies

**Repository `saveAll` methods loop one insert at a time:**
- Problem: No batching/transactions for bulk ingestion
- Cause: Naive iteration implementation
- Improvement path: Batched statements or transaction wrapping

## Fragile Areas

**String-based LLM parsing contracts:**
- Why fragile: Parsing relies on exact `SUMMARY:`, `ENTITIES:`, `TOPICS:` markers
- Common failures: Slight format drift from model causes silent parsing degradation
- Safe modification: Introduce schema-constrained outputs (JSON) with robust parsing
- Test coverage: Basic parsing paths covered, adversarial malformed responses limited

**Dual orchestration modes (Airflow + JobRunr):**
- Why fragile: Two scheduling paths can drift operationally
- Common failures: Different env var assumptions or stage ordering issues
- Safe modification: Keep a single source of truth for stage contracts and env docs
- Test coverage: Airflow path lacks automated tests

---
*Concerns audit: 2026-02-27*
*Update as issues are resolved or newly discovered*
