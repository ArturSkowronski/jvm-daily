<script lang="ts">
	import { bookmarks, totalBookmarkCount } from '$lib/stores/bookmarks';
	import type { Snippet } from 'svelte';

	let { children }: { children: Snippet } = $props();
	let currentTab = $state('digest');

	const badgeCount = $derived(totalBookmarkCount($bookmarks));
</script>

<svelte:head>
	<title>JVM Daily</title>
	<link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Newsreader:ital,wght@0,400;0,600;1,400&display=swap" rel="stylesheet">
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
		margin: 0; font-family: 'Inter', system-ui, sans-serif;
		background: #fafafa; color: #1a1a1a; line-height: 1.5;
	}
	:global(*) { box-sizing: border-box; }
	:global(h1, h2, h3) { font-family: 'Newsreader', serif; }

	.app { display: flex; flex-direction: column; min-height: 100vh; }
	.header {
		display: flex; align-items: center; gap: 24px; padding: 12px 24px;
		background: #fff; border-bottom: 1px solid #eee; position: sticky; top: 0; z-index: 10;
	}
	.logo { font-size: 1.1rem; font-weight: 700; margin: 0; font-style: italic; }
	.tabs { display: flex; gap: 4px; }
	.tab {
		background: none; border: none; padding: 6px 16px; border-radius: 6px;
		cursor: pointer; font-size: 0.82rem; font-weight: 500; color: #666;
	}
	.tab:hover { background: #f5f5f5; }
	.tab.active { background: #1a1a1a; color: #fff; }
	.rots-badge {
		font-size: 0.65rem; background: #f59e0b; color: #fff; border-radius: 10px;
		padding: 1px 6px; margin-left: 4px; font-weight: 700;
	}
	.main { flex: 1; display: flex; overflow: hidden; width: 100%; }
</style>
