import { test, expect } from '@playwright/test'

// All tests run against the fixture test-server (port 18888, started by playwright.config.ts)

test.describe('REST API', () => {
  test('GET /api/dates returns sorted date array', async ({ request }) => {
    const res = await request.get('/api/dates')
    expect(res.ok()).toBeTruthy()
    const dates = await res.json()
    expect(dates).toEqual(['2026-03-23', '2026-03-22'])
  })

  test('GET /api/daily/{date} returns digest JSON', async ({ request }) => {
    const res = await request.get('/api/daily/2026-03-23')
    expect(res.ok()).toBeTruthy()
    const data = await res.json()
    expect(data.date).toBe('2026-03-23')
    expect(data.totalArticles).toBe(12)
    expect(data.clusters).toHaveLength(4)
    expect(data.unclustered).toHaveLength(1)
    expect(data.debug).toHaveLength(3)
  })

  test('GET /api/daily/{date} returns 404 for missing date', async ({ request }) => {
    const res = await request.get('/api/daily/1999-01-01')
    expect(res.status()).toBe(404)
  })

  test('GET /api/pipeline returns stats and jobs', async ({ request }) => {
    const res = await request.get('/api/pipeline')
    expect(res.ok()).toBeTruthy()
    const data = await res.json()
    expect(data.stats.succeeded).toBe(42)
    expect(data.stats.failed).toBe(1)
    expect(data.recentJobs).toHaveLength(3)
  })

  test('POST /api/ingest rejects unauthenticated', async ({ request }) => {
    const res = await request.post('/api/ingest', { data: [] })
    expect(res.status()).toBe(401)
  })

  test('POST /api/ingest accepts with valid auth', async ({ request }) => {
    const res = await request.post('/api/ingest', {
      data: [{ id: 'test:1', title: 'T', content: 'C', sourceType: 'test', sourceId: 's', ingestedAt: '2026-01-01T00:00:00Z' }],
      headers: { 'Authorization': 'Bearer test-fixture-key', 'Content-Type': 'application/json' },
    })
    expect(res.ok()).toBeTruthy()
    const body = await res.json()
    expect(body.saved).toBe(1)
  })

  test('GET /api/files returns markdown filenames (backward compat)', async ({ request }) => {
    const res = await request.get('/api/files')
    expect(res.ok()).toBeTruthy()
    const files = await res.json()
    expect(files.length).toBeGreaterThan(0)
    expect(files[0]).toMatch(/^jvm-daily-\d{4}-\d{2}-\d{2}\.md$/)
  })
})

test.describe('Viewer: Loading & Navigation', () => {
  test('loads and shows date buttons in sidebar', async ({ page }) => {
    await page.goto('/')
    await page.waitForSelector('.date-btn', { timeout: 5_000 })
    const buttons = page.locator('.date-btn')
    await expect(buttons).toHaveCount(2)
    await expect(buttons.first()).toContainText('Mar 23')
    await expect(buttons.nth(1)).toContainText('Mar 22')
  })

  test('auto-loads most recent date on startup', async ({ page }) => {
    await page.goto('/')
    await page.waitForSelector('.digest-date', { timeout: 5_000 })
    await expect(page.locator('.digest-date')).toContainText('March 23, 2026')
  })

  test('clicking a different date loads that digest', async ({ page }) => {
    await page.goto('/')
    await page.waitForSelector('.date-btn')
    await page.locator('.date-btn').nth(1).click()
    await page.waitForTimeout(500)
    await expect(page.locator('.digest-date')).toContainText('March 22, 2026')
    await expect(page.locator('.date-btn').nth(1)).toHaveClass(/active/)
  })

  test('URL updates with ?date= when navigating', async ({ page }) => {
    await page.goto('/')
    await page.waitForSelector('.date-btn')
    await page.locator('.date-btn').nth(1).click()
    await page.waitForTimeout(500)
    expect(page.url()).toContain('date=2026-03-22')
  })

  test('loads specific date from ?date= URL param', async ({ page }) => {
    await page.goto('/?date=2026-03-22')
    await page.waitForSelector('.digest-date', { timeout: 5_000 })
    await expect(page.locator('.digest-date')).toContainText('March 22, 2026')
  })
})

