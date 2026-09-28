import { writable, get } from 'svelte/store';
import { browser } from '$app/environment';

/** Per-date list of cluster keys persisted in localStorage (used by ROTS and Read later). */
export type SavedState = Record<string, string[]>;

export function createSavedList(storageKey: string) {
	function load(): SavedState {
		if (!browser) return {};
		try {
			return JSON.parse(localStorage.getItem(storageKey) || '{}');
		} catch {
			return {};
		}
	}

	function save(data: SavedState) {
		if (!browser) return;
		const pruned: SavedState = {};
		for (const [date, keys] of Object.entries(data)) {
			if (keys.length > 0) pruned[date] = keys;
		}
		try {
			localStorage.setItem(storageKey, JSON.stringify(pruned));
		} catch {
			/* storage unavailable */
		}
	}

	const store = writable<SavedState>(load());
	store.subscribe(save);

	function set(date: string, key: string, on: boolean) {
		store.update((all) => {
			const keys = (all[date] || []).filter((k) => k !== key);
			if (on) keys.push(key);
			return { ...all, [date]: keys };
		});
	}

	return {
		subscribe: store.subscribe,
		update: store.update,
		set,
		toggle(date: string, key: string) {
			set(date, key, !has(get(store), date, key));
		},
		clear() {
			store.set({});
		}
	};
}

export function has(state: SavedState, date: string, key: string): boolean {
	return (state[date] || []).includes(key);
}

export function count(state: SavedState): number {
	return Object.values(state).reduce((s, keys) => s + keys.length, 0);
}
