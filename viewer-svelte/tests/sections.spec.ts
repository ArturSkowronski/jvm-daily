import { test, expect, type Page } from '@playwright/test';

// Runs against tests/test-server.mjs (SvelteKit build + fixture API).
// Fixtures: 2026-03-23 (newest) and 2026-03-22.

const inbox = (page: Page) => page.locator('.digest-content > .review-item, .releases-section .review-item, .mailing-section .review-item');
const focused = (page: Page) => page.locator('.review-item.focused');

async function fresh(page: Page, query = '') {
	await page.goto('/' + query);
	await page.evaluate(() => localStorage.clear());
	await page.goto('/' + query);
	await page.waitForSelector('.review-item', { timeout: 5_000 });
}

test.describe('Inbox flow per day', () => {
	test('opens the newest day with the first cluster focused', async ({ page }) => {
		await fresh(page);
		await expect(page).toHaveURL(/date=2026-03-23/);
		await expect(focused(page)).toHaveCount(1);
		const firstKey = await inbox(page).first().getAttribute('data-key');
		await expect(focused(page)).toHaveAttribute('data-key', firstKey!);
	});

	test('arrow keys and j/k move the focus', async ({ page }) => {
		await fresh(page);
		const keys = await inbox(page).evaluateAll((els) => els.map((e) => e.getAttribute('data-key')));
		await page.keyboard.press('ArrowDown');
		await expect(focused(page)).toHaveAttribute('data-key', keys[1]!);
		await page.keyboard.press('j');
		await expect(focused(page)).toHaveAttribute('data-key', keys[2]!);
		await page.keyboard.press('ArrowUp');
		await page.keyboard.press('k');
		await expect(focused(page)).toHaveAttribute('data-key', keys[0]!);
	});

	test('e marks done, moves focus to the next cluster and u undoes it', async ({ page }) => {
		await fresh(page);
		const keys = await inbox(page).evaluateAll((els) => els.map((e) => e.getAttribute('data-key')));
		await page.keyboard.press('e');
		await expect(inbox(page)).toHaveCount(keys.length - 1);
		await expect(focused(page)).toHaveAttribute('data-key', keys[1]!);
		await expect(page.locator('.archive-section .section-toggle')).toContainText('Reviewed · 1');
		await expect(page.locator('.toast')).toContainText('Done');

		await page.keyboard.press('u');
		await expect(inbox(page)).toHaveCount(keys.length);
		await expect(focused(page)).toHaveAttribute('data-key', keys[0]!);
		await expect(page.locator('.archive-section')).toHaveCount(0);
	});

	test('Read later and Rest of the Story are separate lists', async ({ page }) => {
		await fresh(page);
		const keys = await inbox(page).evaluateAll((els) => els.map((e) => e.getAttribute('data-key')));
		await page.keyboard.press('s'); // first → Read later
		await page.keyboard.press('r'); // second → ROTS
		await expect(page.locator('.later-count')).toHaveText('1');
		await expect(page.locator('.rots-count')).toHaveText('1');

		await page.locator('.tab', { hasText: 'Later' }).click();
		await expect(page.locator('.saved h2')).toContainText('Read later');
		await expect(page.locator('.saved .review-item')).toHaveCount(1);
		await expect(page.locator('.saved .review-item')).toHaveAttribute('data-key', keys[0]!);

		await page.locator('.tab', { hasText: 'ROTS' }).click();
		await expect(page.locator('.rots .cluster')).toHaveCount(1);
		await expect(page.locator('.rots .cluster')).toHaveAttribute('data-key', keys[1]!);
	});

	test('e on the Later list removes the item, u brings it back', async ({ page }) => {
		await fresh(page);
		await page.keyboard.press('s');
		await page.locator('.tab', { hasText: 'Later' }).click();
		await expect(page.locator('.saved .review-item')).toHaveCount(1);
		await page.keyboard.press('e');
		await expect(page.locator('.saved .review-item')).toHaveCount(0);
		await page.keyboard.press('u');
		await expect(page.locator('.saved .review-item')).toHaveCount(1);
	});

	test('buttons work like keys: ✓ on a release takes it out of the inbox', async ({ page }) => {
		await fresh(page);
		const release = page.locator('.releases-section .release-card');
		await expect(release).toHaveCount(1);
		await release.locator('.tick-btn').click();
		await expect(page.locator('.releases-section')).toHaveCount(0);
	});
});

