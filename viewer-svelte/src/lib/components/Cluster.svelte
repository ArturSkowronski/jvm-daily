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
		<button class="bookmark-btn" class:bookmarked title="Bookmark for ROTS"
			onclick={onBookmark}>{bookmarked ? '★' : '☆'}</button>
		<button class="tick-btn" title="Dismiss" onclick={onDismiss}>✓</button>
	</div>
	<div class="article-list">
		{#each mergedArticles as article}
			<ArticleRow {article} clusterSize={mergedArticles.length} />
		{/each}
	</div>
</div>

<style>
	.cluster { background: #fff; border: 1px solid #e8e8e8; border-radius: 12px; padding: 24px; margin-bottom: 20px; }
	.cluster.dismissed { opacity: 0.35; }
	.cluster-head { display: flex; gap: 12px; }
	.cluster-head-text { flex: 1; }
	.cluster-title { font-family: 'Newsreader', serif; font-size: 1.25rem; font-weight: 700; line-height: 1.3; margin-bottom: 12px; }
	.cluster-count { font-family: 'Inter', sans-serif; font-size: 0.72rem; font-weight: 400; color: #999; margin-left: 8px; }
	.cluster-synthesis { font-size: 0.85rem; color: #555; line-height: 1.7; margin-bottom: 8px; }
	.cluster-synthesis :global(p) { margin: 0 0 10px; }
	.cluster-synthesis :global(code) { background: #f1f5f9; padding: 1px 5px; border-radius: 3px; font-size: 0.82rem; }
	.bookmark-btn, .tick-btn {
		background: none; border: 1px solid #ddd; border-radius: 50%; width: 32px; height: 32px;
		cursor: pointer; font-size: 1rem; flex-shrink: 0; display: flex; align-items: center; justify-content: center;
	}
	.bookmark-btn:hover { border-color: #f59e0b; }
	.bookmark-btn.bookmarked { background: #f59e0b; border-color: #f59e0b; color: #fff; }
	.tick-btn:hover { border-color: #22c55e; }
	.article-list { margin-top: 8px; }
</style>
