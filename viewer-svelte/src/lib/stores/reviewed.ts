import { writable } from 'svelte/store';
import { browser } from '$app/environment';

const STORAGE_KEY = 'jvm-daily-reviewed';

function load(): Set<string> {
	if (!browser) return new Set();
	try {
		return new Set(JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]'));
	} catch {
		return new Set();
	}
}

/** Dates whose every item has been handled — skipped when auto-advancing between days. */
export const reviewed = writable<Set<string>>(load());

reviewed.subscribe((s) => {
	if (!browser) return;
	try {
		localStorage.setItem(STORAGE_KEY, JSON.stringify([...s]));
	} catch {
		/* storage unavailable */
	}
});

export function setReviewed(date: string, on: boolean) {
	reviewed.update((s) => {
		if (s.has(date) === on) return s;
		const next = new Set(s);
		if (on) next.add(date);
		else next.delete(date);
		return next;
	});
}
