<script lang="ts">
	import type { DigestCluster } from '$lib/api/types';
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

	function githubSlug(url: string): string | null {
		const m = url.match(/github\.com\/([^/?#]+\/[^/?#]+)/);
		return m ? m[1] : null;
	}

	const badges = $derived(
		cluster.articles.map((a) => {
			const slug = githubSlug(a.url || '');
			return { url: a.url || '#', label: slug || a.handle || 'Article', source: a.sourceType };
		})
	);

	const bulletHtml = $derived(
		cluster.bullets && cluster.bullets.length > 0
			? marked.parse(cluster.bullets.map((b) => `- ${b}`).join('\n')) as string
			: ''
	);

	const summaryHtml = $derived(marked.parse(cluster.summary) as string);
</script>

<div class="release-card" class:dismissed={dismissedState} data-key={cluster.title}>
	<div class="cluster-head">
		<div class="cluster-head-text">
			<div class="cluster-title">
				{cluster.title}
				<span class="cluster-count">{badges.length} sources</span>
			</div>
			{#if bulletHtml}
				<div class="release-bullets">{@html bulletHtml}</div>
			{:else}
				<div class="cluster-synthesis">{@html summaryHtml}</div>
			{/if}
		</div>
		<div class="cluster-actions">
			<button class="action-btn bookmark-btn" class:bookmarked title="Bookmark" onclick={onBookmark}>
				{bookmarked ? '★' : '☆'}
			</button>
			<button class="action-btn tick-btn" title="Dismiss" onclick={onDismiss}>✓</button>
		</div>
	</div>
	<div class="release-badges">
		{#each badges as badge}
			<a class="badge-release" href={badge.url} target="_blank" rel="noopener">↗ {badge.label}</a>
		{/each}
	</div>
</div>

<style>
	.release-card {
		border-bottom: 1px solid #e0e0e0;
		padding: 24px 0;
	}
	.release-card.dismissed { opacity: 0.35; }
	.cluster-head { display: flex; gap: 16px; }
	.cluster-head-text { flex: 1; }
	.cluster-title {
		font-size: 1.2rem; font-weight: 600; margin-bottom: 10px; color: #1a1a1a;
	}
	.cluster-count { font-size: 0.8rem; font-weight: 400; color: #868787; margin-left: 10px; }
	.cluster-synthesis, .release-bullets { font-size: 0.95rem; color: #363737; line-height: 1.7; }
	.release-bullets :global(ul) { margin: 0; padding-left: 20px; }
	.release-bullets :global(li) { margin-bottom: 6px; }
	.release-badges { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 12px; }
	.badge-release {
		font-size: 0.8rem; font-weight: 500; padding: 4px 12px;
		border: 1px solid #ddd; border-radius: 14px;
		color: #555; text-decoration: none; white-space: nowrap;
		transition: border-color 0.15s, color 0.15s;
	}
	.badge-release:hover { border-color: #00a64e; color: #00a64e; }
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

	@media (max-width: 768px) {
		.release-card { position: relative; }
		.cluster-head { display: block; }
		.cluster-title { font-size: 1.05rem; padding-right: 80px; }
		.cluster-actions { flex-direction: row; position: absolute; right: 0; top: 24px; }
	}
</style>
