<script lang="ts">
	import { tick } from 'svelte';
	import { fetchDigest } from '$lib/api/client';
	import type { DigestCluster } from '$lib/api/types';
	import { rots, later } from '$lib/stores/lists';
	import { has } from '$lib/stores/savedList';
	import { compact } from '$lib/stores/compact';
	import { pushUndo, undo, toast } from '$lib/stores/toast';
	import { fmtDigestDate } from '$lib/utils/format';
	import { nextPending, stepFocus } from '$lib/utils/review';
	import Cluster from './Cluster.svelte';

	let { kind }: { kind: 'later' | 'rots' } = $props();

	const TITLES = { later: 'Read later', rots: 'ROTS — Rest of the Story' };
	const EMPTY = {
		later: 'Nothing saved for later. Press s (or the bookmark button) on a cluster in the digest.',
		rots: 'Nothing in Rest of the Story yet. Press r (or ☆) on a cluster in the digest.'
	};

	interface Row {
		id: string;
		date: string;
		cluster: DigestCluster;
	}

	const list = $derived(kind === 'later' ? later : rots);
	const listState = $derived(kind === 'later' ? $later : $rots);

	let rows = $state<Row[]>([]);
	let loading = $state(true);
	let focusId = $state<string | null>(null);

	// Reload rows whenever the list changes (digests are cached by the client).
	$effect(() => {
		const state = listState;
		let cancelled = false;
		(async () => {
			const dates = Object.keys(state).filter((d) => state[d].length > 0).sort().reverse();
			const perDate = await Promise.all(
				dates.map(async (date) => {
					const digest = await fetchDigest(date);
					const keys = new Set(state[date]);
					return (digest?.clusters || [])
						.filter((c) => keys.has(c.title))
						.map((cluster) => ({ id: `${date}|${cluster.title}`, date, cluster }));
				})
			);
			if (cancelled) return;
			rows = perDate.flat();
			loading = false;
		})();
		return () => { cancelled = true; };
	});

	const ids = $derived(rows.map((r) => r.id));

	$effect(() => {
		if (focusId && ids.includes(focusId)) return;
		focusId = ids[0] ?? null;
	});

	async function setFocus(id: string | null) {
		focusId = id;
		await tick();
		document.querySelector('.review-item.focused')?.scrollIntoView({ block: 'start', behavior: 'smooth' });
	}

	function toggleIn(which: 'later' | 'rots', row: Row) {
		const store = which === 'later' ? later : rots;
		const on = !has(which === 'later' ? $later : $rots, row.date, row.cluster.title);
		const leavesList = which === kind && !on;
		if (leavesList && focusId === row.id) {
			setFocus(nextPending(ids, row.id, (id) => id !== row.id));
		}
		store.set(row.date, row.cluster.title, on);
		const name = which === 'later' ? 'Read later' : 'Rest of the Story';
		pushUndo(on ? `Added to ${name}` : `Removed from ${name}`, () => {
			store.set(row.date, row.cluster.title, !on);
			focusId = row.id;
		});
	}

	function onKey(e: KeyboardEvent) {
		const target = e.target as HTMLElement;
		if (target.closest('input, textarea, select, [contenteditable="true"]')) return;
		if (e.metaKey || e.ctrlKey || e.altKey) return;
		const k = e.key;
		if ((k === 'Enter' || k === ' ') && target.closest('a, button')) return;
		if (e.repeat && ['e', 'x', 's', 'r', 'u', 'z'].includes(k)) return;
		const row = rows.find((r) => r.id === focusId);

		if (k === 'j' || k === 'ArrowDown') { e.preventDefault(); setFocus(stepFocus(ids, focusId, 1)); }
		else if (k === 'k' || k === 'ArrowUp') { e.preventDefault(); setFocus(stepFocus(ids, focusId, -1)); }
		else if (k === 'u' || k === 'z') undo();
		else if (k === 'c') compact.update((v) => !v);
		else if (!row) return;
		else if (k === 'e' || k === 'x') toggleIn(kind, row);
		else if (k === 's') toggleIn('later', row);
		else if (k === 'r') toggleIn('rots', row);
		else if (k === 'o' || k === 'Enter') {
			const url = [...row.cluster.articles].sort((a, b) => b.engagementScore - a.engagementScore).find((a) => a.url)?.url;
			if (url) window.open(url, '_blank', 'noopener');
		}
	}

	function onContentClick(e: MouseEvent) {
		const t = e.target as HTMLElement;
		if (t.closest('a, button')) return;
		const el = t.closest<HTMLElement>('[data-row]');
		if (el) focusId = el.dataset.row!;
	}

	async function copyMarkdown() {
		const lines: string[] = [`# ${TITLES[kind]}\n`];
		let lastDate = '';
		for (const r of rows) {
			if (r.date !== lastDate) {
				lines.push(`## ${fmtDigestDate(r.date)}\n`);
				lastDate = r.date;
			}
			lines.push(`### ${r.cluster.title}\n`);
			lines.push(r.cluster.summary + '\n');
			const url = r.cluster.articles.find((a) => a.url)?.url;
			if (url) lines.push(url + '\n');
		}
		await navigator.clipboard.writeText(lines.join('\n'));
		toast('Copied as Markdown');
	}

	function clearAll() {
		if (confirm(`Clear everything in ${TITLES[kind]}?`)) list.clear();
	}
