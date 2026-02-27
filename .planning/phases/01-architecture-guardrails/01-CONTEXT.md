# Phase 1: Architecture Guardrails - Context

**Gathered:** 2026-02-27
**Status:** Ready for planning

<domain>
## Phase Boundary

Establish enforceable architecture boundaries between source adapters, workflow orchestration, and storage so new source adapters can be added with minimal impact and clear contracts.

</domain>

<decisions>
## Implementation Decisions

### Source adapter contract
- Each adapter should perform both fetch and normalize, returning the shared normalized article model.
- Output contract should allow partial records (not all fields required for every source).
- Canonical record identity for deduplication should be computed centrally as fingerprint logic.
- Adapter-level failures should be isolated: fail the adapter, continue the pipeline run for other adapters.

### Claude's Discretion
- Exact normalized schema field-level required/optional matrix.
- Fingerprint algorithm details and collision-handling strategy.
- Error budget and retry count thresholds per adapter.

</decisions>

<specifics>
## Specific Ideas

- Prioritize resilience of the whole daily run over strict per-source perfection.
- Keep source onboarding cheap by pushing stable identity and dedup guarantees to central logic.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 01-architecture-guardrails*
*Context gathered: 2026-02-27*
