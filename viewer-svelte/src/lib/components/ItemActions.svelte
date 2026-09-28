<script lang="ts">
	let {
		done = false,
		later = false,
		rots = false,
		focused = false,
		inline = false,
		onDone,
		onLater,
		onRots
	}: {
		done?: boolean;
		later?: boolean;
		rots?: boolean;
		focused?: boolean;
		inline?: boolean;
		onDone?: () => void;
		onLater?: () => void;
		onRots?: () => void;
	} = $props();
</script>

<div class="item-actions" class:focused class:inline>
	{#if onDone}
		<button class="action-btn tick-btn" class:on={done} title="Done (e)" aria-label="Done" onclick={onDone}>
			✓<kbd>e</kbd>
		</button>
	{/if}
	{#if onLater}
		<button class="action-btn later-btn" class:on={later} title="Read later (s)" aria-label="Read later" onclick={onLater}>
			<svg viewBox="0 0 24 24" width="15" height="15" aria-hidden="true"><path d="M6 3h12v18l-6-4.5L6 21z" fill={later ? 'currentColor' : 'none'} stroke="currentColor" stroke-width="2" stroke-linejoin="round" /></svg><kbd>s</kbd>
		</button>
	{/if}
	{#if onRots}
		<button class="action-btn bookmark-btn" class:on={rots} title="Rest of the Story (r)" aria-label="Rest of the Story" onclick={onRots}>
			{rots ? '★' : '☆'}<kbd>r</kbd>
		</button>
	{/if}
</div>

<style>
	.item-actions { display: flex; flex-direction: column; gap: 6px; flex-shrink: 0; }
	.action-btn {
		position: relative;
		background: #fff; border: 1px solid #ddd; border-radius: 50%;
		width: 34px; height: 34px; cursor: pointer; font-size: 1rem; color: #555;
		display: flex; align-items: center; justify-content: center;
		transition: border-color 0.15s, background 0.15s, color 0.15s;
	}
	.action-btn:hover { border-color: #00a64e; color: #00a64e; }
	.tick-btn.on { background: #00a64e; border-color: #00a64e; color: #fff; }
	.later-btn.on { background: #2563eb; border-color: #2563eb; color: #fff; }
	.bookmark-btn.on { background: #f59e0b; border-color: #f59e0b; color: #fff; }
	kbd {
		display: none; position: absolute; right: -18px; top: 50%; transform: translateY(-50%);
		font-family: ui-monospace, monospace; font-size: 0.65rem; color: #999;
	}
	.focused kbd { display: inline; }
	.inline { flex-direction: row; gap: 4px; }
	.inline .action-btn { width: 28px; height: 28px; font-size: 0.85rem; }
	.inline kbd, .inline.focused kbd { display: none; }

	@media (max-width: 768px) {
		.item-actions { flex-direction: row; }
		.focused kbd { display: none; }
	}
</style>
