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
	<link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet">
</svelte:head>

<div class="app">
	<header class="header">
		<h1 class="logo">JVM Daily</h1>
		<span class="tagline">Daily briefing</span>
		<nav class="tabs">
			<button class="tab" class:active={currentTab === 'digest'}
				onclick={() => currentTab = 'digest'}>Digest</button>
			<button class="tab" class:active={currentTab === 'pipeline'}
				onclick={() => currentTab = 'pipeline'}>Pipeline</button>
			<button class="tab" class:active={currentTab === 'rots'}
				onclick={() => currentTab = 'rots'}>
				ROTS
				{#if badgeCount > 0}<span class="rots-badge">{badgeCount}</span>{/if}
			</button>
			<button class="theme-toggle" onclick={toggleTheme} title="Toggle dark mode">
				{dark ? '☀️' : '🌙'}
			</button>
		</nav>
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
</style>
