<script lang="ts">
	import { onMount } from 'svelte';
	import { fetchDates, fetchDigest } from '$lib/api/client';
	import type { DailyDigest, DigestCluster } from '$lib/api/types';
	import DateSidebar from '$lib/components/DateSidebar.svelte';
	import Cluster from '$lib/components/Cluster.svelte';
	import ReleaseCard from '$lib/components/ReleaseCard.svelte';
	import DebugPanel from '$lib/components/DebugPanel.svelte';
	import { bookmarks, toggleBookmark, isBookmarked } from '$lib/stores/bookmarks';
	import { dismissed, toggleDismiss, isDismissed } from '$lib/stores/dismissed';
	import { fmtDigestDate } from '$lib/utils/format';
	import { isSocialPost } from '$lib/utils/merge';

	let dates = $state<string[]>([]);
	let currentDate = $state('');
	let digest = $state<DailyDigest | null>(null);
	let loading = $state(true);

	onMount(async () => {
		dates = await fetchDates();
		if (dates.length === 0) { loading = false; return; }

		const params = new URLSearchParams(window.location.search);
		const requested = params.get('date');
		currentDate = (requested && dates.includes(requested)) ? requested : dates[0];
		await loadDate(currentDate);
		loading = false;

		window.addEventListener('popstate', (e: PopStateEvent) => {
			const d = (e.state as { date?: string })?.date || dates[0];
			loadDate(d, false);
		});
	});

	async function loadDate(date: string, pushState = true) {
		currentDate = date;
		digest = await fetchDigest(date);
		if (pushState) {
			history.pushState({ date }, '', `?date=${date}`);
		}
	}

	function selectDate(date: string) {
		loadDate(date);
	}

	// Split clusters into topics, releases, standalone tweets
	const topicClusters = $derived(
		(digest?.clusters || []).filter((c) => c.type !== 'release' && !(c.articles.length === 1 && isSocialPost(c.articles[0])))
	);
	const releaseClusters = $derived(
		(digest?.clusters || []).filter((c) => c.type === 'release')
	);
	const standaloneTweets = $derived(
		(digest?.clusters || []).filter((c) => c.articles.length === 1 && isSocialPost(c.articles[0])).map((c) => c.articles[0])
	);
</script>

{#if loading}
	<div class="loading">Loading...</div>
{:else if dates.length === 0}
	<div class="empty">No digest files found. Run the pipeline first.</div>
{:else}
	<DateSidebar {dates} {currentDate} onSelect={selectDate} />
	<div class="digest-content">
		{#if digest}
			<div class="digest-header">
				<div class="digest-date">{fmtDigestDate(currentDate)}</div>
				<div class="digest-stats">
					<span>{digest.totalArticles} articles</span>
					<span>{digest.clusters.length} topics</span>
				</div>
			</div>

			{#each topicClusters as cluster (cluster.id)}
				<Cluster
					{cluster}
					bookmarked={isBookmarked($bookmarks, currentDate, cluster.title)}
					dismissedState={isDismissed($dismissed, currentDate, cluster.title)}
					onBookmark={() => toggleBookmark(currentDate, cluster.title)}
					onDismiss={() => toggleDismiss(currentDate, cluster.title)}
				/>
			{/each}

			{#if releaseClusters.length > 0}
				<div class="releases-section">
					<div class="section-label">Releases</div>
					{#each releaseClusters as cluster (cluster.id)}
						<ReleaseCard
							{cluster}
							bookmarked={isBookmarked($bookmarks, currentDate, cluster.title)}
							dismissedState={isDismissed($dismissed, currentDate, cluster.title)}
							onBookmark={() => toggleBookmark(currentDate, cluster.title)}
							onDismiss={() => toggleDismiss(currentDate, cluster.title)}
						/>
					{/each}
				</div>
			{/if}

			{#if standaloneTweets.length > 0}
				<div class="tweets-section">
					<div class="section-label">Tweets</div>
					{#each standaloneTweets as tweet}
						<div class="tweet-card">
							<div class="tweet-header">
								<span>🦋</span>
								<a href={tweet.url || '#'} target="_blank" rel="noopener">@{tweet.handle || 'Bluesky'}</a>
							</div>
							<p class="tweet-text">{tweet.title}</p>
						</div>
					{/each}
				</div>
			{/if}

			<DebugPanel items={digest.debug || []} />
		{/if}
	</div>
{/if}

<style>
	.loading, .empty { padding: 48px; text-align: center; color: #999; width: 100%; }
	.digest-content { flex: 1; overflow-y: auto; padding: 32px; max-width: 900px; }
	.digest-header { margin-bottom: 24px; }
	.digest-date { font-family: 'Newsreader', serif; font-size: 1.5rem; font-weight: 600; }
	.digest-stats { display: flex; gap: 16px; font-size: 0.78rem; color: #888; margin-top: 4px; }
	.releases-section, .tweets-section { margin-top: 32px; }
	.section-label {
		font-size: 0.68rem; text-transform: uppercase; letter-spacing: 0.1em;
		color: #999; margin-bottom: 12px; font-weight: 600;
	}
	.tweet-card { background: #fff; border: 1px solid #e8e8e8; border-radius: 8px; padding: 10px 14px; margin-bottom: 8px; }
	.tweet-header { display: flex; align-items: center; gap: 6px; margin-bottom: 3px; }
	.tweet-header a { font-size: 0.72rem; color: #0085ff; text-decoration: none; }
	.tweet-header a:hover { text-decoration: underline; }
	.tweet-text { color: #444; font-size: 0.82rem; line-height: 1.5; margin: 0; }
</style>
