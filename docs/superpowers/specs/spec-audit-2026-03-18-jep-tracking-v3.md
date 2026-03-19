# Spec Audit — JEP Tracking Source (Final Re-review)
**Spec:** `docs/superpowers/specs/2026-03-18-jep-tracking-design.md`
**Date:** 2026-03-18
**Auditor:** spec-auditor agent (v3 — final pass)
**Prior rounds:** v1, v2 (all previously-flagged items marked resolved below)

---

## Compliance Status

**COMPLIANT** — No critical or high-severity blockers remain. Two low-severity observations noted below; neither prevents implementation.

---

## Previously Resolved Items (Confirmed)

All items from prior rounds were verified against the actual codebase and confirmed addressed in the spec:

- C1 (`topics` guarantee): Spec defines both prompt hint and code-enforcement snippet. `EnrichmentWorkflow.enrichArticle()` at `EnrichmentWorkflow.kt:122-148` is the exact insertion point; the spec's snippet fits cleanly after `EnrichmentContract.parse()` returns `ParseResult.Success`.
- C2 (ID collision): Spec correctly passes `url=null` and uses `sourceNativeId`. Confirmed `CanonicalArticleId.from()` at `CanonicalArticleId.kt:13` — URL is only used when non-null, so passing `null` skips it.
- H1 (`sourceId` specified): `"openjdk.org/jeps"` is in the spec. Consistent with how other sources use a meaningful `sourceId` (e.g. RSS uses feed URL).
- H2 (timeouts + delay): 10s timeout and 200ms delay are specified. Consistent with RSS source patterns (`RssSource.kt:139-140`).
- H3 (failure handling): List-page failure → FAILED outcome; individual-page failure → warn, fallback, continue. Both cases handled.
- M1–M3 / L1–L2 / NEW-C1 / NEW-H1 / NEW-M1 / NEW-L1: All verified in spec text.

---

## New Findings

### Finding 1 — Low: `fetchOutcomes()` return value on list-page failure needs `sourceId` specified

**Spec Reference:** Section "Failure handling" — "List page fails → `fetchOutcomes()` returns a single `FAILED` outcome."

**Evidence:**
- `FeedIngestResult` at `FeedIngestResult.kt:11` requires both `sourceType` and `sourceId` fields.
- The spec text describes the outcome semantically but does not specify what `sourceId` value to use in the `FeedIngestResult` for the FAILED outcome.
- Existing sources use the feed's URL as `sourceId` (e.g. `RssSource.kt:44-46`); for `JepSource` the natural value would be `"https://openjdk.org/jeps/"` (the list page URL) or the constant `"openjdk.org/jeps"` already defined as `sourceId`.

**Category:** Incomplete

**Severity:** Low — implementers will pick something reasonable, but the spec is silent. No risk of broken behavior, only minor inconsistency with the `sourceId = "openjdk.org/jeps"` already specified for articles.

**Recommendation:** Add one sentence: "The FAILED `FeedIngestResult` uses `sourceId = \"openjdk.org/jeps\"`" — the same value as `Article.sourceId`.

---

### Finding 2 — Low: `EnrichmentWorkflow` `jep` topic injection — insertion point for `raw` variable needs clarification

**Spec Reference:** Section "Article model" — code snippet references `raw.content` and `enriched.topics`.

**Evidence:**
- `EnrichmentWorkflow.enrichArticle()` at `EnrichmentWorkflow.kt:122-148`: the `ParseResult.Success` branch builds a `ProcessedArticle` directly; there is no intermediate `enriched` variable. The `raw` article is the function parameter (`article: Article`).
- The spec snippet uses `raw.content` and `enriched.topics` but the actual code has `article` as the raw article and `result.topics` as the parsed topics list.
- The mapping is unambiguous in intent but the variable names in the spec snippet do not match the existing code. An implementer must mentally map: `raw` → `article`, `enriched.topics` → `result.topics`.

**Category:** Ambiguous

**Severity:** Low — the spec intent is clear. The mismatch is cosmetic and resolves on first read of the code. No functional risk.

**Recommendation:** Update the spec snippet to use the actual variable names present in `EnrichmentWorkflow.enrichArticle()`:
```kotlin
val topics = if (article.content.startsWith("[JEP TRACKING]") && "jep" !in result.topics)
    result.topics + "jep" else result.topics
```
This eliminates any ambiguity about where the injection goes.

---

## Summary

The spec is complete and implementable as written. The two findings above are cosmetic polish items that a competent implementer will resolve naturally. No ambiguities remain that could cause a wrong implementation, and no blockers exist.

**Recommended action:** Optionally apply the two low-severity clarifications above, then proceed to implementation.
