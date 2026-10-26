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
		width: 160px;
		flex-shrink: 0;
		padding: 24px 14px;
		border-right: 1px solid var(--border);
		overflow-y: auto;
		background: var(--bg);
	}
	.sidebar-title {
		font-size: 0.62rem;
		text-transform: uppercase;
		letter-spacing: 0.1em;
		color: var(--text-muted);
		margin: 0 0 14px;
		font-weight: 600;
	}
	.date-btn {
		display: block;
		width: 100%;
		padding: 8px 10px;
		margin-bottom: 3px;
		background: none;
		border: none;
		border-radius: 6px;
		cursor: pointer;
		font-family: 'Inter', system-ui, sans-serif;
		font-size: 0.8rem;
		color: var(--text-secondary);
		text-align: left;
		transition: background 0.15s, color 0.15s;
		font-weight: 400;
	}
	.date-btn:hover { background: var(--accent-pill-bg); color: var(--accent); }
	.date-btn.active { background: var(--accent); color: #fff; font-weight: 600; }

	@media (max-width: 768px) {
		.sidebar {
			width: 100%;
			display: flex;
			flex-wrap: nowrap;
			align-items: center;
			overflow-x: auto;
			overflow-y: hidden;
			border-right: none;
			border-bottom: 1px solid var(--border);
			padding: 10px 16px;
			gap: 5px;
			-webkit-overflow-scrolling: touch;
		}
		.sidebar-title { display: none; }
		.date-btn { white-space: nowrap; width: auto; padding: 5px 12px; margin-bottom: 0; font-size: 0.78rem; flex-shrink: 0; }
	}
</style>
