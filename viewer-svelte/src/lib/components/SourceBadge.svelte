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
	const variants: Record<string, string> = {
		rss: 'rss',
		reddit: 'reddit',
		bluesky: 'bsky',
		github_trending: 'gh',
		github_release: 'gh',
		openjdk_mail: 'jdk',
		hackernews: 'hn',
		jep: 'jep'
	};
	const variant = $derived(variants[sourceType] ?? '');
</script>

<span class="src {variant}">{labels[sourceType] || sourceType}</span>

<style>
	.src {
		font: 600 9px/1 var(--font-mono);
		text-transform: uppercase;
		letter-spacing: 0.08em;
		padding: 3px 6px 4px;
		border-radius: 3px;
		background: var(--bg-soft);
		color: var(--text-3);
		white-space: nowrap;
	}
	.src.reddit { background: var(--src-reddit-bg); color: var(--src-reddit); }
	.src.bsky   { background: var(--src-bsky-bg);   color: var(--src-bsky); }
	.src.hn     { background: var(--src-hn-bg);     color: var(--src-hn); }
	.src.gh     { background: var(--src-gh-bg);     color: var(--src-gh); }
	.src.jdk    { background: var(--src-jdk-bg);    color: var(--src-jdk); }
	.src.rss    { background: var(--src-rss-bg);    color: var(--src-rss); }
	.src.jep    { background: var(--accent-bg);     color: var(--accent-dark); }
	:global([data-theme="dark"]) .src.gh { color: #c5c2b6; }
</style>
