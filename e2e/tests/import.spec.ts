import { expect, test } from '@playwright/test';
import { expectChartDrawn, numberIn, sample } from './helpers';

test.describe('importing roast logs', () => {
  test('imports a CSV, deducts the green coffee and shows the roast with its metrics', async ({ page }) => {
    await page.goto('/inventory');
    await page.getByRole('row', { name: /COL-HUILA-25/ }).click();
    const stockBefore = numberIn(await page.getByTestId('lot-stock').locator('h5').textContent());

    await page.goto('/import');
    await page.getByTestId('file-input').setInputFiles(sample('colombia-huila-medium.csv'));

    await expectChartDrawn(page.getByTestId('roast-chart'));
    await expect(page.getByTestId('preview-dtr')).toContainText('%');
    await expect(page.getByLabel(/^Name/)).toHaveValue('Colombia Huila medium');
    await expect(page.getByText('CSV').first()).toBeVisible();

    await page.getByRole('button', { name: 'Add green coffee lot' }).click();
    await page.getByRole('combobox', { name: 'Lot' }).click();
    await page.getByRole('option', { name: /COL-HUILA-25/ }).click();
    await expect(page.getByLabel('Green weight in grams')).toHaveValue('1000');

    await page.getByRole('button', { name: 'Import roast' }).click();

    await expect(page).toHaveURL(/\/roasts\/\d+$/);
    await expect(page.getByRole('heading', { name: /Colombia Huila medium/ })).toBeVisible();
    await expect(page.getByTestId('metric-loss')).toContainText('14.5');
    await expectChartDrawn(page.getByTestId('roast-chart'));

    await page.getByRole('link', { name: /COL-HUILA-25/ }).first().click();
    await expect(page).toHaveURL(/\/inventory\/\d+$/);
    await expect(async () => {
      const stockAfter = numberIn(await page.getByTestId('lot-stock').locator('h5').textContent());
      expect(stockAfter).toBeCloseTo(stockBefore - 1, 2);
    }).toPass();
    const firstMovement = page.getByTestId('movement-row').first();
    await expect(firstMovement).toContainText('Used in roast');
    await expect(firstMovement).toContainText('-1.000 kg');
  });

  test('previews an Artisan .alog and explains that the cooling phase is left out', async ({ page }) => {
    await page.goto('/import');

    await page.getByTestId('file-input').setInputFiles(sample('brazil-cerrado-dark.alog'));

    await expectChartDrawn(page.getByTestId('roast-chart'));
    await expect(page.getByText('Artisan .alog').first()).toBeVisible();
    await expect(page.getByText(/samples after drop \(cooling\) were not imported/)).toBeVisible();
    await expect(page.getByLabel(/^Name/)).toHaveValue('Brazil Cerrado dark');
    await expect(page.getByTestId('preview-dtr')).toContainText('%');
  });

  test('explains what is wrong with a file that cannot be read', async ({ page }) => {
    await page.goto('/import');

    await page.getByTestId('file-input').setInputFiles({
      name: 'broken.csv',
      mimeType: 'text/csv',
      buffer: Buffer.from('Time,BT\n0,100\n1,not-a-number\n'),
    });

    await expect(page.getByText('Could not read the roast log')).toBeVisible();
    await expect(page.getByText(/invalid bean temperature/)).toBeVisible();
    await expect(page.getByTestId('roast-chart')).toHaveCount(0);
  });

  test('refuses an import that needs more green coffee than is in stock', async ({ page }) => {
    await page.goto('/import');
    await page.getByTestId('file-input').setInputFiles(sample('ethiopia-guji-light.csv'));
    await expectChartDrawn(page.getByTestId('roast-chart'));

    await page.getByRole('button', { name: 'Add green coffee lot' }).click();
    await page.getByRole('combobox', { name: 'Lot' }).click();
    await page.getByRole('option', { name: /SUM-MAND-25/ }).click(); // the demo's empty lot
    await expect(page.getByText(/Only 0\.0 kg in stock/)).toBeVisible();

    await page.getByRole('button', { name: 'Import roast' }).click();

    await expect(page.getByText(/Not enough stock of lot SUM-MAND-25/)).toBeVisible();
    await expect(page).toHaveURL(/\/import$/);
  });
});
