<script lang="ts">
	import type { DigestCluster } from '$lib/api/types';
	import ArticleRow from './ArticleRow.svelte';
	import { mergeByTitle } from '$lib/utils/merge';
	import { marked } from 'marked';
	import ItemActions from './ItemActions.svelte';

	let {
		cluster,
		done = false,
		later = false,
		rots = false,
		focused = false,
		dimmed = false,
		onDone,
		onLater,
		onRots
	}: {
		cluster: DigestCluster;
		done?: boolean;
		later?: boolean;
		rots?: boolean;
		focused?: boolean;
		dimmed?: boolean;
		onDone?: () => void;
		onLater?: () => void;
		onRots?: () => void;
	} = $props();

	const mergedArticles = $derived(
		mergeByTitle([...cluster.articles]).sort((a, b) => b.engagementScore - a.engagementScore)
	);

	const isSingle = $derived(mergedArticles.length === 1);
	let expanded = $state(false);

	const synthesisHtml = $derived(marked.parse(cluster.summary) as string);
</script>

<div class="cluster review-item" class:dismissed={dimmed} class:focused data-key={cluster.title}>
	<div class="cluster-head">
		<div class="cluster-head-text">
			<div class="cluster-title">
				{cluster.title}
				<span class="cluster-count">{mergedArticles.length} articles</span>
			</div>
			<div class="cluster-synthesis">
				{@html synthesisHtml}
			</div>
		</div>
		<ItemActions {done} {later} {rots} {focused} {onDone} {onLater} {onRots} />
	</div>
	{#if isSingle}
		{#if expanded}
			<div class="article-list">
				{#each mergedArticles as article}
					<ArticleRow {article} clusterSize={mergedArticles.length} />
				{/each}
			</div>
		{:else}
			<button class="expand-btn" onclick={() => expanded = true}>
				Show source article
			</button>
		{/if}
	{:else}
		<div class="article-list">
			{#each mergedArticles as article}
				<ArticleRow {article} clusterSize={mergedArticles.length} />
			{/each}
		</div>
	{/if}
</div>

<style>
	.cluster.review-item {
		border-bottom: 1px solid #e0e0e0;
		padding: 28px 16px 28px 16px;
		border-left: 3px solid transparent; margin-left: -19px;
		scroll-margin-top: 84px;
	}
	.cluster.focused {
		border-left-color: #00a64e;
		background: linear-gradient(to right, #f3fbf6, rgba(255, 255, 255, 0) 70%);
	}
	:global(body.compact) .cluster:not(.focused) .cluster-synthesis, :global(body.compact) .cluster:not(.focused) .article-list, :global(body.compact) .cluster:not(.focused) .expand-btn { display: none; }
	.cluster.dismissed { opacity: 0.35; }
	.cluster-head { display: flex; gap: 16px; }
	.cluster-head-text { flex: 1; min-width: 0; }
	.cluster-title {
		font-size: 1.4rem; font-weight: 700; line-height: 1.3;
		margin-bottom: 12px; color: #1a1a1a;
	}
	.cluster-count {
		font-size: 0.8rem; font-weight: 400; color: #868787; margin-left: 10px;
	}
	.cluster-synthesis { font-size: 1rem; color: #363737; line-height: 1.8; }
	.cluster-synthesis :global(p) { margin: 0 0 12px; }
	.cluster-synthesis :global(p:last-child) { margin-bottom: 0; }
	.cluster-synthesis :global(code) {
		background: #f0faf4; padding: 2px 6px; border-radius: 3px; font-size: 0.9rem;
		word-break: break-all;
	}
	.article-list { margin-top: 12px; }
	.expand-btn {
		margin-top: 10px; font-size: 0.78rem; color: #888; background: none;
		border: 1px solid #e0e0e0; border-radius: 6px; padding: 4px 12px;
		cursor: pointer; transition: border-color 0.15s, color 0.15s;
	}
	.expand-btn:hover { border-color: #00a64e; color: #00a64e; }

	@media (max-width: 768px) {
		.cluster.review-item { margin-left: 0; padding-left: 0; padding-right: 0; border-left: none; }
		.cluster.focused { background: none; }
		.cluster { position: relative; }
		.cluster-head { display: block; }
		.cluster-title { font-size: 1.2rem; padding-right: 76px; }
		.cluster-synthesis { font-size: 0.95rem; }
		.cluster-head > :global(.item-actions) { position: absolute; right: 0; top: 28px; }
	}
</style>
