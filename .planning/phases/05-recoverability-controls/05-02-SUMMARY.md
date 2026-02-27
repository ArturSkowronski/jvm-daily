---
phase: 05-recoverability-controls
plan: 02
subsystem: cli-and-operations
tags: [replay-cli, stage-operations, recoverability, docs]
requires:
  - phase: 05-recoverability-controls
    provides: repository selectors and targeted replay mode in enrichment workflow
provides:
  - `enrichment-replay` command with bounded selectors and dry-run mode
  - Replay option parser validation with guardrails for invalid combinations
  - Integration + unit tests covering replay command contract behavior
affects: [operations, recoverability-controls]
tech-stack:
  added: []
  patterns: [bounded-cli-selectors, dry-run-first-recovery]
key-files:
  created:
    - app/src/test/kotlin/jvm/daily/AppReplayOptionsTest.kt
  modified:
    - app/src/main/kotlin/jvm/daily/App.kt
    - app/src/test/kotlin/jvm/daily/workflow/ProcessingPipelineIntegrationTest.kt
    - README.md
key-decisions:
  - "Replay command supports either explicit IDs or time-window/limit selectors, never both"
  - "Dry-run mode is first-class for safe operator preview before mutation"
patterns-established:
  - "Independent stage commands now include a recoverability-focused enrichment replay path"
requirements-completed: [SUM-03, OPS-02]
duration: 20min
completed: 2026-02-27
---

# Phase 5: Recoverability Controls Summary

**Added an operator-facing replay command (`enrichment-replay`) that reruns failed enrichment items without full pipeline rerun.**

## Performance

- **Duration:** 20 min
- **Started:** 2026-02-27T23:40:00Z
- **Completed:** 2026-02-27T24:00:00Z
- **Tasks:** 4
- **Files modified:** 4

## Accomplishments
- Added `enrichment-replay` command to `App.kt` command surface.
- Added replay selector parsing with validation for:
  - `--since-hours`, `--limit`
  - `--ids`
  - `--dry-run`
  - invalid mixed selector combinations.
- Added integration test proving failed item can be replayed to success path.
- Documented replay command usage and operator rules in README.

## Task Commits

1. **Task 1-4: replay CLI contract + tests + docs** - `<pending>` (feat)

## Files Created/Modified
- `app/src/main/kotlin/jvm/daily/App.kt` - new replay command path and option parser.
- `app/src/test/kotlin/jvm/daily/AppReplayOptionsTest.kt` - replay argument contract tests.
- `app/src/test/kotlin/jvm/daily/workflow/ProcessingPipelineIntegrationTest.kt` - replay integration scenario.
- `README.md` - replay command documentation and safety rules.

## Decisions Made
- Replay command resolves explicit IDs against failed records before execution.
- Dry-run prints replay candidates and exits without mutating processed outcomes.

## Deviations from Plan

None.

## Issues Encountered

- Initial compile error due to missing `Clock` import in replay path; fixed during execution.

## User Setup Required

None.

## Next Phase Readiness
- Runbook and findings updates can now build on a concrete replay CLI contract.

---
*Phase: 05-recoverability-controls*
*Completed: 2026-02-27*
