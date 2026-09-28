# Modernize UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Spectral serif with Inter sans-serif, add light/dark mode toggle, and refine card-based layout across the JVM Daily viewer.

**Architecture:** CSS custom properties on `:root` / `[data-theme="dark"]` drive theming; a toggle button in the header stores preference in `localStorage`; all Svelte components consume vars. No new files — only style changes to existing components.

**Tech Stack:** SvelteKit, Svelte 5 runes, Inter (Google Fonts), Playwright for E2E tests.

**Build check:** `cd viewer-svelte && npm run build` — must complete without errors after every task.  
**Test run:** `cd viewer-svelte && npx playwright test --config tests/playwright.config.ts` — all tests must pass.

---

## File Map

| File | Change |
|------|--------|
| `viewer-svelte/src/routes/+layout.svelte` | Inter font, CSS vars, dark mode toggle logic + button, header styles |
| `viewer-svelte/src/routes/+page.svelte` | Release anchor pills, digest header styles, section label styles |
| `viewer-svelte/src/lib/components/Cluster.svelte` | Card styles using CSS vars |
| `viewer-svelte/src/lib/components/ReleaseCard.svelte` | Card styles using CSS vars |
| `viewer-svelte/src/lib/components/DateSidebar.svelte` | Font + color vars |
| `viewer-svelte/src/lib/components/ArticleRow.svelte` | Font + color vars |
| `viewer-svelte/src/lib/components/SourceBadge.svelte` | Font var |
| `viewer-svelte/src/lib/components/TopicTag.svelte` | Font + color vars |
| `viewer-svelte/tests/sections.spec.ts` | Add dark mode toggle test |

---

## Task 1: CSS Variables + Inter Font in Layout

**Files:**
- Modify: `viewer-svelte/src/routes/+layout.svelte`

Replace the Spectral Google Fonts import with Inter and establish CSS custom properties for the entire theme.

- [ ] **Step 1: Replace font import and add CSS vars in `+layout.svelte`**

In the `<svelte:head>` block, replace the Spectral import:
```html
<svelte:head>
	<title>JVM Daily</title>
	<link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet">
</svelte:head>
```

In the `<style>` block, replace the existing `:global(body)` and related global rules with:
```css
:global(:root) {
	--bg: #f8f8f6;
	--bg-card: #ffffff;
	--bg-header: #ffffff;
	--bg-strip: #f8f8f6;
	--border: #e8e8e0;
	--border-strong: #d0d0c8;
	--text: #111111;
	--text-secondary: #555555;
	--text-muted: #aaaaaa;
	--accent: #00a64e;
	--accent-dark: #00743a;
	--accent-pill-bg: #f0faf4;
	--accent-pill-border: #b3e6cc;
	--accent-pill-text: #00743a;
	--badge-count-bg: #f5f5f2;
	--badge-count-text: #999999;
	--badge-hn-bg: #fff8f0;
	--badge-hn-text: #b45309;
	--badge-reddit-bg: #f0f4ff;
	--badge-reddit-text: #4361c2;
	--action-btn-border: #e8e8e0;
	--action-btn-text: #bbbbbb;
}
:global([data-theme="dark"]) {
	--bg: #111111;
	--bg-card: #1a1a1a;
	--bg-header: #1a1a1a;
	--bg-strip: #161616;
	--border: #252525;
	--border-strong: #333333;
	--text: #edede7;
	--text-secondary: #777777;
	--text-muted: #3a3a3a;
	--accent: #00a64e;
	--accent-dark: #4ade80;
	--accent-pill-bg: #0c2318;
	--accent-pill-border: #174d2e;
	--accent-pill-text: #4ade80;
	--badge-count-bg: #222222;
	--badge-count-text: #555555;
	--badge-hn-bg: #221a0a;
	--badge-hn-text: #d97706;
	--badge-reddit-bg: #141826;
	--badge-reddit-text: #6b8cde;
	--action-btn-border: #2e2e2e;
	--action-btn-text: #444444;
}
:global(body) {
	margin: 0;
	font-family: 'Inter', system-ui, sans-serif;
	background: var(--bg);
	color: var(--text);
	line-height: 1.65;
	font-size: 16px;
}
:global(*) { box-sizing: border-box; }
:global(h1, h2, h3) { font-family: 'Inter', system-ui, sans-serif; }
:global(a) { color: var(--accent); }
:global(a:hover) { color: var(--accent-dark); }
```

