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

// Store keyed by date
const dismissed = writable<Record<string, Set<string>>>({});

export function getDismissed(date: string): Set<string> {
	const current = get(dismissed);
	if (!current[date]) {
		current[date] = loadDismissed(date);
		dismissed.set(current);
	}
	return current[date];
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
