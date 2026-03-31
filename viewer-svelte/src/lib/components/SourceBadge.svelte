<script lang="ts">
	let { sourceType }: { sourceType: string } = $props();
	const labels: Record<string, string> = {
		rss: 'RSS',
		reddit: 'Reddit',
		bluesky: 'Bluesky',
		github_trending: 'GitHub',
		github_release: 'GitHub',
		openjdk_mail: 'OpenJDK',
		hackernews: 'HN',
		jep: 'JEP',
		markdown_file: 'Local'
	};
</script>

<span class="source-badge source-{sourceType}">{labels[sourceType] || sourceType}</span>

<style>
	.source-badge {
		font-size: 0.6rem;
		font-weight: 600;
		text-transform: uppercase;
		letter-spacing: 0.05em;
		padding: 2px 6px;
		border-radius: 3px;
		background: var(--badge-count-bg);
		color: var(--badge-count-text);
	}
	.source-reddit { color: #ff4500; background: #fff0eb; }
	.source-bluesky { color: #0085ff; background: #e8f4ff; }
	.source-github_trending, .source-github_release { color: #555; background: var(--badge-count-bg); }
	.source-openjdk_mail { color: #e76f00; background: #fff5eb; }
</style>