- [ ] **Step 2: Build and verify no errors**

```bash
cd viewer-svelte && npm run build 2>&1 | tail -5
```
Expected: `✓ built in` with no errors.

- [ ] **Step 3: Commit**

```bash
git checkout -b feat/modernize-ui
git add viewer-svelte/src/routes/+layout.svelte
git commit -m "feat: Inter font + CSS theme variables (light/dark)"
```

---

## Task 2: Dark Mode Toggle

**Files:**
- Modify: `viewer-svelte/src/routes/+layout.svelte`

Add toggle button to header, wire to localStorage and `data-theme` on `<html>`.

- [ ] **Step 1: Add dark mode state and logic to `<script>` in `+layout.svelte`**

Add after the existing imports:
```typescript
import { onMount } from 'svelte';

let dark = $state(false);

onMount(() => {
	dark = localStorage.getItem('theme') === 'dark';
	document.documentElement.dataset.theme = dark ? 'dark' : 'light';
});

function toggleTheme() {
	dark = !dark;
	const theme = dark ? 'dark' : 'light';
	localStorage.setItem('theme', theme);
	document.documentElement.dataset.theme = theme;
}
```

- [ ] **Step 2: Add toggle button to the header template**

In the `<nav class="tabs">` section, add the toggle button after the ROTS tab button:
```html
<button class="theme-toggle" onclick={toggleTheme} title="Toggle dark mode">
	{dark ? '☀️' : '🌙'}
</button>
```

- [ ] **Step 3: Add toggle button styles**

In the `<style>` block, add:
```css
.theme-toggle {
	background: none;
	border: 1px solid var(--border);
	border-radius: 6px;
	padding: 5px 10px;
	cursor: pointer;
	font-size: 0.85rem;
	color: var(--text-muted);
	margin-left: 8px;
	transition: border-color 0.15s;
}
.theme-toggle:hover {
	border-color: var(--accent);
}
```

- [ ] **Step 4: Build and verify**

```bash
cd viewer-svelte && npm run build 2>&1 | tail -5
```
Expected: `✓ built in` with no errors.

- [ ] **Step 5: Add E2E test for dark mode toggle**

Add to `viewer-svelte/tests/sections.spec.ts`:
```typescript
test.describe('Dark mode toggle', () => {
	test('toggle switches data-theme attribute', async ({ page }) => {
		await page.goto('/');
		await page.waitForSelector('.theme-toggle');

		// Initially light
		await expect(page.locator('html')).not.toHaveAttribute('data-theme', 'dark');

		// Click toggle
		await page.locator('.theme-toggle').click();
		await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');

		// Click again
		await page.locator('.theme-toggle').click();
		await expect(page.locator('html')).toHaveAttribute('data-theme', 'light');
	});

	test('theme persists in localStorage', async ({ page }) => {
		await page.goto('/');
		await page.waitForSelector('.theme-toggle');
		await page.locator('.theme-toggle').click();

		const theme = await page.evaluate(() => localStorage.getItem('theme'));
		expect(theme).toBe('dark');
	});
});
```

- [ ] **Step 6: Run tests**

```bash
cd viewer-svelte && npm run build && npx playwright test --config tests/playwright.config.ts 2>&1 | tail -20
```
Expected: all tests pass including the two new dark mode tests.

- [ ] **Step 7: Commit**

