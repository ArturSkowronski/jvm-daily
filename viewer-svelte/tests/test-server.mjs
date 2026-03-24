/**
 * Test server for SvelteKit viewer tests.
 * Serves the SvelteKit build output + mock API from fixtures.
 */
import { createServer } from 'http';
import { readFileSync, readdirSync, existsSync, statSync } from 'fs';
import { join, resolve } from 'path';

const PORT = 18889;
const BUILD_DIR = resolve(import.meta.dirname, '..', 'build');
const FIXTURE_DIR = resolve(import.meta.dirname, 'fixtures');

function contentType(path) {
	if (path.endsWith('.js')) return 'application/javascript';
	if (path.endsWith('.css')) return 'text/css';
	if (path.endsWith('.html')) return 'text/html';
	if (path.endsWith('.json')) return 'application/json';
	if (path.endsWith('.svg')) return 'image/svg+xml';
	if (path.endsWith('.png')) return 'image/png';
	return 'application/octet-stream';
}

const server = createServer((req, res) => {
	const url = new URL(req.url, `http://localhost:${PORT}`);
	const path = url.pathname;

	// API endpoints (mock data from fixtures)
	if (req.method === 'GET' && path === '/api/dates') {
		const dates = readdirSync(FIXTURE_DIR)
			.filter(f => f.startsWith('daily-') && f.endsWith('.json'))
			.map(f => f.replace('daily-', '').replace('.json', ''))
			.sort().reverse();
		res.writeHead(200, { 'Content-Type': 'application/json' });
		res.end(JSON.stringify(dates));
		return;
	}

	if (req.method === 'GET' && path === '/api/files') {
		const files = readdirSync(FIXTURE_DIR)
			.filter(f => f.startsWith('jvm-daily-') && f.endsWith('.md'))
			.sort().reverse();
		res.writeHead(200, { 'Content-Type': 'application/json' });
		res.end(JSON.stringify(files));
		return;
	}

	const dailyMatch = path.match(/^\/api\/daily\/(\d{4}-\d{2}-\d{2})$/);
	if (req.method === 'GET' && dailyMatch) {
		const file = join(FIXTURE_DIR, `daily-${dailyMatch[1]}.json`);
		if (existsSync(file)) {
			res.writeHead(200, { 'Content-Type': 'application/json' });
			res.end(readFileSync(file));
		} else {
			res.writeHead(404, { 'Content-Type': 'application/json' });
			res.end('{"error":"not found"}');
		}
		return;
	}

	if (req.method === 'GET' && path === '/api/pipeline') {
		res.writeHead(200, { 'Content-Type': 'application/json' });
		res.end(JSON.stringify({
			stats: { succeeded: 42, failed: 1, scheduled: 1 },
			recentJobs: [
				{ id: 'j1', state: 'SUCCEEDED', createdAt: '2026-03-23T06:00:00Z', updatedAt: '2026-03-23T06:05:00Z' },
				{ id: 'j2', state: 'FAILED', createdAt: '2026-03-22T06:00:00Z', updatedAt: '2026-03-22T06:03:00Z' },
			],
		}));
		return;
	}

	// Static files from SvelteKit build
	const requestPath = path === '/' ? '/index.html' : path;
	const filePath = join(BUILD_DIR, requestPath);

	if (existsSync(filePath) && statSync(filePath).isFile()) {
		res.writeHead(200, { 'Content-Type': contentType(filePath) });
		res.end(readFileSync(filePath));
		return;
	}

	// SPA fallback
	const indexPath = join(BUILD_DIR, 'index.html');
	if (existsSync(indexPath)) {
		res.writeHead(200, { 'Content-Type': 'text/html' });
		res.end(readFileSync(indexPath));
		return;
	}

	res.writeHead(404);
	res.end('Not found');
});

server.listen(PORT, () => console.log(`SvelteKit test server on :${PORT}`));
