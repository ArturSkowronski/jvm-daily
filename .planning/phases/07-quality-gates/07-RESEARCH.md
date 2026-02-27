# Phase 7: Quality Gates - Research

**Researched:** 2026-02-27
**Domain:** Dedup quality assertions and daily quality counter reporting
**Confidence:** HIGH

## User Constraints

### Locked Decisions
- Canonical dedup invariants must be enforced through automated tests and runtime checks (`QLT-01`).
- Daily quality counters must include at least new items, duplicates, feed failures, and summarization failures (`QLT-02`).
- Quality regressions must be detectable during routine runs (test/CI/smoke paths).
- Scope is quality gates/reporting; operator inspection UX for low-quality items is Phase 8.

### Claude's Discretion
- Exact output format for quality report artifact (stdout table, markdown/json file, or both).
- Where quality counters are computed (pipeline orchestrator, workflow summaries, dedicated tool).
- Threshold policy specifics (warning/fail limits) for regression checks.

### Deferred Ideas
- Interactive quality dashboard/UI.
- Historical trend storage and anomaly detection.

## Summary

The codebase already has strong primitive signals for quality gating:
- Canonical ID generation is centralized (`CanonicalArticleId`).
- Ingress tracks per-feed `newCount` / `duplicateCount` / failures.
- Enrichment persists explicit failure outcomes and warnings.
- Pipeline telemetry now emits structured stage-level status/durations.

What is missing is a unified quality gate layer that transforms those signals into:
1. deterministic assertions (tests/checks for dedup invariants),
2. daily counters artifact for operators, and
3. regression detection path integrated with routine runs.

Primary direction: first codify dedup/duplicate invariants as explicit tests + check utility, then produce daily quality report counters from storage/workflow outcomes, and finally enforce thresholds/regression checks in automated tests/smoke flow.

**Primary recommendation:** implement a small quality report generator + dedup assertion suite and wire it into the standard pipeline/testing workflow.

## Current Codebase Observations

- `CanonicalArticleId` normalization rules exist and are unit-tested, but end-to-end invariants across ingest/report layers are not surfaced as a quality gate artifact.
- `IngressWorkflow` logs duplicate/new counts per feed but does not persist a daily quality summary object/artifact.
- `EnrichmentWorkflow` failure and warning counts are logged and persisted, creating reliable inputs for quality counters.
- No current dedicated command such as `quality-report` to aggregate and present daily quality metrics.
- README documents reliability/replay behavior, but quality-gate runbook section is not yet present.

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Kotlin stdlib + existing workflow/storage modules | current project | quality counter aggregation and gating logic | keeps architecture consistent |
| DuckDB repositories | current project | source of truth for dedup/failure counters | no new storage dependency |
| JUnit 5 + kotlin-test | current project | quality invariant/regression enforcement | existing test baseline |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| kotlinx.datetime | current project | daily window boundaries for counters | report generation and tests |
| existing CLI entrypoint (`App.kt`) | current project | expose quality-report command | operator execution path |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| explicit quality report command | parse logs only | brittle and hard to automate |
| deterministic threshold tests | manual checks | regression slips likely |
| repository-backed counters | ad hoc in-memory tracking | unreliable across reruns |

## Architecture Patterns

### Pattern 1: Counter-Driven Quality Report
**What:** Aggregate daily counters from durable data (raw + processed repositories).
**When to use:** post-pipeline daily run and on-demand operator checks.

### Pattern 2: Dedup Invariant Assertions
**What:** Assert canonical-id and duplicate-cardinality invariants in tests/check commands.
**When to use:** CI and regression testing.

### Pattern 3: Threshold-Based Regression Gate
**What:** Evaluate counter outputs against expected/allowed ranges and fail tests when violated.
**When to use:** routine validation and smoke checks.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| quality visibility | grep raw logs manually | structured quality report artifact | reproducible and machine-readable |
| dedup confidence | one-off spot checks | dedicated invariant tests | stable regression protection |
| failure trend detection | subjective interpretation | threshold assertions | objective go/no-go gating |

## Common Pitfalls

- Counting duplicates inconsistently across feed-level and global metrics.
- Defining thresholds without deterministic fixtures/tests.
- Mixing runtime observability logs with quality report artifact responsibilities.
- Neglecting failed enrichment outcomes in daily quality summaries.

## Validation Targets for Planning

- Tests asserting canonical dedup invariants across ingest flow.
- Quality report tests for counter correctness (new, duplicate, feed failures, summarization failures).
- Regression tests/smoke checks that fail on threshold breaches.

## Planning Implications

- Plan `07-01` should implement canonical dedup assertions across runtime/test paths.
- Plan `07-02` should add a quality report artifact and command.
- Plan `07-03` should add regression threshold tests and runbook integration.

---
*Phase: 07-quality-gates*
*Research completed: 2026-02-27*
