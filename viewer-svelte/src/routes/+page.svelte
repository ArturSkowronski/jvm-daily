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
			<header class="digest-header">
				<h1 class="digest-date">{fmtDigestDate(currentDate)}</h1>
				<div class="digest-stats">
					<span>{digest.totalArticles} articles</span>
					<span>{digest.clusters.length} topics</span>
				</div>
			</header>

			{#if normalClusters.length > 0}
				<div class="section">News &amp; topics <span class="count">· {normalClusters.length} {normalClusters.length === 1 ? 'cluster' : 'clusters'}</span></div>
			{/if}

			{#each normalClusters as cluster, i (cluster.id)}
				<Cluster
					{cluster}
					bookmarked={false}
					dismissedState={false}
					lead={i === 0}
					onBookmark={() => toggleBookmark(currentDate, cluster.title)}
					onDismiss={() => toggleDismiss(currentDate, cluster.title)}
				/>
			{/each}

			<a class="weekly-pin" href="https://www.jvm-weekly.com/" target="_blank" rel="noopener">
				<span class="label">JVM Weekly</span>
				<span>The deeper read — curated weekly takes on the JVM ecosystem.</span>
				<span class="arrow">→</span>
			</a>

			{#if normalReleases.length > 0}
				<div class="releases-section">
					<div class="section">Releases <span class="count">· {normalReleases.length} today</span></div>
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
					<div class="section">OpenJDK <span class="count">· mailing lists, JEPs, drafts</span></div>
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
					<div class="section rots section-label">★ Rest of the Story</div>
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
					<div class="section">Bluesky <span class="count">· {standaloneTweets.length} standalone {standaloneTweets.length === 1 ? 'post' : 'posts'}</span></div>
					<article class="bsky-card">
						<div class="bsky-rows">
							{#each standaloneTweets as tweet}
								<div class="row social">
									<span class="bsky-icon">🦋</span>
									<div class="art-body">
										<a class="bsky-handle" href={tweet.url || '#'} target="_blank" rel="noopener">@{tweet.handle || 'Bluesky'}</a>
										<p class="bsky-text">{tweet.title}</p>
									</div>
								</div>
							{/each}
						</div>
					</article>
				</div>
			{/if}

			{#if hasArchive}
				<div class="archive-section">
					<div class="section section-label">Archive</div>
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
	.loading, .empty { padding: 48px; text-align: center; color: var(--text-3); width: 100%; }

	.digest-content {
		flex: 1;
		overflow-y: auto;
		padding: 32px 40px 96px;
		max-width: 860px;
		margin: 0 auto;
		width: 100%;
	}

	.digest-header {
		display: flex;
		align-items: baseline;
		gap: 16px;
		flex-wrap: wrap;
		padding-bottom: 18px;
		border-bottom: 1px solid var(--border);
		margin-bottom: 24px;
	}
	.digest-date {
		font-family: var(--font-sans);
		font-weight: 700;
		font-size: 26px;
		letter-spacing: -0.015em;
		line-height: 1.2;
		color: var(--text);
		margin: 0;
	}
	.digest-stats {
		display: flex;
		gap: 12px;
		flex-wrap: wrap;
		font: 500 12px/1 var(--font-mono);
		color: var(--text-3);
	}
	.digest-stats span { white-space: nowrap; }
	.digest-stats span::before {
		content: "·";
		margin-right: 8px;
		color: var(--text-faint);
	}
	.digest-stats span:first-child::before { content: ""; margin: 0; }

	.section {
		display: flex;
		align-items: baseline;
		gap: 10px;
		margin: 36px 0 16px;
		font: 600 11px/1 var(--font-mono);
		letter-spacing: 0.14em;
		text-transform: uppercase;
		color: var(--text-3);
	}
	.section .count {
		font-weight: 400;
		color: var(--text-faint);
		letter-spacing: 0.04em;
		white-space: nowrap;
	}
	.section.rots { color: var(--rots); }

	.releases-section,
	.tweets-section,
	.mailing-section { margin-top: 0; }

	.release-pills {
		display: flex;
		flex-wrap: wrap;
		gap: 5px;
		margin-bottom: 10px;
	}
	.release-pill {
		appearance: none;
		background: var(--accent-bg);
		border: 1px solid var(--accent-bd);
		color: var(--accent-dark);
		font: 500 10.5px/1 var(--font-mono);
		padding: 4px 10px 5px;
		border-radius: 10px;
		cursor: pointer;
	}
	.release-pill:hover { background: var(--bg-card); }

	.mailing-list {
		list-style: none;
		padding: 0;
		margin: 6px 0 0;
		background: var(--bg-card);
		border: 1px solid var(--border);
		border-radius: var(--radius);
	}
	.mailing-item {
		padding: 10px 16px;
		border-top: 1px solid var(--border-soft);
		display: flex;
		align-items: baseline;
		gap: 10px;
		flex-wrap: wrap;
		transition: background .12s;
	}
	.mailing-item:first-child { border-top: 0; }
	.mailing-item:hover { background: var(--bg-soft); }
	.mailing-item a {
		font-size: 14px;
		font-weight: 500;
		color: var(--text);
		text-decoration: none;
		flex: 1;
		min-width: 0;
		line-height: 1.45;
	}
	.mailing-item a:hover { color: var(--accent-dark); }
	.mailing-meta {
		font: 400 10.5px/1 var(--font-mono);
		color: var(--text-faint);
		white-space: nowrap;
	}

	.weekly-pin {
		display: flex;
		align-items: center;
		gap: 12px;
		padding: 14px 18px;
		margin: 24px 0 0;
		background: var(--bg-card);
		border: 1px solid var(--border);
		border-left: 3px solid var(--rots);
		border-radius: var(--radius);
		font-size: 13.5px;
		color: var(--text-2);
		text-decoration: none;
	}
	.weekly-pin:hover { background: var(--rots-bg); text-decoration: none; color: var(--text); }
	.weekly-pin .label {
		font: 600 9.5px/1 var(--font-mono);
		letter-spacing: 0.12em;
		text-transform: uppercase;
		color: var(--rots);
		white-space: nowrap;
	}
	.weekly-pin .arrow { margin-left: auto; color: var(--rots); }

	.rots-inline-section { margin: 0 0 24px; padding-bottom: 16px; }

	.archive-section { margin-top: 0; }
	.archive-section .section { color: var(--text-faint); }

	.bsky-card {
		background: var(--bg-card);
		border: 1px solid var(--border);
		border-radius: var(--radius);
		padding: 8px 20px;
		margin-bottom: 12px;
		box-shadow: var(--shadow-sm);
	}
	.bsky-rows .row.social {
		display: flex;
		gap: 12px;
		padding: 12px 0 11px;
		border-top: 1px solid var(--border-soft);
		align-items: flex-start;
	}
	.bsky-rows .row.social:first-child { border-top: 0; }
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
	.art-body { flex: 1; min-width: 0; }
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

	@media (max-width: 760px) {
		.digest-content { padding: 18px 14px 60px; max-width: 100%; overflow-x: hidden; word-wrap: break-word; overflow-wrap: break-word; }
		.digest-date { font-size: 19px; }
	}
</style>
