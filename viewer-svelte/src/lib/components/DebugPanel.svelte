<script lang="ts">
	import type { DebugRejected } from '$lib/api/types';
	let { items }: { items: DebugRejected[] } = $props();
	let expanded = $state(false);
</script>

{#if items && items.length > 0}
	<button class="debug-toggle" onclick={() => expanded = !expanded}>
		{expanded ? '▲' : '▼'} Debug ({items.length} rejected)
	</button>
	{#if expanded}
		<div class="debug-list">
			{#each items as item}
				<div class="debug-item">
					<span class="debug-reason">{item.reason}</span>
					<span class="debug-title">{item.title}</span>
				</div>
			{/each}
		</div>
	{/if}
{/if}

<style>
	.debug-toggle {
		background: none; border: 1px solid #e0e0e0; border-radius: 8px;
		padding: 8px 16px; cursor: pointer; font-size: 0.78rem; color: #888;
		margin-top: 24px; width: 100%;
	}
	.debug-toggle:hover { border-color: #ccc; color: #555; }
	.debug-list { margin-top: 8px; }
	.debug-item { display: flex; gap: 8px; padding: 4px 0; font-size: 0.78rem; color: #888; }
	.debug-reason { font-weight: 600; color: #c4463a; background: #fef2f2; padding: 1px 6px; border-radius: 3px; white-space: nowrap; }
	.debug-title { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
