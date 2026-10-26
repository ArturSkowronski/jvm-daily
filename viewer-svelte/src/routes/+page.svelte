<script lang="ts">
	import { onMount, onDestroy, tick } from 'svelte';
	import { get } from 'svelte/store';
	import { fetchDates, fetchDigest } from '$lib/api/client';
	import type { DailyDigest, DigestCluster } from '$lib/api/types';
	import DateSidebar from '$lib/components/DateSidebar.svelte';
	import Cluster from '$lib/components/Cluster.svelte';
	import ReleaseCard from '$lib/components/ReleaseCard.svelte';
	import ItemActions from '$lib/components/ItemActions.svelte';
	import KeyboardHelp from '$lib/components/KeyboardHelp.svelte';
	import DebugPanel from '$lib/components/DebugPanel.svelte';
	import { rots, later } from '$lib/stores/lists';
	import { has } from '$lib/stores/savedList';
	import { dismissed, setDismissed, isDismissed, ensureDismissedLoaded } from '$lib/stores/dismissed';
	import { reviewed, setReviewed } from '$lib/stores/reviewed';
	import { compact } from '$lib/stores/compact';
	import { toast, pushUndo, undo } from '$lib/stores/toast';
	import { fmtDigestDate, fmtShortDate } from '$lib/utils/format';
	import { isSocialPost } from '$lib/utils/merge';
	import {
		type Marks,
		isHandled,
		nextPending,
		stepFocus,
		adjacentDate,
		nextUnreviewedDate,
		startDate
	} from '$lib/utils/review';

	type Kind = 'topic' | 'release' | 'openjdk' | 'compact';
	interface Item {
		key: string;
		cluster: DigestCluster;
		kind: Kind;
	}

	let dates = $state<string[]>([]);
	let currentDate = $state('');
	let digest = $state<DailyDigest | null>(null);
	let loading = $state(true);
	let focusKey = $state<string | null>(null);
	let showHelp = $state(false);
	let showReviewed = $state(false);
	let alive = true;

	function onPopState(e: PopStateEvent) {
		const d = (e.state as { date?: string })?.date || dates[0];
		loadDate(d, 'none');
	}

	onMount(async () => {
		dates = await fetchDates();
		if (dates.length === 0) { loading = false; return; }

		const requested = new URLSearchParams(window.location.search).get('date');
		const first = requested && dates.includes(requested) ? requested : startDate(dates, get(reviewed))!;
		await loadDate(first, 'replace');
		loading = false;
		window.addEventListener('popstate', onPopState);
	});

	onDestroy(() => {
		alive = false;
		if (typeof window !== 'undefined') window.removeEventListener('popstate', onPopState);
	});

	async function loadDate(date: string, nav: 'push' | 'replace' | 'none' = 'push') {
		ensureDismissedLoaded(date);
		const d = await fetchDigest(date);
		if (!alive) return;
		digest = d;
		currentDate = date;
		focusKey = null;
		showReviewed = false;
		if (nav === 'push') history.pushState({ date }, '', `?date=${date}`);
		else if (nav === 'replace') history.replaceState({ date }, '', `?date=${date}`);
		window.scrollTo(0, 0);
	}

	function isRelease(c: DigestCluster): boolean {
		return c.type === 'release';
	}
	function isStandaloneTweet(c: DigestCluster): boolean {
		return c.articles.length === 1 && isSocialPost(c.articles[0]);
	}
	function isMailingListCluster(c: DigestCluster): boolean {
		return c.articles.every((a) => a.sourceType === 'openjdk_mail' || a.sourceType === 'jep');
	}

	/** Every reviewable cluster of the day, in reading order. */
	const items = $derived.by((): Item[] => {
		const cs = digest?.clusters || [];
		const item = (kind: Kind) => (cluster: DigestCluster): Item => ({ key: cluster.title, cluster, kind });
		const topics = cs.filter((c) => !isRelease(c) && !isStandaloneTweet(c) && !isMailingListCluster(c));
		const releases = cs.filter(isRelease);
		const jdk = cs
			.filter((c) => !isRelease(c) && !isStandaloneTweet(c) && isMailingListCluster(c))
			.sort((a, b) => b.engagementScore - a.engagementScore);
		const jdkTop = jdk.filter((c) => c.articles.length > 1 || c.engagementScore > 3);
		const jdkCompact = jdk.filter((c) => c.articles.length <= 1 && c.engagementScore <= 3).slice(0, 5);
		return [
			...topics.map(item('topic')),
			...releases.map(item('release')),
			...jdkTop.map(item('openjdk')),
			...jdkCompact.map(item('compact'))
		];
	});

	const standaloneTweets = $derived(
		(digest?.clusters || []).filter((c) => isStandaloneTweet(c)).map((c) => c.articles[0])
	);

	function marksOf(key: string, date = currentDate): Marks {
		return {
			done: isDismissed($dismissed, date, key),
			later: has($later, date, key),
			rots: has($rots, date, key)
		};
	}
	function isPending(key: string): boolean {
		return !isHandled(marksOf(key));
	}

	const pending = $derived(items.filter((i) => isPending(i.key)));
	const handled = $derived(items.filter((i) => !isPending(i.key)));
	const pendingKeys = $derived(pending.map((i) => i.key));
	const pendingOf = (kinds: Kind[]) => pending.filter((i) => kinds.includes(i.kind));

	// Keep a valid focus: first pending item when the current one is gone.
	$effect(() => {
		if (focusKey && pendingKeys.includes(focusKey)) return;
		focusKey = pendingKeys[0] ?? null;
	});

	// A day with nothing left in its inbox is "reviewed" and gets skipped by auto-advance.
	$effect(() => {
		if (!digest || !currentDate) return;
		setReviewed(currentDate, pending.length === 0);
	});

	async function setFocus(key: string | null, scroll = true) {
		focusKey = key;
		if (!scroll || !key) return;
		await tick();
		document.querySelector('.review-item.focused')?.scrollIntoView({ block: 'start', behavior: 'smooth' });
	}

	function applyMarks(date: string, key: string, m: Marks) {
		setDismissed(date, key, m.done);
		later.set(date, key, m.later);
		rots.set(date, key, m.rots);
	}

	/**
	 * Change marks of `keys`, move focus to the next pending item, and when the day's inbox
	 * becomes empty jump to the next unreviewed day. Every action is undoable (u).
	 */
	function act(keys: string[], change: (m: Marks) => Marks, label: string) {
		if (keys.length === 0) return;
		const date = currentDate;
		const order = [...pendingKeys];
		const prev = keys.map((k) => ({ k, m: marksOf(k) }));
		const from = focusKey;
		for (const { k, m } of prev) applyMarks(date, k, change(m));
		if (from && keys.includes(from) && !isPending(from)) {
			setFocus(nextPending(order, from, isPending));
		}

		const restore = () => {
			for (const { k, m } of prev) applyMarks(date, k, m);
			if (!alive) return;
			const back = () => { if (isPending(prev[0].k)) setFocus(prev[0].k); };
			if (currentDate !== date) loadDate(date).then(back);
			else back();
		};

		const dayDone = items.length > 0 && items.every((i) => !isPending(i.key));
		if (!dayDone || order.length === 0) return pushUndo(label, restore);

		setReviewed(date, true);
		const next = nextUnreviewedDate(dates, get(reviewed), date);
		if (next) {
			pushUndo(label, restore, `${label} · day reviewed → ${fmtShortDate(next)}`);
			loadDate(next);
		} else {
			pushUndo(label, restore, `${label} · all days reviewed`);
		}
	}

	function toggle(key: string, field: keyof Marks) {
		const on = !marksOf(key)[field];
		const labels: Record<keyof Marks, [string, string]> = {
			done: ['Done', 'Back in inbox'],
			later: ['Saved for later', 'Removed from Read later'],
			rots: ['Added to Rest of the Story', 'Removed from Rest of the Story']
		};
		act([key], (m) => ({ ...m, [field]: on }), labels[field][on ? 0 : 1]);
	}

	function markDayReviewed() {
		const keys = [...pendingKeys];
		if (keys.length === 0) return toast('Nothing left on this day');
		act(keys, (m) => ({ ...m, done: true }), `${keys.length} marked done`);
	}

	function goDay(dir: 1 | -1) {
		const next = adjacentDate(dates, currentDate, dir);
		if (!next) return toast(dir > 0 ? 'This is the oldest digest' : 'This is the newest digest');
		loadDate(next);
	}

	function goUnreviewed() {
		const next = nextUnreviewedDate(dates, $reviewed, currentDate);
		if (!next) return toast('Every other day is reviewed');
		loadDate(next);
	}

	function primaryUrl(c: DigestCluster): string | null {
		const sorted = [...c.articles].sort((a, b) => b.engagementScore - a.engagementScore);
		return sorted.find((a) => a.url)?.url ?? null;
	}

	function onKey(e: KeyboardEvent) {
		const target = e.target as HTMLElement;
		if (target.closest('input, textarea, select, [contenteditable="true"]')) return;
		if (e.metaKey || e.ctrlKey || e.altKey) return;
		const k = e.key;
		if ((k === 'Enter' || k === ' ') && target.closest('a, button')) return;
		if (k === '?') { e.preventDefault(); showHelp = !showHelp; return; }
		if (k === 'Escape') { showHelp = false; return; }
		if (showHelp) return;
		if (e.repeat && ['e', 'x', 's', 'r', 'E', 'u', 'z'].includes(k)) return; // a held key must not act on the next item

		if (k === 'j' || k === 'ArrowDown') { e.preventDefault(); setFocus(stepFocus(pendingKeys, focusKey, 1)); }
		else if (k === 'k' || k === 'ArrowUp') { e.preventDefault(); setFocus(stepFocus(pendingKeys, focusKey, -1)); }
		else if ((k === 'ArrowRight' && e.shiftKey) || k === 'N') { e.preventDefault(); goUnreviewed(); }
		else if (k === 'ArrowRight' || k === 'n') { e.preventDefault(); goDay(1); }
		else if (k === 'ArrowLeft' || k === 'p') { e.preventDefault(); goDay(-1); }
		else if (k === 'u' || k === 'z') undo();
		else if (k === 'c') compact.update((v) => !v);
		else if (k === 'E') markDayReviewed();
		else if (!focusKey) return;
		else if (k === 'e' || k === 'x') toggle(focusKey, 'done');
		else if (k === 's') toggle(focusKey, 'later');
		else if (k === 'r') toggle(focusKey, 'rots');
		else if (k === 'o' || k === 'Enter') {
			const item = pending.find((i) => i.key === focusKey);
			const url = item && primaryUrl(item.cluster);
			if (url) window.open(url, '_blank', 'noopener');
		}
	}

	function onContentClick(e: MouseEvent) {
		const t = e.target as HTMLElement;
		if (t.closest('a, button')) return;
		const key = t.closest<HTMLElement>('.review-item')?.dataset.key;
		if (key && pendingKeys.includes(key)) focusKey = key;
	}