test.describe('Moving between days', () => {
	test('→ goes to the older day, ← back to the newer one', async ({ page }) => {
		await fresh(page);
		await page.keyboard.press('ArrowRight');
		await expect(page).toHaveURL(/date=2026-03-22/);
		await expect(page.locator('.digest-date')).toContainText('March 22');
		await page.keyboard.press('ArrowLeft');
		await expect(page).toHaveURL(/date=2026-03-23/);
	});

	test('finishing a day jumps to the next unreviewed day', async ({ page }) => {
		await fresh(page);
		await page.keyboard.press('Shift+E');
		await expect(page).toHaveURL(/date=2026-03-22/);
		await expect(page.locator('.toast')).toContainText('day reviewed');
		await expect(page.locator('.date-btn.reviewed')).toHaveCount(1);
	});

	test('a fresh visit skips days that are already reviewed', async ({ page }) => {
		await fresh(page);
		await page.keyboard.press('Shift+E');
		await expect(page).toHaveURL(/date=2026-03-22/);
		await page.goto('/');
		await page.waitForSelector('.review-item');
		await expect(page).toHaveURL(/date=2026-03-22/);
	});

	test('undo after the auto-jump returns to the previous day', async ({ page }) => {
		await fresh(page);
		await page.keyboard.press('Shift+E');
		await expect(page).toHaveURL(/date=2026-03-22/);
		await page.keyboard.press('u');
		await expect(page).toHaveURL(/date=2026-03-23/);
		await expect(page.locator('.date-btn.reviewed')).toHaveCount(0);
	});

	test('the last day shows inbox zero when everything is reviewed', async ({ page }) => {
		await fresh(page, '?date=2026-03-22');
		await page.keyboard.press('Shift+E'); // → jumps to 2026-03-23
		await expect(page).toHaveURL(/date=2026-03-23/);
		await page.keyboard.press('Shift+E'); // nothing left anywhere
		await expect(page.locator('.inbox-zero')).toBeVisible();
		await expect(page.locator('.toast')).toContainText('all days reviewed');
	});
});

test.describe('Help', () => {
	test('? opens the keyboard help, Esc closes it', async ({ page }) => {
		await fresh(page);
		await page.keyboard.press('?');
		await expect(page.locator('.help')).toBeVisible();
		await page.keyboard.press('Escape');
		await expect(page.locator('.help')).toHaveCount(0);
	});
});

test.describe('Dark mode toggle', () => {
	test.beforeEach(async ({ page }) => {
		await page.goto('/');
		await page.evaluate(() => localStorage.clear());
		await page.reload();
	});

	test('toggle switches data-theme attribute', async ({ page }) => {
		await page.goto('/');
		await page.waitForSelector('.theme-toggle');

		// Initially light
		await expect(page.locator('html')).not.toHaveAttribute('data-theme', 'dark');

		// Click toggle
		await page.locator('.theme-toggle').click();
		await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');

		// Click again
		await page.locator('.theme-toggle').click();
		await expect(page.locator('html')).toHaveAttribute('data-theme', 'light');
	});

	test('theme persists in localStorage', async ({ page }) => {
		await page.goto('/');
		await page.waitForSelector('.theme-toggle');
		await page.locator('.theme-toggle').click();

		const theme = await page.evaluate(() => localStorage.getItem('theme'));
		expect(theme).toBe('dark');
	});
});
