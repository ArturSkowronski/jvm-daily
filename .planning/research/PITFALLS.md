# Pitfalls Research

**Domain:** JVM newsletter ingestion/summarization platform
**Researched:** 2026-02-27
**Confidence:** HIGH

## Critical Pitfalls

### Pitfall 1: False dedup confidence

**What goes wrong:** duplicates leak into daily digest despite basic ID checks.  
**Why it happens:** source IDs vary across feed updates or mirrors.  
**How to avoid:** canonical key strategy (normalized URL/title fingerprint) + regression tests.  
**Warning signs:** daily duplicate count fluctuates upward after feed changes.  
**Phase to address:** Phase 3 and Phase 7.

### Pitfall 2: Silent partial failures

**What goes wrong:** pipeline “finishes” but several feeds or enrichment items fail silently.  
**Why it happens:** logs are present but no explicit quality gates or failure summary.  
**How to avoid:** mandatory per-stage counters and failure budget threshold.  
**Warning signs:** low new-item count without corresponding alerts.  
**Phase to address:** Phase 6 and Phase 8.

### Pitfall 3: Connector architecture erosion

**What goes wrong:** each new source requires touching many unrelated modules.  
**Why it happens:** quick source-specific branching in workflow layer.  
**How to avoid:** enforce adapter contract tests and source boundary rules.  
**Warning signs:** PRs for new connector require workflow/storage edits in multiple places.  
**Phase to address:** Phase 1 and Phase 8.

### Pitfall 4: Non-deterministic summarization quality

**What goes wrong:** summary quality drifts and trust drops.  
**Why it happens:** no retry strategy or no quality/format checks on LLM responses.  
**How to avoid:** parse/validation layer + retry queue + failure triage path.  
**Warning signs:** increasing malformed summaries and manual fixes each morning.  
**Phase to address:** Phase 4 and Phase 5.

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Single-pass processing without retry queue | Faster initial implementation | Lost items and manual reruns | MVP prototype only |
| Print-only logs with no summary artifact | Easy instrumentation | Hard to detect trend regressions | Short-lived local experiments |
| No adapter contract tests | Faster connector spikes | Connector additions break core pipeline | Never for production daily runs |

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| RSS feeds | No timeout/backoff and no per-feed isolation | Add timeouts, retries, and feed-level error handling |
| LLM provider | Assume output format is always valid | Validate output shape and fallback/retry on parse errors |
| Scheduler | Trust task success without data-level checks | Add data-quality gates (new/dupe/fail counts) |

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Sequential enrichment for growing volume | Processing window spills into workday | Controlled concurrency and batching | Dozens/hundreds of new items daily |
| Row-by-row writes only | Longer run times and DB contention | Batch/transaction writes where safe | Higher feed count and frequent reruns |

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Leaking API keys in logs or config dumps | Credential compromise | Strict env-var handling and log redaction |
| Unsanitized content propagation to render layer | Injection/XSS risk in future UI | Sanitize/escape before any HTML rendering |

## "Looks Done But Isn't" Checklist

- [ ] Daily run says success but includes feed-level failure counters and thresholds.
- [ ] Dedup works across reruns and across feed URL/title variants.
- [ ] Summaries are persisted and traceable to source article IDs.
- [ ] Connector contract tests exist before adding non-RSS sources.

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| False dedup confidence | Phase 3, 7 | Re-run ingest with fixture duplicates, expect zero duplicate inserts |
| Silent partial failures | Phase 6, 8 | Daily quality report contains failures and non-zero alerts |
| Connector architecture erosion | Phase 1, 8 | New source can be added with adapter + tests only |
| Non-deterministic summarization | Phase 4, 5 | Summary parse failures tracked and retried |

## Sources

- Existing code and tests in repository
- Existing codebase concerns and architecture map
- User-defined v1 goals and out-of-scope boundaries

---
*Pitfalls research for: JVM Weekly ingestion platform*
*Researched: 2026-02-27*
