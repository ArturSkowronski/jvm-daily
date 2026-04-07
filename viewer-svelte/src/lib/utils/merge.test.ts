import { describe, it, expect } from 'vitest';
import { mergeByTitle } from './merge';
import type { DigestArticle } from '$lib/api/types';

function makeArticle(overrides: Partial<DigestArticle> = {}): DigestArticle {
	return {
		id: '1',
		title: 'Test Article',
		url: 'https://example.com',
		summary: 'A summary',
		topics: [],
		entities: [],
		engagementScore: 10,
		publishedAt: '2026-01-01',
		ingestedAt: '2026-01-01',
		sourceType: 'rss',
		...overrides
	};
}

describe('mergeByTitle', () => {
	it('returns single articles unchanged', () => {
		const articles = [makeArticle({ id: '1', title: 'Unique Title' })];
		const result = mergeByTitle(articles);
		expect(result).toHaveLength(1);
		expect(result[0].title).toBe('Unique Title');
	});

	it('merges articles with identical titles', () => {
		const articles = [
			makeArticle({ id: '1', title: 'Same Title', engagementScore: 5 }),
			makeArticle({ id: '2', title: 'Same Title', engagementScore: 10 })
		];
		const result = mergeByTitle(articles);
		expect(result).toHaveLength(1);
		expect(result[0].engagementScore).toBe(15);
	});

	it('merges articles differing only by pipe-separated site suffix', () => {
		const articles = [
			makeArticle({
				id: '1',
				title: 'Java Annotated Monthly – April 2026 | The IntelliJ IDEA Blog',
				url: 'https://jb.gg/jam',
				summary: 'Short'
			}),
			makeArticle({
				id: '2',
				title: 'Java Annotated Monthly – April 2026',
				url: 'https://blog.jetbrains.com/idea/2026/04/java-annotated-monthly-april-2026/',
				summary: 'A much longer summary with more details about the article'
			})
		];
		const result = mergeByTitle(articles);
		expect(result).toHaveLength(1);
		// Should pick the article with the longest summary
		expect(result[0].summary).toContain('much longer');
	});

	it('does not merge articles with different titles', () => {
		const articles = [
			makeArticle({ id: '1', title: 'First Article' }),
			makeArticle({ id: '2', title: 'Second Article' })
		];
		const result = mergeByTitle(articles);
		expect(result).toHaveLength(2);
	});

	it('does not strip en-dash content from titles (only pipe)', () => {
		const articles = [
			makeArticle({ id: '1', title: 'Java Annotated Monthly – April 2026' }),
			makeArticle({ id: '2', title: 'Java Annotated Monthly – March 2026' })
		];
		const result = mergeByTitle(articles);
		expect(result).toHaveLength(2);
	});

	it('merges social links from all duplicates', () => {
		const articles = [
			makeArticle({
				id: '1',
				title: 'Article | Blog',
				socialLinks: [{ source: 'bluesky', url: 'https://bsky.app/1', handle: '@user1' }]
			}),
			makeArticle({
				id: '2',
				title: 'Article',
				socialLinks: [{ source: 'bluesky', url: 'https://bsky.app/2', handle: '@user2' }]
			})
		];
		const result = mergeByTitle(articles);
		expect(result).toHaveLength(1);
		expect(result[0].socialLinks).toHaveLength(2);
	});
});
