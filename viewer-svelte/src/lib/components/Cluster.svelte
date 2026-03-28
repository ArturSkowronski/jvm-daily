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
		</div>
		<div class="cluster-actions">
			<button class="action-btn bookmark-btn" class:bookmarked title="Bookmark for ROTS"
				onclick={onBookmark}>{bookmarked ? '★' : '☆'}</button>
			<button class="action-btn tick-btn" title="Dismiss" onclick={onDismiss}>✓</button>
		</div>
	</div>
	<div class="article-list">
		{#each mergedArticles as article}
			<ArticleRow {article} clusterSize={mergedArticles.length} />
		{/each}
	</div>
</div>

<style>
	.cluster {
		border-bottom: 1px solid #e0e0e0;
		padding: 28px 0;
	}
	.cluster:first-child { padding-top: 0; }
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
	.cluster-actions { display: flex; flex-direction: column; gap: 6px; flex-shrink: 0; }
	.action-btn {
		background: none; border: 1px solid #ddd; border-radius: 50%;
		width: 34px; height: 34px; cursor: pointer; font-size: 1rem;
		display: flex; align-items: center; justify-content: center;
		transition: border-color 0.15s, background 0.15s;
	}
	.bookmark-btn:hover { border-color: #00a64e; color: #00a64e; }
	.bookmark-btn.bookmarked { background: #00a64e; border-color: #00a64e; color: #fff; }
	.tick-btn:hover { border-color: #00a64e; color: #00a64e; }
	.article-list { margin-top: 12px; }

	@media (max-width: 768px) {
		.cluster { position: relative; }
		.cluster-head { display: block; }
		.cluster-title { font-size: 1.2rem; padding-right: 76px; }
		.cluster-synthesis { font-size: 0.95rem; }
		.cluster-actions { flex-direction: row; position: absolute; right: 0; top: 28px; }
	}
</style>