```bash
git add viewer-svelte/src/routes/+layout.svelte viewer-svelte/tests/sections.spec.ts
git commit -m "feat: dark mode toggle with localStorage persistence"
```

---

## Task 3: Header Styles

**Files:**
- Modify: `viewer-svelte/src/routes/+layout.svelte`

Update `.header`, `.logo`, `.tab`, `.rots-badge` styles to use CSS vars and new Inter design.

- [ ] **Step 1: Replace header styles in `+layout.svelte`**

Replace all styles for `.app`, `.header`, `.logo`, `.tabs`, `.tab`, `.rots-badge`, and `.main` with:
```css
.app { display: flex; flex-direction: column; min-height: 100vh; }

.header {
	display: flex;
	align-items: center;
	gap: 16px;
	padding: 11px 32px;
	background: var(--bg-header);
	border-bottom: 1px solid var(--border);
	position: sticky;
	top: 0;
	z-index: 10;
}

.logo {
	font-size: 1.05rem;
	font-weight: 700;
	letter-spacing: -0.03em;
	color: var(--text);
	margin: 0;
	font-style: normal;
}

.tagline {
	font-size: 0.6rem;
	color: var(--text-muted);
	letter-spacing: 0.06em;
	text-transform: uppercase;
	font-weight: 500;
}

.tabs { display: flex; gap: 2px; align-items: center; margin-left: auto; }

.tab {
	background: none;
	border: none;
	padding: 5px 13px;
	border-radius: 4px;
	cursor: pointer;
	font-family: 'Inter', system-ui, sans-serif;
	font-size: 0.72rem;
	font-weight: 500;
	color: var(--text-muted);
	transition: background 0.15s, color 0.15s;
}
.tab:hover { background: var(--accent-pill-bg); color: var(--accent); }
.tab.active { background: var(--accent); color: #fff; font-weight: 600; }

.rots-badge {
	font-size: 0.65rem;
	background: var(--accent);
	color: #fff;
	border-radius: 10px;
	padding: 1px 6px;
	margin-left: 3px;
	font-weight: 700;
}

.main { flex: 1; display: flex; overflow: hidden; width: 100%; min-width: 0; }

@media (max-width: 768px) {
	.header { padding: 10px 16px; gap: 10px; }
	.tagline { display: none; }
	.logo { font-size: 0.95rem; }
	.tab { padding: 5px 10px; font-size: 0.75rem; }
	.main { flex-direction: column; overflow: visible; }
}
```

- [ ] **Step 2: Add tagline to header template**

In the header, add the tagline between logo and tabs:
```html
<header class="header">
	<h1 class="logo">JVM Daily</h1>
	<span class="tagline">Daily briefing</span>
	<nav class="tabs">
		...existing tabs...
	</nav>
</header>
```

- [ ] **Step 3: Build and verify**

```bash
cd viewer-svelte && npm run build 2>&1 | tail -5
```

- [ ] **Step 4: Commit**

```bash
git add viewer-svelte/src/routes/+layout.svelte
git commit -m "feat: Inter header with tagline and CSS var theming"
```

---

## Task 4: Cluster Card Styles

**Files:**
- Modify: `viewer-svelte/src/lib/components/Cluster.svelte`

Replace all hardcoded colors/fonts with CSS vars and apply card design from spec.

- [ ] **Step 1: Replace all styles in `Cluster.svelte`**

