<script lang="ts">
	import type { DigestArticle } from '$lib/api/types';
	import TopicTag from './TopicTag.svelte';
	import SourceBadge from './SourceBadge.svelte';
	import SocialLinks from './SocialLinks.svelte';
	import { getDomain, faviconUrl } from '$lib/utils/format';
	import { isSocialPost } from '$lib/utils/merge';

	let { article, clusterSize = 1 }: { article: DigestArticle; clusterSize?: number } = $props();
	const social = clusterSize > 1 && isSocialPost(article);
	const showSummary = clusterSize > 1;
	const domain = getDomain(article.url || '');
	const favicon = faviconUrl(article.url || '');

	function extractTweetText(title: string): string {
		const m = title.match(/^\[.*?\]\s*([\s\S]+)/);
		return m ? m[1] : title;
	}
</script>

{#if social}
	<div class="article-row article-row-social">
		<span class="social-card-icon">🦋</span>
		<div class="article-body">
			<a class="social-card-author" href={article.url || '#'} target="_blank" rel="noopener">
				@{article.handle || 'Bluesky'}
			</a>
			<p class="social-card-text">{extractTweetText(article.title)}</p>
			<div class="article-meta">
				<SourceBadge sourceType={article.sourceType} />
				{#each article.topics as topic}<TopicTag {topic} />{/each}
			</div>
		</div>
	</div>
{:else}
	<div class="article-row">
		{#if favicon}
			<img class="article-favicon" src={favicon} alt="" loading="lazy"
				onerror={(e: Event) => { (e.target as HTMLElement).style.display = 'none'; }} />
		{/if}
		<div class="article-body">
			<div class="article-title-row">
				<a class="article-title" href={article.url || '#'} target="_blank" rel="noopener">
					{article.title}
				</a>
				<span class="article-source">{domain}</span>
			</div>
			{#if showSummary}
				<p class="article-summary">{article.summary}</p>
			{/if}
			<div class="article-meta">
				<SourceBadge sourceType={article.sourceType} />
				{#if article.taxonomyArea}
					<span class="taxonomy-badge">{article.taxonomyArea}{#if article.taxonomySubArea}/{article.taxonomySubArea}{/if}</span>
				{/if}
				{#each article.topics as topic}<TopicTag {topic} />{/each}
			</div>
			<SocialLinks links={article.socialLinks || []} />
		</div>
	</div>
{/if}

<style>
	.article-row { display: flex; gap: 10px; padding: 10px 0; border-top: 1px solid var(--border); }
	.article-row-social { padding: 8px 0; gap: 10px; }
	.article-favicon { width: 16px; height: 16px; border-radius: 3px; margin-top: 2px; flex-shrink: 0; }
	.article-body { flex: 1; min-width: 0; }
	.article-title-row { display: flex; align-items: baseline; gap: 8px; flex-wrap: wrap; }
	.article-title {
		font-size: 0.8rem;
		font-weight: 500;
		color: var(--text-secondary);
		text-decoration: none;
		line-height: 1.4;
	}
	.article-title:hover { color: var(--accent); }
	.article-source { font-size: 0.68rem; color: var(--text-muted); white-space: nowrap; }
	.article-summary { color: var(--text-secondary); font-size: 0.75rem; line-height: 1.6; margin: 5px 0 7px; }
	.article-meta { display: flex; flex-wrap: wrap; gap: 5px; align-items: center; }
	.taxonomy-badge {
		font-size: 0.65rem;
		font-weight: 600;
		padding: 2px 7px;
		border-radius: 3px;
		background: var(--accent-pill-bg);
		color: var(--accent-dark);
		white-space: nowrap;
	}
	.social-card-icon { font-size: 0.85rem; margin-top: 2px; flex-shrink: 0; line-height: 1; }
	.social-card-author {
		display: block;
		font-size: 0.75rem;
		font-weight: 600;
		color: var(--text-secondary);
		text-decoration: none;
		margin-bottom: 3px;
	}
	.social-card-author:hover { color: var(--accent); }
	.social-card-text { color: var(--text-secondary); font-size: 0.75rem; line-height: 1.6; margin: 0 0 6px; }
</style>
