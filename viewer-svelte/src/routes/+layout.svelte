<script lang="ts">
	import { bookmarks, totalBookmarkCount } from '$lib/stores/bookmarks';
	import type { Snippet } from 'svelte';
	import { onMount } from 'svelte';

	let { children }: { children: Snippet } = $props();
	let currentTab = $state('digest');

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

	const badgeCount = $derived(totalBookmarkCount($bookmarks));
</script>

<svelte:head>
	<title>JVM Daily</title>
	<link rel="preconnect" href="https://fonts.googleapis.com">
	<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin="">
	<link href="https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@400;500;600;700&family=IBM+Plex+Mono:wght@400;500;600&display=swap" rel="stylesheet">
</svelte:head>

<div class="app">
	<header class="header">
		<div class="header-inner">
			<div class="brand">
				<span class="logo">JVM Daily</span>
				<span class="tagline">Daily briefing</span>
			</div>
			<nav class="tabs" aria-label="Sections">
				<button class="tab" class:active={currentTab === 'digest'}
					onclick={() => currentTab = 'digest'}>Digest</button>
				<button class="tab" class:active={currentTab === 'pipeline'}
					onclick={() => currentTab = 'pipeline'}>Pipeline</button>
				<button class="tab" class:active={currentTab === 'rots'}
					onclick={() => currentTab = 'rots'}>
					ROTS
					{#if badgeCount > 0}<span class="count">{badgeCount}</span>{/if}
				</button>
				<button class="theme-btn theme-toggle" onclick={toggleTheme} title="Toggle dark mode">
					{dark ? '☀️' : '🌙'}
				</button>
			</nav>
		</div>
	</header>

	<main class="main">
		{#if currentTab === 'digest'}
			{@render children()}
		{:else if currentTab === 'pipeline'}
			{#await import('./pipeline/+page.svelte') then module}
				<module.default />
			{/await}
		{:else if currentTab === 'rots'}
			{#await import('./rots/+page.svelte') then module}
				<module.default />
			{/await}
		{/if}
	</main>
</div>

<style>
	:global(:root) {
		--bg:           #f7f6f1;
		--bg-card:      #ffffff;
		--bg-soft:      #f1efe7;
		--bg-header:    #fbfaf5;
		--border:       #e6e2d4;
		--border-soft:  #efece1;
		--border-strong:#cdc8b6;

		--text:         #1a1a17;
		--text-2:       #45433c;
		--text-3:       #78766a;
		--text-faint:   #aeab9d;

		--accent:       #0a8a43;
		--accent-dark:  #066130;
		--accent-bg:    #ebf6ef;
		--accent-bd:    #c2dccb;

		--rots:         #b35a00;
		--rots-bg:      #fbf1e2;

		--src-reddit:   #d93a00;  --src-reddit-bg: #fdefe9;
		--src-hn:       #b35309;  --src-hn-bg:     #fdf3e4;
		--src-bsky:     #1d6dd8;  --src-bsky-bg:   #ecf3fd;
		--src-gh:       #2d2d2d;  --src-gh-bg:     #efece3;
		--src-jdk:      #b8460c;  --src-jdk-bg:    #fdf1e6;
		--src-rss:      #6d4aa3;  --src-rss-bg:    #f2eefa;

		--radius:       8px;
		--shadow-sm:    0 1px 1px rgba(20,18,10,.04);
		--shadow-md:    0 2px 12px -4px rgba(20,18,10,.08);

		--font-sans:    "IBM Plex Sans", ui-sans-serif, system-ui, sans-serif;
		--font-mono:    "IBM Plex Mono", ui-monospace, Menlo, monospace;

		/* Aliases kept for back-compat with components that haven't been retokened */
		--text-secondary: var(--text-2);
		--text-muted:     var(--text-3);
		--bg-strip:       var(--bg);
		--accent-pill-bg:     var(--accent-bg);
		--accent-pill-border: var(--accent-bd);
		--accent-pill-text:   var(--accent-dark);
		--badge-count-bg:   var(--bg-soft);
		--badge-count-text: var(--text-3);
		--badge-hn-bg:      var(--src-hn-bg);
		--badge-hn-text:    var(--src-hn);
		--badge-reddit-bg:  var(--src-reddit-bg);
		--badge-reddit-text:var(--src-reddit);
		--action-btn-border:var(--border);
		--action-btn-text:  var(--text-faint);
	}
	:global([data-theme="dark"]) {
		--bg:           #14140f;
		--bg-card:      #1c1c17;
		--bg-soft:      #1f1f19;
		--bg-header:    #1a1a15;
		--border:       #2c2b24;
		--border-soft:  #24231d;
		--border-strong:#3a392f;
		--text:         #ecebe0;
		--text-2:       #b6b4a5;
		--text-3:       #807e70;
		--text-faint:   #54524a;
		--accent:       #4ec27a;
		--accent-dark:  #78d89a;
		--accent-bg:    #132a1d;
		--accent-bd:    #224d36;
		--rots:         #e4963c;
		--rots-bg:      #2a1e10;
		--src-reddit-bg:#2a1710;  --src-hn-bg:#2a1e0e;  --src-bsky-bg:#0f1e2e;
		--src-gh-bg:#23221c;      --src-jdk-bg:#2a1e10; --src-rss-bg:#201a2e;
	}
	:global(body) {
		margin: 0;
		font-family: var(--font-sans);
		background: var(--bg);
		color: var(--text);
		line-height: 1.65;
		font-size: 15px;
		-webkit-font-smoothing: antialiased;
	}
	:global(*) { box-sizing: border-box; }
	:global(html), :global(body) { padding: 0; }
	:global(h1, h2, h3) { font-family: var(--font-sans); }
	:global(a) { color: var(--accent); text-decoration: none; }
	:global(a:hover) { color: var(--accent-dark); text-decoration: underline; text-underline-offset: 2px; }
	:global(:focus-visible) { outline: 2px solid var(--accent); outline-offset: 2px; border-radius: 4px; }

	.app { display: flex; flex-direction: column; min-height: 100vh; }

	.header {
		position: sticky; top: 0; z-index: 10;
		background: var(--bg-header);
		border-bottom: 1px solid var(--border);
	}
	.header-inner {
		display: flex; align-items: center; gap: 16px;
		padding: 11px 28px;
		max-width: 1200px; margin: 0 auto;
	}

	.brand { display: flex; align-items: baseline; gap: 10px; }
	.logo {
		font-family: var(--font-sans);
		font-weight: 700;
		font-size: 16px;
		letter-spacing: -0.01em;
		color: var(--text);
		display: inline-flex; align-items: baseline; gap: 7px;
	}
	.logo::before {
		content: "";
		width: 7px; height: 7px;
		background: var(--accent);
		border-radius: 50%;
		display: inline-block;
		transform: translateY(-1px);
	}
	.tagline {
		font: 500 9.5px/1 var(--font-mono);
		letter-spacing: 0.12em;
		text-transform: uppercase;
		color: var(--text-3);
		white-space: nowrap;
	}

	.tabs { display: flex; gap: 2px; margin-left: auto; align-items: center; }
	.tab {
		appearance: none;
		background: transparent; border: 0;
		padding: 5px 12px;
		border-radius: 5px;
		font: 500 11.5px/1 var(--font-sans);
		color: var(--text-3);
		cursor: pointer;
		display: inline-flex; align-items: center; gap: 6px;
		transition: background .12s, color .12s;
	}
	.tab:hover { background: var(--bg-soft); color: var(--text-2); }
	.tab.active { background: var(--accent); color: #fff; font-weight: 600; }
	.tab .count {
		font: 600 9.5px/1 var(--font-mono);
		background: var(--bg-soft);
		color: var(--text-3);
		padding: 2px 5px;
		border-radius: 10px;
	}
	.tab.active .count {
		background: rgba(255,255,255,.25);
		color: #fff;
	}

	.theme-btn {
		appearance: none;
		background: transparent;
		border: 1px solid var(--border);
		border-radius: 5px;
		padding: 4px 9px;
		font-size: 13px;
		cursor: pointer;
		margin-left: 6px;
		color: var(--text-3);
	}
	.theme-btn:hover { border-color: var(--border-strong); }

	.main { flex: 1; display: flex; overflow: hidden; width: 100%; min-width: 0; max-width: 1200px; margin: 0 auto; }

	@media (max-width: 760px) {
		.header-inner { padding: 10px 14px; gap: 10px; }
		.tagline { display: none; }
		.tab { padding: 5px 10px; font-size: 11px; }
		.main { flex-direction: column; overflow: visible; }
	}
</style>
