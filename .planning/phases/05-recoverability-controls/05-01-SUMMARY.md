---
phase: 05-recoverability-controls
plan: 01
subsystem: enrichment-replay
tags: [recoverability, replay, enrichment, repository]
requires:
  - phase: 04-summarization-core
    provides: persisted failure metadata and strict enrichment outcomes
provides:
  - Failed-item replay selectors in processed repository
  - Targeted enrichment replay mode for explicit raw article IDs
  - Replay-focused regression tests for repository/workflow behavior
affects: [summarization-core, recoverability-controls]
tech-stack:
  added: []
  patterns: [deterministic-replay-selection, targeted-stage-reexecution]
key-files:
  created: []
  modified:
    - app/src/main/kotlin/jvm/daily/storage/ProcessedArticleRepository.kt
    - app/src/main/kotlin/jvm/daily/storage/DuckDbProcessedArticleRepository.kt
    - app/src/main/kotlin/jvm/daily/workflow/EnrichmentWorkflow.kt
    - app/src/test/kotlin/jvm/daily/storage/DuckDbProcessedArticleRepositoryTest.kt
    - app/src/test/kotlin/jvm/daily/workflow/EnrichmentWorkflowReliabilityTest.kt
key-decisions:
  - "Expose failed replay selectors as repository contract methods, not ad hoc SQL"
  - "Use explicit replay ID set in EnrichmentWorkflow to avoid full unprocessed scan during recovery"
patterns-established:
  - "Recover by targeted failed ID replay, independent from default unprocessed flow"
requirements-completed: [SUM-03, OPS-02]
duration: 25min
completed: 2026-02-27
---

# Phase 5: Recoverability Controls Summary

**Implemented deterministic failed-item selection and targeted enrichment replay execution without rerunning the full pipeline.**

## Performance

- **Duration:** 25 min
- **Started:** 2026-02-27T23:14:00Z
- **Completed:** 2026-02-27T23:39:00Z
- **Tasks:** 3
- **Files modified:** 8

## Accomplishments
- Added replay-oriented `ProcessedArticleRepository` methods:
  - `findFailedRawArticleIds(since, limit)`
  - `findFailedByIds(ids)`
- Implemented deterministic DuckDB selectors for failed replay candidates.
- Added `EnrichmentWorkflow` replay mode via `replayRawArticleIds` while preserving default unprocessed flow.
- Added replay regression tests for repository ordering/filtering and workflow targeted replay behavior.

## Task Commits

1. **Task 1-3: failed replay selectors + targeted replay mode + regression tests** - `<pending>` (feat)

## Files Created/Modified
- `app/src/main/kotlin/jvm/daily/storage/ProcessedArticleRepository.kt` - replay selector contract methods.
- `app/src/main/kotlin/jvm/daily/storage/DuckDbProcessedArticleRepository.kt` - failed-item selector implementations.
- `app/src/main/kotlin/jvm/daily/workflow/EnrichmentWorkflow.kt` - targeted replay execution mode.
- `app/src/test/kotlin/jvm/daily/storage/DuckDbProcessedArticleRepositoryTest.kt` - replay selector deterministic behavior tests.
- `app/src/test/kotlin/jvm/daily/workflow/EnrichmentWorkflowReliabilityTest.kt` - replay-mode workflow tests.

## Decisions Made
- Replay selectors return stable ordering (latest processed failures first, then ID tie-break).
- Replay mode skips missing raw IDs and continues deterministic processing of available targets.

## Deviations from Plan

None.

## Issues Encountered

None.

## User Setup Required

None.

## Next Phase Readiness
- Foundation for CLI replay command is now in place.
- Plan 05-02 can wire operator-facing replay/stage commands to these selectors.

---
*Phase: 05-recoverability-controls*
*Completed: 2026-02-27*
