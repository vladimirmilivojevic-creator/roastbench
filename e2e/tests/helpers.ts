import { expect, type Locator, type Page } from '@playwright/test';
import { fileURLToPath } from 'node:url';

/** Path of a file in the repository's samples/ folder. */
export function sample(name: string): string {
  return fileURLToPath(new URL(`../../samples/${name}`, import.meta.url));
}

/** A number from text such as "93.00 kg" or "16.4 %". */
export function numberIn(text: string | null): number {
  const match = /-?\d+(?:\.\d+)?/.exec((text ?? '').replace(',', ''));
  if (!match) throw new Error(`No number in "${text}"`);
  return Number(match[0]);
}

/** The chart is a canvas, so check that something was actually painted on it. */
export async function expectChartDrawn(chart: Locator): Promise<void> {
  const canvas = chart.locator('canvas').first();
  await expect(canvas).toBeVisible();
  await expect
    .poll(
      async () =>
        canvas.evaluate((el) => {
          const c = el as HTMLCanvasElement;
          const data = c.getContext('2d')!.getImageData(0, 0, c.width, c.height).data;
          let painted = 0;
          for (let i = 3; i < data.length; i += 16) if (data[i] > 0) painted++;
          return painted;
        }),
      { message: 'the chart canvas should contain drawn pixels', timeout: 15_000 },
    )
    .toBeGreaterThan(500);
}

export function nav(page: Page, name: string): Locator {
  return page.getByRole('navigation', { name: 'Main navigation' }).getByRole('link', { name });
}
