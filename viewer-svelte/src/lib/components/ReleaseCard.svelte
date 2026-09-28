<script lang="ts">
	import type { DigestCluster } from '$lib/api/types';
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

<div class="release-card review-item" class:dismissed={dimmed} class:focused data-key={cluster.title}>
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
		<ItemActions {done} {later} {rots} {focused} {onDone} {onLater} {onRots} />
	</div>
	<div class="release-badges">
		{#each badges as badge}
			<a class="badge-release" href={badge.url} target="_blank" rel="noopener">↗ {badge.label}</a>
		{/each}
	</div>
</div>

<style>
	.release-card.review-item {
		border-bottom: 1px solid #e0e0e0;
		padding: 24px 16px 24px 16px;
		border-left: 3px solid transparent; margin-left: -19px;
		scroll-margin-top: 84px;
	}
	.release-card.focused {
		border-left-color: #00a64e;
		background: linear-gradient(to right, #f3fbf6, rgba(255, 255, 255, 0) 70%);
	}
	:global(body.compact) .release-card:not(.focused) .cluster-synthesis, :global(body.compact) .release-card:not(.focused) .release-bullets, :global(body.compact) .release-card:not(.focused) .release-badges { display: none; }
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

	@media (max-width: 768px) {
		.release-card.review-item { margin-left: 0; padding-left: 0; padding-right: 0; border-left: none; }
		.release-card.focused { background: none; }
		.release-card { position: relative; }
		.cluster-head { display: block; }
		.cluster-title { font-size: 1.05rem; padding-right: 80px; }
		.cluster-head > :global(.item-actions) { position: absolute; right: 0; top: 24px; }
	}
</style>
