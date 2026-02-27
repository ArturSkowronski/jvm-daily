# Phase 8: Connector Readiness - Research

**Researched:** 2026-02-27
**Domain:** Connector onboarding certification and failed/low-quality inspection readiness
**Confidence:** HIGH

## User Constraints

### Locked Decisions
- New source adapters must be validated through connector contract tests/checklist before production enablement (`ARC-02`).
- Failed/low-quality processed items must be inspectable for manual follow-up (`QLT-03`).
- Team must be able to dry-run a new connector type with bounded impact on existing pipeline.
- Scope is readiness/validation tooling, not full implementation of Reddit/Twitter connectors.

### Claude's Discretion
- Exact structure/format for connector certification checklist artifact.
- Report format for failed/low-quality inspection view (CLI/markdown/table/JSON).
- Skeleton connector selection and dry-run depth.

### Deferred Ideas
- Production-grade non-RSS connector implementations.
- Rich UI for manual triage beyond report-based inspection.

## Summary

The platform already has core boundaries and quality primitives needed for connector readiness:
- `Source` contract and registry guardrails are enforced.
- Ingestion reliability and dedup quality gates are implemented.
- Enrichment failures and warnings are persisted with queryable metadata.
- Quality report command now exists for daily counters.

What remains for Phase 8 is “admission control” and “inspection ergonomics”:
1. A connector certification checklist + contract tests that every new source must pass.
2. A concrete failed/low-quality inspection path for operators (report/command).
3. A dry-run workflow proving a new connector skeleton can be added without destabilizing orchestration.

Primary direction: formalize connector certification artifacts first, then implement inspection report for failed/low-quality records, and finally validate connector skeleton onboarding with dry-run tests/docs.

**Primary recommendation:** treat connector onboarding as a quality gate pipeline with checklist + tests + dry-run command path.

## Current Codebase Observations

- `Source` interface has explicit normalized-record expectations and default outcome wrapping.
- `SourceRegistry` already rejects blank/duplicate `sourceType`.
- Contract tests exist (`SourceContractTest`, `SourceRegistryContractTest`) but not yet shaped as a certification checklist artifact.
- `ProcessedArticleRepository` supports failed item queries; warning/low-quality data exists in persisted records.
- No dedicated operator command currently exists for failed/low-quality inspection report.

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Kotlin stdlib + existing source/workflow/storage modules | current project | connector certification + inspection report tooling | minimizes architectural risk |
| DuckDB repositories | current project | failed/low-quality record inspection queries | existing durable data source |
| JUnit 5 + kotlin-test | current project | connector contract/certification and dry-run regression tests | existing testing baseline |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Existing CLI entrypoint (`App.kt`) | current project | expose inspection and connector dry-run commands | operator workflow integration |
| Markdown docs (`README`, `Findings`) | current project | checklist/runbook artifact and onboarding guidance | human-operable certification path |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| explicit certification checklist | implicit “tests passing means ok” | lacks onboarding consistency and review clarity |
| report command for low-quality items | raw SQL/manual queries | error-prone and inconsistent operational triage |
| skeleton dry-run workflow | full connector implementation | unnecessary scope/risk for readiness phase |

## Architecture Patterns

### Pattern 1: Certification as Contract
**What:** Define connector onboarding checklist mapped to required contract tests.
**When to use:** before enabling any new source adapter.

### Pattern 2: Inspection by Report Artifact
**What:** Generate a deterministic failed/low-quality inspection report from persisted processed records.
**When to use:** routine triage and manual follow-up.

### Pattern 3: Skeleton Dry-Run Before Build-Out
**What:** Add/test a minimal connector skeleton against contracts and pipeline boundaries.
**When to use:** early evaluation of new source types with bounded impact.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| connector readiness decisions | ad hoc PR reviewer judgment | explicit checklist + contract test results | repeatable onboarding quality |
| low-quality triage | manual DB probing | dedicated report command/artifact | consistent operator workflow |
| onboarding safety | immediate full connector coding | skeleton + dry-run gate | lower risk and faster feedback |

## Common Pitfalls

- Treating connector readiness as documentation-only without executable tests.
- Defining low-quality heuristics without grounding in existing persisted fields.
- Skipping dry-run isolation and accidentally coupling connector code to workflow internals.

## Validation Targets for Planning

- Contract test suite/checklist mapping for connector certification.
- Failed/low-quality inspection report tests and deterministic ordering.
- Skeleton connector dry-run validation proving bounded impact and contract compliance.

## Planning Implications

- Plan `08-01` should create certification checklist + enforceable contract tests.
- Plan `08-02` should implement failed/low-quality inspection report command/artifact.
- Plan `08-03` should add and validate a connector skeleton dry-run path against contracts.

---
*Phase: 08-connector-readiness*
*Research completed: 2026-02-27*
