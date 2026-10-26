import { defineConfig } from '@playwright/test'

export default defineConfig({
  outputDir: '../demo-output/test-results',
  testDir: './e2e',
  timeout: 420_000,
  use: {
    baseURL: process.env.DEMO_BASE_URL ?? 'http://localhost:8888',
    headless: false,
    video: { mode: 'on', size: { width: 1920, height: 1080 } },
    viewport: { width: 1920, height: 1080 },
    launchOptions: { args: ['--window-size=1920,1080'], slowMo: 250 },
  },
  workers: 1,
})
