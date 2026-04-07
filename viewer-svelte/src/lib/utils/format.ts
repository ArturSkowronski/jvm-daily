export function fmtDigestDate(dateStr: string): string {
	const d = new Date(dateStr + 'T00:00:00');
	return d.toLocaleDateString('en-US', {
		weekday: 'long',
		year: 'numeric',
		month: 'long',
		day: 'numeric'
	});
}

export function fmtShortDate(dateStr: string): string {
	const d = new Date(dateStr + 'T00:00:00');
	return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
}

export function fmtTimestamp(iso: string): string {
	const d = new Date(iso);
	return d.toLocaleDateString('en-US', {
		month: 'short',
		day: 'numeric',
		hour: '2-digit',
		minute: '2-digit'
	});
}

export function fmtDuration(start: string, end: string): string {
	if (!start || !end) return '';
	const sec = Math.round((new Date(end).getTime() - new Date(start).getTime()) / 1000);
	if (sec < 60) return sec + 's';
	return Math.floor(sec / 60) + 'm ' + (sec % 60) + 's';
}

/** Known feed proxy domains → regex to extract the real publisher slug from the URL path. */
const FEED_PROXIES: Record<string, RegExp> = {
	'feeds.feedblitz.com': /\/0\/(\w+)~/
};

/**
 * Resolve the display domain for an article URL.
 * For known feed proxies (e.g. feedblitz), extracts the actual publisher domain.
 */
export function getDomain(url: string): string {
	try {
		const u = new URL(url);
		const host = u.hostname.replace('www.', '');
		const proxy = FEED_PROXIES[host];
		if (proxy) {
			const m = u.pathname.match(proxy);
			if (m) return `${m[1]}.com`;
		}
		return host;
	} catch {
		return '';
	}
}

export function faviconUrl(url: string): string {
	const domain = getDomain(url);
	if (!domain) return '';
	return `https://www.google.com/s2/favicons?sz=32&domain=${domain}`;
}
