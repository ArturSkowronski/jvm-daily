# Phase 3: Persistence and Idempotency - Research

**Researched:** 2026-02-27
**Domain:** Kotlin + DuckDB persistence guarantees for ingest dedup/idempotency
**Confidence:** HIGH

## User Constraints

### Locked Decisions
- Persisted raw article rows must retain source metadata (`source_type`, `source_id`) and ingest timestamp for every record.
- Canonical dedup identity must be deterministic across re-runs and source payload variations.
- Re-running ingest for the same content must not increase raw article cardinality.
- Repository behavior should be explicit and testable for insert/update/duplicate scenarios.
- Keep scope focused on storage and dedup semantics only.
- Provide a small operator-facing validation/backfill script for existing rows before Phase 4.

### Claude's Discretion
- Specific canonical key algorithm details (normalization rules and fallback ordering).
- Exact script UX and output formatting for validation/backfill commands.

### Deferred Ideas
- UI/reporting dashboards for quality and failure analysis (Phase 7+).
- New connector implementation details for Reddit/Twitter/mailing lists (future milestone).

## Summary

Current storage already uses `articles.id` as primary key with `INSERT OR REPLACE`, which is a good base for idempotency, but canonical identity generation is still distributed across source-specific code paths. To satisfy Phase 3 requirements cleanly, identity generation should be centralized in a single canonical key strategy and covered by deterministic regression tests.

The ingress path currently avoids duplicates via `existsById` checks before `save`, but long-term idempotency should rely on canonical keys and repository guarantees, not only workflow-level checks. Repository-level tests should explicitly cover reruns and variant payload cases (same URL/title variants, missing URL fallbacks, and source-specific identifiers).

Backfill/validation should remain lightweight: detect rows violating canonical-key assumptions, optionally produce deterministic replacement IDs, and report collisions before mutating data. This keeps risk low and prepares safe migration for future phases.

**Primary recommendation:** Implement a centralized canonical dedup key utility, adopt it in ingest paths, then add repository-level idempotency tests and a validation/backfill script for existing records.

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Kotlin stdlib + existing project modules | current | canonical key normalization and deterministic mapping | avoids unnecessary dependencies and matches existing architecture |
| DuckDB via JDBC (`duckdb_jdbc`) | 1.1.3 (current project) | primary-key-enforced idempotent persistence | already integrated, low migration risk |
| JUnit 5 + kotlin-test | current project | regression/idempotency test coverage | existing test stack and patterns already in repo |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| kotlinx.datetime | current project | deterministic timestamp handling in tests and migrations | when asserting stable persisted metadata |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| in-repo canonical key helper | external hashing/slug libs | extra dependency surface without clear payoff for current scope |
| focused repository tests | full integration-only coverage | slower feedback, harder fault isolation |

## Architecture Patterns

### Pattern 1: Canonical Key Utility as Single Source of Truth
**What:** One utility computes canonical dedup keys from normalized source fields.
**When to use:** Any write-path producing `Article.id` (RSS, markdown, future connectors, backfill tooling).

### Pattern 2: Repository Guarantees + Workflow Guards
**What:** Keep workflow duplicate checks, but enforce idempotency at repository key level.
**When to use:** All ingest reruns and adapter retries.

### Pattern 3: Validate First, Mutate Second
**What:** Backfill tooling first reports mismatches/collisions; update mode is explicit and bounded.
**When to use:** Existing datasets where IDs might not match new canonical rules.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| dedup consistency | per-adapter ad hoc key logic | shared canonical-key utility | prevents drift across adapters |
| idempotency confidence | manual local checks | automated repository/workflow regression tests | repeatable proof in CI |
| migration safety | blind in-place rewrites | validate/report mode before update mode | avoids accidental data corruption |

## Common Pitfalls

- Deriving IDs differently across adapters, causing duplicate rows for equivalent content.
- Treating `existsById` in workflow as sole dedup protection without repository-level assertions.
- Updating historical IDs without collision report, which can silently overwrite data.
- Allowing title/url normalization rules to vary between runtime and backfill paths.

## Validation Targets for Planning

- Canonical key behavior matrix for URL/title/source-id edge cases.
- Idempotent persistence tests proving reruns preserve row cardinality.
- Backfill script dry-run report with explicit collision counts and no mutation by default.

---
*Phase: 03-persistence-and-idempotency*
*Research completed: 2026-02-27*
