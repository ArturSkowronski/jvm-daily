# Phase 2: RSS Ingest Reliability - Research

**Researched:** 2026-02-27
**Domain:** RSS ingestion reliability and per-source fault isolation
**Confidence:** HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- Partial feed failures should result in `SUCCESS with warnings`.
- No hard percentage threshold for failed feeds when run completes.
- All feeds failing in a run must produce `FAIL`.
- Run with zero new articles is still `SUCCESS`.
- On feed failure: log and continue immediately.
- End-of-run report must include per-feed summary table.
- No quarantine mechanism in this phase.
- Partial parse results are accepted with warning reporting.

### Claude's Discretion
- Exact enum names and report structure.
- Warning escalation heuristics.

### Deferred Ideas (OUT OF SCOPE)
- Feed quarantine.
</user_constraints>

<research_summary>
## Summary

This phase should harden reliability at the source boundary without changing overall pipeline architecture. The key pattern is fail-isolated feed processing with deterministic outcome classification and explicit run reporting.

Recommended implementation: classify per-feed results (`ok`, `partial`, `failed`), aggregate into run-level status (`SUCCESS`, `SUCCESS_WITH_WARNINGS`, `FAIL`), and expose a machine-readable summary structure that tests can assert.

**Primary recommendation:** implement robust per-feed result model first, then wire retry/timeout policy and reporting around it.
</research_summary>

<architecture_patterns>
## Architecture Patterns

### Pattern 1: Per-feed fault isolation
- Each feed fetch/parse handled independently.
- Any feed failure is non-blocking to other feeds.

### Pattern 2: Explicit run status classification
- Aggregate per-feed outcomes to run status using deterministic rules.
- Keep zero-new-items as success case.

### Pattern 3: Structured reliability reporting
- Emit end-of-run summary with feed-level rows and aggregate counters.
- Prefer stable fields over free-form logs for tests and ops.

### Anti-patterns to avoid
- Global try/catch wrapping all feeds causing all-or-nothing behavior.
- Status inferred from log strings only.
- Retrying endlessly without bounded policy.
</architecture_patterns>

<common_pitfalls>
## Common Pitfalls

### Pitfall: retry policy hides hard failures
- Keep bounded retries and include final failure reason in summary.

### Pitfall: partial parse treated as total fail
- Persist valid entries and record parse warning counts.

### Pitfall: success criteria drift
- Encode run-status rules in tests (including all-feeds-failed edge case).
</common_pitfalls>

<sources>
## Sources

### Primary
- `.planning/phases/02-rss-ingest-reliability/02-CONTEXT.md`
- Existing ingest implementation: `IngressWorkflow`, `RssSource`
- Existing source tests in `app/src/test/kotlin/jvm/daily/source/`
</sources>

---
*Phase: 02-rss-ingest-reliability*
*Research gathered: 2026-02-27*
