# Spec Re-Audit: JEP Tracking Source (v2)
**Spec:** `2026-03-18-jep-tracking-design.md`
**Re-audit date:** 2026-03-18
**Auditor:** spec-auditor agent
**Basis:** Previous audit `spec-audit-2026-03-18-jep-tracking.md` (v1)

---

## Summary

The two Critical blockers from v1 are resolved. All six High/Medium/Low issues
from v1 are also addressed. Four new issues were introduced by the revisions —
one of them a new blocker.

**Compliance status: Non-Compliant (one new Critical issue)**

---

## Previous Issues — Resolution Verification

### C1 — `topics` field missing on `Article` — RESOLVED

**Spec resolution:** Section "Article model" now removes the `topics = ["jep"]`
field claim entirely and instead specifies encoding `[JEP TRACKING]\ntopics: jep`
in the `content` field, with a one-line update to the `EnrichmentWorkflow` system
prompt instructing the LLM to preserve `jep` in topics when it sees that header.

**Verification:** `Article.kt` (line 5–15) has no `topics` field — confirmed.
The spec no longer requires one. No model change needed.

**Evidence of approach soundness:** `EnrichmentContract.parse()` (EnrichmentContract.kt
lines 61–82) reads `topics` solely from the LLM JSON response; it has no
mechanism to merge source-provided topics with LLM-generated topics. The
content-encoding workaround correctly routes around this gap. The LLM will see
the `[JEP TRACKING]` header in `article.content.take(4000)` (EnrichmentWorkflow.kt
line 181) because the JEP content block will be short.

**Status: Resolved.** No action needed on this issue.

---

### C2 — Article ID collision for repeated JEP changes — RESOLVED

**Spec resolution:** Section "Article identity" now explicitly instructs passing
`url = null` and `sourceNativeId = "jep-{number}-{updatedDate}-{changeType}"` to
`CanonicalArticleId.from()`. The `url` field on `Article` is still set for display
only.

**Verification:** `CanonicalArticleId.from()` (CanonicalArticleId.kt line 13)
uses the URL as the id only when `url` is non-null and non-blank. With `url = null`,
execution falls through to `normalizeToken(sourceNativeId)` (line 14), producing
`jep:jep-491-20260318-status`. Two separate change events for the same JEP on
different dates will produce different ids, as required.

**Status: Resolved.** No action needed on this issue.

---

### H1 — `sourceId` not specified — RESOLVED

**Spec resolution:** Section "JepSource" now specifies `sourceId = "openjdk.org/jeps"`.
Section "Article generation" confirms `sourceId = "openjdk.org/jeps"` in the
Article field list.

**Status: Resolved.**

---

### H2 — Rate limiting / timeouts unspecified — RESOLVED

**Spec resolution:** Section "Fetch logic" now specifies:
- Timeout: 10 s per request (both list page and individual pages)
- Delay: 200 ms between individual page requests

**Status: Resolved.**

---

### H3 — `fetchOutcomes()` / failure handling not addressed — RESOLVED

**Spec resolution:** Section "Failure handling" now explicitly specifies:
- List page failure → return single `FAILED` outcome, no snapshots updated
- Individual JEP page failure → log warning, skip that JEP's content diff,
  status/title diff from list page still applies
- `fetchOutcomes()` is overridden directly (not using default `fetch()` wrapper)

**Status: Resolved.**

---

### M1 — "Completed/Withdrawn" status labels not matching real data — RESOLVED

**Spec resolution:** Section "Fetch logic" now names the actual status strings
from the JEP page: `Closed/Delivered`, `Closed/Withdrawn`, `Closed/Rejected`.

**Status: Resolved.**

---

### M2 — Snapshot write ordering not specified — RESOLVED

**Spec resolution:** Section "Snapshot write ordering" now explicitly states
update-after-emit with rationale (crash risk: duplicate articles, not silent loss).

**Status: Resolved.**

---

### M3 — Constructor wiring ambiguous — RESOLVED

**Spec resolution:** Section "JepSource" specifies
`JepSource(repository: JepSnapshotRepository, config: JepConfig, clock: Clock)`
and explicitly notes it is the first source with a storage dependency, wired in
`App.kt` alongside repository initialization, following the
`ClusteringWorkflow(clusterRepository, ...)` pattern.

**Status: Resolved.**

---

### L1 — `initialSeed` no guard if table already populated — RESOLVED

**Spec resolution:** Section "Initial seed handling" now specifies an explicit
warning log when `initialSeed=true` but the table is already non-empty:
`"[jep] initialSeed=true but snapshot table already has {n} rows — skipping seed"`
and proceeds normally (does not re-seed).

**Status: Resolved.**

---