Replace the entire `<style>` block with:
```css
.cluster {
	border: 1px solid var(--border);
	border-radius: 8px;
	padding: 16px 18px;
	background: var(--bg-card);
	margin-bottom: 8px;
	border-left: 3px solid var(--border);
	transition: border-left-color 0.15s;
}
.cluster:first-of-type {
	border-left-color: var(--accent);
}
.cluster.dismissed { opacity: 0.35; }

.cluster-head { display: flex; gap: 12px; align-items: flex-start; }
.cluster-head-text { flex: 1; min-width: 0; }

.cluster-title {
	font-size: 0.92rem;
	font-weight: 600;
	color: var(--text);
	line-height: 1.4;
	margin-bottom: 6px;
}
.cluster-count {
	font-size: 0.62rem;
	font-weight: 500;
	color: var(--text-muted);
	margin-left: 8px;
	background: var(--badge-count-bg);
	padding: 1px 7px;
	border-radius: 10px;
}
.cluster-synthesis {
	font-size: 0.75rem;
	color: var(--text-secondary);
	line-height: 1.65;
	margin-bottom: 10px;
}
.cluster-synthesis :global(p) { margin: 0; }

.cluster-actions { display: flex; flex-direction: column; gap: 4px; flex-shrink: 0; }
.action-btn {
	background: none;
	border: 1px solid var(--action-btn-border);
	border-radius: 4px;
	width: 28px;
	height: 28px;
	cursor: pointer;
	font-size: 0.72rem;
	color: var(--action-btn-text);
	display: flex;
	align-items: center;
	justify-content: center;
	transition: border-color 0.15s, color 0.15s;
}
.bookmark-btn:hover { border-color: var(--accent); color: var(--accent); }
.bookmark-btn.bookmarked { background: var(--accent); border-color: var(--accent); color: #fff; }
.tick-btn:hover { border-color: var(--accent); color: var(--accent); }

.article-list { margin-top: 10px; }

.expand-btn {
	margin-top: 8px;
	background: none;
	border: none;
	padding: 0;
	font-size: 0.72rem;
	color: var(--accent);
	cursor: pointer;
	font-family: 'Inter', system-ui, sans-serif;
	font-weight: 500;
}
.expand-btn:hover { text-decoration: underline; }

.cluster-badges {
	display: flex;
	flex-wrap: wrap;
	gap: 5px;
	margin-top: 8px;
	align-items: center;
}
.badge-count {
	font-size: 0.58rem;
	font-weight: 500;
	padding: 2px 8px;
	border-radius: 10px;
	background: var(--badge-count-bg);
	color: var(--badge-count-text);
}
.badge-hn {
	font-size: 0.58rem;
	font-weight: 500;
	padding: 2px 8px;
	border-radius: 10px;
	background: var(--badge-hn-bg);
	color: var(--badge-hn-text);
}
.badge-reddit {
	font-size: 0.58rem;
	font-weight: 500;
	padding: 2px 8px;
	border-radius: 10px;
	background: var(--badge-reddit-bg);
	color: var(--badge-reddit-text);
}

@media (max-width: 768px) {
	.cluster { position: relative; padding: 14px 14px; }
	.cluster-title { font-size: 0.88rem; padding-right: 70px; }
	.cluster-actions { flex-direction: row; position: absolute; right: 14px; top: 14px; }
}
```

- [ ] **Step 2: Add cluster badges to template**

In the cluster template, after `.cluster-head`, add badges for article count, HN score, and reddit — inside the `cluster-head-text` div, after `.cluster-synthesis`:

```svelte
<div class="cluster-badges">
	<span class="badge-count">{mergedArticles.length} article{mergedArticles.length !== 1 ? 's' : ''}</span>
	{#each cluster.articles.filter(a => a.sourceType === 'hackernews') as hn}
		{#if hn.engagementScore > 0}
			<span class="badge-hn">HN · {hn.engagementScore}</span>
		{/if}
	{/each}
	{#if cluster.articles.some(a => a.sourceType === 'reddit')}
		<span class="badge-reddit">reddit</span>
	{/if}
</div>
```

- [ ] **Step 3: Build and verify**

```bash
cd viewer-svelte && npm run build 2>&1 | tail -5
```

- [ ] **Step 4: Run all tests**

```bash
cd viewer-svelte && npx playwright test --config tests/playwright.config.ts 2>&1 | tail -15
```
Expected: all tests pass. The tests use `.cluster` class selector — verify it's still present.

