# Release Cluster Type — Design

**Date:** 2026-03-20
**Branch:** feat/compact-social-card-in-clusters
**Status:** Approved

## Problem

When multiple articles cover the same software release (e.g. a GitHub release note + a blog announcement + Bluesky shares), they currently appear as individual article cards inside a topic cluster. This is redundant — the reader sees the same event described three times. Releases need a distinct rendering: a concise bullet-point summary with all sources shown as compact badges below.

## Approach

Extend `ClusteringWorkflow`'s LLM prompt to classify clusters as `type: "release" | "topic"` and generate bullet-point summaries for release clusters. No separate workflow; classification happens at clustering time.

## Scope

- Any cluster whose primary subject is a software release gets `type = "release"` — including the generic "Releases" roundup and dedicated major-release clusters (e.g. "Spring Boot 4.1.0-M3", "Kotlin 2.3.20")
- Non-release clusters (discussions, tutorials, performance analysis) remain `type = "topic"` with unchanged prose synthesis

## Changes

### 1. `ClusteringWorkflow` — grouping prompt

Add `RELEASE: YES` to the LLM output format for the semantic grouping step:

```
GROUP: Spring Boot 4.1.0-M3
MAJOR: YES
RELEASE: YES
INDICES: 2, 7, 11
```

**Rule added to prompt:**
> Add `RELEASE: YES` for any cluster primarily about a software release:
> - The generic "Releases" roundup
> - Any dedicated release cluster (e.g. "Spring Boot 4.1.0", "Kotlin 2.3.20 Release")
> Omit for non-release clusters (discussions, tutorials, blog series, performance analysis).

### 2. `ClusteringWorkflow` — synthesis prompt for release clusters

When `isRelease = true`, replace the prose synthesis prompt with a bullet prompt:

```
TITLE: Spring Boot 4.1.0-M3
BULLET: Adds support for virtual thread-per-request model by default
BULLET: New @RestClientTest slice includes MockMvcTester
BULLET: Deprecates spring.mvc.pathmatch.use-suffix-pattern
```

- Up to 5 bullets; each bullet is 1–2 sentences
- `parseClusterResponse` collects `BULLET:` lines into `List<String>`
- Topic clusters: bullets list is empty; release clusters: summary field is empty (bullets carry the content)

### 3. Model — `ArticleCluster` (ProcessedArticle.kt)

```kotlin
val type: String = "topic",            // "topic" | "release"
val bullets: List<String> = emptyList(),
```

Default values preserve backward compatibility with existing DB rows.

### 4. Model — `DigestCluster` (DigestJson.kt)

```kotlin
val type: String = "topic",
val bullets: List<String> = emptyList(),
```

### 5. DB schema — `ClusterRepository`

Two new columns with defaults:

```sql
type    VARCHAR DEFAULT 'topic',
bullets VARCHAR DEFAULT '[]'   -- JSON array stored as string
```

### 6. `OutgressWorkflow` — pass-through

Copy `type` and `bullets` from `ArticleCluster` → `DigestCluster`. No other logic changes — the articles list remains intact; the viewer decides rendering.

### 7. Viewer (`serve.py` template)

For `cluster.type === "release"`:
- Render cluster title as heading
- Render `bullets` as an unordered list (up to 5 items)
- Render all `articles` as compact source badges (same visual style as existing `socialLinks` badges)
- No individual article cards

For `cluster.type === "topic"`: unchanged behaviour.

## Data Flow

```
ClusteringWorkflow
  groupBySemantic()        → LLM outputs RELEASE: YES flag per group
  createCluster(isRelease) → LLM outputs BULLET: lines (up to 5)
  ArticleCluster.type      → "release" | "topic"
  ArticleCluster.bullets   → List<String>

OutgressWorkflow
  DigestCluster.type       → copied from ArticleCluster
  DigestCluster.bullets    → copied from ArticleCluster

Viewer
  cluster.type === "release"  → bullets + badges
  cluster.type === "topic"    → prose synthesis + article cards
```

## Error Handling

- If the LLM omits `RELEASE:` lines, `isRelease` defaults to `false` → safe fallback to topic rendering
- If fewer than 5 bullets are generated, render only what exists (no padding)
- If `bullets` is empty on a release cluster (LLM parse failure), fall back to rendering the summary field as prose

## Testing

- Unit test `parseGroupResponse`: RELEASE: YES parsed → `isRelease = true`
- Unit test `parseClusterResponse`: BULLET: lines collected correctly, max 5 enforced
- Unit test `ClusterRepository`: `type` and `bullets` round-trip through DB serialization
- Unit test `OutgressWorkflow`: release cluster passes `type` and `bullets` to `DigestCluster`