### L2 — Combined-change title format not specified — RESOLVED

**Spec resolution:** Section "Change detection" and the `changeType = "multi"` rule
now cover the combined case, with example title:
`"JEP 491: status Candidate→Targeted (JDK 26), content updated"`.

**Status: Resolved.**

---

## New Issues Introduced by Revisions

---

### NEW-C1 — `[JEP TRACKING]` content header bypasses the relevance gate for `jep` sourceType — but `jep` is absent from `RELEVANCE_GATED_SOURCES`

**Spec reference (Section "Article model"):**
> "the LLM enrichment prompt already instructs the model to preserve [the jep topic]"

**Evidence:**
`EnrichmentWorkflow.kt` (line 97):
```kotlin
if (article.sourceType in RELEVANCE_GATED_SOURCES && !isRelevant(article)) {
```
`RELEVANCE_GATED_SOURCES` (line 310):
```kotlin
private val RELEVANCE_GATED_SOURCES = setOf("openjdk_mail", "bluesky", "rss")
```
`JepSource.sourceType = "jep"` (spec, Section "JepSource"). Since `"jep"` is not
in `RELEVANCE_GATED_SOURCES`, the relevance gate is **never triggered** for JEP
articles. This is actually correct behaviour — JEP change articles should never
be filtered — but it is an **undocumented assumption in the spec**.

**The real finding is different:** the spec instructs the updated enrichment prompt
to add the line:
> "If the article content starts with `[JEP TRACKING]`, always include `jep` in topics."

But the enrichment prompt is a **system-level instruction** (`ENRICHMENT_SYSTEM_PROMPT`,
EnrichmentWorkflow.kt lines 311–325), prepended before every article. The spec
does not state where the new line is inserted — into `ENRICHMENT_SYSTEM_PROMPT` or
into `buildEnrichmentPrompt()`. This is unambiguous in intent but unspecified in
placement.

More critically: the spec says the LLM "always" preserves `jep` in topics if the
header is present. This is a **reliability claim against an LLM**, not a code
contract. `EnrichmentContract.parse()` does not check for `[JEP TRACKING]` at all
(EnrichmentContract.kt lines 26–94). If the LLM ignores the instruction and omits
`jep` from the topics array, the contract will accept the response anyway (any
non-empty topics list passes). No fallback enforcement exists in code.

**Category:** Incomplete

**Severity: Critical** — the `[JEP TRACKING]`/`topics: jep` guarantee has no
code-level enforcement. The spec claims the mechanism "guarantees" the topic
survives, but the actual guarantee rests entirely on LLM compliance. Under the
existing `EnrichmentContract`, a JEP article that returns topics `["java"]` from
the LLM will be stored with `topics = ["java"]`, silently dropping `jep`. The
spec should either acknowledge this is a best-effort prompt hint (not a guarantee),
or specify a post-parse enforcement step in `EnrichmentContract`.

**Recommendation:** Choose one:
- A) Acknowledge in the spec that `jep` topic inclusion is prompt-based (best-effort),
  not enforced in code.
- B) Specify a code-level enforcement rule: after `EnrichmentContract.parse()`,
  if `article.content.startsWith("[JEP TRACKING]")` and `topics` does not contain
  `"jep"`, inject it. This belongs in `EnrichmentWorkflow.enrichArticle()`, not in
  `EnrichmentContract` (to avoid coupling the contract to article content format).

---

### NEW-H1 — `sourceNativeId` includes `updatedDate` from the individual page, but individual page may fail

**Spec reference (Section "Article identity"):**
```
sourceNativeId = "jep-$number-$updatedDate-$changeType"
```
**Spec reference (Section "Failure handling"):**
> "Individual JEP page fails → log warning, skip that JEP's **content** diff for this run (status/title diff from list page still applies)."

**Evidence:**
The list page provides `status`, `title`, and `target_release` but not `updated_date`
(confirmed by spec: `Updated: YYYY/MM/DD` is parsed from the individual page only,
Section "Step 2"). When an individual JEP page fails, `updatedDate` is unknown.

**The spec says status/title changes from the list page still produce articles in
this case**, but the article identity formula `"jep-$number-$updatedDate-$changeType"`
requires `updatedDate`. The spec does not specify what value to use for `updatedDate`
when the individual page fetch failed.

Options with different consequences:
- Use `null` → `sourceNativeId = "jep-491-null-status"` — works syntactically but
  may collide if two different runs both fail to fetch the individual page for JEP 491
  and both detect a status change (same id, `INSERT OR REPLACE` overwrites).
- Use the snapshot's last-known `updated_date` → safer but requires spec to say so.
- Use today's run date from the `Clock` → unique per run but loses semantic meaning.

**Category:** Incomplete

