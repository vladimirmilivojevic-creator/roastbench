import { expect, test } from '@playwright/test';
import { numberIn } from './helpers';

test.describe('green coffee inventory', () => {
  test('lists the demo lots with status and the low-stock summary', async ({ page }) => {
    await page.goto('/inventory');

    await expect(page.getByTestId('lot-row')).toHaveCount(7);
    await expect(page.getByTestId('kpi-low')).toContainText('2');
    await expect(page.getByTestId('kpi-out')).toContainText('1');
    await expect(page.getByRole('row', { name: /KEN-NYERI-25/ })).toContainText('Low stock');
    await expect(page.getByRole('row', { name: /SUM-MAND-25/ })).toContainText('Out of stock');

    await page.getByLabel('Search lots').fill('guji');
    await expect(page.getByTestId('lot-row')).toHaveCount(1);
  });

  test('a new lot raises a low-stock alert until enough coffee is received', async ({ page }) => {
    const code = `E2E-${Date.now()}`;
    await page.goto('/inventory');

    await page.getByTestId('new-lot').click();
    await page.getByTestId('lot-code').fill(code);
    await page.getByTestId('lot-name').fill('End to end test lot');
    await page.getByTestId('lot-country').fill('Peru');
    await page.getByTestId('lot-min').fill('10');
    await page.getByTestId('lot-save').click();

    await expect(page).toHaveURL(/\/inventory\/\d+$/);
    await expect(page.getByRole('heading', { name: new RegExp(code) })).toBeVisible();
    await expect(page.getByTestId('lot-alert')).toContainText('This lot is empty');

    // receive 6 kg: still below the minimum of 10 kg
    await page.getByTestId('receive').click();
    await page.getByRole('combobox', { name: 'Location' }).click();
    await page.getByRole('option', { name: /Main warehouse/ }).click();
    await page.getByTestId('movement-quantity').fill('6');
    await page.getByTestId('movement-save').click();

    await expect(page.getByTestId('lot-stock').locator('h5')).toContainText('6.00 kg');
    await expect(page.getByTestId('lot-alert')).toContainText('below the minimum');
    await expect(page.getByTestId('lot-alert')).toContainText('Order at least 4.0 kg');

    // the dashboard shows the alert too
    await page.goto('/');
    await expect(page.getByTestId('alert-list')).toContainText(code);

    // a correction of +5 kg lifts it above the minimum and the alert disappears
    await page.goto('/inventory');
    await page.getByRole('row', { name: new RegExp(code) }).click();
    await page.getByTestId('adjust').click();
    await page.getByRole('combobox', { name: 'Location' }).click();
    await page.getByRole('option', { name: /Main warehouse/ }).click();
    await page.getByTestId('movement-quantity').fill('5');
    await page.getByTestId('movement-reason').fill('Stock count');
    await page.getByTestId('movement-save').click();

    await expect(page.getByTestId('lot-stock').locator('h5')).toContainText('11.00 kg');
    await expect(page.getByTestId('lot-alert')).toHaveCount(0);

    // the ledger keeps both bookings with their running balance
    const rows = page.getByTestId('movement-row');
    await expect(rows).toHaveCount(2);
    await expect(rows.first()).toContainText('Adjustment');
    await expect(rows.first()).toContainText('11.000 kg');
    await expect(rows.last()).toContainText('Received');
    expect(numberIn(await rows.last().textContent())).toBeGreaterThan(0);
  });

  test('refuses an adjustment that would make the location negative', async ({ page }) => {
    await page.goto('/inventory');
    await page.getByRole('row', { name: /GUA-ANTI-25/ }).click();

    await page.getByTestId('adjust').click();
    await page.getByRole('combobox', { name: 'Location' }).click();
    await page.getByRole('option', { name: /Main warehouse/ }).click();
    await page.getByTestId('movement-quantity').fill('-9999');
    await page.getByTestId('movement-reason').fill('Typo');
    await page.getByTestId('movement-save').click();

    await expect(page.getByText(/The location holds only/)).toBeVisible();
  });
});
