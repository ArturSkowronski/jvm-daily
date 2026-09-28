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
	.item-actions { display: flex; flex-direction: column; gap: 4px; flex-shrink: 0; }
	.action-btn {
		position: relative;
		appearance: none;
		width: 26px; height: 26px;
		border: 1px solid var(--border);
		border-radius: 5px;
		background: transparent;
		color: var(--text-faint);
		cursor: pointer;
		font-size: 12px;
		display: inline-flex; align-items: center; justify-content: center;
		transition: all .12s;
	}
	.action-btn:hover { border-color: var(--border-strong); color: var(--text-2); background: var(--bg-soft); }
	.tick-btn.on { background: var(--accent); border-color: var(--accent); color: #fff; }
	.later-btn.on { background: var(--src-bsky); border-color: var(--src-bsky); color: #fff; }
	.bookmark-btn.on { background: var(--rots); border-color: var(--rots); color: #fff; }
	.action-btn.on:hover { opacity: 0.92; }
	svg { width: 12px; height: 12px; }
	kbd {
		display: none; position: absolute; right: -15px; top: 50%; transform: translateY(-50%);
		font: 500 9.5px/1 var(--font-mono); color: var(--text-faint);
	}
	.focused kbd { display: inline; }
	.inline { flex-direction: row; }
	.inline kbd, .inline.focused kbd { display: none; }

	@media (max-width: 760px) {
		.item-actions { flex-direction: row; }
		.focused kbd { display: none; }
	}
</style>
