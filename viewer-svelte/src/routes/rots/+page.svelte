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
			lines.push(`## ${fmtDigestDate(entry.date)}\n`);
			for (const c of entry.clusters) {
				lines.push(`### ${c.title}\n`);
				lines.push(c.summary + '\n');
			}
		}
		await navigator.clipboard.writeText(lines.join('\n'));
	}

	function clearAll() {
		if (confirm('Clear all ROTS bookmarks?')) {
			clearAllBookmarks();
		}
	}
</script>

<div class="rots">
	<div class="rots-toolbar">
		<button onclick={copyMarkdown}>Copy as Markdown</button>
		<button onclick={clearAll}>Clear all</button>
	</div>

	{#if loading}
		<div class="empty">Loading...</div>
	{:else if entries.length === 0}
		<div class="empty">No bookmarked clusters yet. Click ☆ on any cluster to add it here.</div>
	{:else}
		{#each entries as entry}
			<div class="rots-date-header">{fmtDigestDate(entry.date)}</div>
			{#each entry.clusters as cluster}
				<Cluster {cluster} bookmarked={true} />
			{/each}
		{/each}
	{/if}
</div>

<style>
	.rots { padding: 32px; flex: 1; overflow-y: auto; max-width: 900px; }
	.rots-toolbar { display: flex; gap: 8px; margin-bottom: 24px; }
	.rots-toolbar button {
		font-size: 0.78rem; padding: 6px 14px; border: 1px solid #ddd; border-radius: 6px;
		background: #fff; cursor: pointer; color: #555;
	}
	.rots-toolbar button:hover { border-color: #999; }
	.rots-date-header {
		font-family: 'Newsreader', serif; font-size: 1.1rem; font-weight: 600;
		color: #555; padding: 16px 0 8px; border-bottom: 1px solid #eee; margin-bottom: 12px;
	}
	.empty { color: #999; padding: 48px 0; text-align: center; }
</style>
