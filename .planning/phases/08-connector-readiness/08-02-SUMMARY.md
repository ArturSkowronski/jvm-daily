---
phase: 08-connector-readiness
plan: 02
subsystem: inspection-report
tags: [connector-readiness, quality-inspection, triage]
requires:
  - phase: 08-connector-readiness
    plan: 01
    provides: connector certification contract baseline
provides:
  - Repository query contract for failed or warning-heavy processed records
  - `inspect-quality` CLI command with deterministic report output
  - Operator runbook for failed/low-quality manual follow-up
affects: [app-cli, processed-storage, operations]
tech-stack:
  added: []
  patterns: [deterministic-triage-report, repository-backed-inspection]
key-files:
  created: []
  modified:
    - app/src/main/kotlin/jvm/daily/App.kt
    - app/src/main/kotlin/jvm/daily/storage/ProcessedArticleRepository.kt
    - app/src/main/kotlin/jvm/daily/storage/DuckDbProcessedArticleRepository.kt
    - app/src/test/kotlin/jvm/daily/storage/DuckDbProcessedArticleRepositoryTest.kt
    - app/src/test/kotlin/jvm/daily/AppReplayOptionsTest.kt
    - README.md
key-decisions:
  - "Low-quality candidates include FAILED outcomes or warning count at/above threshold"
  - "Inspection report ordering is processed_at DESC then id ASC for deterministic triage"
patterns-established:
  - "Manual follow-up now has a single CLI entrypoint backed by repository queries"
requirements-completed: [QLT-03]
duration: 18min
completed: 2026-02-27
---

# Phase 8: Connector Readiness Summary (08-02)

**Implemented failed/low-quality inspection query path and operator report command.**

## Performance

- **Duration:** 18 min
- **Tasks:** 3
- **Files modified:** 6

## Accomplishments
- Added `findInspectionCandidates(since, limit, minWarnings)` to processed repository contract and DuckDB implementation.
- Added deterministic inspection filtering logic for failed outcomes and warning-heavy low-quality records.
- Added `inspect-quality` command in `App.kt` with option parsing:
  - `--since-hours`
  - `--limit`
  - `--min-warnings`
  - `--output`
- Added markdown inspection report generation and output artifact (`inspect-quality-<date>.md`).
- Extended tests for repository inspection queries and CLI option parsing.
- Documented inspection command usage and manual follow-up runbook in README.

## Verification
- `./gradlew test --tests 'jvm.daily.storage.DuckDbProcessedArticleRepositoryTest'`
- `./gradlew test --tests 'jvm.daily.AppReplayOptionsTest' --tests 'jvm.daily.storage.DuckDbProcessedArticleRepositoryTest'`
- `rg -n "inspect-quality|failed|low-quality|manual follow-up" README.md`

## Deviations from Plan

None.

## Issues Encountered

None.

## Next Phase Readiness
- Inspection workflow required by QLT-03 is now implemented and test-covered.
- Ready for connector skeleton dry-run contract validation (08-03).

---
*Phase: 08-connector-readiness*
*Plan: 08-02*
