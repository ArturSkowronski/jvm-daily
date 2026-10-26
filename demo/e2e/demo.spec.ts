import { expect, test } from '@playwright/test'
import { pauseForScene } from 'playwright-narrator/playwright'
import { scenes } from '../manifest.mjs'

/** Smooth scroll by pixels over duration */
async function smoothScroll(page, pixels: number, durationMs = 2000) {
  const steps = 30
  const delay = durationMs / steps
  const perStep = pixels / steps
  for (let i = 0; i < steps; i++) {
    await page.evaluate((px) => window.scrollBy(0, px), perStep)
    await page.waitForTimeout(delay)
  }
}

test('JVM Daily demo walkthrough @demo', async ({ page }) => {
  // Scene 1: Overview — land on the main page, let it load
  await page.goto('/')
  await page.waitForSelector('.date-btn')
  await page.waitForSelector('.cluster')
  await page.waitForTimeout(1000)
  await pauseForScene(page, 'overview', scenes)

  // Scene 2: Browse digest — scroll slowly through first clusters
  await smoothScroll(page, 400, 2500)
  await page.waitForTimeout(800)
  await smoothScroll(page, 300, 2000)
  await page.waitForTimeout(500)
  await pauseForScene(page, 'browse-digest', scenes)

  // Scene 3: Cluster detail — scroll to first article with social links
  await smoothScroll(page, 500, 2500)
  await page.waitForTimeout(1000)
  // Hover over a social link to highlight it
  const socialLink = page.locator('.social-link, .social-card-author').first()
  if (await socialLink.isVisible()) {
    await socialLink.hover()
    await page.waitForTimeout(800)
  }
  await smoothScroll(page, 300, 1500)
  await page.waitForTimeout(500)
  await pauseForScene(page, 'cluster-detail', scenes)

  // Scene 4: Navigate dates — scroll back to top, click different dates
  await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'smooth' }))
  await page.waitForTimeout(1000)

  const dateButtons = page.locator('.date-btn')
  const secondDate = dateButtons.nth(1)
  await secondDate.click()
  await page.waitForSelector('.cluster')
  await page.waitForTimeout(1500)
  await smoothScroll(page, 500, 2000)
  await page.waitForTimeout(800)

  // Click another date
  const thirdDate = dateButtons.nth(2)
  await thirdDate.click()
  await page.waitForSelector('.cluster')
  await page.waitForTimeout(1000)
  await smoothScroll(page, 300, 1500)
  await page.waitForTimeout(500)
  await pauseForScene(page, 'navigate-dates', scenes)

  // Scene 5: ROTS bookmarks — scroll up, bookmark multiple clusters
  await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'smooth' }))
  await page.waitForTimeout(1000)

  // Go back to first date for richer content
  const firstDate = dateButtons.nth(0)
  await firstDate.click()
  await page.waitForSelector('.cluster')
  await page.waitForTimeout(1000)

  // Bookmark first cluster
  const bookmarkBtn1 = page.locator('.bookmark-btn').first()
  await bookmarkBtn1.scrollIntoViewIfNeeded()
  await page.waitForTimeout(500)
  await bookmarkBtn1.click()
  await page.waitForTimeout(600)

  // Scroll down and bookmark second cluster
  await smoothScroll(page, 400, 1500)
  const bookmarkBtn2 = page.locator('.bookmark-btn').nth(1)
  if (await bookmarkBtn2.isVisible()) {
    await bookmarkBtn2.click()
    await page.waitForTimeout(600)
  }

  // Open ROTS tab
  const rotsTab = page.getByRole('button', { name: 'ROTS' })
  await rotsTab.click()
  await page.waitForTimeout(1500)
  await smoothScroll(page, 300, 1500)
  await page.waitForTimeout(500)
  await pauseForScene(page, 'rots-bookmarks', scenes)

  // Scene 6: Pipeline view
  const pipelineTab = page.getByRole('button', { name: 'Pipeline' })
  await pipelineTab.click()
  await page.waitForTimeout(2000)
  await pauseForScene(page, 'pipeline-view', scenes)
})
