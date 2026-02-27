# Phase 1: Architecture Guardrails - Research

**Researched:** 2026-02-27
**Domain:** Kotlin JVM pipeline architecture boundaries and adapter contracts
**Confidence:** HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- Each source adapter performs `fetch + normalize` to a shared normalized model.
- Contract allows partial records.
- Canonical identity for deduplication is computed centrally (fingerprint).
- Adapter failures are isolated (pipeline continues for other adapters).

### Claude's Discretion
- Exact required/optional field matrix for normalized model.
- Fingerprint algorithm and collision policy.
- Retry/error budget thresholds per adapter.

### Deferred Ideas (OUT OF SCOPE)
- None.
</user_constraints>

<research_summary>
## Summary

Phase 1 should avoid broad refactors and instead install explicit guardrails: a contract test layer for adapters, a dependency-boundary test layer for workflow/storage seams, and architecture documentation tied to executable checks. In this repo, the core boundaries already exist (`Source`, workflows, repositories), so the work is mainly making drift visible and fail-fast in CI.

The recommended approach is to codify architecture invariants as tests under `app/src/test/kotlin/jvm/daily/architecture/`, and to keep adapter-specific behavior behind `Source` implementations only. Central fingerprint logic should live outside adapter code, preserving connector simplicity and consistent dedup semantics.

**Primary recommendation:** Implement executable architecture checks first, then align docs to those checks.
</research_summary>

<architecture_patterns>
## Architecture Patterns

### Pattern 1: Contract-at-boundary tests
- For every `Source` implementation, verify normalized output behavior and error isolation contract.
- Ensures ARC-01 is continuously enforced.

### Pattern 2: Dependency-direction checks
- Workflow layer depends on abstractions/interfaces, not concrete source adapters.
- Storage remains behind repository interfaces where orchestration interacts.

### Pattern 3: Docs synchronized with executable invariants
- Keep `ARCHITECTURE.md` and `CONVENTIONS.md` in sync with what tests enforce.
- Prevents stale architecture guidance.

### Anti-patterns to avoid
- Embedding source-specific logic in workflows.
- Allowing concrete adapter references in orchestration code.
- Treating architecture docs as non-binding narrative.
</architecture_patterns>

<common_pitfalls>
## Common Pitfalls

### Pitfall: boundary checks exist but are not in CI path
- **Avoid:** ensure checks run in standard `./gradlew test` path.

### Pitfall: adapter contract too strict for heterogeneous sources
- **Avoid:** allow partial records but enforce clear minimum identity and source metadata.

### Pitfall: docs diverge from code
- **Avoid:** update docs only alongside invariant test changes.
</common_pitfalls>

<sources>
## Sources

### Primary
- Local repository code under `app/src/main/kotlin/jvm/daily/*`
- Local tests under `app/src/test/kotlin/jvm/daily/*`
- `.planning/phases/01-architecture-guardrails/01-CONTEXT.md`
- `.planning/ROADMAP.md`, `.planning/REQUIREMENTS.md`, `.planning/STATE.md`
</sources>

---
*Phase: 01-architecture-guardrails*
*Research gathered: 2026-02-27*
