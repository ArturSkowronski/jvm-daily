# JVM Weekly Platform

## What This Is

Internal platform for building JVM Weekly from continuously ingested ecosystem sources. In v1 it focuses on daily RSS ingestion, storage, and summarization so the morning review has high signal with minimal manual effort. The architecture is intentionally built as a solid base for adding future source types (Reddit, mailing lists, Twitter/X, and others).

## Core Value

Every morning, have as much relevant JVM information as possible available in one deduplicated place.

## Requirements

### Validated

- ✓ Daily ingest pipeline exists for RSS and local markdown sources — existing
- ✓ DuckDB-backed persistence for raw and processed articles exists — existing
- ✓ Basic deduplication based on stable article IDs exists in ingest stage — existing
- ✓ Summarization/enrichment workflow exists (currently via mock LLM provider contract) — existing
- ✓ Scheduled orchestration path exists (Airflow DAG + optional JobRunr daemon mode) — existing

### Active

- [ ] Improve ingestion quality to maximize useful JVM coverage every morning
- [ ] Ensure daily runs produce new entries without duplicates as operational baseline
- [ ] Strengthen and maintain solid source-plugin architecture for future connectors
- [ ] Prepare and standardize extension boundaries for Reddit, mailing lists, Twitter/X (post-v1 implementation)

### Out of Scope

- Email newsletter publishing in v1 — explicitly deferred to later phase
- Twitter/X and Reddit ingestion implementation in v1 — architecture now, connectors later

## Context

Existing Kotlin/JVM codebase already implements a multi-stage workflow: ingress, enrichment, clustering, and outgress. Data is stored in DuckDB and orchestrated either directly from CLI/service entry points or via Airflow. Current priority is to use and harden this foundation for reliable daily operation and to keep extensibility first-class for upcoming source families.

## Constraints

- **Audience**: Single-user internal workflow (for now) — Scope is optimized for speed and quality of personal morning digest
- **Quality Baseline**: Daily new entries with no duplicates — This is the minimum success criterion declared by user
- **Architecture**: Must remain solid and extensible — Future non-RSS sources are planned and should not require major rewrites
- **Scope Boundary**: No email publication and no Twitter/Reddit connectors in v1 — Keep current milestone focused

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Treat current project as brownfield foundation | Existing pipeline, storage, and orchestration already deliver meaningful capabilities | — Pending |
| Keep v1 focused on RSS + storage + summarization quality | Fastest path to reliable daily value for one user | — Pending |
| Prioritize extensible source boundaries before adding new connectors | Reddit/mailing/Twitter are planned and architecture must absorb them safely | — Pending |
| Defer email publishing to future milestone | Not required for immediate morning information objective | — Pending |

---
*Last updated: 2026-02-27 after initialization*
