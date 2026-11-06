import { describe, expect, it } from 'vitest';
import { formatDaysLeft, shortfallKg, statusColor, statusLabel, stockBarPercent } from './stock';

describe('stockBarPercent', () => {
  it('puts the minimum level in the middle of the bar', () => {
    expect(stockBarPercent(20, 20)).toBe(50);
    expect(stockBarPercent(10, 20)).toBe(25);
    expect(stockBarPercent(40, 20)).toBe(100);
    expect(stockBarPercent(400, 20)).toBe(100);
  });

  it('is empty without stock and full without a minimum', () => {
    expect(stockBarPercent(0, 20)).toBe(0);
    expect(stockBarPercent(-3, 20)).toBe(0);
    expect(stockBarPercent(5, 0)).toBe(100);
  });
});

describe('status helpers', () => {
  it('maps status to colour and label', () => {
    expect(statusColor('OK')).toBe('success');
    expect(statusColor('LOW')).toBe('warning');
    expect(statusColor('OUT')).toBe('error');
    expect(statusLabel('LOW')).toBe('Low stock');
  });
});

describe('formatDaysLeft', () => {
  it('formats the projection', () => {
    expect(formatDaysLeft(null)).toBe('–');
    expect(formatDaysLeft(1)).toBe('1 day');
    expect(formatDaysLeft(42)).toBe('42 days');
    expect(formatDaysLeft(900)).toBe('over a year');
  });
});

describe('shortfallKg', () => {
  it('is the distance to the minimum level, never negative', () => {
    expect(shortfallKg({ stockKg: 8, minStockKg: 15 })).toBe(7);
    expect(shortfallKg({ stockKg: 30, minStockKg: 15 })).toBe(0);
  });
});
