# JVM Daily — Modernized UI Design Spec

**Date:** 2026-03-31  
**Status:** Approved

---

## Summary

Modernize the JVM Daily viewer with a clean Inter font, light/dark mode toggle, and refined card-based layout — inspired by jvm-weekly.com's editorial style but more readable.

---

## Design Decisions

### Typography
- **Font:** Inter (replaces Spectral) — full sans-serif for both headlines and body
- **No italic** anywhere in the UI
- Weight hierarchy: 700 logo, 600 cluster titles, 400 body text

### Color Palette

**Light mode:**
- Background: `#f8f8f6`
- Card background: `#fff`
- Border: `#e8e8e0`
- Primary text: `#111`
- Secondary text: `#555`
- Muted text: `#aaa`

**Dark mode:**
- Background: `#111`
- Card background: `#1a1a1a`
- Border: `#252525`
- Primary text: `#edede7`
- Secondary text: `#777`
- Muted text: `#3a3a3a`

**Accent (both modes):**
- Green: `#00a64e` (unchanged)
- Release pills dark: `#4ade80` on `#0c2318` bg
- HN badge: amber tones
- Reddit badge: blue tones

### Header
- Logo: `font-weight: 700`, `letter-spacing: -0.03em`, no italic
- Tagline: small uppercase muted text "Daily briefing"
- Tabs: same 3 (Digest, Pipeline, ROTS) — active tab `#00a64e` bg
- Sticky, `border-bottom: 1px solid`

### Date strip
- Slim bar below header: date · article count
- No releases here

### Releases section
- Separate labeled section ("RELEASES") above the cluster list
- Horizontal scrollable pills linking to release cards below
- Clicking pill scrolls to and briefly highlights the release card (green outline flash)

### Cluster cards
- `border-radius: 8px`, card on white/`#1a1a1a` bg
- Top story: `border-left: 3px solid #00a64e`
- Other clusters: neutral border-left
- Badges: article count, HN score (amber), reddit (blue) — pill shaped, small
- Bookmark ☆ and dismiss ✓ buttons top-right

### Release cards (unchanged content)
- 4 bullet points from existing `bullets` field — **not prose**
- Source badges as `↗ repo-name` pill links
- Same bookmark/dismiss buttons

### Dark mode toggle
- Toggle button in header (or system `prefers-color-scheme` detection)
- CSS variables or Svelte class-based theming

---

## Scope

**In scope:**
- `+layout.svelte` — header, global CSS variables, font, dark mode toggle
- `Cluster.svelte` — card styles
- `ReleaseCard.svelte` — card styles, bullets unchanged
- `+page.svelte` — releases section with anchor pills

**Out of scope:**
- Pipeline page restyling
- ROTS page restyling
- Any changes to data fetching or backend
- Changes to bullet content generation
