<script lang="ts">
	let { onClose }: { onClose: () => void } = $props();

	const groups: [string, [string, string][]][] = [
		['Move', [
			['↓ / j', 'Next cluster'],
			['↑ / k', 'Previous cluster'],
			['→ / n', 'Older day'],
			['← / p', 'Newer day'],
			['⇧→ / N', 'Next unreviewed day']
		]],
		['Act (moves to the next cluster)', [
			['e / x', 'Done'],
			['s', 'Read later'],
			['r', 'Rest of the Story'],
			['⇧E', 'Mark the whole day reviewed'],
			['u / z', 'Undo']
		]],
		['View', [
			['o / Enter', 'Open the top article (changes nothing)'],
			['c', 'Compact: summaries only on the focused cluster'],
			['?', 'This help'],
			['Esc', 'Close']
		]]
	];
</script>

<!-- svelte-ignore a11y_click_events_have_key_events, a11y_no_static_element_interactions -->
<div class="overlay" onclick={onClose}>
	<!-- svelte-ignore a11y_click_events_have_key_events, a11y_no_static_element_interactions -->
	<div class="help" role="dialog" aria-label="Keyboard shortcuts" tabindex="-1" onclick={(e) => e.stopPropagation()}>
		<h2>Keyboard</h2>
		<p class="lead">
			Each day is an inbox. Done, Read later and ROTS all take a cluster out of it. When the day is empty
			you jump to the next day you haven't reviewed yet.
		</p>
		{#each groups as [title, keys]}
			<h3>{title}</h3>
			<dl>
				{#each keys as [key, desc]}
					<dt><kbd>{key}</kbd></dt>
					<dd>{desc}</dd>
				{/each}
			</dl>
		{/each}
		<button class="close" onclick={onClose}>Close <kbd>Esc</kbd></button>
	</div>
</div>

<style>
	.overlay {
		position: fixed; inset: 0; background: rgba(0, 0, 0, 0.35); z-index: 40;
		display: flex; align-items: center; justify-content: center; padding: 16px;
	}
	.help {
		background: #fff; border-radius: 10px; padding: 24px 28px; max-width: 480px; width: 100%;
		max-height: calc(100vh - 32px); overflow-y: auto; box-shadow: 0 10px 40px rgba(0, 0, 0, 0.2);
	}
	h2 { margin: 0 0 8px; font-size: 1.3rem; }
	h3 {
		font-size: 0.75rem; text-transform: uppercase; letter-spacing: 0.1em; color: #868787;
		margin: 18px 0 6px;
	}
	.lead { font-size: 0.9rem; color: #555; margin: 0; line-height: 1.5; }
	dl { display: grid; grid-template-columns: 110px 1fr; gap: 4px 12px; margin: 0; font-size: 0.9rem; }
	dt, dd { margin: 0; }
	kbd {
		font-family: ui-monospace, monospace; font-size: 0.75rem;
		padding: 1px 5px; border: 1px solid #ccc; border-radius: 3px; background: #f7f7f7;
	}
	.close {
		margin-top: 20px; background: #fff; border: 1px solid #ddd; border-radius: 6px;
		padding: 6px 12px; font-family: inherit; cursor: pointer;
	}
</style>
