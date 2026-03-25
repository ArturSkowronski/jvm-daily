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
	.social-links { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px; }
	.social-link {
		font-size: 0.8rem;
		padding: 3px 10px;
		border: 1px solid #ddd;
		border-radius: 14px;
		color: #666;
		text-decoration: none;
		white-space: nowrap;
		transition: border-color 0.15s, color 0.15s;
	}
	.social-link:hover { border-color: #00a64e; color: #00a64e; }
	.social-link-bluesky { color: #0085ff; border-color: #b8daff; }
	.social-link-bluesky:hover { border-color: #0085ff; color: #0070d6; }
</style>
