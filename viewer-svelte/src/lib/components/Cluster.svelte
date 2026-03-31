<script lang="ts">
	import type { DigestCluster } from '$lib/api/types';
	import ArticleRow from './ArticleRow.svelte';
	import { mergeByTitle } from '$lib/utils/merge';
	import { marked } from 'marked';

	let {
		cluster,
		bookmarked = false,
		dismissedState = false,
		onBookmark,
		onDismiss
	}: {
		cluster: DigestCluster;
		bookmarked?: boolean;
		dismissedState?: boolean;
		onBookmark?: () => void;
		onDismiss?: () => void;
	} = $props();

	const mergedArticles = $derived(
		mergeByTitle([...cluster.articles]).sort((a, b) => b.engagementScore - a.engagementScore)
	);

	const isSingle = $derived(mergedArticles.length === 1);
	let expanded = $state(false);

	const synthesisHtml = $derived(marked.parse(cluster.summary) as string);
</script>

<div class="cluster" class:dismissed={dismissedState} data-key={cluster.title}>
	<div class="cluster-head">
		<div class="cluster-head-text">
			<div class="cluster-title">
				{cluster.title}
				<span class="cluster-count">{mergedArticles.length} articles</span>
			</div>
			<div class="cluster-synthesis">
				{@html synthesisHtml}
			</div>
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
		</div>
		<div class="cluster-actions">
			<button class="action-btn bookmark-btn" class:bookmarked title="Bookmark for ROTS"
				onclick={onBookmark}>{bookmarked ? '★' : '☆'}</button>
			<button class="action-btn tick-btn" title="Dismiss" onclick={onDismiss}>✓</button>
		</div>
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
</style>
