<script lang="ts">
	import type { DigestArticle } from '$lib/api/types';
	import TopicTag from './TopicTag.svelte';
	import SourceBadge from './SourceBadge.svelte';
	import SocialLinks from './SocialLinks.svelte';
	import { getDomain, faviconUrl } from '$lib/utils/format';
	import { isSocialPost } from '$lib/utils/merge';

	let { article, clusterSize = 1 }: { article: DigestArticle; clusterSize?: number } = $props();
	const social = isSocialPost(article);
	const showSummary = clusterSize > 1;
	const domain = getDomain(article.url || '');
	const favicon = faviconUrl(article.url || '');

	function extractTweetText(title: string): string {
		const m = title.match(/^\[.*?\]\s*([\s\S]+)/);
		return m ? m[1] : title;
	}

	function favSourceClass(sourceType: string): string {
		switch (sourceType) {
			case 'reddit':           return 'fav-reddit';
			case 'hackernews':       return 'fav-hn';
			case 'bluesky':          return 'fav-bsky';
			case 'github_trending':
			case 'github_release':   return 'fav-gh';
			case 'openjdk_mail':     return 'fav-jdk';
			case 'jep':              return 'fav-jdk';
			case 'rss':              return 'fav-rss';
			default:                 return '';
		}
	}
</script>

{#if social}
	<div class="row social">
		<span class="bsky-icon">🦋</span>
		<div class="art-body">
			<a class="bsky-handle" href={article.url || '#'} target="_blank" rel="noopener">
				@{article.handle || 'Bluesky'}
			</a>
			<p class="bsky-text">{extractTweetText(article.title)}</p>
			<div class="art-meta">
				<SourceBadge sourceType={article.sourceType} />
				{#each article.topics as topic}<TopicTag {topic} />{/each}
			</div>
		</div>
	</div>
{:else}
	<div class="row">
		<span class="fav {favSourceClass(article.sourceType)}">
			{#if favicon}
				<img src={favicon} alt="" loading="lazy"
					onerror={(e: Event) => { (e.currentTarget as HTMLElement).style.display = 'none'; }} />
			{/if}
		</span>
		<div class="art-body">
			<div class="art-title-row">
				<a class="art-title" href={article.url || '#'} target="_blank" rel="noopener">
					{article.title}
				</a>
				<span class="art-source">{domain}</span>
			</div>
			{#if showSummary}
				<p class="art-summary">{article.summary}</p>
			{/if}
			<div class="art-meta">
				<SourceBadge sourceType={article.sourceType} />
				{#if article.taxonomyArea}
					<span class="tax">{article.taxonomyArea}{#if article.taxonomySubArea}/{article.taxonomySubArea}{/if}</span>
				{/if}
				{#each article.topics as topic}<TopicTag {topic} />{/each}
			</div>
			<SocialLinks links={article.socialLinks || []} />
		</div>
	</div>
{/if}

<style>
	.row {
		display: flex;
		gap: 10px;
		padding: 9px 0 8px;
		border-top: 1px solid var(--border-soft);
		align-items: flex-start;
	}
	.row:first-child { border-top: 1px solid var(--border); padding-top: 10px; }

	.fav {
		width: 16px; height: 16px;
		border-radius: 3px;
		flex-shrink: 0;
		margin-top: 3px;
		background: var(--bg-soft);
		border: 1px solid var(--border-soft);
		display: inline-flex;
		align-items: center;
		justify-content: center;
		font: 600 9px/1 var(--font-mono);
		color: var(--text-3);
		overflow: hidden;
	}
	.fav img { width: 100%; height: 100%; object-fit: contain; display: block; }
	.fav-reddit { background: var(--src-reddit-bg); }
	.fav-hn     { background: var(--src-hn-bg); }
	.fav-bsky   { background: var(--src-bsky-bg); }
	.fav-gh     { background: var(--src-gh-bg); }
	.fav-jdk    { background: var(--src-jdk-bg); }
	.fav-rss    { background: var(--src-rss-bg); }

	.art-body { flex: 1; min-width: 0; }
	.art-title-row { display: flex; align-items: baseline; gap: 8px; flex-wrap: wrap; }
	.art-title {
		font: 500 13px/1.4 var(--font-sans);
		color: var(--text);
		text-decoration: none;
		flex: 1;
		min-width: 0;
	}
	.art-title:hover { color: var(--accent-dark); text-decoration: underline; text-underline-offset: 2px; }
	.art-source {
		font: 400 11px/1 var(--font-mono);
		color: var(--text-faint);
		white-space: nowrap;
	}
	.art-summary {
		font-size: 12.5px;
		line-height: 1.6;
		color: var(--text-2);
		margin: 4px 0 6px;
		max-width: 72ch;
	}
	.art-meta { display: flex; flex-wrap: wrap; gap: 5px; align-items: center; }
	.tax {
		font: 600 10px/1 var(--font-mono);
		letter-spacing: 0.02em;
		color: var(--accent-dark);
		background: var(--accent-bg);
		border: 1px solid var(--accent-bd);
		padding: 2px 7px 3px;
		border-radius: 3px;
		white-space: nowrap;
	}

	/* Social */
	.row.social { align-items: flex-start; }
	.bsky-icon {
		width: 18px; height: 18px;
		border-radius: 50%;
		background: var(--src-bsky-bg);
		color: var(--src-bsky);
		display: inline-flex;
		align-items: center;
		justify-content: center;
		font-size: 10px;
		flex-shrink: 0;
		margin-top: 2px;
	}
	.bsky-handle {
		font: 500 11px/1 var(--font-mono);
		color: var(--text-2);
		display: inline-block;
		margin-bottom: 3px;
		text-decoration: none;
	}
	.bsky-handle:hover { color: var(--accent-dark); }
	.bsky-text {
		font-size: 13px;
		line-height: 1.55;
		color: var(--text);
		margin: 0 0 6px;
	}
</style>
