<script lang="ts">
	import type { DigestCluster } from '$lib/api/types';
	import ArticleRow from './ArticleRow.svelte';
	import { mergeByTitle } from '$lib/utils/merge';
	import { marked } from 'marked';

	let {
		cluster,
		bookmarked = false,
		dismissedState = false,
		lead = false,
		onBookmark,
		onDismiss
	}: {
		cluster: DigestCluster;
		bookmarked?: boolean;
		dismissedState?: boolean;
		lead?: boolean;
		onBookmark?: () => void;
		onDismiss?: () => void;
	} = $props();

	const mergedArticles = $derived(
		mergeByTitle([...cluster.articles]).sort((a, b) => b.engagementScore - a.engagementScore)
	);

	const synthesisHtml = $derived(marked.parse(cluster.summary) as string);

	const hnEngagement = $derived.by(() => {
		const hn = cluster.articles.find(
			(a) => a.sourceType === 'hackernews' && a.engagementScore > 0
		);
		return hn ? hn.engagementScore : 0;
	});
	const hasReddit  = $derived(cluster.articles.some((a) => a.sourceType === 'reddit'));
	const hasBsky    = $derived(cluster.articles.some((a) => a.sourceType === 'bluesky'));
	const hasGh      = $derived(cluster.articles.some((a) => a.sourceType === 'github_trending' || a.sourceType === 'github_release'));
	const hasJdk     = $derived(cluster.articles.some((a) => a.sourceType === 'openjdk_mail' || a.sourceType === 'jep'));
	const hasRss     = $derived(cluster.articles.some((a) => a.sourceType === 'rss'));
</script>

<article class="cluster" class:lead class:dismissed={dismissedState} data-key={cluster.title}>
	<div class="cluster-head">
		<div class="cluster-head-text">
			<h3 class="cluster-title">
				{cluster.title}
				<span class="count-pill">{mergedArticles.length} article{mergedArticles.length !== 1 ? 's' : ''}</span>
			</h3>
			<div class="cluster-synthesis">
				{@html synthesisHtml}
			</div>
			<div class="cluster-badges">
				{#if hnEngagement > 0}<span class="badge badge-hn">HN · {hnEngagement}</span>{/if}
				{#if hasReddit}<span class="badge badge-reddit">reddit</span>{/if}
				{#if hasBsky}<span class="badge badge-bsky">bluesky</span>{/if}
				{#if hasGh}<span class="badge badge-gh">github</span>{/if}
				{#if hasJdk}<span class="badge badge-jdk">openjdk</span>{/if}
				{#if hasRss}<span class="badge badge-rss">rss</span>{/if}
			</div>
		</div>
		<div class="cluster-actions">
			<button class="act bm bookmark-btn" class:bookmarked title="Save for ROTS" onclick={onBookmark}>
				{bookmarked ? '★' : '☆'}
			</button>
			<button class="act tick-btn" title="Dismiss" onclick={onDismiss}>✓</button>
		</div>
	</div>
	<div class="articles">
		{#each mergedArticles as article}
			<ArticleRow {article} clusterSize={mergedArticles.length} />
		{/each}
	</div>
</article>

<style>
	.cluster {
		background: var(--bg-card);
		border: 1px solid var(--border);
		border-radius: var(--radius);
		padding: 18px 20px 16px;
		margin-bottom: 12px;
		box-shadow: var(--shadow-sm);
		border-left: 3px solid var(--border);
		position: relative;
		transition: border-color .12s, box-shadow .12s;
	}
	.cluster:hover { box-shadow: var(--shadow-md); }
	.cluster.lead { border-left-color: var(--accent); }
	.cluster.dismissed { opacity: 0.35; }

	.cluster-head { display: flex; gap: 12px; align-items: flex-start; }
	.cluster-head-text { flex: 1; min-width: 0; }

	.cluster-title {
		font: 600 16px/1.4 var(--font-sans);
		color: var(--text);
		margin: 0 0 6px;
		letter-spacing: -0.005em;
		text-wrap: pretty;
	}
	.count-pill {
		display: inline-block;
		margin-left: 8px;
		font: 500 10.5px/1.5 var(--font-mono);
		color: var(--text-3);
		background: var(--bg-soft);
		padding: 1px 8px;
		border-radius: 10px;
		vertical-align: 2px;
		white-space: nowrap;
	}

	.cluster-synthesis {
		font-size: 14px;
		line-height: 1.7;
		color: var(--text-2);
		margin: 6px 0 10px;
		max-width: 70ch;
	}
	.cluster-synthesis :global(p) { margin: 0 0 0.5em; }
	.cluster-synthesis :global(p:last-child) { margin-bottom: 0; }
	.cluster-synthesis :global(code) {
		font-family: var(--font-mono);
		font-size: 11.5px;
		background: var(--bg-soft);
		padding: 1px 5px;
		border-radius: 3px;
		border: 1px solid var(--border-soft);
		color: var(--text);
	}

	.cluster-badges {
		display: flex;
		flex-wrap: wrap;
		gap: 5px;
		align-items: center;
		margin-top: 6px;
	}
	.badge {
		font: 500 10px/1 var(--font-mono);
		padding: 3px 8px 4px;
		border-radius: 10px;
		background: var(--bg-soft);
		color: var(--text-3);
		letter-spacing: 0.02em;
		white-space: nowrap;
	}
	.badge-hn      { background: var(--src-hn-bg);     color: var(--src-hn); }
	.badge-reddit  { background: var(--src-reddit-bg); color: var(--src-reddit); }
	.badge-bsky    { background: var(--src-bsky-bg);   color: var(--src-bsky); }
	.badge-gh      { background: var(--src-gh-bg);     color: var(--src-gh); }
	.badge-jdk     { background: var(--src-jdk-bg);    color: var(--src-jdk); }
	.badge-rss     { background: var(--src-rss-bg);    color: var(--src-rss); }

	.cluster-actions {
		display: flex;
		flex-direction: column;
		gap: 4px;
		flex-shrink: 0;
	}
	.act {
		appearance: none;
		width: 26px; height: 26px;
		border: 1px solid var(--border);
		border-radius: 5px;
		background: transparent;
		color: var(--text-faint);
		cursor: pointer;
		font-size: 12px;
		display: inline-flex;
		align-items: center;
		justify-content: center;
		transition: all .12s;
	}
	.act:hover { border-color: var(--border-strong); color: var(--text-2); background: var(--bg-soft); }
	.act.bookmarked {
		background: var(--rots);
		border-color: var(--rots);
		color: #fff;
	}
	.act.bookmarked:hover { opacity: 0.92; }

	.articles { margin-top: 12px; }

	@media (max-width: 760px) {
		.cluster { position: relative; padding: 12px 12px 10px; }
		.cluster-title { font-size: 13.5px; padding-right: 70px; }
		.cluster-actions { flex-direction: row; position: absolute; right: 12px; top: 12px; }
	}
</style>
