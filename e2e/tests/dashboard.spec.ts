import { expect, test } from '@playwright/test';
import { nav } from './helpers';

test.describe('dashboard and navigation', () => {
  test('shows the statistics and the low-stock alerts of the demo roastery', async ({ page }) => {
    await page.goto('/');

    await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();
    await expect(page.getByText(/synthetic/i).first()).toBeVisible();
    await expect(page.getByTestId('kpi-roasts')).toContainText('in total');

    const alerts = page.getByTestId('alert-list').getByRole('listitem');
    await expect(alerts.first()).toContainText('SUM-MAND-25');
    await expect(alerts.first()).toContainText('Out of stock');
    await expect(page.getByTestId('alert-list')).toContainText('KEN-NYERI-25');
    await expect(page.getByTestId('alert-list')).toContainText('RWA-NYAM-25');
  });

  test('moves between all pages from the side navigation', async ({ page }) => {
    await page.goto('/');

    await nav(page, 'Roasts').click();
    await expect(page.getByRole('heading', { name: 'Roasts', exact: true })).toBeVisible();
    await nav(page, 'Import').click();
    await expect(page.getByRole('heading', { name: 'Import a roast' })).toBeVisible();
    await nav(page, 'Profiles').click();
    await expect(page.getByRole('heading', { name: 'Profiles', exact: true })).toBeVisible();
    await expect(page.getByTestId('profile-card')).toHaveCount(3);
    await nav(page, 'Green coffee').click();
    await expect(page.getByRole('heading', { name: 'Green coffee', exact: true })).toBeVisible();
    await nav(page, 'About').click();
    await expect(page.getByRole('heading', { name: 'About RoastBench' })).toBeVisible();
    await nav(page, 'Dashboard').click();
    await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();
  });

  test('deep links open directly and unknown pages say so', async ({ page }) => {
    await page.goto('/roasts/1');
    await expect(page.getByRole('heading', { name: /PR-0001/ })).toBeVisible();

    await page.goto('/this/does/not/exist');
    await expect(page.getByText('Page not found')).toBeVisible();
  });

  test('switches between light and dark theme', async ({ page }) => {
    await page.goto('/');
    const background = () => page.evaluate(() => getComputedStyle(document.body).backgroundColor);
    const before = await background();

    await page.getByRole('button', { name: 'Toggle colour theme' }).click();

    await expect.poll(background).not.toBe(before);
  });
});
