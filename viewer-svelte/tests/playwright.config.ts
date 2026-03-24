import { defineConfig } from '@playwright/test';

export default defineConfig({
	testDir: '.',
	testMatch: '*.spec.ts',
	timeout: 15_000,
	retries: 0,
	use: {
		baseURL: 'http://localhost:18889',
		headless: true,
	},
	workers: 1,
	webServer: {
		command: 'node tests/test-server.mjs',
		port: 18889,
		cwd: import.meta.dirname + '/..',
		reuseExistingServer: false,
	},
});
