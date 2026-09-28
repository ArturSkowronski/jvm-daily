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
		background: #1a1a1a; color: #fff; padding: 9px 16px; border-radius: 6px;
		font-size: 0.85rem; display: flex; gap: 14px; align-items: center;
		max-width: calc(100vw - 32px); box-shadow: 0 4px 16px rgba(0, 0, 0, 0.2);
	}
	button {
		background: none; border: none; color: #fff; font-weight: 700; text-decoration: underline;
		cursor: pointer; font-family: inherit; font-size: inherit; white-space: nowrap; padding: 0;
	}
</style>
