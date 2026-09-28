/**
 * Pure review-flow helpers (no Svelte / browser imports, unit-tested with node --test).
 *
 * Model borrowed from newsletter-ingest: every cluster of a day is an inbox item.
 * An item leaves the inbox when it is marked done, saved for later or put in ROTS.
 * Dates are listed newest first; "forward" in the reading queue means older.
 */

export interface Marks {
	done: boolean;
	later: boolean;
	rots: boolean;
}

export const NO_MARKS: Marks = { done: false, later: false, rots: false };

export function isHandled(m: Marks): boolean {
	return m.done || m.later || m.rots;
}

/**
 * The item to focus after `from` left the inbox: the next still-pending item after it,
 * otherwise the nearest pending one before it, otherwise null.
 */
export function nextPending(order: string[], from: string, isPending: (key: string) => boolean): string | null {
	const i = order.indexOf(from);
	if (i < 0) return order.find(isPending) ?? null;
	for (let k = i + 1; k < order.length; k++) if (isPending(order[k])) return order[k];
	for (let k = i - 1; k >= 0; k--) if (isPending(order[k])) return order[k];
	return null;
}

/** Move focus by `dir` within `order`, clamped to the ends. */
export function stepFocus(order: string[], current: string | null, dir: 1 | -1): string | null {
	if (order.length === 0) return null;
	const i = current ? order.indexOf(current) : -1;
	if (i < 0) return order[0];
	return order[Math.max(0, Math.min(order.length - 1, i + dir))];
}

/** Adjacent date in the list (dir 1 = older, -1 = newer), or null at the end. */
export function adjacentDate(dates: string[], current: string, dir: 1 | -1): string | null {
	const i = dates.indexOf(current);
	if (i < 0) return null;
	return dates[i + dir] ?? null;
}

/**
 * Next day that has not been reviewed yet, continuing the reading queue from `current`:
 * older days first, then newer ones. Null when every other day is reviewed.
 */
export function nextUnreviewedDate(dates: string[], reviewed: Set<string>, current: string): string | null {
	const i = dates.indexOf(current);
	for (let k = i + 1; k < dates.length; k++) if (!reviewed.has(dates[k])) return dates[k];
	for (let k = i - 1; k >= 0; k--) if (!reviewed.has(dates[k])) return dates[k];
	return null;
}

/** Day to open on entry: the newest not-yet-reviewed day, or the newest day if all are reviewed. */
export function startDate(dates: string[], reviewed: Set<string>): string | null {
	if (dates.length === 0) return null;
	return dates.find((d) => !reviewed.has(d)) ?? dates[0];
}
