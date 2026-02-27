# Requirements: JVM Weekly Platform

**Defined:** 2026-02-27
**Core Value:** Every morning, have as much relevant JVM information as possible available in one deduplicated place.

## v1 Requirements

### Ingestion

- [x] **ING-01**: User can run a daily RSS ingest that reads all enabled feeds from configuration.
- [x] **ING-02**: User can continue ingest even when individual feeds fail (per-feed isolation with retry/timeout behavior).
- [x] **ING-03**: User can store all fetched raw articles with source metadata and ingest timestamp.
- [x] **ING-04**: User can rerun ingest safely without creating duplicate article records.

### Summarization

- [x] **SUM-01**: User can process newly ingested articles into stored summaries.
- [x] **SUM-02**: User can persist extracted entities/topics linked to each processed article.
- [x] **SUM-03**: User can retry or reprocess failed summarization items without rerunning the entire pipeline.

### Quality

- [ ] **QLT-01**: User can enforce canonical dedup logic (URL/title normalization or equivalent) verified by automated tests.
- [ ] **QLT-02**: User can view daily quality counters (new items, duplicates, feed failures, summarization failures).
- [ ] **QLT-03**: User can inspect a list of failed or low-quality processed items for manual follow-up.

### Operations

- [ ] **OPS-01**: User can run the full pipeline automatically on a daily schedule.
- [x] **OPS-02**: User can run each pipeline stage independently for debugging and recovery.
- [ ] **OPS-03**: User can see stage-level logs including start/end status and basic duration.

### Architecture

- [x] **ARC-01**: User can add a new source type by implementing a source adapter contract without modifying core workflow orchestration.
- [ ] **ARC-02**: User can validate a new source adapter using connector contract tests/checklist before enabling it in production runs.
- [x] **ARC-03**: User can rely on documented boundaries between source adapters, workflows, and storage components.

## v2 Requirements

### New Sources

- **SRC-01**: User can ingest Reddit posts from selected subreddits into the same normalized article model.
- **SRC-02**: User can ingest selected JVM mailing lists into the same normalized article model.
- **SRC-03**: User can ingest Twitter/X content from selected accounts/queries into the same normalized article model.

### Publishing

- **PUB-01**: User can generate and send an email newsletter from curated daily content.
- **PUB-02**: User can publish a web-readable newsletter edition from daily processed content.

## Out of Scope

| Feature | Reason |
|---------|--------|
| Email newsletter publishing in v1 | Explicitly deferred by user to keep v1 focused on ingestion + summarization quality |
| Reddit connector implementation in v1 | Planned for future; v1 focuses on RSS hardening |
| Twitter/X connector implementation in v1 | Planned for future; architecture prepared first |
| Multi-user product UX in v1 | Current audience is single internal user |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| ING-01 | Phase 2 | Complete |
| ING-02 | Phase 2 | Complete |
| ING-03 | Phase 3 | Complete |
| ING-04 | Phase 3 | Complete |
| SUM-01 | Phase 4 | Complete |
| SUM-02 | Phase 4 | Complete |
| SUM-03 | Phase 5 | Complete |
| QLT-01 | Phase 7 | Pending |
| QLT-02 | Phase 7 | Pending |
| QLT-03 | Phase 8 | Pending |
| OPS-01 | Phase 6 | Pending |
| OPS-02 | Phase 5 | Complete |
| OPS-03 | Phase 6 | Pending |
| ARC-01 | Phase 1 | Complete |
| ARC-02 | Phase 8 | Pending |
| ARC-03 | Phase 1 | Complete |

**Coverage:**
- v1 requirements: 16 total
- Mapped to phases: 16
- Unmapped: 0 ✓

---
*Requirements defined: 2026-02-27*
*Last updated: 2026-02-27 after Phase 5 completion*
