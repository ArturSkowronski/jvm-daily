import type { DigestArticle } from '$lib/api/types';

/**
 * Merge articles that share the same title (e.g., multiple Bluesky accounts
 * sharing the same blog post via different URLs). Combines social links and
 * sums engagement scores.
 */
export function mergeByTitle(articles: DigestArticle[]): DigestArticle[] {
	const groups = new Map<string, DigestArticle[]>();
	for (const a of articles) {
		const key = (a.title || '').trim().toLowerCase();
		if (!groups.has(key)) groups.set(key, []);
		groups.get(key)!.push(a);
	}
	return [...groups.values()].map((group) => {
		if (group.length === 1) return group[0];
		// Pick the article with the longest summary as primary
		group.sort((a, b) => (b.summary || '').length - (a.summary || '').length);
		const primary = { ...group[0] };
		// Merge socialLinks from all articles
		const allLinks = group.flatMap((a) => a.socialLinks || []);
		const seen = new Set<string>();
		primary.socialLinks = allLinks.filter((l) => {
			const k = l.url || l.handle;
			if (seen.has(k)) return false;
			seen.add(k);
			return true;
		});
		primary.engagementScore = group.reduce((s, a) => s + (a.engagementScore || 0), 0);
		return primary;
	});
}

export function isSocialPost(a: DigestArticle): boolean {
	return a.sourceType === 'bluesky' && (a.url || '').includes('bsky.app');
}
