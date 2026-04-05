import type { DailyDigest, PipelineStatus, FeedRunSummary } from './types';

/** API base URL — empty string for same-origin, or full URL for cross-origin (e.g. Cloudflare Pages → Fly.io) */
const API_BASE = import.meta.env.VITE_API_BASE_URL || '';

const digestCache = new Map<string, DailyDigest>();

export async function fetchDates(): Promise<string[]> {
	const res = await fetch(`${API_BASE}/api/dates`);
	if (!res.ok) return [];
	return res.json();
}

export async function fetchDigest(date: string): Promise<DailyDigest | null> {
	if (digestCache.has(date)) return digestCache.get(date)!;
	const res = await fetch(`${API_BASE}/api/daily/${date}`);
	if (!res.ok) return null;
	const data: DailyDigest = await res.json();
	digestCache.set(date, data);
	return data;
}

export async function fetchPipeline(): Promise<PipelineStatus | null> {
	const res = await fetch(`${API_BASE}/api/pipeline`);
	if (!res.ok) return null;
	return res.json();
}

export async function fetchFeedRuns(): Promise<FeedRunSummary[]> {
	const res = await fetch(`${API_BASE}/api/feed-runs`);
	if (!res.ok) return [];
	return res.json();
}