- [ ] **Step 5: Commit**

```bash
git add viewer-svelte/src/lib/components/Cluster.svelte
git commit -m "feat: Cluster card — Inter font, CSS vars, article badges"
```

---

## Task 5: ReleaseCard Styles

**Files:**
- Modify: `viewer-svelte/src/lib/components/ReleaseCard.svelte`

Update styles to match card design. Bullets and content are unchanged.

- [ ] **Step 1: Replace styles in `ReleaseCard.svelte`**

Replace the entire `<style>` block with:
```css
.release-card {
	border: 1px solid var(--border);
	border-radius: 8px;
	padding: 16px 18px;
	background: var(--bg-card);
	margin-bottom: 8px;
	border-left: 3px solid var(--accent);
}
.release-card.dismissed { opacity: 0.35; }

.cluster-head { display: flex; gap: 12px; align-items: flex-start; }
.cluster-head-text { flex: 1; min-width: 0; }

.cluster-title {
	font-size: 0.92rem;
	font-weight: 700;
	color: var(--text);
	line-height: 1.4;
	margin-bottom: 10px;
}
.cluster-count {
	font-size: 0.62rem;
	font-weight: 500;
	color: var(--text-muted);
	margin-left: 8px;
	background: var(--badge-count-bg);
	padding: 1px 7px;
	border-radius: 10px;
}

.cluster-synthesis,
.release-bullets {
	font-size: 0.75rem;
	color: var(--text-secondary);
	line-height: 1.7;
}
.release-bullets :global(ul) { margin: 0; padding-left: 18px; }
.release-bullets :global(li) { margin-bottom: 5px; }
.release-bullets :global(code) {
	background: var(--badge-count-bg);
	padding: 1px 4px;
	border-radius: 3px;
	font-size: 0.7rem;
	color: var(--text);
}

.cluster-actions { display: flex; flex-direction: column; gap: 4px; flex-shrink: 0; }
.action-btn {
	background: none;
	border: 1px solid var(--action-btn-border);
	border-radius: 4px;
	width: 28px;
	height: 28px;
	cursor: pointer;
	font-size: 0.72rem;
	color: var(--action-btn-text);
	display: flex;
	align-items: center;
	justify-content: center;
	transition: border-color 0.15s, color 0.15s;
}
.bookmark-btn:hover { border-color: var(--accent); color: var(--accent); }
.bookmark-btn.bookmarked { background: var(--accent); border-color: var(--accent); color: #fff; }
.tick-btn:hover { border-color: var(--accent); color: var(--accent); }

.release-badges { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 10px; }
.badge-release {
	font-size: 0.65rem;
	font-weight: 500;
	padding: 3px 10px;
	border: 1px solid var(--border);
	border-radius: 12px;
	color: var(--text-secondary);
	text-decoration: none;
	white-space: nowrap;
	transition: border-color 0.15s, color 0.15s;
}
.badge-release:hover { border-color: var(--accent); color: var(--accent); }

@media (max-width: 768px) {
	.release-card { position: relative; }
	.cluster-head { display: block; }
	.cluster-title { font-size: 0.88rem; padding-right: 70px; }
	.cluster-actions { flex-direction: row; position: absolute; right: 14px; top: 14px; }
}
```

- [ ] **Step 2: Build and run tests**

```bash
cd viewer-svelte && npm run build && npx playwright test --config tests/playwright.config.ts 2>&1 | tail -15
```
Expected: all tests pass. Tests use `.release-card` class — verify it's still present.

- [ ] **Step 3: Commit**

```bash
git add viewer-svelte/src/lib/components/ReleaseCard.svelte
git commit -m "feat: ReleaseCard — Inter font, CSS vars, green left border"
```

---

## Task 6: Page Styles + Release Anchor Pills

**Files:**
- Modify: `viewer-svelte/src/routes/+page.svelte`

