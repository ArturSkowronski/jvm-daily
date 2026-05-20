<script lang="ts">
	import { onMount } from 'svelte';
	import { bookmarks, clearAllBookmarks } from '$lib/stores/bookmarks';
	import { fetchDigest } from '$lib/api/client';
	import type { DigestCluster } from '$lib/api/types';
	import { fmtDigestDate } from '$lib/utils/format';
	import Cluster from '$lib/components/Cluster.svelte';

	interface RotsEntry {
		date: string;
		clusters: DigestCluster[];
	}

	let entries = $state<RotsEntry[]>([]);
	let loading = $state(true);
	let selected = $state(new Set<string>());
	let copyLabel = $state('Copy as Markdown');

	function entryKey(date: string, title: string): string {
		return `${date}::${title}`;
	}

	function toggleSelected(date: string, title: string) {
		const k = entryKey(date, title);
		const next = new Set(selected);
		if (next.has(k)) next.delete(k);
		else next.add(k);
		selected = next;
	}

	async function loadRots() {
		loading = true;
		const rots = $bookmarks;
		const result: RotsEntry[] = [];
		const dates = Object.keys(rots).filter((d) => rots[d].length > 0).sort().reverse();
		await Promise.all(
			dates.map(async (date) => {
				const digest = await fetchDigest(date);
				if (!digest) return;
				const keys = new Set(rots[date]);
				const clusters = digest.clusters.filter((c) => keys.has(c.title));
				if (clusters.length > 0) {
					result.push({ date, clusters });
				}
			})
		);
		entries = result.sort((a, b) => b.date.localeCompare(a.date));
		// Select all by default; keep existing deselections for keys that still exist
		const allKeys = new Set(entries.flatMap((e) => e.clusters.map((c) => entryKey(e.date, c.title))));
		const kept = new Set([...selected].filter((k) => allKeys.has(k)));
		for (const k of allKeys) if (!kept.has(k)) kept.add(k);
		selected = kept;
		loading = false;
	}

	onMount(loadRots);

	// Reload when bookmarks change
	$effect(() => {
		$bookmarks; // track
		loadRots();
	});

	async function copyMarkdown() {
		const lines: string[] = ['# ROTS — Rest of the Story\n'];
		for (const entry of entries) {
			const selectedClusters = entry.clusters.filter((c) =>
				selected.has(entryKey(entry.date, c.title))
			);
			if (selectedClusters.length === 0) continue;
			lines.push(`## ${fmtDigestDate(entry.date)}\n`);
			for (const c of selectedClusters) {
				const primaryUrl = c.articles.find((a) => a.url)?.url;
				const heading = primaryUrl ? `[${c.title}](${primaryUrl})` : c.title;
				lines.push(`### ${heading}\n`);
				lines.push(c.summary + '\n');
				const links = c.articles.filter((a) => a.url);
				if (links.length > 1) {
					lines.push('Sources:');
					for (const a of links) lines.push(`- [${a.title}](${a.url})`);
					lines.push('');
				}
			}
		}
		try {
			await navigator.clipboard.writeText(lines.join('\n'));
			copyLabel = '✓ Copied!';
		} catch {
			copyLabel = '✗ Failed — try again';
		}
		setTimeout(() => { copyLabel = 'Copy as Markdown'; }, 2200);
	}

	function clearSelected() {
		const toRemove = [...selected].map((k) => {
			const idx = k.indexOf('::');
			return { date: k.slice(0, idx), title: k.slice(idx + 2) };
		});
		if (toRemove.length === 0) return;
		if (!confirm(`Remove ${toRemove.length} selected bookmark${toRemove.length > 1 ? 's' : ''}?`)) return;
		bookmarks.update((rots) => {
			const updated = { ...rots };
			for (const { date, title } of toRemove) {
				updated[date] = (updated[date] || []).filter((k) => k !== title);
			}
			return updated;
		});
	}

	function clearAll() {
		if (confirm('Clear all ROTS bookmarks?')) {
			clearAllBookmarks();
		}
	}

	const someSelected = $derived(selected.size > 0);
	const allSelected = $derived(
		selected.size === entries.flatMap((e) => e.clusters).length
	);

	function toggleAll() {
		if (allSelected) {
			selected = new Set();
		} else {
			selected = new Set(entries.flatMap((e) => e.clusters.map((c) => entryKey(e.date, c.title))));
		}
	}
