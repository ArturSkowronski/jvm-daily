# Phase 6: Daily Automation and Telemetry - Research

**Researched:** 2026-02-27
**Domain:** Scheduler contract hardening and stage-level operational telemetry for daily runs
**Confidence:** HIGH

## User Constraints

### Locked Decisions
- Full pipeline must run automatically each day on configured scheduler (`OPS-01`).
- Stage logs must include start/end status and duration (`OPS-03`).
- Operational failures must be visible without deep manual log digging.
- Scope is automation + telemetry; broader quality analytics stay in later phases.

### Claude's Discretion
- Which scheduler is treated as canonical for v1 contract (JobRunr daemon, Airflow DAG, or explicit dual-mode policy).
- Telemetry artifact format (structured log lines vs JSON report file vs both).
- Minimal smoke-check strategy and test fixture shape for scheduler validation.

### Deferred Ideas
- Alerting integrations (Slack/email/pager) for stage failures.
- Historical telemetry dashboards and trend visualization.

## Summary

The codebase currently has two automation surfaces:
1. **JobRunr daemon path** in `App.kt` (`main` with no args) with `PIPELINE_CRON`.
2. **Airflow DAG path** in `airflow/dags/jvm_daily_pipeline.py` with its own cron and task orchestration.

This duality creates contract risk: daily automation semantics can drift between runtime paths. Phase 6 should define and enforce one explicit contract (single canonical scheduler or explicitly documented compatibility contract), then add tests/docs to prevent silent drift.

Stage telemetry is partially present (`[pipeline] ▶/✓` with elapsed ms), but currently unstructured and success-only per step in `PipelineService`. There is no explicit stage outcome model with failure records, run identifiers, or machine-readable artifact for quick diagnosis.

Primary direction for planning: first align scheduler contract and execution entrypoint semantics, then introduce structured stage telemetry + durations + outcome statuses, and finish with smoke checks that validate scheduler behavior and telemetry output in a reproducible way.

**Primary recommendation:** define a canonical daily-run contract in code/docs, add structured telemetry emitted per stage/run, and lock behavior with targeted smoke/integration checks.

## Current Codebase Observations

- `PipelineService` logs stage start/end + duration but has no structured outcome object or persisted run report.
- `PipelineServiceTest` verifies order and fail-fast only; no telemetry assertions.
- `App.startDaemon` schedules `PipelineService.run` via JobRunr with env-driven cron.
- Airflow DAG independently schedules `ingress -> branch -> enrichment -> clustering -> outgress` at `0 7 * * *`.
- Existing workflow logs (ingress/enrichment) include useful status summaries, but aggregation into one operational telemetry artifact is missing.

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Kotlin stdlib + existing app modules | current project | scheduler contract + telemetry implementation | preserves current architecture |
| JobRunr (current usage) | project current | in-process scheduler contract | already wired in `App.kt` |
| JUnit 5 + kotlin-test | current project | automation/telemetry regression checks | existing test baseline |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| kotlinx.serialization | current project | structured telemetry JSON artifact (if selected) | machine-readable run reports |
| Airflow DAG python checks | current project | scheduler-path smoke validation | when keeping Airflow parity contract |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| structured local telemetry artifact | logs only | harder to parse and automate failure visibility |
| canonicalized scheduler contract | independent schedulers with no parity checks | drift risk and ambiguous operator behavior |
| smoke tests | manual scheduler verification | fragile, non-repeatable ops validation |

## Architecture Patterns

### Pattern 1: One Contract, Multiple Entrypoints
**What:** Define expected daily pipeline contract once; validate both JobRunr and Airflow paths against it (or deprecate one path).
**When to use:** scheduler configuration and docs updates.

### Pattern 2: Stage Telemetry Envelope
**What:** Emit structured per-stage records: `stage`, `status`, `started_at`, `ended_at`, `duration_ms`, optional `error`.
**When to use:** every pipeline run step.

### Pattern 3: Smoke Checks as Ops Guardrail
**What:** Add lightweight tests/check scripts proving scheduler invocation and telemetry output format.
**When to use:** CI and pre-release reliability checks.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| scheduler drift handling | ad hoc docs-only notes | code-level contract constants + tests | prevents silent divergence |
| stage diagnostics | unstructured print strings only | structured telemetry records + summary line | supports quick failure triage |
| scheduler validation | manual runbook only | executable smoke checks in test/CI | repeatable operational confidence |

## Common Pitfalls

- Keeping cron defaults different between JobRunr and Airflow without explicit rationale.
- Logging durations only for success path; losing failed-stage timing context.
- Emitting telemetry but not documenting where operators should read it.
- Over-coupling telemetry to one scheduler path and leaving the other unverified.

## Validation Targets for Planning

- Unit tests for stage telemetry emission on success/failure with duration capture.
- Scheduler contract tests for CLI/daemon path and (if retained) Airflow DAG schedule parity.
- Smoke checks proving full pipeline scheduled run path and telemetry artifact visibility.

## Planning Implications

- Plan `06-01` should align scheduler path and daily execution contract.
- Plan `06-02` should add structured stage telemetry model/emission + tests.
- Plan `06-03` should add scheduler/telemetry smoke checks and runbook alignment.

---
*Phase: 06-daily-automation-and-telemetry*
*Research completed: 2026-02-27*
