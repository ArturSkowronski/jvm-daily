<script lang="ts">
	import { toastState, undo } from '$lib/stores/toast';
</script>

{#if $toastState}
	<div class="toast" role="status">
		<span>{$toastState.msg}</span>
		{#if $toastState.canUndo}
			<button onclick={undo}>Undo (u)</button>
		{/if}
	</div>
{/if}

<style>
	.toast {
		position: fixed; bottom: 22px; left: 50%; transform: translateX(-50%); z-index: 50;
		background: var(--text); color: var(--bg); padding: 9px 16px; border-radius: 6px;
		font: 500 13px/1.4 var(--font-sans); display: flex; gap: 14px; align-items: center;
		max-width: calc(100vw - 32px); box-shadow: var(--shadow-md);
	}
	button {
		background: none; border: none; color: var(--bg); font-weight: 700; text-decoration: underline;
		cursor: pointer; font: inherit; font-weight: 700; white-space: nowrap; padding: 0;
	}
</style>
