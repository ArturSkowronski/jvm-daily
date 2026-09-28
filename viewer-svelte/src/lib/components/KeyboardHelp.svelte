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
		position: fixed; inset: 0; background: rgba(0, 0, 0, 0.4); z-index: 40;
		display: flex; align-items: center; justify-content: center; padding: 16px;
	}
	.help {
		background: var(--bg-card); color: var(--text); border: 1px solid var(--border);
		border-radius: var(--radius); padding: 22px 26px; max-width: 480px; width: 100%;
		max-height: calc(100vh - 32px); overflow-y: auto; box-shadow: var(--shadow-md);
	}
	h2 { margin: 0 0 8px; font: 700 18px/1.3 var(--font-sans); }
	h3 {
		font: 600 10px/1 var(--font-mono); text-transform: uppercase; letter-spacing: 0.14em;
		color: var(--text-3); margin: 18px 0 8px;
	}
	.lead { font-size: 13.5px; color: var(--text-2); margin: 0; line-height: 1.55; }
	dl { display: grid; grid-template-columns: 110px 1fr; gap: 5px 12px; margin: 0; font-size: 13.5px; }
	dt, dd { margin: 0; }
	dd { color: var(--text-2); }
	kbd {
		font: 500 11px/1 var(--font-mono); padding: 2px 5px;
		border: 1px solid var(--border); border-radius: 3px; background: var(--bg-soft); color: var(--text);
	}
	.close {
		margin-top: 20px; background: transparent; border: 1px solid var(--border); border-radius: 5px;
		padding: 6px 12px; font: 500 12px/1 var(--font-sans); color: var(--text-2); cursor: pointer;
	}
	.close:hover { border-color: var(--border-strong); }
</style>
