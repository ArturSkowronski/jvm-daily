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
	const normalOpenJdkClusters = $derived(
		allOpenJdkClusters.filter((c) =>
			!isBookmarked($bookmarks, currentDate, c.title) &&
			!isDismissed($dismissed, currentDate, c.title)
		)
	);
	// Top clusters shown as full cards, rest as compact links (max 5)
	const openjdkTopClusters = $derived(normalOpenJdkClusters.filter((c) => c.articles.length > 1 || c.engagementScore > 3));
	const openjdkCompactLinks = $derived(
		normalOpenJdkClusters
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
	const rotsOpenJdk = $derived(
		allOpenJdkClusters.filter((c) => isBookmarked($bookmarks, currentDate, c.title))
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
	const archivedOpenJdk = $derived(
		allOpenJdkClusters.filter((c) =>
			isDismissed($dismissed, currentDate, c.title) &&
			!isBookmarked($bookmarks, currentDate, c.title)
		)
	);

	const hasRots = $derived(rotsClusters.length > 0 || rotsReleases.length > 0 || rotsOpenJdk.length > 0);
	const hasArchive = $derived(archivedClusters.length > 0 || archivedReleases.length > 0 || archivedOpenJdk.length > 0);
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
					<div class="release-pills">
						{#each normalReleases as cluster (cluster.id)}
							<button
								class="release-pill"
								onclick={() => {
									const el = document.getElementById(`release-${cluster.id}`);
									if (el) { el.scrollIntoView({ behavior: 'smooth', block: 'center' }); el.style.outline = '2px solid var(--accent)'; setTimeout(() => { el.style.outline = ''; }, 1200); }
								}}
							>{cluster.title}</button>
						{/each}
					</div>
					{#each normalReleases as cluster (cluster.id)}
						<div id="release-{cluster.id}">
							<ReleaseCard
								{cluster}
								bookmarked={false}
								dismissedState={false}
								onBookmark={() => toggleBookmark(currentDate, cluster.title)}
								onDismiss={() => toggleDismiss(currentDate, cluster.title)}
							/>
						</div>
					{/each}
				</div>
			{/if}

			{#if normalOpenJdkClusters.length > 0}
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
					{#each rotsOpenJdk as cluster (cluster.id)}
						<Cluster
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
					{#each archivedOpenJdk as cluster (cluster.id)}
						<Cluster
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
	.loading, .empty { padding: 48px; text-align: center; color: var(--text-muted); width: 100%; }

	.digest-content {
		flex: 1;
		overflow-y: auto;
		padding: 32px 40px;
		max-width: 920px;
		margin: 0 auto;
		width: 100%;
	}

	.digest-header {
		margin-bottom: 28px;
		padding-bottom: 16px;
		border-bottom: 1px solid var(--border);
	}
	.digest-date {
		font-size: 1.6rem;
		font-weight: 700;
		letter-spacing: -0.02em;
		color: var(--text);
		line-height: 1.2;
	}
	.digest-stats {
		display: flex;
		gap: 12px;
		font-size: 0.75rem;
		color: var(--text-muted);
		margin-top: 6px;
		font-weight: 500;
	}

	.section-label {
		font-size: 0.62rem;
		text-transform: uppercase;
		letter-spacing: 0.1em;
		color: var(--text-muted);
		margin-bottom: 12px;
		font-weight: 600;
		display: inline-block;
	}

	.releases-section,
	.tweets-section,
	.mailing-section { margin-top: 32px; }

	.release-pills {
		display: flex;
		flex-wrap: wrap;
		gap: 6px;
		margin-bottom: 14px;
	}
	.release-pill {
		background: var(--accent-pill-bg);
		border: 1px solid var(--accent-pill-border);
		color: var(--accent-pill-text);
		font-size: 0.65rem;
		padding: 4px 11px;
		border-radius: 12px;
		font-weight: 600;
		text-decoration: none;
		cursor: pointer;
		border-style: solid;
		font-family: 'Inter', system-ui, sans-serif;
		transition: opacity 0.15s;
	}
	.release-pill:hover { opacity: 0.8; }

	.mailing-list { list-style: none; padding: 0; margin: 0; }
	.mailing-item {
		padding: 10px 0;
		border-bottom: 1px solid var(--border);
		display: flex;
		align-items: baseline;
		gap: 10px;
		flex-wrap: wrap;
	}
	.mailing-item a {
		font-size: 0.92rem;
		font-weight: 600;
		color: var(--text);
		text-decoration: none;
		line-height: 1.4;
	}
	.mailing-item a:hover { color: var(--accent); }
	.mailing-meta { font-size: 0.75rem; color: var(--text-muted); }

	.rots-inline-section { margin: 32px 0 24px; padding-bottom: 16px; }
	.rots-inline-section .section-label { color: #b45309; }

	.archive-section { margin-top: 32px; }
	.archive-section .section-label { color: var(--text-muted); }

	.tweet-card { border-bottom: 1px solid var(--border); padding: 14px 0; }
	.tweet-header { display: flex; align-items: center; gap: 6px; margin-bottom: 6px; }
	.tweet-header a { font-size: 0.85rem; color: var(--text-secondary); text-decoration: none; }
	.tweet-header a:hover { color: var(--accent); }
	.tweet-text { color: var(--text-secondary); font-size: 0.85rem; line-height: 1.65; margin: 0; }

	@media (max-width: 768px) {
		.digest-content { padding: 16px; max-width: 100%; overflow-x: hidden; word-wrap: break-word; overflow-wrap: break-word; }
		.digest-date { font-size: 1.3rem; }
	}
</style>
