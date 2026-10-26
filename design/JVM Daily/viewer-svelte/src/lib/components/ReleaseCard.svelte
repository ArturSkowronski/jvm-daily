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
		border: 1px solid var(--border);
		border-radius: 8px;
		padding: 16px 18px;
		background: var(--bg-card);
		margin-bottom: 8px;
		border-left: 3px solid var(--accent);
	}
	.release-card.dismissed { opacity: 0.35; }

	.cluster-head { display: flex; gap: 12px; align-items: flex-start; }
	.cluster-head-text { flex: 1; min-width: 0; }

	.cluster-title {
		font-size: 0.92rem;
		font-weight: 700;
		color: var(--text);
		line-height: 1.4;
		margin-bottom: 10px;
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

	.cluster-synthesis,
	.release-bullets {
		font-size: 0.75rem;
		color: var(--text-secondary);
		line-height: 1.7;
	}
	.release-bullets :global(ul) { margin: 0; padding-left: 18px; }
	.release-bullets :global(li) { margin-bottom: 5px; }
	.release-bullets :global(code) {
		background: var(--badge-count-bg);
		padding: 1px 4px;
		border-radius: 3px;
		font-size: 0.7rem;
		color: var(--text);
	}

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

	.release-badges { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 10px; }
	.badge-release {
		font-size: 0.65rem;
		font-weight: 500;
		padding: 3px 10px;
		border: 1px solid var(--border);
		border-radius: 12px;
		color: var(--text-secondary);
		text-decoration: none;
		white-space: nowrap;
		transition: border-color 0.15s, color 0.15s;
	}
	.badge-release:hover { border-color: var(--accent); color: var(--accent); }

	@media (max-width: 768px) {
		.release-card { position: relative; }
		.cluster-head { display: block; }
		.cluster-title { font-size: 0.88rem; padding-right: 70px; }
		.cluster-actions { flex-direction: row; position: absolute; right: 14px; top: 14px; }
	}
</style>
