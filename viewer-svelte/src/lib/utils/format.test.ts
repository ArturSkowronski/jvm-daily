import { describe, it, expect } from 'vitest';
import { getDomain, faviconUrl } from './format';

describe('getDomain', () => {
	it('extracts hostname from regular URL', () => {
		expect(getDomain('https://blog.jetbrains.com/idea/post')).toBe('blog.jetbrains.com');
	});

	it('strips www prefix', () => {
		expect(getDomain('https://www.baeldung.com/article')).toBe('baeldung.com');
	});

	it('resolves feedblitz proxy to actual publisher', () => {
		expect(
			getDomain(
				'https://feeds.feedblitz.com/~/950486324/0/baeldung~Introduction-to-HiveMQ-MQTT-Client'
			)
		).toBe('baeldung.com');
	});

	it('resolves feedblitz with different publisher slugs', () => {
		expect(
			getDomain('https://feeds.feedblitz.com/~/123/0/javacodegeeks~Some-Article')
		).toBe('javacodegeeks.com');
	});

	it('returns empty string for invalid URL', () => {
		expect(getDomain('')).toBe('');
		expect(getDomain('not-a-url')).toBe('');
	});
});

describe('faviconUrl', () => {
	it('returns Google favicon URL for resolved domain', () => {
		const url = faviconUrl('https://feeds.feedblitz.com/~/123/0/baeldung~Article');
		expect(url).toBe('https://www.google.com/s2/favicons?sz=32&domain=baeldung.com');
	});

	it('returns empty string for invalid URL', () => {
		expect(faviconUrl('')).toBe('');
	});
});
