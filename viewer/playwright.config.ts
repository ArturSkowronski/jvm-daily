import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: '.',
  testMatch: '*.spec.ts',
  timeout: 30_000,
  retries: 0,
  use: {
    baseURL: 'http://localhost:18888',
    headless: true,
  },
  workers: 1,
  webServer: {
    command: 'node viewer/test-server.mjs',
    port: 18888,
    cwd: process.cwd().replace(/\/viewer$/, ''),
    reuseExistingServer: false,
  },
})
