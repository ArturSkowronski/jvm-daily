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

	const MONTHS = ['January','February','March','April','May','June','July','August','September','October','November','December'];

	type Item = { kind: 'month'; label: string } | { kind: 'date'; date: string };

	const items = $derived.by(() => {
		const out: Item[] = [];
		let lastKey = '';
		for (const date of dates) {
			const d = new Date(date);
			const key = `${d.getFullYear()}-${d.getMonth()}`;
			if (key !== lastKey && lastKey !== '') {
				out.push({ kind: 'month', label: MONTHS[d.getMonth()] });
			}
			out.push({ kind: 'date', date });
			lastKey = key;
		}
		return out;
	});
</script>

<aside class="sidebar">
	<div class="sidebar-title">Archive</div>
	{#each items as item}
		{#if item.kind === 'month'}
			<div class="sidebar-month">{item.label}</div>
		{:else}
			<button
				class="date-btn"
				class:active={item.date === currentDate}
				onclick={() => onSelect(item.date)}
			>
				<span>{fmtShortDate(item.date)}</span>
			</button>
		{/if}
	{/each}
</aside>

<style>
	.sidebar {
		width: 168px;
		flex-shrink: 0;
		padding: 22px 12px 40px 22px;
		border-right: 1px solid var(--border);
		overflow-y: auto;
		background: var(--bg);
		position: sticky;
		top: 50px;
		align-self: start;
		max-height: calc(100vh - 50px);
	}
	.sidebar-title {
		font: 600 9.5px/1 var(--font-mono);
		text-transform: uppercase;
		letter-spacing: 0.14em;
		color: var(--text-3);
		margin: 0 0 10px;
		padding-left: 8px;
	}
	.sidebar-month {
		font: 500 9.5px/1 var(--font-mono);
		text-transform: uppercase;
		letter-spacing: 0.1em;
		color: var(--text-faint);
		margin: 14px 0 6px;
		padding-left: 8px;
	}
	.date-btn {
		display: flex;
		align-items: baseline;
		justify-content: space-between;
		gap: 8px;
		width: 100%;
		padding: 6px 10px;
		margin-bottom: 2px;
		background: transparent;
		border: 0;
		border-radius: 5px;
		cursor: pointer;
		font: 400 13.5px/1.4 var(--font-sans);
		color: var(--text-2);
		text-align: left;
		transition: background .12s, color .12s;
	}
	.date-btn:hover { background: var(--bg-soft); color: var(--text); }
	.date-btn.active {
		background: var(--accent);
		color: #fff;
		font-weight: 600;
	}

	@media (max-width: 760px) {
		.sidebar {
			width: 100%;
			display: flex;
			flex-wrap: nowrap;
			align-items: center;
			overflow-x: auto;
			overflow-y: hidden;
			border-right: none;
			border-bottom: 1px solid var(--border);
			padding: 8px 12px;
			gap: 4px;
			position: static;
			max-height: none;
			-webkit-overflow-scrolling: touch;
		}
		.sidebar-title, .sidebar-month { display: none; }
		.date-btn { white-space: nowrap; width: auto; padding: 5px 12px; margin-bottom: 0; flex-shrink: 0; }
	}
</style>
