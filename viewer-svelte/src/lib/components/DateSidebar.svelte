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
		width: 160px; flex-shrink: 0; padding: 24px 16px;
		border-right: 1px solid #e8e8e8; overflow-y: auto;
	}
	.sidebar-title {
		font-size: 0.75rem; text-transform: uppercase; letter-spacing: 0.12em;
		color: #868787; margin: 0 0 16px; font-weight: 600;
	}
	.date-btn {
		display: block; width: 100%; padding: 9px 12px; margin-bottom: 4px;
		background: none; border: none; border-radius: 6px; cursor: pointer;
		font-family: 'Spectral', Georgia, serif;
		font-size: 0.9rem; color: #555; text-align: left;
		transition: background 0.15s, color 0.15s;
	}
	.date-btn:hover { background: #f0faf4; color: #00a64e; }
	.date-btn.active { background: #00a64e; color: #fff; font-weight: 600; }

	@media (max-width: 768px) {
		.sidebar {
			width: 100%; display: flex; flex-wrap: nowrap; align-items: center;
			overflow-x: auto; overflow-y: hidden;
			border-right: none; border-bottom: 1px solid #e8e8e8;
			padding: 12px 16px; gap: 6px;
			-webkit-overflow-scrolling: touch;
		}
		.sidebar-title { display: none; }
		.date-btn {
			white-space: nowrap; width: auto;
			padding: 6px 14px; margin-bottom: 0;
			font-size: 0.85rem; flex-shrink: 0;
		}
	}
</style>