Update digest header styles, section labels, and add anchor pill row above releases.

- [ ] **Step 1: Replace styles in `+page.svelte`**

Replace the entire `<style>` block with:
```css
.loading, .empty { padding: 48px; text-align: center; color: var(--text-muted); width: 100%; }

.digest-content {
	flex: 1;
	overflow-y: auto;
	padding: 32px 40px;
	max-width: 920px;
	margin: 0 auto;
	width: 100%;
}

.digest-header {
	margin-bottom: 28px;
	padding-bottom: 16px;
	border-bottom: 1px solid var(--border);
}
.digest-date {
	font-size: 1.6rem;
	font-weight: 700;
	letter-spacing: -0.02em;
	color: var(--text);
	line-height: 1.2;
}
.digest-stats {
	display: flex;
	gap: 12px;
	font-size: 0.75rem;
	color: var(--text-muted);
	margin-top: 6px;
	font-weight: 500;
}

.section-label {
	font-size: 0.62rem;
	text-transform: uppercase;
	letter-spacing: 0.1em;
	color: var(--text-muted);
	margin-bottom: 12px;
	font-weight: 600;
	display: inline-block;
}

.releases-section,
.tweets-section,
.mailing-section { margin-top: 32px; }

.release-pills {
	display: flex;
	flex-wrap: wrap;
	gap: 6px;
	margin-bottom: 14px;
}
.release-pill {
	background: var(--accent-pill-bg);
	border: 1px solid var(--accent-pill-border);
	color: var(--accent-pill-text);
	font-size: 0.65rem;
	padding: 4px 11px;
	border-radius: 12px;
	font-weight: 600;
	text-decoration: none;
	cursor: pointer;
	border-style: solid;
	font-family: 'Inter', system-ui, sans-serif;
	transition: opacity 0.15s;
}
.release-pill:hover { opacity: 0.8; }

.mailing-list { list-style: none; padding: 0; margin: 0; }
.mailing-item {
	padding: 10px 0;
	border-bottom: 1px solid var(--border);
	display: flex;
	align-items: baseline;
	gap: 10px;
	flex-wrap: wrap;
}
.mailing-item a {
	font-size: 0.92rem;
	font-weight: 600;
	color: var(--text);
	text-decoration: none;
	line-height: 1.4;
}
.mailing-item a:hover { color: var(--accent); }
.mailing-meta { font-size: 0.75rem; color: var(--text-muted); }

.rots-inline-section { margin: 32px 0 24px; padding-bottom: 16px; }
.rots-inline-section .section-label { color: #b45309; }

.archive-section { margin-top: 32px; }
.archive-section .section-label { color: var(--text-muted); }

.tweet-card { border-bottom: 1px solid var(--border); padding: 14px 0; }
.tweet-header { display: flex; align-items: center; gap: 6px; margin-bottom: 6px; }
.tweet-header a { font-size: 0.85rem; color: var(--text-secondary); text-decoration: none; }
.tweet-header a:hover { color: var(--accent); }
.tweet-text { color: var(--text-secondary); font-size: 0.85rem; line-height: 1.65; margin: 0; }

@media (max-width: 768px) {
	.digest-content { padding: 16px; max-width: 100%; overflow-x: hidden; word-wrap: break-word; overflow-wrap: break-word; }
	.digest-date { font-size: 1.3rem; }
}
```

- [ ] **Step 2: Add release anchor pills to template**

In the `{#if normalReleases.length > 0}` block in the template, add the pills row before the release cards. The pills use `scrollIntoView` to jump to the matching card by ID. Replace the releases section block with:

```svelte
{#if normalReleases.length > 0}
	<div class="releases-section">
		<div class="section-label">Releases</div>
		<div class="release-pills">
			{#each normalReleases as cluster (cluster.id)}
				<button
					class="release-pill"
					onclick={() => {
						const el = document.getElementById(`release-${cluster.id}`);
						if (el) { el.scrollIntoView({ behavior: 'smooth', block: 'center' }); el.style.outline = '2px solid var(--accent)'; setTimeout(() => { el.style.outline = ''; }, 1200); }
					}}
				>{cluster.title}</button>
			{/each}
		</div>
		{#each normalReleases as cluster (cluster.id)}
			<div id="release-{cluster.id}">
				<ReleaseCard
					{cluster}
					bookmarked={false}
					dismissedState={false}
					onBookmark={() => toggleBookmark(currentDate, cluster.title)}
					onDismiss={() => toggleDismiss(currentDate, cluster.title)}
				/>
			</div>
		{/each}
	</div>
{/if}
```

- [ ] **Step 3: Build and run tests**

```bash
cd viewer-svelte && npm run build && npx playwright test --config tests/playwright.config.ts 2>&1 | tail -15
```
Expected: all tests pass.

- [ ] **Step 4: Commit**

```bash
git add viewer-svelte/src/routes/+page.svelte
git commit -m "feat: page styles + release anchor pills"
```

---

## Task 7: Remaining Component Styles

**Files:**
- Modify: `viewer-svelte/src/lib/components/DateSidebar.svelte`
- Modify: `viewer-svelte/src/lib/components/ArticleRow.svelte`
- Modify: `viewer-svelte/src/lib/components/SourceBadge.svelte`
- Modify: `viewer-svelte/src/lib/components/TopicTag.svelte`

- [ ] **Step 1: Update `DateSidebar.svelte` styles**

Replace the `<style>` block with:
```css
.sidebar {
	width: 160px;
	flex-shrink: 0;
	padding: 24px 14px;
	border-right: 1px solid var(--border);
	overflow-y: auto;
	background: var(--bg);
}
.sidebar-title {
	font-size: 0.62rem;
	text-transform: uppercase;
	letter-spacing: 0.1em;
	color: var(--text-muted);
	margin: 0 0 14px;
	font-weight: 600;
}
.date-btn {
	display: block;
	width: 100%;
	padding: 8px 10px;
	margin-bottom: 3px;
	background: none;
	border: none;
	border-radius: 6px;
	cursor: pointer;
	font-family: 'Inter', system-ui, sans-serif;
	font-size: 0.8rem;
	color: var(--text-secondary);
	text-align: left;
	transition: background 0.15s, color 0.15s;
	font-weight: 400;
}
.date-btn:hover { background: var(--accent-pill-bg); color: var(--accent); }
.date-btn.active { background: var(--accent); color: #fff; font-weight: 600; }

@media (max-width: 768px) {
	.sidebar {
		width: 100%;
		display: flex;
		flex-wrap: nowrap;
		align-items: center;
		overflow-x: auto;
		overflow-y: hidden;
		border-right: none;
		border-bottom: 1px solid var(--border);
		padding: 10px 16px;
		gap: 5px;
		-webkit-overflow-scrolling: touch;
	}
	.sidebar-title { display: none; }
	.date-btn { white-space: nowrap; width: auto; padding: 5px 12px; margin-bottom: 0; font-size: 0.78rem; flex-shrink: 0; }
}
```

- [ ] **Step 2: Update `ArticleRow.svelte` styles**

