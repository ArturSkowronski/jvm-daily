# Coding Conventions

**Analysis Date:** 2026-02-27

## Naming Patterns

**Files:**
- Kotlin classes and interfaces use `PascalCase.kt`
- Tests use corresponding production name with `Test` suffix (`IngressWorkflowTest.kt`)

**Functions and variables:**
- `camelCase` for methods and local variables
- Constants often use `UPPER_SNAKE_CASE` (see system prompts in workflows)

**Types:**
- Data types and interfaces use `PascalCase` without prefixing (`Article`, `LLMClient`)

## Code Style

**Formatting:**
- Kotlin style consistent with IntelliJ defaults (4-space indentation)
- Named parameters used in constructors for readability in model creation
- String templates and multiline `trimIndent()` used heavily for prompts and SQL

**Imports:**
- Standard Kotlin import organization; no custom alias scheme observed

## Error Handling

- Boundary validation in entry layer (`App.kt`) with early `error(...)` on invalid env setup
- Per-item resilience in workflow loops (`try/catch` around individual article enrichment)
- Repository methods generally allow SQL exceptions to propagate

## Logging

- Logging via `println`/`System.err.println` with stage tags (`[ingress]`, `[enrichment]`, etc.)
- No dedicated structured logging library currently integrated

## Comments

- Short, purpose-driven doc comments on workflows and major entry points
- Comments used to explain pipeline stage intent and TODOs (e.g., cluster persistence)

## Function Design

- Constructor injection for dependencies (repositories, clients, clocks)
- `suspend` functions for workflow execution and source fetching
- Helper methods extracted for parsing/building prompt logic in workflow classes

## Module Design

- Interface-first boundaries for key extension points: `Source`, `Workflow`, repositories, `LLMClient`
- Concrete implementations follow interface contracts and are wired in composition root (`App.kt`)
- Package structure aligns to responsibility domains (`source`, `storage`, `workflow`)

## Architecture Guardrails

- New source connectors belong in `source/` and implement `Source`; avoid placing source-specific logic in workflow classes.
- Keep workflow code dependent on abstractions, not concrete source/storage implementations.
- Any change that alters layer boundaries should update architecture tests in:
  - `app/src/test/kotlin/jvm/daily/architecture/WorkflowBoundaryTest.kt`
  - `app/src/test/kotlin/jvm/daily/architecture/LayerDependencyTest.kt`
- Boundary checks must stay in the default `./gradlew test` execution path.

## Practical guidance for new changes

- Keep new logic behind existing interfaces when possible
- Maintain stage-specific logging prefixes for operational readability
- Add tests in mirrored package under `app/src/test/kotlin/jvm/daily/...`
- Preserve env-var based configuration style for runtime options

---
*Convention analysis: 2026-02-27*
*Update when style or patterns shift materially*
