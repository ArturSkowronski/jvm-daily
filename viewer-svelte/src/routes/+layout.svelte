<script lang="ts">
	import { bookmarks, totalBookmarkCount } from '$lib/stores/bookmarks';
	import type { Snippet } from 'svelte';

	let { children }: { children: Snippet } = $props();
	let currentTab = $state('digest');

	const badgeCount = $derived(totalBookmarkCount($bookmarks));
</script>

<svelte:head>
	<title>JVM Daily</title>
	<link href="https://fonts.googleapis.com/css2?family=Spectral:ital,wght@0,400;0,600;0,700;1,400&display=swap" rel="stylesheet">
</svelte:head>

<div class="app">
	<header class="header">
		<h1 class="logo">JVM Daily</h1>
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
	:global(body) {
		margin: 0;
		font-family: 'Spectral', Georgia, serif;
		background: #fff;
		color: #1a1a1a;
		line-height: 1.7;
		font-size: 17px;
	}
	:global(*) { box-sizing: border-box; }
	:global(h1, h2, h3) { font-family: 'Spectral', Georgia, serif; }
	:global(a) { color: #00a64e; }
	:global(a:hover) { color: #008a3e; }

	.app { display: flex; flex-direction: column; min-height: 100vh; }
	.header {
		display: flex; align-items: center; gap: 24px;
		padding: 14px 32px;
		background: #fff;
		border-bottom: 1px solid #e0e0e0;
		position: sticky; top: 0; z-index: 10;
		box-shadow: 0 1px 3px rgba(0,0,0,0.04);
	}
	.logo {
		font-size: 1.3rem; font-weight: 700; margin: 0;
		font-style: italic; letter-spacing: -0.01em;
	}
	.tabs { display: flex; gap: 4px; }
	.tab {
		background: none; border: none; padding: 7px 18px; border-radius: 6px;
		cursor: pointer; font-family: 'Spectral', Georgia, serif;
		font-size: 0.9rem; font-weight: 400; color: #666;
		transition: background 0.15s, color 0.15s;
	}
	.tab:hover { background: #f0faf4; color: #00a64e; }
	.tab.active { background: #00a64e; color: #fff; font-weight: 600; }
	.rots-badge {
		font-size: 0.7rem; background: #00a64e; color: #fff; border-radius: 10px;
		padding: 1px 7px; margin-left: 4px; font-weight: 700;
	}
	.main { flex: 1; display: flex; overflow: hidden; width: 100%; min-width: 0; }

	@media (max-width: 768px) {
		.header { padding: 10px 16px; gap: 12px; }
		.logo { font-size: 1.1rem; }
		.tab { padding: 6px 12px; font-size: 0.85rem; }
		.main { flex-direction: column; overflow: visible; }
	}
</style>
