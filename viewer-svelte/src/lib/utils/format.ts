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

export function getDomain(url: string): string {
	try {
		return new URL(url).hostname.replace('www.', '');
	} catch {
		return '';
	}
}

export function faviconUrl(url: string): string {
	const domain = getDomain(url);
	if (!domain) return '';
	return `https://www.google.com/s2/favicons?sz=32&domain=${domain}`;
}
