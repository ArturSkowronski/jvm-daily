# Phase 3: Persistence and Idempotency - Context

**Gathered:** 2026-02-27
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 3 hardens storage semantics for raw ingest data so re-runs are deterministic and safe.
Primary scope:
- canonical article identity strategy (stable dedup key)
- repository idempotency guarantees
- validation/backfill tooling for already stored records

Out of scope in this phase:
- new source connectors (Reddit/Twitter/mailing lists)
- summarization logic quality or cluster generation behavior
- publishing/email workflows

</domain>

<decisions>
## Decisions

### Data Integrity
- Persisted raw article rows must retain source metadata (`source_type`, `source_id`) and ingest timestamp for every record.
- Canonical dedup identity must be deterministic across re-runs and source payload variations.

### Idempotency
- Re-running ingest for the same content must not increase raw article cardinality.
- Repository behavior should be explicit and testable for insert/update/duplicate scenarios.

### Delivery Strategy
- Keep scope focused on storage and dedup semantics only.
- Provide a small operator-facing validation/backfill script for existing rows before Phase 4.

### Claude's Discretion
- Specific canonical key algorithm details (normalization rules and fallback ordering).
- Exact script UX and output formatting for validation/backfill commands.

</decisions>

<specifics>
## Specific Ideas

- Prefer reusable canonical-id utility shared by source adapters and storage validation logic.
- Add regression tests around rerun idempotency and edge cases (missing URL, title variants, source-specific IDs).
- Validate historical rows for key completeness and dedup collisions before changing downstream assumptions.

</specifics>

<deferred>
## Deferred Ideas

- UI/reporting dashboards for quality and failure analysis (Phase 7+).
- New connector implementation details for Reddit/Twitter/mailing lists (future milestone).

</deferred>

---

*Phase: 03-persistence-and-idempotency*
*Context gathered: 2026-02-27*
