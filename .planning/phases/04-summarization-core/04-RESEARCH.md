# Phase 4: Summarization Core - Research

**Researched:** 2026-02-27
**Domain:** Reliable enrichment parsing, persistence, and quality checks for processed articles
**Confidence:** HIGH

## User Constraints

### Locked Decisions
- Response format is strict JSON.
- Invalid JSON is a hard parse failure (no processed record persisted).
- Missing `entities`/`topics` normalize to empty lists.
- Empty/blank `summary` is a validation failure.
- Summary length has minimum-only enforcement; over-target length is warning (not failure).
- Topics are open vocabulary in this phase.
- Entities normalize via trim + dedupe, with original casing preserved.
- Retry policy: 2 retries (3 attempts total) for transport/provider failures only.
- Retry includes 429 and uses fixed 2s backoff.
- Failed summarization persists explicit failure state with reason, timestamp, and attempt count.
- Stage continues on partial failures and completes with warning status.
- Topics quality: minimum 1 topic, max 5 topics, topic length 1-40 with invalid entries dropped.
- Missing entities is a quality warning.
- LLM input includes title, content, source metadata, and URL when available.
- Content is not truncated in this phase.
- Empty content with title still processes, but produces quality warning.

### Claude's Discretion
- Exact schema names for failure-state fields.
- Logging/warning message format.
- Internal parser/validator helper module structure.

### Deferred Ideas
- Topic taxonomy governance/whitelist.
- Adaptive retry policies beyond fixed retries/backoff.
- Chunked/map-reduce summarization for long content.

## Summary

Current enrichment logic uses text-tag parsing (`SUMMARY:`, `ENTITIES:`, `TOPICS:`), has no strict contract validation, and swallows failures as runtime exceptions counted in logs only. This is insufficient for Phase 4 goals because parsing reliability, explicit failure persistence, and entities/topics integrity are not enforced structurally.

`ProcessedArticle` and `DuckDbProcessedArticleRepository` already provide stable persistence primitives, but there is no explicit representation for failed summarization attempts and limited quality enforcement around entities/topics/topic cardinality. The workflow can process articles and persist successful output, yet observability of why records failed and deterministic normalization rules remain weak.

Primary direction for planning: introduce JSON-contract parser/validator with retry semantics split by error type, extend persistence to capture failure outcomes explicitly, and add dedicated tests for parser correctness plus entities/topics integrity and quality constraints.

**Primary recommendation:** Refactor enrichment into explicit parse/validate/result steps, persist both success and failure outcomes deterministically, and lock behavior with focused regression tests.

## Current Codebase Observations

- `EnrichmentWorkflow` currently parses line-based tagged text and defaults missing fields silently.
- Processing loop catches exceptions and increments `errorCount`, but does not persist failure metadata.
- `ProcessedArticle` stores `entities` and `topics` as arrays serialized to JSON strings in DuckDB.
- Existing tests validate happy paths but do not enforce strict schema/quality contract from context decisions.
- Integration tests currently depend on tagged LLM output, so migration to strict JSON requires test fixture updates.

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Kotlin stdlib + kotlinx.serialization | current project | strict JSON parsing and typed contract mapping | already in stack; deterministic parsing |
| DuckDB JDBC repository layer | current project | persistence of processed outcomes | existing primary storage path |
| JUnit 5 + kotlin-test | current project | contract/integrity regression coverage | existing test standard |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| kotlinx.datetime | current project | failure timestamp persistence/verification | failure state metadata tests |
| kotlinx.coroutines | current project | retry/backoff behavior in workflow loop | provider retry policy implementation |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| strict typed JSON model | loose map parsing | weaker schema guarantees, harder validation |
| explicit failure persistence | log-only errors | cannot query/report failed records later |
| focused parser tests | integration-only verification | slower feedback, poor root-cause visibility |

## Architecture Patterns

### Pattern 1: Parse -> Validate -> Normalize -> Persist
**What:** Isolate enrichment pipeline steps so each policy decision maps to one deterministic stage.
**When to use:** Every article processed by enrichment stage.

### Pattern 2: Error Taxonomy for Retry Control
**What:** Separate transport/provider failures from parse/validation failures to apply retry only where allowed.
**When to use:** LLM call and response handling.

### Pattern 3: Persist Outcome, Not Just Success
**What:** Persist explicit failure outcomes with metadata for operational visibility.
**When to use:** Any article that exits enrichment without valid processed payload.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| JSON contract handling | ad hoc string slicing | typed serializer + explicit validation layer | deterministic and testable |
| retry decision logic | inline catch-all retry | typed error classes + policy branch | aligns with locked retry rules |
| quality checks | scattered inline ifs | centralized validator helper | easier to test and evolve |

## Common Pitfalls

- Treating parse and transport failures equally, causing invalid retries.
- Persisting partial/invalid summaries without explicit failure state.
- Letting topics exceed constraints or include invalid lengths without normalization.
- Losing failure reason granularity in logs-only handling.
- Breaking integration fixtures by switching contract format without test updates.

## Validation Targets for Planning

- Strict JSON contract tests (invalid JSON, missing fields, blank summary).
- Retry policy tests proving only transport/provider failures retry.
- Persistence tests for entities/topics normalization and failure-state queryability.
- Workflow status tests for partial-failure warning completion behavior.

---
*Phase: 04-summarization-core*
*Research completed: 2026-02-27*
