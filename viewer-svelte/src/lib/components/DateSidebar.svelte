<script lang="ts">
	import { fmtShortDate } from '$lib/utils/format';

	let {
		dates,
		currentDate,
		onSelect
	}: {
		dates: string[];
		currentDate: string;
		onSelect: (date: string) => void;
	} = $props();
</script>

<aside class="sidebar">
	<h2 class="sidebar-title">Archive</h2>
	{#each dates as date}
		<button
			class="date-btn"
			class:active={date === currentDate}
			onclick={() => onSelect(date)}
		>
			{fmtShortDate(date)}
		</button>
	{/each}
</aside>

<style>
	.sidebar {
		width: 120px; flex-shrink: 0; padding: 20px 16px;
		border-right: 1px solid #eee; overflow-y: auto;
	}
	.sidebar-title { font-size: 0.7rem; text-transform: uppercase; letter-spacing: 0.1em; color: #999; margin: 0 0 12px; }
	.date-btn {
		display: block; width: 100%; padding: 8px 10px; margin-bottom: 4px;
		background: none; border: none; border-radius: 6px; cursor: pointer;
		font-size: 0.82rem; color: #555; text-align: left;
	}
	.date-btn:hover { background: #f5f5f5; }
	.date-btn.active { background: #1a1a1a; color: #fff; font-weight: 600; }
</style>
