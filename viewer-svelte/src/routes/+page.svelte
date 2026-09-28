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

{#snippet itemView(item: Item, isDone: boolean)}
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
		<div class="mailing-item review-item" class:focused class:dismissed={isDone} data-key={item.key}>
			<div class="mailing-text">
				<a href={item.cluster.articles[0]?.url || '#'} target="_blank" rel="noopener">{item.cluster.title}</a>
				<span class="mailing-meta">
					{item.cluster.articles.length} source{item.cluster.articles.length > 1 ? 's' : ''}
				</span>
			</div>
			<ItemActions
				inline done={m.done} later={m.later} rots={m.rots} {focused}
				onDone={() => toggle(item.key, 'done')}
				onLater={() => toggle(item.key, 'later')}
				onRots={() => toggle(item.key, 'rots')}
			/>
		</div>
	{:else}
		<Cluster
			cluster={item.cluster} done={m.done} later={m.later} rots={m.rots} {focused} dimmed={isDone}
			onDone={() => toggle(item.key, 'done')}
			onLater={() => toggle(item.key, 'later')}
			onRots={() => toggle(item.key, 'rots')}
		/>
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
			<div class="digest-header">
				<div class="digest-nav">
					<button class="nav-btn" title="Newer day (←)" onclick={() => goDay(-1)}
						disabled={dates.indexOf(currentDate) <= 0}>←</button>
					<div class="digest-date">{fmtDigestDate(currentDate)}</div>
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
					<button class="tool" onclick={() => (showHelp = true)}>Keys <kbd>?</kbd></button>
				</div>
			</div>

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

			{#each pendingOf(['topic']) as item (item.cluster.id)}
				{@render itemView(item, false)}
			{/each}

			{#if pendingOf(['release']).length > 0}
				<div class="releases-section">
					<div class="section-label">Releases</div>
					{#each pendingOf(['release']) as item (item.cluster.id)}
						{@render itemView(item, false)}
					{/each}
				</div>
			{/if}

			{#if pendingOf(['openjdk', 'compact']).length > 0}
				<div class="mailing-section">
					<div class="section-label">OpenJDK</div>
					{#each pendingOf(['openjdk', 'compact']) as item (item.cluster.id)}
						{@render itemView(item, false)}
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

			{#if handled.length > 0}
				<div class="archive-section">
					<button class="section-label section-toggle" onclick={() => (showReviewed = !showReviewed)}>
						{showReviewed ? '▾' : '▸'} Reviewed ({handled.length})
					</button>
					{#if showReviewed}
						{#each handled as item (item.cluster.id)}
							{@render itemView(item, true)}
						{/each}
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
	.loading, .empty { padding: 48px; text-align: center; color: #999; width: 100%; }
	.digest-content {
		flex: 1; overflow-y: auto;
		padding: 40px 48px;
		max-width: 920px; margin: 0 auto;
	}
	.digest-header { margin-bottom: 32px; padding-bottom: 20px; border-bottom: 2px solid #1a1a1a; }
	.digest-nav { display: flex; align-items: center; gap: 12px; }
	.digest-date { font-size: 2rem; font-weight: 700; line-height: 1.2; }
	.nav-btn {
		background: none; border: 1px solid #ddd; border-radius: 50%; width: 32px; height: 32px;
		cursor: pointer; color: #555; font-size: 1rem; flex-shrink: 0;
	}
	.nav-btn:hover:not(:disabled) { border-color: #00a64e; color: #00a64e; }
	.nav-btn:disabled { opacity: 0.3; cursor: default; }
	.digest-stats { display: flex; gap: 16px; font-size: 0.85rem; color: #868787; margin-top: 8px; }
	.progress-label { color: #00a64e; font-weight: 600; }
	.progress { height: 3px; background: #eee; border-radius: 2px; margin-top: 10px; overflow: hidden; }
	.progress-bar { height: 100%; background: #00a64e; transition: width 0.25s; }
	.toolbar { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; margin-top: 14px; }
	.bigdone {
		background: #00a64e; color: #fff; border: none; border-radius: 6px;
		padding: 7px 14px; font-family: inherit; font-size: 0.85rem; font-weight: 600; cursor: pointer;
	}
	.bigdone:hover { filter: brightness(1.08); }
	.tool {
		background: #fff; border: 1px solid #ddd; border-radius: 6px; color: #555;
		padding: 6px 12px; font-family: inherit; font-size: 0.8rem; cursor: pointer;
	}
	.tool:hover { border-color: #999; }
	.tool.on { background: #f0faf4; border-color: #00a64e; color: #00a64e; }
	kbd {
		font-family: ui-monospace, monospace; font-size: 0.7rem; margin-left: 6px;
		padding: 0 4px; border: 1px solid currentColor; border-radius: 3px; opacity: 0.7;
	}

	.inbox-zero { padding: 32px 0 40px; color: #555; }
	.inbox-zero-title { font-size: 1.4rem; font-weight: 700; color: #00a64e; margin-bottom: 12px; }

	.releases-section, .tweets-section, .mailing-section { margin-top: 40px; }
	.mailing-item {
		padding: 10px 16px; margin-left: -19px;
		border-bottom: 1px solid #f0f0f0; border-left: 3px solid transparent;
		display: flex; align-items: center; gap: 10px;
		scroll-margin-top: 84px;
	}
	.mailing-item.focused { border-left-color: #00a64e; background: #f3fbf6; }
	.mailing-item.dismissed { opacity: 0.35; }
	.mailing-text { flex: 1; min-width: 0; display: flex; align-items: baseline; gap: 10px; flex-wrap: wrap; }
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
	.archive-section {
		margin-top: 40px; padding-top: 16px;
	}
	.archive-section .section-label { border-bottom-color: #d0d0d0; color: #b0b0b0; }
	.section-toggle {
		background: none; border-top: none; border-left: none; border-right: none;
		font-family: inherit; cursor: pointer; padding-left: 0; padding-right: 0;
	}
	.section-toggle:hover { color: #555; }
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
		.toolbar kbd, .tool:not(.bigdone):last-child { display: none; }
		.mailing-item { margin-left: 0; padding-left: 0; padding-right: 0; border-left: none; }
	}
</style>
