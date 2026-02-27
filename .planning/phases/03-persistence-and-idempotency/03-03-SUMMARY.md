---
phase: 03-persistence-and-idempotency
plan: 03
subsystem: infra
tags: [backfill, validation, operations, cli]
requires:
  - phase: 03-persistence-and-idempotency
    provides: canonical ID strategy and idempotency tests
provides:
  - Raw article ID validation/backfill tool
  - CLI command for dry-run and apply modes
  - Operator documentation for safe execution
affects: [operations, summarization-core]
tech-stack:
  added: []
  patterns: [dry-run-first-tooling, collision-safe-backfill]
key-files:
  created:
    - app/src/main/kotlin/jvm/daily/tools/ValidateRawArticleIds.kt
    - app/src/test/kotlin/jvm/daily/tools/ValidateRawArticleIdsTest.kt
  modified:
    - app/src/main/kotlin/jvm/daily/App.kt
    - README.md
    - Findings.md
key-decisions:
  - "Default validation mode must be non-mutating"
  - "Apply mode should skip conflicting target IDs and report collisions"
patterns-established:
  - "Backfill tooling follows dry-run → verify → apply safety flow"
requirements-completed: [ING-03, ING-04]
duration: 30min
completed: 2026-02-27
---

# Phase 3: Persistence and Idempotency Summary

**Historical raw rows can now be safely validated and backfilled to canonical IDs through a dry-run-first CLI workflow.**

## Performance

- **Duration:** 30 min
- **Started:** 2026-02-27T21:57:00Z
- **Completed:** 2026-02-27T22:27:00Z
- **Tasks:** 3
- **Files modified:** 5

## Accomplishments
- Added `ValidateRawArticleIds` tool with mismatch/collision reporting and optional update mode.
- Exposed `validate-raw-ids [--apply]` command in `App.kt` for operator execution.
- Added deterministic tests for dry-run, update, and collision-safe behavior.
- Updated README and Findings with migration/validation runbook guidance.

## Task Commits

1. **Task 1-3: validation/backfill tool + CLI + docs** - `fca2a79` (feat)

## Files Created/Modified
- `app/src/main/kotlin/jvm/daily/tools/ValidateRawArticleIds.kt` - raw row validation and optional ID updates.
- `app/src/test/kotlin/jvm/daily/tools/ValidateRawArticleIdsTest.kt` - deterministic behavior tests.
- `app/src/main/kotlin/jvm/daily/App.kt` - CLI command wiring for validation/backfill.
- `README.md` - operator runbook for dry-run and apply flows.
- `Findings.md` - phase findings and behavior notes.

## Decisions Made
- Backfill updates are explicit (`--apply`) and collision-safe by default.

## Deviations from Plan

None - plan executed as written.

## Issues Encountered

Intermittent sandbox filesystem limitation prevented XML test report writes for some targeted test invocations; full test execution succeeded and was used as final verification signal.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Existing datasets can be validated/backfilled safely before downstream summarization assumptions.
- Phase 4 can rely on stable canonical raw persistence behavior.

---
*Phase: 03-persistence-and-idempotency*
*Completed: 2026-02-27*
