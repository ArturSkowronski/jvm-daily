---
phase: 06-daily-automation-and-telemetry
plan: 01
subsystem: scheduler-contract
tags: [jobrunr, airflow, scheduling, ops]
requires:
  - phase: 05-recoverability-controls
    provides: stage-level recovery commands and stable pipeline stage flow
provides:
  - Canonical daily cron contract shared by scheduler entrypoints
  - Airflow DAG schedule aligned to app scheduler env contract
  - Pipeline stage-order scheduler regression assertions
affects: [daily-automation-and-telemetry, operations]
tech-stack:
  added: []
  patterns: [single-scheduler-contract, stage-sequence-invariant]
key-files:
  created: []
  modified:
    - app/src/main/kotlin/jvm/daily/App.kt
    - app/src/test/kotlin/jvm/daily/PipelineServiceTest.kt
    - airflow/dags/jvm_daily_pipeline.py
    - airflow/README.md
key-decisions:
  - "Use shared PIPELINE_CRON contract with default 0 7 * * * across JobRunr and Airflow"
  - "Treat ingress->enrichment->clustering->outgress ordering as scheduler contract invariant"
patterns-established:
  - "Scheduler path parity is explicit in code and runbook"
requirements-completed: [OPS-01, OPS-03]
duration: 15min
completed: 2026-02-27
---

# Phase 6: Daily Automation and Telemetry Summary

**Aligned scheduler contract semantics between JobRunr daemon mode and Airflow DAG mode.**

## Performance

- **Duration:** 15 min
- **Started:** 2026-02-27T23:28:00Z
- **Completed:** 2026-02-27T23:43:00Z
- **Tasks:** 3
- **Files modified:** 4

## Accomplishments
- Added `DEFAULT_PIPELINE_CRON` in app entrypoint and switched daemon schedule fallback to that constant.
- Updated Airflow DAG to read `PIPELINE_CRON` (same default) for schedule parity with app mode.
- Added PipelineService test verifying stage start/end markers preserve canonical stage order.
- Updated Airflow runbook with explicit Phase 6 scheduler contract notes.

## Task Commits

1. **Task 1-3: scheduler contract alignment + tests + docs** - `<pending>` (feat)

## Files Created/Modified
- `app/src/main/kotlin/jvm/daily/App.kt` - canonical cron default constant.
- `airflow/dags/jvm_daily_pipeline.py` - env-driven schedule with shared default.
- `app/src/test/kotlin/jvm/daily/PipelineServiceTest.kt` - stage sequence and marker contract test.
- `airflow/README.md` - scheduler contract documentation.

## Decisions Made
- Keep one cron contract (`PIPELINE_CRON`) instead of scheduler-specific defaults.
- Document stage sequence as part of automation contract, not implementation detail.

## Deviations from Plan

None.

## Issues Encountered

None.

## User Setup Required

None.

## Next Phase Readiness
- Scheduler semantics are aligned and documented.
- Ready for structured telemetry envelope work in 06-02.

---
*Phase: 06-daily-automation-and-telemetry*
*Completed: 2026-02-27*
