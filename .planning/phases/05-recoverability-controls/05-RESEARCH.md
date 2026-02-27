# Phase 5: Recoverability Controls - Research

**Researched:** 2026-02-27
**Domain:** Replay/retry controls for failed enrichment plus stage-isolated execution hardening
**Confidence:** HIGH

## User Constraints

### Locked Decisions
- Failed enrichment items must be retryable without rerunning full ingest + processing pipeline.
- Operators must be able to execute stages independently for debugging/recovery.
- Replay paths must be documented and protected by tests.
- Scope for this phase is recoverability controls only (not scheduler/telemetry quality dashboards).
- Phase must satisfy requirement IDs `SUM-03` and `OPS-02`.

### Claude's Discretion
- Exact replay selector interface (ID list, since-window, limit, dry-run output format).
- Repository API shape for marking/rewriting replayed failed records.
- CLI UX details for standalone stage and replay commands.

### Deferred Ideas
- Automated recurring retries and adaptive retry policy tuning (Phase 6+).
- Rich operational dashboards for replay/failure analytics (Phase 7+).

## Summary

Phase 4 already persists failed enrichment outcomes (`outcome_status=FAILED`, `failure_reason`, `attempt_count`, `last_attempt_at`) and keeps pipeline completion behavior resilient under partial failures. This gives a strong data foundation for Phase 5: recovery can be implemented as deterministic reprocessing of failed IDs, not as ad hoc manual DB surgery.

Current command surface in `App.kt` supports full pipeline and per-stage commands (`ingress`, `enrichment`, `clustering`, `outgress`), which partially satisfies `OPS-02`, but recoverability-focused commands are missing: no replay entrypoint, no failed-item selector, and no operator runbook for safe replay flow.

Primary direction for planning: add repository-backed failed-item query/replay primitives, add a dedicated replay command path that can target failed rows without re-running whole pipeline, harden stage commands for explicit recoverability ergonomics, and lock everything with focused tests plus runbook updates.

**Primary recommendation:** introduce a replay-oriented enrichment execution path and CLI contract that operates on failed IDs deterministically, then test and document end-to-end recovery procedures.

## Current Codebase Observations

- `EnrichmentWorkflow` currently processes only `findUnprocessedRawArticles(since=7.days)`; failed records already in `processed_articles` are excluded from subsequent runs.
- `ProcessedArticleRepository` already exposes `findFailedSince(since)` but has no replay-specific helpers (e.g., failed IDs by age/limit or targeted ID fetch/update flow).
- `DuckDbProcessedArticleRepository` stores the required failure metadata and can query failed rows, so replay selection can be implemented without schema changes.
- `App.kt` provides stage-isolated commands and is the right place to expose replay/recovery command UX.
- Existing tests cover enrichment reliability behavior but not recovery replay command semantics.

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Kotlin stdlib + existing workflow/repository modules | current project | replay orchestration + CLI wiring | keeps implementation aligned with existing architecture |
| DuckDB JDBC repository layer | current project | failed-item selection and replay persistence updates | reuses proven storage path |
| JUnit 5 + kotlin-test | current project | replay and command-contract regression coverage | existing test baseline |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| kotlinx.datetime | current project | deterministic replay cutoff windows and timestamps in tests | replay selector and CLI arg logic |
| kotlinx.coroutines test | current project | deterministic workflow replay behavior tests | enrichment retry/replay path assertions |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| in-process replay command | external SQL/manual scripts only | high operator error risk and weak testability |
| repository-driven failed selector | scan raw + join at workflow layer only | duplicates storage query logic and weakens boundary clarity |
| focused replay tests | manual validation only | no regression protection for recovery guarantees |

## Architecture Patterns

### Pattern 1: Recover by Selector, Not by Full Rerun
**What:** Replay command chooses failed targets (IDs/time window/limit) and reruns enrichment only for those rows.
**When to use:** Any summarization failure recovery incident.

### Pattern 2: Stage Contracts Stay Composable
**What:** Stage commands remain independent and deterministic (`ingress`, `enrichment`, `clustering`, `outgress`, `enrichment-replay`).
**When to use:** Debugging, incident response, and partial reruns.

### Pattern 3: Dry-Run First Recovery
**What:** Replay command supports non-mutating preview of candidate failed rows before applying.
**When to use:** Operator validation before replaying potentially large batches.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| failed-item discovery | one-off SQL in docs | repository methods + tested command | reproducible and safe |
| replay execution path | duplicate enrichment logic | parameterized enrichment workflow selection mode | avoids divergence |
| recovery verification | ad hoc manual checks | deterministic tests + runbook checklist | auditable and repeatable |

## Common Pitfalls

- Reprocessing all recent articles instead of failed-only targets, causing unnecessary cost/noise.
- Losing original failure metadata history when replay overwrites records without clear semantics.
- CLI behavior ambiguity (`since`, `limit`, `ids`) leading to accidental broad reruns.
- Missing dry-run mode for operational safety.
- Documenting replay steps without executable test fixtures.

## Validation Targets for Planning

- Repository replay-selector tests: failed item query by cutoff/limit and deterministic ordering.
- Workflow replay tests: failed IDs can be retried and transitioned to success/failure with updated metadata.
- CLI tests: command parsing and standalone stage command behavior for recovery workflows.
- Runbook verification: docs map to runnable commands and expected counters/status.

## Planning Implications

- Plan `05-01` should implement failed-item selector and replay-capable enrichment path.
- Plan `05-02` should harden stage/replay command contracts and operator-facing command ergonomics.
- Plan `05-03` should add recovery runbook and replay fixtures/tests for operational confidence.

---
*Phase: 05-recoverability-controls*
*Research completed: 2026-02-27*