</script>

<svelte:window onkeydown={onKey} />

<!-- svelte-ignore a11y_click_events_have_key_events, a11y_no_static_element_interactions -->
<div class="saved" onclick={onContentClick}>
	<div class="saved-header">
		<h2>{TITLES[kind]} <span class="count">{rows.length}</span></h2>
		<div class="saved-toolbar">
			<button onclick={copyMarkdown} disabled={rows.length === 0}>Copy as Markdown</button>
			<button onclick={clearAll} disabled={rows.length === 0}>Clear all</button>
		</div>
		<div class="hint">
			<kbd>j</kbd>/<kbd>k</kbd> move · <kbd>e</kbd> remove from this list · <kbd>s</kbd> later ·
			<kbd>r</kbd> ROTS · <kbd>o</kbd> open · <kbd>u</kbd> undo
		</div>
	</div>

	{#if loading}
		<div class="empty">Loading...</div>
	{:else if rows.length === 0}
		<div class="empty">{EMPTY[kind]}</div>
	{:else}
		{#each rows as row, i (row.id)}
			{#if i === 0 || rows[i - 1].date !== row.date}
				<div class="date-header">{fmtDigestDate(row.date)}</div>
			{/if}
			<div data-row={row.id}>
				<Cluster
					cluster={row.cluster}
					focused={focusId === row.id}
					later={has($later, row.date, row.cluster.title)}
					rots={has($rots, row.date, row.cluster.title)}
					onLater={() => toggleIn('later', row)}
					onRots={() => toggleIn('rots', row)}
				/>
			</div>
		{/each}
	{/if}
</div>

<style>
	.saved { padding: 32px 48px; flex: 1; max-width: 920px; margin: 0 auto; }
	.saved-header { margin-bottom: 24px; padding-bottom: 16px; border-bottom: 2px solid #1a1a1a; }
	h2 { margin: 0; font-size: 1.8rem; }
	.count { font-size: 1rem; color: #868787; font-weight: 400; margin-left: 6px; }
	.saved-toolbar { display: flex; gap: 8px; margin-top: 12px; }
	.saved-toolbar button {
		font-size: 0.78rem; padding: 6px 14px; border: 1px solid #ddd; border-radius: 6px;
		background: #fff; cursor: pointer; color: #555; font-family: inherit;
	}
	.saved-toolbar button:hover:not(:disabled) { border-color: #999; }
	.saved-toolbar button:disabled { opacity: 0.4; cursor: default; }
	.hint { margin-top: 10px; font-size: 0.8rem; color: #999; }
	kbd {
		font-family: ui-monospace, monospace; font-size: 0.7rem;
		padding: 0 4px; border: 1px solid #ddd; border-radius: 3px;
	}
	.date-header {
		font-size: 1rem; font-weight: 600; color: #868787;
		padding: 24px 0 4px; text-transform: uppercase; letter-spacing: 0.06em;
	}
	.empty { color: #999; padding: 48px 0; text-align: center; }

	@media (max-width: 768px) {
		.saved { padding: 20px 16px; max-width: 100%; }
		.hint { display: none; }
	}
</style>
