import type { Lot, StockStatus } from '../api/types';

export type StatusColor = 'success' | 'warning' | 'error';

export function statusColor(status: StockStatus): StatusColor {
  switch (status) {
    case 'OK':
      return 'success';
    case 'LOW':
      return 'warning';
    case 'OUT':
      return 'error';
  }
}

export function statusLabel(status: StockStatus): string {
  switch (status) {
    case 'OK':
      return 'In stock';
    case 'LOW':
      return 'Low stock';
    case 'OUT':
      return 'Out of stock';
  }
}

/**
 * Fill of the stock bar, 0-100. With a minimum level the bar is scaled so the minimum sits at the middle:
 * below half the bar the lot is under its minimum.
 */
export function stockBarPercent(stockKg: number, minStockKg: number): number {
  if (stockKg <= 0) return 0;
  if (minStockKg > 0) return Math.min(100, (stockKg / (minStockKg * 2)) * 100);
  return 100;
}

export function formatDaysLeft(days: number | null | undefined): string {
  if (days === null || days === undefined) return '–';
  if (days > 365) return 'over a year';
  if (days === 1) return '1 day';
  return `${days} days`;
}

/** Kilograms still needed to bring a lot back to its minimum level; 0 when it is not below it. */
export function shortfallKg(lot: Pick<Lot, 'stockKg' | 'minStockKg'>): number {
  return Math.max(0, lot.minStockKg - lot.stockKg);
}
