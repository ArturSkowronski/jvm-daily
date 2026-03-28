<script lang="ts">
	import { onMount } from 'svelte';
	import { fetchDates, fetchDigest } from '$lib/api/client';
	import type { DailyDigest, DigestCluster } from '$lib/api/types';
	import DateSidebar from '$lib/components/DateSidebar.svelte';
	import Cluster from '$lib/components/Cluster.svelte';
	import ReleaseCard from '$lib/components/ReleaseCard.svelte';
	import DebugPanel from '$lib/components/DebugPanel.svelte';
	import { bookmarks, toggleBookmark, isBookmarked } from '$lib/stores/bookmarks';
	import { dismissed, toggleDismiss, isDismissed, ensureDismissedLoaded } from '$lib/stores/dismissed';
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
		ensureDismissedLoaded(date);
		digest = await fetchDigest(date);
		if (pushState) {
			history.pushState({ date }, '', `?date=${date}`);
		}
	}

	function selectDate(date: string) {
		loadDate(date);
	}

	function clusterKey(c: DigestCluster): string { return c.title; }
	function isRelease(c: DigestCluster): boolean {
		return c.type === 'release';
	}
	function isStandaloneTweet(c: DigestCluster): boolean {
		return c.articles.length === 1 && isSocialPost(c.articles[0]);
	}
	function isMailingListCluster(c: DigestCluster): boolean {
		return c.articles.every((a) => a.sourceType === 'openjdk_mail' || a.sourceType === 'jep');
	}

	const allTopicClusters = $derived(
		(digest?.clusters || []).filter((c) => !isRelease(c) && !isStandaloneTweet(c) && !isMailingListCluster(c))
	);
	// OpenJDK section: mailing list discussions + JEP changes as full clusters
	const allOpenJdkClusters = $derived(
		(digest?.clusters || [])
			.filter((c) => !isRelease(c) && !isStandaloneTweet(c) && isMailingListCluster(c))
			.sort((a, b) => b.engagementScore - a.engagementScore)
	);
	// Top clusters shown as full cards, rest as compact links (max 5)
	const openjdkTopClusters = $derived(allOpenJdkClusters.filter((c) => c.articles.length > 1 || c.engagementScore > 3));
	const openjdkCompactLinks = $derived(
		allOpenJdkClusters
			.filter((c) => c.articles.length <= 1 && c.engagementScore <= 3)
			.slice(0, 5)
	);
	const allReleaseClusters = $derived(
		(digest?.clusters || []).filter((c) => isRelease(c))
	);
	const standaloneTweets = $derived(
		(digest?.clusters || []).filter((c) => isStandaloneTweet(c)).map((c) => c.articles[0])
	);

	const rotsClusters = $derived(
		allTopicClusters.filter((c) => isBookmarked($bookmarks, currentDate, c.title))
	);
	const rotsReleases = $derived(
		allReleaseClusters.filter((c) => isBookmarked($bookmarks, currentDate, c.title))
	);

	const normalClusters = $derived(
		allTopicClusters.filter((c) =>
			!isBookmarked($bookmarks, currentDate, c.title) &&
			!isDismissed($dismissed, currentDate, c.title)
		)
	);
	const normalReleases = $derived(
		allReleaseClusters.filter((c) =>
			!isBookmarked($bookmarks, currentDate, c.title) &&
			!isDismissed($dismissed, currentDate, c.title)
		)
	);

	const archivedClusters = $derived(
		allTopicClusters.filter((c) =>
			isDismissed($dismissed, currentDate, c.title) &&
			!isBookmarked($bookmarks, currentDate, c.title)
		)
	);
	const archivedReleases = $derived(
		allReleaseClusters.filter((c) =>
			isDismissed($dismissed, currentDate, c.title) &&
			!isBookmarked($bookmarks, currentDate, c.title)
		)
	);

	const hasRots = $derived(rotsClusters.length > 0 || rotsReleases.length > 0);
	const hasArchive = $derived(archivedClusters.length > 0 || archivedReleases.length > 0);
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

			{#each normalClusters as cluster (cluster.id)}
				<Cluster
					{cluster}
					bookmarked={false}
					dismissedState={false}
					onBookmark={() => toggleBookmark(currentDate, cluster.title)}
					onDismiss={() => toggleDismiss(currentDate, cluster.title)}
				/>
			{/each}

			{#if normalReleases.length > 0}
				<div class="releases-section">
					<div class="section-label">Releases</div>
					{#each normalReleases as cluster (cluster.id)}
						<ReleaseCard
							{cluster}
							bookmarked={false}
							dismissedState={false}
							onBookmark={() => toggleBookmark(currentDate, cluster.title)}
							onDismiss={() => toggleDismiss(currentDate, cluster.title)}
						/>
					{/each}
				</div>
			{/if}

			{#if allOpenJdkClusters.length > 0}
				<div class="mailing-section">
					<div class="section-label">OpenJDK</div>
					{#each openjdkTopClusters as cluster (cluster.id)}
						<Cluster
							{cluster}
							bookmarked={isBookmarked($bookmarks, currentDate, cluster.title)}
							dismissedState={isDismissed($dismissed, currentDate, cluster.title)}
							onBookmark={() => toggleBookmark(currentDate, cluster.title)}
							onDismiss={() => toggleDismiss(currentDate, cluster.title)}
						/>
					{/each}
					{#if openjdkCompactLinks.length > 0}
						<ul class="mailing-list">
							{#each openjdkCompactLinks as cluster (cluster.id)}
								<li class="mailing-item">
									<a href={cluster.articles[0]?.url || '#'} target="_blank" rel="noopener">
										{cluster.title}
									</a>
									<span class="mailing-meta">
										{cluster.articles.length} source{cluster.articles.length > 1 ? 's' : ''}
									</span>
								</li>
							{/each}
						</ul>
					{/if}
				</div>
			{/if}

			{#if hasRots}
				<div class="rots-inline-section">
					<div class="section-label">★ Rest of the Story</div>
					{#each rotsClusters as cluster (cluster.id)}
						<Cluster
							{cluster}
							bookmarked={true}
							dismissedState={false}
							onBookmark={() => toggleBookmark(currentDate, cluster.title)}
							onDismiss={() => toggleDismiss(currentDate, cluster.title)}
						/>
					{/each}
					{#each rotsReleases as cluster (cluster.id)}
						<ReleaseCard
							{cluster}
							bookmarked={true}
							dismissedState={false}
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

			{#if hasArchive}
				<div class="archive-section">
					<div class="section-label">Archive</div>
					{#each archivedClusters as cluster (cluster.id)}
						<Cluster
							{cluster}
							bookmarked={false}
							dismissedState={true}
							onBookmark={() => toggleBookmark(currentDate, cluster.title)}
							onDismiss={() => toggleDismiss(currentDate, cluster.title)}
						/>
					{/each}
					{#each archivedReleases as cluster (cluster.id)}
						<ReleaseCard
							{cluster}
							bookmarked={false}
							dismissedState={true}
							onBookmark={() => toggleBookmark(currentDate, cluster.title)}
							onDismiss={() => toggleDismiss(currentDate, cluster.title)}
						/>
					{/each}
				</div>
			{/if}

			<DebugPanel items={digest.debug || []} />
		{/if}
	</div>
{/if}

<style>
	.loading, .empty { padding: 48px; text-align: center; color: #999; width: 100%; }
	.digest-content {
		flex: 1; overflow-y: auto;
		padding: 40px 48px;
		max-width: 780px; margin: 0 auto;
	}
	.digest-header { margin-bottom: 32px; padding-bottom: 20px; border-bottom: 2px solid #1a1a1a; }
	.digest-date { font-size: 2rem; font-weight: 700; line-height: 1.2; }
	.digest-stats { display: flex; gap: 16px; font-size: 0.85rem; color: #868787; margin-top: 8px; }

	.releases-section, .tweets-section, .mailing-section { margin-top: 40px; }
	.mailing-list {
		list-style: none; padding: 0; margin: 0;
	}
	.mailing-item {
		padding: 10px 0;
		border-bottom: 1px solid #f0f0f0;
		display: flex; align-items: baseline; gap: 10px;
		flex-wrap: wrap;
	}
	.mailing-item a {
		font-size: 1rem; font-weight: 600; text-decoration: none;
		line-height: 1.4;
	}
	.mailing-item a:hover { text-decoration: underline; }
	.mailing-meta {
		font-size: 0.8rem; color: #868787;
	}
	.section-label {
		font-size: 0.8rem; text-transform: uppercase; letter-spacing: 0.12em;
		color: #868787; margin-bottom: 16px; font-weight: 600;
		padding-bottom: 8px; border-bottom: 2px solid #00a64e;
		display: inline-block;
	}
	.rots-inline-section {
		margin: 40px 0 24px; padding-bottom: 16px;
	}
	.rots-inline-section .section-label { border-bottom-color: #f59e0b; color: #b45309; }
	.archive-section {
		margin-top: 40px; padding-top: 16px;
	}
	.archive-section .section-label { border-bottom-color: #d0d0d0; color: #b0b0b0; }
	.tweet-card {
		border-bottom: 1px solid #e8e8e8;
		padding: 16px 0; margin-bottom: 0;
	}
	.tweet-header { display: flex; align-items: center; gap: 6px; margin-bottom: 6px; }
	.tweet-header a { font-size: 0.85rem; text-decoration: none; }
	.tweet-header a:hover { text-decoration: underline; }
	.tweet-text { color: #363737; font-size: 0.95rem; line-height: 1.7; margin: 0; }

	@media (max-width: 768px) {
		.digest-content {
			padding: 20px 16px;
			max-width: 100%;
			overflow-x: hidden;
			word-wrap: break-word;
			overflow-wrap: break-word;
		}
		.digest-date { font-size: 1.5rem; }
	}
</style>
