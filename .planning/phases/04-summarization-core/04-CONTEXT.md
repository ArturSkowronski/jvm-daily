# Phase 4: Summarization Core - Context

**Gathered:** 2026-02-27
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 4 stabilizes transformation of new raw articles into reliable processed records:
- summarize new raw articles and persist to processed repository
- persist entities/topics linked to each processed article
- surface summary parsing/validation failures explicitly

Scope is implementation clarity for this phase only. New capabilities stay out of scope.

</domain>

<decisions>
## Decisions

### LLM Response Contract
- Lock response format to strict JSON.
- Invalid JSON is a hard parse failure (do not persist processed record).
- Missing `entities`/`topics` fields are normalized to empty lists.
- Empty/blank `summary` is a validation failure.
- `summary` length enforces minimum only (no hard max limit).
- Over-target quality length should be persisted with warning (not failure).
- `topics` are open vocabulary (no whitelist in this phase).
- `entities` normalization: trim + deduplicate, preserve casing.

### Error and Retry Policy
- Per-article retry policy: 2 retries (3 attempts total) for transport/provider failures.
- Retry applies only to transport/provider failures (timeouts, 5xx, transient API errors, 429), not parse/validation failures.
- Retry backoff: fixed 2 seconds between attempts.
- Failed summarization records must persist explicit failure state: `failed` status + `ERROR_CODE: message` reason + last attempt timestamp + attempt count.
- Stage continues on partial failures and completes with warning status when any articles fail.

### Quality Rules
- `topics` must contain at least 1 value for a valid processed record.
- Keep at most 5 topics; truncate extras.
- Single topic length must be 1-40 chars; out-of-range topics are dropped.
- Missing `entities` is a quality warning (not a hard failure).

### Summarization Input Scope
- Input payload to LLM always includes: title, content, and source metadata.
- Include article URL as additional context when available.
- Send full content without truncation in this phase.
- If content is empty but title exists, process using title + metadata and mark quality warning.

### Claude's Discretion
- Exact schema/type names for failure-state fields in processed records.
- Logging format and warning message layout.
- Internal helper/module structure for parser and validator.

</decisions>

<specifics>
## Specific Ideas

- Enforce JSON contract via parser tests for malformed JSON and missing fields.
- Add retry tests that explicitly separate transient provider failures from validation failures.
- Keep quality warnings queryable for follow-up quality-gate phase.

</specifics>

<deferred>
## Deferred Ideas

- Topic taxonomy governance/whitelisting (future quality phase).
- Adaptive retry policies beyond fixed retry/backoff.
- Chunking/map-reduce summarization for very long content.

</deferred>

---

*Phase: 04-summarization-core*
*Context gathered: 2026-02-27*