</script>

<svelte:window onkeydown={onKey} />

{#snippet itemView(item: Item, isDone: boolean, lead = false)}
	{@const m = marksOf(item.key)}
	{@const focused = !isDone && focusKey === item.key}
	{#if item.kind === 'release'}
		<ReleaseCard
			cluster={item.cluster} done={m.done} later={m.later} rots={m.rots} {focused} dimmed={isDone}
			onDone={() => toggle(item.key, 'done')}
			onLater={() => toggle(item.key, 'later')}
			onRots={() => toggle(item.key, 'rots')}
		/>
	{:else if item.kind === 'compact'}
		<li class="mailing-item review-item" class:focused class:dismissed={isDone} data-key={item.key}>
			<a href={item.cluster.articles[0]?.url || '#'} target="_blank" rel="noopener">{item.cluster.title}</a>
			<span class="mailing-meta">
				{item.cluster.articles.length} source{item.cluster.articles.length > 1 ? 's' : ''}
			</span>
			<ItemActions
				inline done={m.done} later={m.later} rots={m.rots} {focused}
				onDone={() => toggle(item.key, 'done')}
				onLater={() => toggle(item.key, 'later')}
				onRots={() => toggle(item.key, 'rots')}
			/>
		</li>
	{:else}
		<Cluster
			cluster={item.cluster} done={m.done} later={m.later} rots={m.rots} {focused} dimmed={isDone} {lead}
			onDone={() => toggle(item.key, 'done')}
			onLater={() => toggle(item.key, 'later')}
			onRots={() => toggle(item.key, 'rots')}
		/>
	{/if}
{/snippet}

{#snippet itemList(list: Item[], isDone: boolean)}
	{#each list.filter((i) => i.kind !== 'compact') as item (item.cluster.id)}
		{@render itemView(item, isDone)}
	{/each}
	{#if list.some((i) => i.kind === 'compact')}
		<ul class="mailing-list">
			{#each list.filter((i) => i.kind === 'compact') as item (item.cluster.id)}
				{@render itemView(item, isDone)}
			{/each}
		</ul>
	{/if}
{/snippet}

{#if loading}
	<div class="loading">Loading...</div>
{:else if dates.length === 0}
	<div class="empty">No digest files found. Run the pipeline first.</div>
{:else}
	<DateSidebar {dates} {currentDate} reviewed={$reviewed} onSelect={(d) => loadDate(d)} />
	<!-- svelte-ignore a11y_click_events_have_key_events, a11y_no_static_element_interactions -->
	<div class="digest-content" onclick={onContentClick}>
		{#if digest}
			<header class="digest-header">
				<div class="digest-nav">
					<button class="nav-btn" title="Newer day (←)" onclick={() => goDay(-1)}
						disabled={dates.indexOf(currentDate) <= 0}>←</button>
					<h1 class="digest-date">{fmtDigestDate(currentDate)}</h1>
					<button class="nav-btn" title="Older day (→)" onclick={() => goDay(1)}
						disabled={dates.indexOf(currentDate) >= dates.length - 1}>→</button>
				</div>
				<div class="digest-stats">
					<span>{digest.totalArticles} articles</span>
					<span>{digest.clusters.length} topics</span>
					<span class="progress-label">{handled.length}/{items.length} reviewed</span>
				</div>
				<div class="progress" aria-hidden="true">
					<div class="progress-bar" style:width="{items.length ? (handled.length / items.length) * 100 : 100}%"></div>
				</div>
				<div class="toolbar">
					{#if pending.length > 0}
						<button class="bigdone" onclick={markDayReviewed}>✓ Mark day reviewed <kbd>⇧E</kbd></button>
					{/if}
					<button class="tool" class:on={$compact} onclick={() => compact.update((v) => !v)}>Compact <kbd>c</kbd></button>
					<button class="tool keys-btn" onclick={() => (showHelp = true)}>Keys <kbd>?</kbd></button>
				</div>
			</header>

			{#if pending.length === 0}
				<div class="inbox-zero">
					<div class="inbox-zero-title">✓ Day reviewed</div>
					{#if nextUnreviewedDate(dates, $reviewed, currentDate)}
						<button class="tool" onclick={goUnreviewed}>Next unreviewed day <kbd>⇧→</kbd></button>
					{:else}
						<p>Every digest is reviewed. Saved clusters wait in <b>Later</b> and <b>ROTS</b>.</p>
					{/if}
				</div>
			{/if}

			{#if pendingOf(['topic']).length > 0}
				<div class="section">News &amp; topics <span class="count">· {pendingOf(['topic']).length} {pendingOf(['topic']).length === 1 ? 'cluster' : 'clusters'}</span></div>
				{#each pendingOf(['topic']) as item, i (item.cluster.id)}
					{@render itemView(item, false, i === 0)}
				{/each}
			{/if}

			<a class="weekly-pin" href="https://www.jvm-weekly.com/" target="_blank" rel="noopener">
				<span class="label">JVM Weekly</span>
				<span>The deeper read — curated weekly takes on the JVM ecosystem.</span>
				<span class="arrow">→</span>
			</a>

			{#if pendingOf(['release']).length > 0}
				<div class="releases-section">
					<div class="section">Releases <span class="count">· {pendingOf(['release']).length} today</span></div>
					<div class="release-pills">
						{#each pendingOf(['release']) as item (item.cluster.id)}
							<button class="release-pill" onclick={() => setFocus(item.key)}>{item.cluster.title}</button>
						{/each}
					</div>
					{@render itemList(pendingOf(['release']), false)}
				</div>
			{/if}

			{#if pendingOf(['openjdk', 'compact']).length > 0}
				<div class="mailing-section">
					<div class="section">OpenJDK <span class="count">· mailing lists, JEPs, drafts</span></div>
					{@render itemList(pendingOf(['openjdk', 'compact']), false)}
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

			{#if handled.length > 0}
				<div class="archive-section">
					<button class="section section-toggle" onclick={() => (showReviewed = !showReviewed)}>
						{showReviewed ? '▾' : '▸'} Reviewed <span class="count">· {handled.length}</span>
					</button>
					{#if showReviewed}
						{@render itemList(handled, true)}
					{/if}
				</div>
			{/if}

			<DebugPanel items={digest.debug || []} />
		{/if}
	</div>
{/if}

{#if showHelp}
	<KeyboardHelp onClose={() => (showHelp = false)} />
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
		align-items: center;
		gap: 10px 16px;
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

	.digest-nav { display: flex; align-items: center; gap: 10px; }
	.nav-btn {
		appearance: none; width: 28px; height: 28px; flex-shrink: 0;
		border: 1px solid var(--border); border-radius: 5px; background: transparent;
		color: var(--text-3); cursor: pointer; font-size: 13px;
	}
	.nav-btn:hover:not(:disabled) { border-color: var(--accent-bd); color: var(--accent-dark); background: var(--accent-bg); }
	.nav-btn:disabled { opacity: 0.35; cursor: default; }
	.digest-stats .progress-label { color: var(--accent); font-weight: 600; }
	.progress { width: 100%; height: 3px; background: var(--border-soft); border-radius: 2px; overflow: hidden; }
	.progress-bar { height: 100%; background: var(--accent); transition: width .25s; }
	.toolbar { display: flex; gap: 6px; align-items: center; flex-wrap: wrap; width: 100%; }
	.bigdone {
		appearance: none; background: var(--accent); color: #fff; border: 0; border-radius: 5px;
		padding: 6px 12px; font: 600 12px/1 var(--font-sans); cursor: pointer;
	}
	.bigdone:hover { filter: brightness(1.08); }
	.tool {
		appearance: none; background: transparent; border: 1px solid var(--border); border-radius: 5px;
		padding: 5px 10px; font: 500 11.5px/1 var(--font-sans); color: var(--text-3); cursor: pointer;
	}
	.tool:hover { border-color: var(--border-strong); color: var(--text-2); }
	.tool.on { background: var(--accent-bg); border-color: var(--accent-bd); color: var(--accent-dark); }
	kbd {
		font: 500 9.5px/1 var(--font-mono); margin-left: 6px; padding: 1px 4px;
		border: 1px solid currentColor; border-radius: 3px; opacity: 0.7;
	}

	.inbox-zero { padding: 24px 0 8px; color: var(--text-2); font-size: 14px; }
	.inbox-zero-title { font: 700 20px/1.3 var(--font-sans); color: var(--accent); margin-bottom: 12px; }

	.mailing-item.review-item { align-items: center; border-left: 3px solid transparent; scroll-margin-top: 72px; }
	.mailing-item.focused { border-left-color: var(--accent); background: var(--accent-bg); }
	.mailing-item.dismissed { opacity: 0.35; }

	.section-toggle {
		appearance: none; background: none; border: 0; padding: 0; cursor: pointer; width: 100%;
	}
	.section-toggle:hover { color: var(--text-3); }

	@media (max-width: 760px) {
		.keys-btn, .toolbar kbd { display: none; }
		.mailing-item.review-item { border-left: none; }
		.digest-content { padding: 18px 14px 60px; max-width: 100%; overflow-x: hidden; word-wrap: break-word; overflow-wrap: break-word; }
		.digest-date { font-size: 19px; }
	}
</style>
