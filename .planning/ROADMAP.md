# Roadmap: JVM Weekly Platform

## Overview

Roadmap hardens the existing JVM Daily brownfield pipeline into a reliable JVM Weekly ingestion platform: first secure architecture boundaries, then reliability of RSS ingest and dedup, then summarization and recoverability, and finally quality gates and connector-readiness for post-v1 sources.

## Phases

- [x] **Phase 1: Architecture Guardrails** - Lock source/workflow/storage boundaries for safe evolution. (completed 2026-02-27)
- [ ] **Phase 2: RSS Ingest Reliability** - Ensure daily RSS ingest remains resilient per feed.
- [ ] **Phase 3: Persistence and Idempotency** - Guarantee rerun-safe storage and canonical dedup behavior.
- [ ] **Phase 4: Summarization Core** - Stabilize summary/entity/topic processing and persistence.
- [ ] **Phase 5: Recoverability Controls** - Add replay/retry controls and independent stage operations.
- [ ] **Phase 6: Daily Automation and Telemetry** - Make daily execution automatic and operationally visible.
- [ ] **Phase 7: Quality Gates** - Enforce data quality assertions and duplicate controls.
- [ ] **Phase 8: Connector Readiness** - Validate onboarding framework for non-RSS connectors.

## Phase Details

### Phase 1: Architecture Guardrails
**Goal**: Core boundaries are explicit and enforceable before further feature hardening.
**Depends on**: Nothing (first phase)
**Requirements**: ARC-01, ARC-03
**Success Criteria** (what must be TRUE):
  1. A new source adapter can be added without editing workflow orchestration code.
  2. Boundary documentation exists and matches actual package/module responsibilities.
  3. Core architecture tests or checks fail when boundary contracts are violated.
**Plans**: 3 plans

Plans:
- [x] 01-01: Formalize source adapter contract and extension guide
- [x] 01-02: Codify workflow-storage boundaries with focused tests
- [x] 01-03: Update architecture documentation and invariants

### Phase 2: RSS Ingest Reliability
**Goal**: Daily RSS ingest is robust against partial source failures.
**Depends on**: Phase 1
**Requirements**: ING-01, ING-02
**Success Criteria** (what must be TRUE):
  1. Daily ingest reads all enabled RSS feeds from config.
  2. Failure of one feed does not stop ingestion of other feeds.
  3. Retry/timeout behavior is visible in logs and test-covered.
**Plans**: 3 plans

Plans:
- [ ] 02-01: Harden feed fetch timeouts/backoff/per-feed isolation
- [ ] 02-02: Improve RSS ingest tests for degraded feed scenarios
- [ ] 02-03: Add ingest run summary for feed-level outcomes

### Phase 3: Persistence and Idempotency
**Goal**: Raw ingest persistence is canonical and rerun-safe.
**Depends on**: Phase 2
**Requirements**: ING-03, ING-04
**Success Criteria** (what must be TRUE):
  1. Raw articles persist with complete source metadata and timestamps.
  2. Rerunning ingest does not create duplicate stored records.
  3. Dedup behavior is deterministic and covered by regression tests.
**Plans**: 3 plans

Plans:
- [ ] 03-01: Canonical dedup key strategy and migration
- [ ] 03-02: Repository-level idempotency and edge-case tests
- [ ] 03-03: Backfill/validation script for existing stored records

### Phase 4: Summarization Core
**Goal**: New raw articles are consistently transformed into reliable processed records.
**Depends on**: Phase 3
**Requirements**: SUM-01, SUM-02
**Success Criteria** (what must be TRUE):
  1. New raw articles are summarized and stored in processed repository.
  2. Entities and topics are persisted and queryable per article.
  3. Summary parsing/validation failures are explicitly surfaced.
**Plans**: 3 plans

Plans:
- [ ] 04-01: Strengthen enrichment parse contract and validation
- [ ] 04-02: Persist and verify entities/topics integrity
- [ ] 04-03: Add summarization quality assertions in tests

### Phase 5: Recoverability Controls
**Goal**: Operators can recover from partial processing failures quickly.
**Depends on**: Phase 4
**Requirements**: SUM-03, OPS-02
**Success Criteria** (what must be TRUE):
  1. Failed enrichment items can be retried without full pipeline rerun.
  2. Each stage can be executed independently for debugging/recovery.
  3. Replay paths are documented and verified by tests.
**Plans**: 3 plans

Plans:
- [ ] 05-01: Build failed-item retry/replay path
- [ ] 05-02: Harden standalone stage CLI/service commands
- [ ] 05-03: Add recovery runbook and test fixtures

### Phase 6: Daily Automation and Telemetry
**Goal**: Daily execution is automated with clear stage-level operational visibility.
**Depends on**: Phase 5
**Requirements**: OPS-01, OPS-03
**Success Criteria** (what must be TRUE):
  1. Full pipeline runs automatically each day on configured scheduler.
  2. Stage logs include start/end status and duration.
  3. Operational failures are visible without deep manual log digging.
**Plans**: 3 plans

Plans:
- [ ] 06-01: Align scheduler path and daily execution contract
- [ ] 06-02: Add structured stage telemetry and duration metrics
- [ ] 06-03: Validate scheduler behavior with integration smoke checks

### Phase 7: Quality Gates
**Goal**: Daily output quality is measurable and regression-resistant.
**Depends on**: Phase 6
**Requirements**: QLT-01, QLT-02
**Success Criteria** (what must be TRUE):
  1. Canonical dedup invariants are enforced by tests and run checks.
  2. Daily quality counters include new, duplicate, and failure categories.
  3. Quality regressions are detectible during routine runs.
**Plans**: 3 plans

Plans:
- [ ] 07-01: Implement canonical dedup assertions across data path
- [ ] 07-02: Generate daily quality report artifact
- [ ] 07-03: Add regression tests for quality counters and thresholds

### Phase 8: Connector Readiness
**Goal**: Platform is demonstrably ready for future non-RSS connector rollout.
**Depends on**: Phase 7
**Requirements**: ARC-02, QLT-03
**Success Criteria** (what must be TRUE):
  1. Connector onboarding checklist and contract tests exist and pass.
  2. Failed/low-quality processed items can be inspected for manual follow-up.
  3. Team can add next connector type with bounded impact to existing pipeline.
**Plans**: 3 plans

Plans:
- [ ] 08-01: Define connector certification checklist + contract tests
- [ ] 08-02: Implement failed/low-quality item inspection view/report
- [ ] 08-03: Dry-run new connector skeleton against contracts

## Progress

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Architecture Guardrails | 3/3 | Complete    | 2026-02-27 |
| 2. RSS Ingest Reliability | 0/3 | Not started | - |
| 3. Persistence and Idempotency | 0/3 | Not started | - |
| 4. Summarization Core | 0/3 | Not started | - |
| 5. Recoverability Controls | 0/3 | Not started | - |
| 6. Daily Automation and Telemetry | 0/3 | Not started | - |
| 7. Quality Gates | 0/3 | Not started | - |
| 8. Connector Readiness | 0/3 | Not started | - |
