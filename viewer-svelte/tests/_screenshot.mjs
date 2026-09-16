import { chromium } from 'playwright';
const browser = await chromium.launch();
const ctx = await browser.newContext({ viewport: { width: 1280, height: 1600 } });
const page = await ctx.newPage();
await page.goto('http://localhost:18889/?date=2026-03-23', { waitUntil: 'networkidle' });
await page.waitForTimeout(800);
await page.screenshot({ path: '/tmp/loosened.png', fullPage: true });
await browser.close();
console.log('OK');
