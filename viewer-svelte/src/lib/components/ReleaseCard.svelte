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

<div class="release-card cluster" class:dismissed={dismissedState} data-key={cluster.title}>
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
		<button class="bookmark-btn" class:bookmarked title="Bookmark" onclick={onBookmark}>
			{bookmarked ? '★' : '☆'}
		</button>
		<button class="tick-btn" title="Dismiss" onclick={onDismiss}>✓</button>
	</div>
	<div class="release-badges">
		{#each badges as badge}
			<a class="badge-release" href={badge.url} target="_blank" rel="noopener">↗ {badge.label}</a>
		{/each}
	</div>
</div>

<style>
	.release-card { background: #fff; border: 1px solid #e8e8e8; border-radius: 12px; padding: 20px; margin-bottom: 12px; }
	.release-card.dismissed { opacity: 0.35; }
	.cluster-head { display: flex; gap: 12px; }
	.cluster-head-text { flex: 1; }
	.cluster-title { font-family: 'Newsreader', serif; font-size: 1.1rem; font-weight: 600; margin-bottom: 8px; }
	.cluster-count { font-family: 'Inter', sans-serif; font-size: 0.72rem; font-weight: 400; color: #999; margin-left: 8px; }
	.cluster-synthesis, .release-bullets { font-size: 0.82rem; color: #555; line-height: 1.6; }
	.release-bullets :global(ul) { margin: 0; padding-left: 20px; }
	.release-bullets :global(li) { margin-bottom: 4px; }
	.release-badges { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 10px; }
	.badge-release {
		font-size: 0.7rem; font-weight: 500; padding: 3px 10px;
		border: 1px solid #ddd; border-radius: 12px;
		color: #555; text-decoration: none; white-space: nowrap;
	}
	.badge-release:hover { border-color: #999; color: #333; }
	.bookmark-btn, .tick-btn {
		background: none; border: 1px solid #ddd; border-radius: 50%; width: 32px; height: 32px;
		cursor: pointer; font-size: 1rem; flex-shrink: 0; display: flex; align-items: center; justify-content: center;
	}
	.bookmark-btn.bookmarked { background: #f59e0b; border-color: #f59e0b; color: #fff; }
</style>
