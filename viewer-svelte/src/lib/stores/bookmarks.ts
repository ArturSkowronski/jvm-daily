import { writable } from 'svelte/store';
import { browser } from '$app/environment';

const STORAGE_KEY = 'jvm-daily-rots';

function loadRots(): Record<string, string[]> {
	if (!browser) return {};
	try {
		return JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}');
	} catch {
		return {};
	}
}

function saveRots(data: Record<string, string[]>) {
	if (!browser) return;
	// Prune empty dates
	const pruned: Record<string, string[]> = {};
	for (const [date, keys] of Object.entries(data)) {
		if (keys.length > 0) pruned[date] = keys;
	}
	localStorage.setItem(STORAGE_KEY, JSON.stringify(pruned));
}

export const bookmarks = writable<Record<string, string[]>>(loadRots());
bookmarks.subscribe(saveRots);

export function toggleBookmark(date: string, clusterKey: string) {
	bookmarks.update((rots) => {
		const keys = rots[date] || [];
		const idx = keys.indexOf(clusterKey);
		if (idx >= 0) {
			keys.splice(idx, 1);
		} else {
			keys.push(clusterKey);
		}
		return { ...rots, [date]: keys };
	});
}

export function isBookmarked(rots: Record<string, string[]>, date: string, key: string): boolean {
	return (rots[date] || []).includes(key);
}

export function clearAllBookmarks() {
	bookmarks.set({});
}

export function totalBookmarkCount(rots: Record<string, string[]>): number {
	return Object.values(rots).reduce((s, keys) => s + keys.length, 0);
}
