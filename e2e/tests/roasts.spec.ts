import { expect, test } from '@playwright/test';
import { expectChartDrawn, numberIn } from './helpers';

test.describe('roast list, detail and comparison', () => {
  test('lists roasts with paging and sorts by development time ratio', async ({ page }) => {
    await page.goto('/roasts');

    await expect(page.getByTestId('roast-row')).toHaveCount(20);
    await expect(page.getByText(/of 2\d/).first()).toBeVisible();

    await page.getByRole('button', { name: 'DTR' }).click(); // first click: highest first
    await expect(page).toHaveURL(/sort=dtr/);
    await expect(async () => {
      const cells = await page.getByTestId('roast-row').locator('td:nth-child(9)').allTextContents();
      const values = cells.map(numberIn);
      expect(values).toEqual([...values].sort((a, b) => b - a));
    }).toPass();
  });

  test('filters by profile', async ({ page }) => {
    await page.goto('/roasts');

    await page.getByRole('combobox', { name: 'Profile' }).click();
    await page.getByRole('option', { name: 'Filter light' }).click();

    await expect(page.getByTestId('roast-row')).toHaveCount(6);
    await expect(page).toHaveURL(/profileId=/);
  });

  test('a roast shows metrics, phases and the interactive chart', async ({ page }) => {
    await page.goto('/roasts/1');

    await expect(page.getByRole('heading', { name: /PR-0001/ })).toBeVisible();
    await expect(page.getByTestId('metric-dtr')).toContainText('%');
    await expect(page.getByTestId('metric-loss')).toContainText('%');
    await expectChartDrawn(page.getByTestId('roast-chart'));

    await expect(page.getByRole('table', { name: 'Roast phases' }).getByRole('row')).toHaveCount(4); // header + 3 phases
    await expect(page.getByRole('table', { name: 'Roast events' })).toContainText('First crack start');

    await page.getByRole('button', { name: '60s' }).click();
    await expectChartDrawn(page.getByTestId('roast-chart'));
  });

  test('compares a roast with the reference roast of its profile', async ({ page }) => {
    await page.goto('/roasts/1'); // PR-0001; the reference of "Filter light" is PR-0002

    await expect(page.getByText(/Reference: .*PR-0002/)).toBeVisible();
    await expect(page.getByTestId('cmp-rmse')).toContainText('°C');
    await expectChartDrawn(page.getByTestId('deviation-chart'));

    await page.goto('/roasts/2');
    await expect(page.getByText('This roast is the reference')).toBeVisible();
  });

  test('overlays several roasts and shows differences to a chosen reference', async ({ page }) => {
    await page.goto('/roasts');
    const rows = page.getByTestId('roast-row');
    await expect(rows).toHaveCount(20);
    for (const index of [0, 1, 2]) {
      await rows.nth(index).getByRole('checkbox').check(); // the three newest roasts
    }

    await page.getByRole('button', { name: 'Compare (3)' }).click();

    await expect(page).toHaveURL(/\/compare\?ids=/);
    await expect(page.getByRole('heading', { name: 'Compare roasts' })).toBeVisible();
    await expectChartDrawn(page.getByTestId('roast-chart'));
    const stats = page.getByRole('table', { name: 'Roast statistics' });
    await expect(stats.getByRole('columnheader')).toHaveCount(4);

    await page.getByRole('combobox', { name: 'Reference roast' }).click();
    await page.getByRole('option').nth(1).click(); // option 0 is "None"
    await expectChartDrawn(page.getByTestId('deviation-chart'));
    await expect(stats).toContainText('(ref)');
  });

  test('the reference of a profile can be changed with the star button', async ({ page }) => {
    await page.goto('/roasts/3'); // PR-0003, profile "Filter light"

    await page.getByRole('button', { name: 'Set as reference' }).click();
    await expect(page.getByRole('button', { name: 'Reference roast' })).toBeVisible();
    await expect(page.getByText('This roast is the reference')).toBeVisible();

    // put it back so other tests see the original demo state
    await page.goto('/roasts/2');
    await page.getByRole('button', { name: 'Set as reference' }).click();
    await expect(page.getByRole('button', { name: 'Reference roast' })).toBeVisible();
  });

  test('marked events can be edited and the development ratio follows', async ({ page }) => {
    await page.goto('/roasts/4');
    const before = numberIn(await page.getByTestId('metric-dtr').textContent());

    await page.getByRole('button', { name: 'Edit events' }).click();
    const crack = page.getByTestId('event-FIRST_CRACK_START');
    const current = await crack.inputValue();
    const [minutes, seconds] = current.split(':').map(Number);
    const later = minutes * 60 + seconds + 20; // first crack 20 s later: shorter development
    await crack.fill(`${Math.floor(later / 60)}:${String(later % 60).padStart(2, '0')}`);
    await page.getByRole('button', { name: 'Save' }).click();

    await expect(async () => {
      const after = numberIn(await page.getByTestId('metric-dtr').textContent());
      expect(after).toBeLessThan(before);
    }).toPass();
  });
});