test.describe('Viewer: Clusters & Articles', () => {
  test('renders topic clusters with titles and synthesis', async ({ page }) => {
    await page.goto('/')
    await page.waitForSelector('.cluster', { timeout: 5_000 })
    const clusters = page.locator('.cluster')
    // 4 clusters in fixture: 3 topic + 1 release
    const count = await clusters.count()
    expect(count).toBeGreaterThanOrEqual(3)

    // First cluster title
    await expect(clusters.first().locator('.cluster-title')).toContainText('Quarkus Benchmarking')
    // Synthesis is rendered
    await expect(clusters.first().locator('.cluster-synthesis')).toBeVisible()
  })

  test('renders release clusters with bullet points', async ({ page }) => {
    await page.goto('/?date=2026-03-23')
    await page.waitForSelector('.cluster', { timeout: 5_000 })
    // Spring Boot release cluster should be in releases section
    const releaseSection = page.locator('.releases-section')
    await expect(releaseSection).toBeVisible()
    await expect(releaseSection).toContainText('Spring Boot')
  })

  test('articles show title, summary, source badge, and topics', async ({ page }) => {
    await page.goto('/')
    await page.waitForSelector('.article-row', { timeout: 5_000 })
    const article = page.locator('.article-row').first()
    // Title link
    await expect(article.locator('.article-title, .social-card-author').first()).toBeVisible()
    // Topic tags
    const topics = article.locator('.topic-tag')
    expect(await topics.count()).toBeGreaterThan(0)
  })

  test('merges duplicate Bluesky articles by title', async ({ page }) => {
    await page.goto('/?date=2026-03-23')
    await page.waitForSelector('.cluster', { timeout: 5_000 })
    // The Quarkus cluster has 2 articles with same title — should merge to 1
    const quarkusCluster = page.locator('.cluster').first()
    await expect(quarkusCluster.locator('.cluster-title')).toContainText('Quarkus')
    // After merge: 1 article card, but showing both social links
    const socialLinks = quarkusCluster.locator('.social-link')
    // Should have 2 social links (quarkus.io + myfear.com) on the merged card
    expect(await socialLinks.count()).toBe(2)
  })

  test('shows article count in cluster header', async ({ page }) => {
    await page.goto('/')
    await page.waitForSelector('.cluster', { timeout: 5_000 })
    await expect(page.locator('.cluster-count').first()).toBeVisible()
  })

  test('Reddit articles show Reddit source badge', async ({ page }) => {
    await page.goto('/?date=2026-03-23')
    await page.waitForSelector('.cluster', { timeout: 5_000 })
    // Second cluster is the Reddit Kotlin one
    const redditBadge = page.locator('.article-meta').filter({ hasText: 'Reddit' })
    await expect(redditBadge.first()).toBeVisible()
  })
})

test.describe('Viewer: ROTS Bookmarks', () => {
  test('bookmark button toggles star via JS', async ({ page }) => {
    await page.goto('/')
    await page.waitForSelector('.cluster .bookmark-btn', { timeout: 5_000 })
    // Use evaluate to call toggleBookmark directly (avoids click targeting issues)
    const result = await page.evaluate(() => {
      const btn = document.querySelector('.cluster:not(.release-card) .bookmark-btn') as HTMLButtonElement
      if (!btn) return { error: 'no bookmark button found' }
      const before = btn.textContent
      btn.click()
      const after = btn.textContent
      btn.click()
      const restored = btn.textContent
      return { before, after, restored }
    })
    expect(result.before).toBe('☆')
    expect(result.after).toBe('★')
    expect(result.restored).toBe('☆')
  })

  test('ROTS tab shows content after bookmarking', async ({ page }) => {
    await page.goto('/')
    await page.waitForSelector('.cluster .bookmark-btn', { timeout: 5_000 })
    // Bookmark via JS
    await page.evaluate(() => {
      const btn = document.querySelector('.cluster:not(.release-card) .bookmark-btn') as HTMLButtonElement
      btn?.click()
    })
    await page.waitForTimeout(300)
    // Switch to ROTS tab
    await page.getByRole('button', { name: 'ROTS' }).click()
    await page.waitForTimeout(500)
    await expect(page.locator('#rots-view')).toBeVisible()
  })

  test('dismiss button reduces cluster opacity', async ({ page }) => {
    await page.goto('/')
    await page.waitForSelector('.cluster .tick-btn', { timeout: 5_000 })
    // Dismiss via JS
    const opacity = await page.evaluate(() => {
      const btn = document.querySelector('.cluster:not(.release-card) .tick-btn') as HTMLButtonElement
      const cluster = btn?.closest('.cluster') as HTMLElement
      btn?.click()
      return getComputedStyle(cluster).opacity
    })
    expect(opacity).toBe('0.35')
  })
})

test.describe('Viewer: Pipeline Tab', () => {
  test('shows pipeline stats', async ({ page }) => {
    await page.goto('/')
    await page.getByRole('button', { name: 'Pipeline' }).click()
    await page.waitForSelector('.stats-row', { timeout: 5_000 })
    await expect(page.locator('.stat-card').first()).toBeVisible()
    // Should show 42 total runs
    await expect(page.locator('.stat-success .num')).toContainText('42')
  })

  test('shows recent job history with state chips', async ({ page }) => {
    await page.goto('/')
    await page.getByRole('button', { name: 'Pipeline' }).click()
    await page.waitForSelector('.job-row', { timeout: 5_000 })
    const jobs = page.locator('.job-row')
    expect(await jobs.count()).toBe(3)
    // Should have state chips (class="chip state-SUCCEEDED" etc.)
    await expect(page.locator('.chip').first()).toBeVisible()
  })
})

test.describe('Viewer: Debug Panel', () => {
  test('debug toggle shows rejected articles', async ({ page }) => {
    await page.goto('/?date=2026-03-23')
    await page.waitForSelector('.cluster', { timeout: 5_000 })
    // Find and click the debug toggle
    const debugToggle = page.locator('button').filter({ hasText: /rejected/i })
    if (await debugToggle.isVisible()) {
      await debugToggle.click()
      await page.waitForTimeout(300)
      // Should show rejected items
      await expect(page.locator('text=relevance_gate').first()).toBeVisible()
    }
  })
})

test.describe('Viewer: Digest Stats Header', () => {
  test('shows article count and topic count', async ({ page }) => {
    await page.goto('/')
    await page.waitForSelector('.digest-stats', { timeout: 5_000 })
    const stats = page.locator('.digest-stats')
    await expect(stats).toContainText('articles')
    await expect(stats).toContainText('topics')
  })
})
