<script lang="ts">
	let { topic }: { topic: string } = $props();
</script>

<span class="tag">{topic}</span>

<style>
	.tag {
		font: 500 10.5px/1 var(--font-mono);
		color: var(--accent-dark);
		background: var(--accent-bg);
		border: 1px solid var(--accent-bd);
		padding: 2px 7px 3px;
		border-radius: 4px;
		white-space: nowrap;
	}
</style>