</script>

<div class="rots">
	<div class="rots-toolbar">
		<button onclick={copyMarkdown} class:copied={copyLabel.startsWith('✓')} class:failed={copyLabel.startsWith('✗')}>{copyLabel}</button>
		{#if someSelected}
			<button class="btn-danger" onclick={clearSelected}>Clear selected ({selected.size})</button>
		{/if}
		<button onclick={clearAll}>Clear all</button>
	</div>

	{#if loading}
		<div class="empty">Loading...</div>
	{:else if entries.length === 0}
		<div class="empty">No bookmarked clusters yet. Click ☆ on any cluster to add it here.</div>
	{:else}
		<div class="select-bar">
			<label class="select-all">
				<input type="checkbox" checked={allSelected} indeterminate={someSelected && !allSelected} onchange={toggleAll} />
				{allSelected ? 'Deselect all' : 'Select all'}
			</label>
			<span class="select-count">{selected.size} of {entries.flatMap((e) => e.clusters).length} selected</span>
		</div>
		{#each entries as entry}
			<div class="rots-date-header">{fmtDigestDate(entry.date)}</div>
			{#each entry.clusters as cluster}
				<div class="rots-entry">
					<label class="rots-check-wrap" title={selected.has(entryKey(entry.date, cluster.title)) ? 'Deselect' : 'Select'}>
						<input
							type="checkbox"
							class="rots-check"
							checked={selected.has(entryKey(entry.date, cluster.title))}
							onchange={() => toggleSelected(entry.date, cluster.title)}
						/>
					</label>
					<div class="rots-cluster" class:dimmed={!selected.has(entryKey(entry.date, cluster.title))}>
						<Cluster {cluster} bookmarked={true} />
					</div>
				</div>
			{/each}
		{/each}
	{/if}
</div>

<style>
	.rots { padding: 32px; flex: 1; overflow-y: auto; max-width: 900px; margin: 0 auto; }

	.rots-toolbar { display: flex; gap: 8px; margin-bottom: 16px; flex-wrap: wrap; }
	.rots-toolbar button {
		font-size: 0.78rem; padding: 6px 14px; border: 1px solid var(--border); border-radius: 6px;
		background: var(--bg-card); cursor: pointer; color: var(--text-2);
		transition: border-color .15s, color .15s, background .15s;
	}
	.rots-toolbar button:hover { border-color: var(--border-strong); color: var(--text); }
	.rots-toolbar button.copied { background: var(--accent-bg); border-color: var(--accent-bd); color: var(--accent-dark); }
	.rots-toolbar button.failed { background: #fff0f0; border-color: #ffaaaa; color: #c00; }
	.rots-toolbar button.btn-danger { color: #c00; border-color: #ffaaaa; }
	.rots-toolbar button.btn-danger:hover { background: #fff0f0; }

	.select-bar {
		display: flex;
		align-items: center;
		gap: 12px;
		margin-bottom: 12px;
		padding: 6px 8px;
		background: var(--bg-soft);
		border-radius: 6px;
	}
	.select-all {
		display: flex;
		align-items: center;
		gap: 6px;
		font: 500 12px/1 var(--font-mono);
		color: var(--text-2);
		cursor: pointer;
	}
	.select-count {
		font: 400 11px/1 var(--font-mono);
		color: var(--text-faint);
		margin-left: auto;
	}

	.rots-date-header {
		font-family: var(--font-sans); font-size: 1.1rem; font-weight: 600;
		color: var(--text-3); padding: 16px 0 8px; border-bottom: 1px solid var(--border); margin-bottom: 12px;
	}

	.rots-entry {
		display: flex;
		gap: 8px;
		align-items: flex-start;
		margin-bottom: 0;
	}
	.rots-check-wrap {
		padding-top: 18px;
		flex-shrink: 0;
		cursor: pointer;
	}
	.rots-check {
		width: 15px;
		height: 15px;
		cursor: pointer;
		accent-color: var(--accent);
	}
	.rots-cluster {
		flex: 1;
		min-width: 0;
		transition: opacity .15s;
	}
	.rots-cluster.dimmed { opacity: 0.45; }

	.empty { color: var(--text-faint); padding: 48px 0; text-align: center; }
</style>
