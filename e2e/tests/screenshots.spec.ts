import { expect, test, type Page } from '@playwright/test';
import { fileURLToPath } from 'node:url';
import { expectChartDrawn, sample } from './helpers';

// Not part of the normal test run: `npm run screenshots` regenerates the pictures used in the README.
const out = (name: string) => fileURLToPath(new URL(`../../docs/screenshots/${name}.png`, import.meta.url));

async function shot(page: Page, name: string, fullPage = false) {
  await page.waitForTimeout(400);
  await page.screenshot({ path: out(name), fullPage });
}

test('README screenshots', async ({ page }) => {
  test.setTimeout(180_000);

  await page.goto('/');
  await expect(page.getByTestId('alert-list')).toBeVisible();
  await shot(page, 'dashboard');

  await page.goto('/roasts');
  await expect(page.getByTestId('roast-row').first()).toBeVisible();
  await shot(page, 'roasts');

  await page.goto('/roasts/1');
  await expectChartDrawn(page.getByTestId('roast-chart'));
  await expectChartDrawn(page.getByTestId('deviation-chart'));
  await shot(page, 'roast-detail', true);

  await page.goto('/compare?ids=1,2,3,5');
  await expectChartDrawn(page.getByTestId('roast-chart'));
  await page.getByRole('combobox', { name: 'Reference roast' }).click();
  await page.getByRole('option', { name: /PR-0002/ }).click();
  await expectChartDrawn(page.getByTestId('deviation-chart'));
  await shot(page, 'compare', true);

  await page.goto('/import');
  await page.getByTestId('file-input').setInputFiles(sample('kenya-aa-light.alog'));
  await expectChartDrawn(page.getByTestId('roast-chart'));
  await shot(page, 'import', true);

  await page.goto('/inventory');
  await expect(page.getByTestId('lot-row').first()).toBeVisible();
  await shot(page, 'inventory');

  await page.getByRole('row', { name: /KEN-NYERI-25/ }).click();
  await expect(page.getByTestId('lot-alert')).toBeVisible();
  await shot(page, 'lot-detail', true);

  await page.goto('/profiles');
  await expect(page.getByTestId('profile-card').first()).toBeVisible();
  await shot(page, 'profiles');

  await page.goto('/roasts/2');
  await expectChartDrawn(page.getByTestId('roast-chart'));
  await page.getByRole('button', { name: 'Toggle colour theme' }).click();
  await expectChartDrawn(page.getByTestId('roast-chart'));
  await shot(page, 'roast-detail-dark');
});
