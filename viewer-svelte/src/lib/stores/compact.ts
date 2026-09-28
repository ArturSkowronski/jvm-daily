import { writable } from 'svelte/store';
import { browser } from '$app/environment';

const STORAGE_KEY = 'jvm-daily-compact';

function load(): boolean {
	if (!browser) return false;
	try {
		return localStorage.getItem(STORAGE_KEY) === '1';
	} catch {
		return false;
	}
}

/** Compact mode: only the focused cluster shows its synthesis and articles. */
export const compact = writable<boolean>(load());

compact.subscribe((on) => {
	if (!browser) return;
	document.body.classList.toggle('compact', on);
	try {
		localStorage.setItem(STORAGE_KEY, on ? '1' : '0');
	} catch {
		/* storage unavailable */
	}
});
