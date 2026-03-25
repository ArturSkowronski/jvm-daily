<script lang="ts">
	let { topic }: { topic: string } = $props();
</script>

<span class="topic-tag">{topic}</span>

<style>
	.topic-tag {
		font-size: 0.75rem;
		background: #ecfdf5;
		color: #065f46;
		padding: 2px 8px;
		border-radius: 4px;
		white-space: nowrap;
	}
</style>
