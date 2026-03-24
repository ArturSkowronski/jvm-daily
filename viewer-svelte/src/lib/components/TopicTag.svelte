<script lang="ts">
	let { topic }: { topic: string } = $props();
</script>

<span class="topic-tag">{topic}</span>

<style>
	.topic-tag {
		font-size: 0.7rem;
		background: #f1f5f9;
		color: #475569;
		padding: 2px 8px;
		border-radius: 4px;
		white-space: nowrap;
	}
</style>
