# JVM Weekly Platform

## What This Is

Internal platform for building a high-signal JVM daily/weekly digest from ingested ecosystem sources. v1.0 delivers reliable RSS ingestion, deterministic persistence/dedup, summarization workflows, recoverability, and connector-readiness guardrails.

## Core Value

Every morning, have as much relevant JVM information as possible available in one deduplicated place.

## Current State (Shipped v1.0)

- Status: `v1.0` shipped on 2026-02-28
- Scope shipped: 8 phases, 24 plans, 73 tasks
- Git range: `feat(01-01)` -> `feat(phase-8)`
- Code delta (milestone range): 111 files changed, 9374 insertions, 243 deletions
- Kotlin codebase size: ~5968 LOC (`app/**/*.kt`)

### Shipped Capabilities

- Architecture boundary enforcement for source/workflow/storage layers.
- Resilient RSS ingest with per-feed isolation and reliability status reporting.
- Canonical article ID strategy and idempotent storage behavior.
- Enrichment with strict contract parsing, failure metadata, and replay controls.
- Daily automation telemetry and quality report thresholds.
- Connector certification checklist/tests plus failed/low-quality inspection workflow.

## Next Milestone Goals

- Define next milestone requirements from archived v1 learnings.
- Prioritize first post-v1 connector implementation (e.g., Reddit or mailing lists) without breaking source boundary contracts.
- Expand operational quality gates toward connector-specific reliability/quality SLAs.

## Constraints

- Keep architecture extensible; new connectors must not require workflow orchestration rewrites.
- Preserve daily-run reliability and dedup quality baseline as non-negotiable.
- Maintain fast manual triage path for failed/low-quality records.

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Enforce boundary contracts with executable tests | Brownfield safety and predictable extension behavior | ✓ Good |
| Treat reliability and quality counters as first-class runtime outputs | Operators need quick diagnosis without deep log digging | ✓ Good |
| Gate connector onboarding with checklist + contract tests + dry-run | Reduces rollout risk for post-v1 source expansion | ✓ Good |
| Keep v1 focused on core pipeline quality, defer connector implementations | Preserves delivery focus and minimizes scope risk | ✓ Good |

---
*Last updated: 2026-02-28 after v1.0 milestone completion*
