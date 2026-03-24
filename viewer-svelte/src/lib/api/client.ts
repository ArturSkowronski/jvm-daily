import type { DailyDigest, PipelineStatus } from './types';

const digestCache = new Map<string, DailyDigest>();

export async function fetchDates(): Promise<string[]> {
	const res = await fetch('/api/dates');
	if (!res.ok) return [];
	return res.json();
}

export async function fetchDigest(date: string): Promise<DailyDigest | null> {
	if (digestCache.has(date)) return digestCache.get(date)!;
	const res = await fetch(`/api/daily/${date}`);
	if (!res.ok) return null;
	const data: DailyDigest = await res.json();
	digestCache.set(date, data);
	return data;
}

export async function fetchPipeline(): Promise<PipelineStatus | null> {
	const res = await fetch('/api/pipeline');
	if (!res.ok) return null;
	return res.json();
}