**Severity: High** — failure path produces an undefined `updatedDate` value, which
directly affects the uniqueness guarantee of `sourceNativeId`.

**Recommendation:** Spec must state what `updatedDate` to substitute when the
individual page fetch fails and a status/title change is still detected. The most
natural choice: use the snapshot's existing `updated_date` (or `"unknown"` if this
is a new JEP with no snapshot).

---

### NEW-M1 — `JepConfig` data class is not defined in the spec

**Spec reference (Section "JepSource"):**
```kotlin
Constructor: JepSource(repository: JepSnapshotRepository, config: JepConfig, clock: Clock)
```
**Spec reference (Section "Configuration"):**
```yaml
jep:
  enabled: true
  initialSeed: false
  activeStatuses: [...]
```

**Evidence:**
The spec names `JepConfig` as a constructor parameter but provides no definition
of the `JepConfig` data class or its fields. `SourcesConfig.kt` (lines 9–23) lists
every existing config class; `JepConfig` is absent. All other config classes are
defined in `SourcesConfig.kt` as `@Serializable data class`.

The YAML block shows `enabled`, `initialSeed`, and `activeStatuses`, but:
- `enabled` is not a pattern used by any other config class — the source is
  conditionally registered in `App.kt` using `if (config.field != null)` or similar
  guards. How `enabled: false` prevents `JepSource` from being registered is not
  specified.
- `SourcesConfig` would need a new `jep: JepConfig? = null` field — not mentioned.

**Category:** Missing

**Severity: Medium** — implementer must infer the entire config class structure,
the null-vs-non-null registration pattern, and the `SourcesConfig` extension.
Every other source config class is fully specified in the project's existing code.

**Recommendation:** Add a `JepConfig` definition to the spec (fields, types,
defaults) and specify that `jep: JepConfig? = null` is added to `SourcesConfig`,
following the existing `bluesky: BlueskyConfig? = null` pattern (SourcesConfig.kt
line 16).

---

### NEW-L1 — `initialSeed` proceeds "normally" after warning — but what is "normally"?

**Spec reference (Section "Initial seed handling"):**
> "If `initialSeed=true` but the table is already non-empty: log a warning
> `"[jep] initialSeed=true but snapshot table already has {n} rows — skipping seed"`
> and proceed normally."

**Evidence:**
"Proceed normally" is ambiguous in context. After the warning, does the source:
- (A) Run full change-detection and emit articles as if `initialSeed` were `false`? or
- (B) Exit early and emit nothing (treating the whole run as a no-op, same as seed mode)?

Option A is the most useful operator experience (wrong config doesn't silently suppress
changes). Option B is safer but means the operator must restart without the flag to
see any articles. The phrase "skipping seed" in the log message suggests B (skip the
seed step) but "proceed normally" suggests A.

**Category:** Ambiguous

**Severity: Low** — edge case; only affects operators who mistakenly run with
`initialSeed=true` after first setup.

**Recommendation:** Clarify: after the warning, the source should proceed as if
`initialSeed=false` (option A), so the operator gets expected change-detection
output rather than a silent no-op.

---

## Clarification Questions (New)

1. **`[JEP TRACKING]` topic guarantee (NEW-C1):** Is the `jep` topic preservation
   a code-level contract or a prompt-level best-effort hint? If it must be
   guaranteed, should `EnrichmentWorkflow.enrichArticle()` inject `"jep"` into topics
   post-parse when it detects the `[JEP TRACKING]` header?

2. **`updatedDate` when individual page fails (NEW-H1):** When a JEP's individual
   page fetch fails but a status/title change is detected from the list page, what
   value should fill `{updatedDate}` in `sourceNativeId = "jep-{number}-{updatedDate}-{changeType}"`?
   Snapshot's last-known `updated_date`, today's date, or `"unknown"`?

3. **`initialSeed=true` + non-empty table (NEW-L1):** After logging the warning,
   should the source proceed with full change-detection (emit articles), or exit
   early (emit nothing)?

---

## Unchanged Findings (No New Evidence)

All medium and low findings from v1 are resolved. No existing issues remain open.

---

## Compliance Status

**Non-Compliant — blocked by NEW-C1 (Critical)**

The spec claims the `[JEP TRACKING]` content header "guarantees" the `jep` topic
survives enrichment, but no code-level enforcement exists or is specified. This
needs a decision (acknowledge as best-effort, or specify code enforcement) before
implementation.

NEW-H1 (High) must also be resolved: the `sourceNativeId` formula is undefined for
the individual-page-failure path.

Once NEW-C1 and NEW-H1 are resolved, and NEW-M1 (`JepConfig` definition) is added,
the spec will be implementable without ambiguity.
