<script lang="ts">
	import type { SocialLink } from '$lib/api/types';
	let { links }: { links: SocialLink[] } = $props();
</script>

{#if links && links.length > 0}
	<div class="social-links">
		{#each links as link}
			<a class="social-link social-link-{link.source}" href={link.url} target="_blank" rel="noopener">
				{#if link.source === 'bluesky'}🦋 {link.handle}{:else}↗ {link.handle}{/if}
			</a>
		{/each}
	</div>
{/if}

<style>
	.social-links { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 6px; }
	.social-link {
		font-size: 0.7rem;
		padding: 2px 8px;
		border: 1px solid #ddd;
		border-radius: 12px;
		color: #666;
		text-decoration: none;
		white-space: nowrap;
	}
	.social-link:hover { border-color: #999; color: #333; }
	.social-link-bluesky { color: #0085ff; border-color: #b8daff; }
	.social-link-bluesky:hover { border-color: #0085ff; }
</style>
