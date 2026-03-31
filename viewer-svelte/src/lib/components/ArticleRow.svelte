<script lang="ts">
	import type { DigestArticle } from '$lib/api/types';
	import TopicTag from './TopicTag.svelte';
	import SourceBadge from './SourceBadge.svelte';
	import SocialLinks from './SocialLinks.svelte';
	import { getDomain, faviconUrl } from '$lib/utils/format';
	import { isSocialPost } from '$lib/utils/merge';

	let { article, clusterSize = 1 }: { article: DigestArticle; clusterSize?: number } = $props();
	const social = clusterSize > 1 && isSocialPost(article);
	const domain = getDomain(article.url || '');
	const favicon = faviconUrl(article.url || '');

	function extractTweetText(title: string): string {
		const m = title.match(/^\[.*?\]\s*([\s\S]+)/);
		return m ? m[1] : title;
	}
</script>

{#if social}
	<div class="article-row article-row-social">
		<div class="article-body">
			<div class="social-card-header">
				<span class="social-card-icon">🦋</span>
				<a class="social-card-author" href={article.url || '#'} target="_blank" rel="noopener">
					@{article.handle || 'Bluesky'}
				</a>
			</div>
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
			<p class="article-summary">{article.summary}</p>
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
	.article-row { display: flex; gap: 12px; padding: 14px 0; border-top: 1px solid var(--border); }
	.article-row-social { padding: 10px 0; gap: 8px; }
	.article-favicon { width: 18px; height: 18px; border-radius: 3px; margin-top: 3px; flex-shrink: 0; }
	.article-body { flex: 1; min-width: 0; }
	.article-title-row { display: flex; align-items: baseline; gap: 8px; flex-wrap: wrap; }
	.article-title {
		font-size: 0.88rem;
		font-weight: 600;
		color: var(--text);
		text-decoration: none;
		line-height: 1.4;
	}
	.article-title:hover { color: var(--accent); }
	.article-source { font-size: 0.72rem; color: var(--text-muted); white-space: nowrap; }
	.article-summary { color: var(--text-secondary); font-size: 0.78rem; line-height: 1.65; margin: 6px 0 8px; }
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
	.social-card-header { display: flex; align-items: center; gap: 6px; margin-bottom: 4px; }
	.social-card-icon { font-size: 0.85rem; }
	.social-card-author {
		font-size: 0.8rem;
		font-weight: 600;
		color: var(--text-secondary);
		text-decoration: none;
	}
	.social-card-author:hover { color: var(--accent); }
	.social-card-text { color: var(--text-secondary); font-size: 0.82rem; line-height: 1.65; margin: 0 0 6px; }
</style>