Replace the entire `<style>` block with:
```css
.article-row { display: flex; gap: 12px; padding: 14px 0; border-top: 1px solid var(--border); }
.article-row-social { padding: 10px 0; gap: 8px; }
.article-favicon { width: 18px; height: 18px; border-radius: 3px; margin-top: 3px; flex-shrink: 0; }
.article-body { flex: 1; min-width: 0; }
.article-title-row { display: flex; align-items: baseline; gap: 8px; flex-wrap: wrap; }
.article-title {
	font-size: 0.88rem;
	font-weight: 600;
	color: var(--text);
	text-decoration: none;
	line-height: 1.4;
}
.article-title:hover { color: var(--accent); }
.article-source { font-size: 0.72rem; color: var(--text-muted); white-space: nowrap; }
.article-summary { color: var(--text-secondary); font-size: 0.78rem; line-height: 1.65; margin: 6px 0 8px; }
.article-meta { display: flex; flex-wrap: wrap; gap: 5px; align-items: center; }
.taxonomy-badge {
	font-size: 0.65rem;
	font-weight: 600;
	padding: 2px 7px;
	border-radius: 3px;
	background: var(--accent-pill-bg);
	color: var(--accent-dark);
	white-space: nowrap;
}
.social-card-header { display: flex; align-items: center; gap: 6px; margin-bottom: 4px; }
.social-card-icon { font-size: 0.85rem; }
.social-card-author {
	font-size: 0.8rem;
	font-weight: 600;
	color: var(--text-secondary);
	text-decoration: none;
}
.social-card-author:hover { color: var(--accent); }
.social-card-text { color: var(--text-secondary); font-size: 0.82rem; line-height: 1.65; margin: 0 0 6px; }
```

- [ ] **Step 3: Update `SourceBadge.svelte` styles**

Replace the `<style>` block with:
```css
.source-badge {
	font-size: 0.6rem;
	font-weight: 600;
	text-transform: uppercase;
	letter-spacing: 0.05em;
	padding: 2px 6px;
	border-radius: 3px;
	background: var(--badge-count-bg);
	color: var(--badge-count-text);
}
.source-reddit { color: #ff4500; background: #fff0eb; }
.source-bluesky { color: #0085ff; background: #e8f4ff; }
.source-github_trending, .source-github_release { color: #555; background: var(--badge-count-bg); }
.source-openjdk_mail { color: #e76f00; background: #fff5eb; }
```

- [ ] **Step 4: Update `TopicTag.svelte` styles**

Replace the `<style>` block with:
```css
.topic-tag {
	font-size: 0.65rem;
	background: var(--accent-pill-bg);
	color: var(--accent-dark);
	padding: 2px 7px;
	border-radius: 4px;
	white-space: nowrap;
	font-weight: 500;
}
```

- [ ] **Step 5: Build and run all tests**

```bash
cd viewer-svelte && npm run build && npx playwright test --config tests/playwright.config.ts 2>&1 | tail -15
```
Expected: all tests pass.

- [ ] **Step 6: Commit**

```bash
git add viewer-svelte/src/lib/components/DateSidebar.svelte \
        viewer-svelte/src/lib/components/ArticleRow.svelte \
        viewer-svelte/src/lib/components/SourceBadge.svelte \
        viewer-svelte/src/lib/components/TopicTag.svelte
git commit -m "feat: Inter + CSS vars across DateSidebar, ArticleRow, SourceBadge, TopicTag"
```

---

## Task 8: Push PR

- [ ] **Step 1: Run full build + tests one final time**

```bash
cd viewer-svelte && npm run build && npx playwright test --config tests/playwright.config.ts 2>&1 | tail -20
```
Expected: all tests pass.

- [ ] **Step 2: Push branch and open PR**

```bash
git push -u origin feat/modernize-ui
gh pr create --title "feat: modernize UI — Inter font, light/dark mode, card layout" --body "$(cat <<'EOF'
## Summary
- Replaces Spectral serif with Inter sans-serif across all components
- Adds light/dark mode toggle (persisted in localStorage) via CSS custom properties
- Refines card layout: rounded cards, green left-border accent on top story, pill badges for article count / HN / reddit
- Release anchor pills scroll to matching release card on click
- No changes to data fetching, API, or content generation

## Test plan
- [ ] All existing Playwright E2E tests pass
- [ ] New dark mode toggle tests pass
- [ ] Visually verify light and dark modes in browser
- [ ] Check mobile layout on narrow viewport
EOF
)"
```
