<script lang="ts">
	let { topic }: { topic: string } = $props();
</script>

<span class="topic-tag">{topic}</span>

<style>
	.topic-tag {
		font-size: 0.65rem;
		background: var(--accent-pill-bg);
		color: var(--accent-dark);
		padding: 2px 7px;
		border-radius: 4px;
		white-space: nowrap;
		font-weight: 500;
	}
</style>
