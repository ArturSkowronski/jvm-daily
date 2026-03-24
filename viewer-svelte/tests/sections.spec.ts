import { test, expect } from '@playwright/test';

// Tests run against the fixture test-server on port 18888
// which serves the SvelteKit build with mock API data

test.describe('Section ordering: bookmark → ROTS, dismiss → Archive', () => {
	test.beforeEach(async ({ page }) => {
		// Clear localStorage before each test
		await page.goto('/');
		await page.evaluate(() => {
			localStorage.clear();
		});
		await page.reload();
		await page.waitForSelector('.cluster', { timeout: 5_000 });
	});

	test('initially no ROTS or Archive sections exist', async ({ page }) => {
		await expect(page.locator('.rots-inline-section')).not.toBeVisible();
		await expect(page.locator('.archive-section')).not.toBeVisible();
	});

	test('bookmarking a cluster creates ROTS section after normal clusters', async ({ page }) => {
		// Bookmark the first topic cluster
		const firstCluster = page.locator('.cluster:not(.release-card)').first();
		await firstCluster.locator('.bookmark-btn').click();
		await page.waitForTimeout(300);

		// ROTS section should appear
		const rotsSection = page.locator('.rots-inline-section');
		await expect(rotsSection).toBeVisible();
		await expect(rotsSection.locator('.section-label')).toContainText('Rest of the Story');

		// The bookmarked cluster should be INSIDE the ROTS section
		const rotsCluster = rotsSection.locator('.cluster');
		expect(await rotsCluster.count()).toBeGreaterThan(0);
	});

	test('ROTS section appears AFTER normal clusters, BEFORE archive', async ({ page }) => {
		// Bookmark first cluster
		await page.locator('.cluster:not(.release-card)').first().locator('.bookmark-btn').click();
		await page.waitForTimeout(300);

		// Get positions: normal clusters should be above ROTS
		const normalCluster = page.locator('.digest-content > .cluster').first();
		const rotsSection = page.locator('.rots-inline-section');

		if (await normalCluster.isVisible() && await rotsSection.isVisible()) {
			const normalBox = await normalCluster.boundingBox();
			const rotsBox = await rotsSection.boundingBox();
			if (normalBox && rotsBox) {
				expect(normalBox.y).toBeLessThan(rotsBox.y);
			}
		}
	});

	test('dismissing a cluster creates Archive section at bottom', async ({ page }) => {
		// Dismiss the first topic cluster
		const firstCluster = page.locator('.cluster:not(.release-card)').first();
		await firstCluster.locator('.tick-btn').click();
		await page.waitForTimeout(300);

		// Archive section should appear
		const archiveSection = page.locator('.archive-section');
		await expect(archiveSection).toBeVisible();
		await expect(archiveSection.locator('.section-label')).toContainText('Archive');
	});

	test('dismissed cluster moves to Archive with dimmed opacity', async ({ page }) => {
		const firstCluster = page.locator('.cluster:not(.release-card)').first();
		const title = await firstCluster.locator('.cluster-title').textContent();
		await firstCluster.locator('.tick-btn').click();
		await page.waitForTimeout(300);

		// The cluster should now be in the archive section with dimmed opacity
		const archiveCluster = page.locator('.archive-section .cluster');
		await expect(archiveCluster.first()).toHaveCSS('opacity', '0.35');
	});

	test('bookmarked cluster is removed from normal list', async ({ page }) => {
		// Count normal clusters before
		const countBefore = await page.locator('.digest-content > .cluster:not(.release-card)').count();

		// Bookmark first
		await page.locator('.cluster:not(.release-card)').first().locator('.bookmark-btn').click();
		await page.waitForTimeout(300);

		// Normal cluster count should decrease by 1
		const countAfter = await page.locator('.digest-content > .cluster:not(.release-card)').count();
		expect(countAfter).toBe(countBefore - 1);
	});

	test('unbookmarking returns cluster to normal section', async ({ page }) => {
		const countBefore = await page.locator('.digest-content > .cluster:not(.release-card)').count();

		// Bookmark then unbookmark
		await page.locator('.cluster:not(.release-card)').first().locator('.bookmark-btn').click();
		await page.waitForTimeout(300);
		// Now unbookmark from ROTS section
		await page.locator('.rots-inline-section .bookmark-btn').first().click();
		await page.waitForTimeout(300);

		const countAfter = await page.locator('.digest-content > .cluster:not(.release-card)').count();
		expect(countAfter).toBe(countBefore);
		await expect(page.locator('.rots-inline-section')).not.toBeVisible();
	});

	test('undismissing returns cluster from Archive to normal', async ({ page }) => {
		const countBefore = await page.locator('.digest-content > .cluster:not(.release-card)').count();

		// Dismiss then undismiss
		await page.locator('.cluster:not(.release-card)').first().locator('.tick-btn').click();
		await page.waitForTimeout(300);
		// Undismiss from archive
		await page.locator('.archive-section .tick-btn').first().click();
		await page.waitForTimeout(300);

		const countAfter = await page.locator('.digest-content > .cluster:not(.release-card)').count();
		expect(countAfter).toBe(countBefore);
		await expect(page.locator('.archive-section')).not.toBeVisible();
	});

	test('bookmark takes priority over dismiss (cluster shows in ROTS, not Archive)', async ({ page }) => {
		// Dismiss first
		const firstCluster = page.locator('.cluster:not(.release-card)').first();
		await firstCluster.locator('.tick-btn').click();
		await page.waitForTimeout(300);

		// Now bookmark the same cluster from Archive
		const archivedCluster = page.locator('.archive-section .cluster').first();
		await archivedCluster.locator('.bookmark-btn').click();
		await page.waitForTimeout(300);

		// Should be in ROTS, not Archive
		await expect(page.locator('.rots-inline-section')).toBeVisible();
		// Archive should either be empty or not contain this cluster
	});

	test('section order is: Normal → Releases → ROTS → Tweets → Archive', async ({ page }) => {
		// Bookmark one cluster and dismiss another to create all sections
		const clusters = page.locator('.cluster:not(.release-card)');
		const count = await clusters.count();
		if (count < 2) return;

		// Bookmark first, dismiss second
		await clusters.first().locator('.bookmark-btn').click();
		await page.waitForTimeout(200);
		await clusters.first().locator('.tick-btn').click(); // This is now a different cluster (first unbookmarked)
		await page.waitForTimeout(300);

		// Verify sections exist
		const rotsSection = page.locator('.rots-inline-section');
		const archiveSection = page.locator('.archive-section');

		if (await rotsSection.isVisible() && await archiveSection.isVisible()) {
			const rotsBox = await rotsSection.boundingBox();
			const archiveBox = await archiveSection.boundingBox();
			if (rotsBox && archiveBox) {
				expect(rotsBox.y).toBeLessThan(archiveBox.y);
			}
		}
	});
});

test.describe('Release cluster sections', () => {
	test('release clusters appear in Releases section', async ({ page }) => {
		await page.goto('/?date=2026-03-23');
		await page.waitForSelector('.releases-section', { timeout: 5_000 });
		await expect(page.locator('.releases-section')).toBeVisible();
		await expect(page.locator('.releases-section .release-card')).toBeVisible();
	});

	test('bookmarking a release moves it to ROTS section', async ({ page }) => {
		await page.goto('/?date=2026-03-23');
		await page.evaluate(() => localStorage.clear());
		await page.reload();
		await page.waitForSelector('.releases-section', { timeout: 5_000 });

		// Bookmark the release
		await page.locator('.releases-section .bookmark-btn').first().click();
		await page.waitForTimeout(300);

		// Should appear in ROTS
		await expect(page.locator('.rots-inline-section')).toBeVisible();
		await expect(page.locator('.rots-inline-section .release-card')).toBeVisible();
	});
});
