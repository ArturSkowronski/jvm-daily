# Architecture Research

**Domain:** JVM newsletter ingestion/summarization platform
**Researched:** 2026-02-27
**Confidence:** HIGH

## Standard Architecture

### System Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    Orchestration Layer                      │
│   Airflow DAG / JobRunr daemon / CLI stage runners         │
└───────────────┬─────────────────────────────────────────────┘
                │
┌───────────────▼─────────────────────────────────────────────┐
│                      Workflow Layer                         │
│   Ingress → Enrichment → Clustering/Outgress               │
└───────────────┬─────────────────────────────────────────────┘
                │
┌───────────────▼─────────────────────────────────────────────┐
│                  Domain Adapter Layer                       │
│   Source adapters (RSS now, Reddit/Mailing/Twitter later)  │
└───────────────┬─────────────────────────────────────────────┘
                │
┌───────────────▼─────────────────────────────────────────────┐
│                    Persistence Layer                        │
│   Raw articles + processed articles + run metadata          │
└─────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Responsibility | Typical Implementation |
|-----------|----------------|------------------------|
| Orchestrator | Schedule and retry daily pipeline | Airflow DAG + optional in-process daemon |
| Source adapters | Fetch and normalize external content | `Source` interface + source-specific implementations |
| Workflow stages | Transform data between pipeline states | Stage-specific `Workflow` classes |
| Repositories | Persist, query, and deduplicate data | DuckDB repositories with typed models |
| Quality monitor | Produce operational counters and failure signals | Stage logs + quality summary artifacts |

## Recommended Project Structure

```
app/src/main/kotlin/jvm/daily/
├── workflow/             # stage orchestration logic
├── source/               # source adapter contracts + implementations
├── storage/              # repositories and DB access
├── model/                # raw/processed domain models
├── config/               # typed config loading
└── ai/                   # LLM boundary and providers
```

### Structure Rationale

- **`source/` isolation:** new connectors can be added without touching workflow core.
- **`workflow/` stage contracts:** deterministic and testable transformations.
- **`storage/` boundary:** dedup and persistence behavior remains centralized.

## Architectural Patterns

### Pattern 1: Adapter-per-source

**What:** every source implements one contract and returns normalized articles.  
**When to use:** whenever onboarding new external source type.  
**Trade-offs:** clear boundaries vs extra boilerplate.

### Pattern 2: Stage pipeline with idempotent transitions

**What:** each stage can be rerun safely and processes only pending/new items.  
**When to use:** daily scheduled batch with retry semantics.  
**Trade-offs:** requires explicit status tracking but improves recovery.

### Pattern 3: Persistence-backed observability

**What:** keep run counters and failure categories next to data pipeline.  
**When to use:** whenever trust in daily output is critical.  
**Trade-offs:** small schema overhead for major debugging gains.

## Data Flow

1. Scheduler triggers ingress daily.
2. Ingress fetches feeds, normalizes records, and persists raw articles.
3. Dedup/idempotency gate filters repeats.
4. Enrichment processes only unprocessed/new items and stores summaries + metadata.
5. Optional downstream stages prepare clustered/exported outputs.
6. Daily quality report surfaces counts, duplicates, and failures.

## Scaling Considerations

| Scale | Architecture Adjustments |
|-------|--------------------------|
| Single-user v1 | Current modular monolith is sufficient |
| More sources + higher volume | Add controlled concurrency and batch writes |
| Multi-user/productized | Introduce external DB/service boundaries and stronger auth model |

## Anti-Patterns

- Adding source-specific hacks directly in workflows.
- Mixing orchestration concerns with parser/business logic.
- Shipping connector implementations before adapter contracts and tests.

## Integration Points

| Boundary | Communication | Notes |
|----------|---------------|-------|
| Orchestrator ↔ Workflow | command invocation / task trigger | Keep stage arguments explicit |
| Workflow ↔ Source adapters | interface call | No direct knowledge of source internals |
| Workflow ↔ Storage | repository contracts | Ensures testability and future DB swaps |

## Sources

- Existing code architecture (`app/src/main/kotlin/jvm/daily/*`)
- Codebase map (`.planning/codebase/ARCHITECTURE.md`, `STRUCTURE.md`)
- User target architecture requirement (“solidna architektura”)

---
*Architecture research for: JVM Weekly ingestion platform*
*Researched: 2026-02-27*
