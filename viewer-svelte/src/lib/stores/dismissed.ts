import { writable, get } from 'svelte/store';
import { browser } from '$app/environment';

function storageKey(date: string) {
	return `jvm-daily-dismissed-${date}`;
}

function loadDismissed(date: string): Set<string> {
	if (!browser) return new Set();
	try {
		return new Set(JSON.parse(localStorage.getItem(storageKey(date)) || '[]'));
	} catch {
		return new Set();
	}
}

function saveDismissed(date: string, keys: Set<string>) {
	if (!browser) return;
	localStorage.setItem(storageKey(date), JSON.stringify([...keys]));
}

// Store keyed by date — lazily loads from localStorage on first access
const dismissed = writable<Record<string, Set<string>>>({});

/** Ensure dismissed data for a date is loaded from localStorage into the store. */
export function ensureDismissedLoaded(date: string) {
	dismissed.update((state) => {
		if (!state[date]) {
			state[date] = loadDismissed(date);
		}
		return { ...state };
	});
}

export function toggleDismiss(date: string, clusterKey: string) {
	dismissed.update((state) => {
		const keys = state[date] || loadDismissed(date);
		if (keys.has(clusterKey)) {
			keys.delete(clusterKey);
		} else {
			keys.add(clusterKey);
		}
		saveDismissed(date, keys);
		return { ...state, [date]: new Set(keys) };
	});
}

export function isDismissed(state: Record<string, Set<string>>, date: string, key: string): boolean {
	return (state[date] || new Set()).has(key);
}

export { dismissed };
