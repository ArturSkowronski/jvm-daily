import { chromium } from 'playwright';
const browser = await chromium.launch();
const page = await browser.newPage();
await page.goto('https://jvm-daily.fly.dev/', { waitUntil: 'networkidle', timeout: 20000 });
await page.waitForTimeout(1500);
const probe = await page.evaluate(() => {
  const root = getComputedStyle(document.documentElement);
  const body = getComputedStyle(document.body);
  return {
    bg: body.backgroundColor,
    font: body.fontFamily,
    accent: root.getPropertyValue('--accent'),
    bgVar: root.getPropertyValue('--bg'),
    srcBsky: root.getPropertyValue('--src-bsky'),
    rots: root.getPropertyValue('--rots'),
  };
});
console.log(JSON.stringify(probe, null, 2));
await browser.close();
