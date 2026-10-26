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

	const links = $derived(
		cluster.articles.map((a) => {
			const slug = githubSlug(a.url || '');
			return { url: a.url || '#', label: slug || a.handle || 'Article' };
		})
	);

	const bulletHtml = $derived(
		cluster.bullets && cluster.bullets.length > 0
			? marked.parse(cluster.bullets.map((b) => `- ${b}`).join('\n')) as string
			: ''
	);

	const summaryHtml = $derived(marked.parse(cluster.summary) as string);
</script>

<article class="release release-card review-item" class:dismissed={dimmed} class:focused data-key={cluster.title}>
	<div class="cluster-head">
		<div class="cluster-head-text">
			<h3 class="cluster-title">
				{cluster.title}
				<span class="count-pill">{links.length} source{links.length !== 1 ? 's' : ''}</span>
			</h3>
			{#if bulletHtml}
				<div class="bullets">{@html bulletHtml}</div>
			{:else}
				<div class="cluster-synthesis">{@html summaryHtml}</div>
			{/if}
			{#if links.length > 0}
				<div class="release-links">
					{#each links as link}
						<a class="release-link" href={link.url} target="_blank" rel="noopener">{link.label}</a>
					{/each}
				</div>
			{/if}
		</div>
		<ItemActions {done} {later} {rots} {focused} {onDone} {onLater} {onRots} />
	</div>
</article>

<style>
	.release {
		background: var(--bg-card);
		border: 1px solid var(--border);
		border-left: 3px solid var(--accent);
		border-radius: var(--radius);
		padding: 18px 20px 16px;
		margin-bottom: 12px;
		box-shadow: var(--shadow-sm);
		transition: box-shadow .12s;
	}
	.release:hover { box-shadow: var(--shadow-md); }
	.release.dismissed { opacity: 0.35; }

	.cluster-head { display: flex; gap: 12px; align-items: flex-start; }
	.cluster-head-text { flex: 1; min-width: 0; }

	.cluster-title {
		font: 600 16px/1.4 var(--font-sans);
		color: var(--text);
		margin: 0 0 8px;
		letter-spacing: -0.005em;
	}
	.cluster-title::before {
		content: "";
		display: inline-block;
		width: 6px; height: 6px;
		background: var(--accent);
		border-radius: 1px;
		transform: rotate(45deg);
		margin-right: 8px;
		vertical-align: 2px;
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

	.cluster-synthesis,
	.bullets {
		font-size: 14px;
		line-height: 1.7;
		color: var(--text-2);
	}
	.cluster-synthesis :global(p) { margin: 0 0 0.5em; }
	.cluster-synthesis :global(p:last-child) { margin-bottom: 0; }
	.bullets :global(ul) { margin: 4px 0 0; padding: 0; list-style: none; }
	.bullets :global(li) {
		position: relative;
		padding-left: 16px;
		margin-bottom: 5px;
		font-size: 14px;
		line-height: 1.7;
		color: var(--text-2);
	}
	.bullets :global(li)::before {
		content: "";
		position: absolute;
		left: 2px; top: 9px;
		width: 8px; height: 1px;
		background: var(--accent);
	}
	.bullets :global(code), .cluster-synthesis :global(code) {
		font-family: var(--font-mono);
		font-size: 11.5px;
		background: var(--bg-soft);
		padding: 1px 5px;
		border-radius: 3px;
		border: 1px solid var(--border-soft);
		color: var(--text);
	}


	.release-links {
		display: flex;
		flex-wrap: wrap;
		gap: 5px;
		margin-top: 10px;
		padding-top: 9px;
		border-top: 1px solid var(--border-soft);
	}
	.release-link {
		font: 500 10.5px/1 var(--font-mono);
		padding: 4px 9px 5px;
		border: 1px solid var(--border);
		border-radius: 10px;
		color: var(--text-2);
		background: var(--bg-card);
		white-space: nowrap;
		transition: all .12s;
		display: inline-flex;
		align-items: center;
		gap: 4px;
		text-decoration: none;
	}
	.release-link::before { content: "↗"; color: var(--accent); }
	.release-link:hover {
		border-color: var(--accent-bd);
		color: var(--accent-dark);
		background: var(--accent-bg);
		text-decoration: none;
	}

	.release.review-item { scroll-margin-top: 72px; }
	.release.focused {
		border-left-color: var(--accent);
		box-shadow: 0 0 0 1px var(--accent-bd), var(--shadow-md);
	}
	:global(body.compact) .release:not(.focused) .bullets, :global(body.compact) .release:not(.focused) .cluster-synthesis, :global(body.compact) .release:not(.focused) .release-links { display: none; }

	@media (max-width: 760px) {
		.release { position: relative; padding: 12px 12px 10px; }
		.cluster-head { display: block; }
		.cluster-title { font-size: 13.5px; padding-right: 96px; }
		.cluster-head > :global(.item-actions) { position: absolute; right: 12px; top: 12px; }
	}
</style>
