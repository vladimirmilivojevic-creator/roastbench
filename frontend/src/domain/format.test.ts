import { describe, expect, it } from 'vitest';
import {
  DASH,
  formatDate,
  formatDateTime,
  formatDuration,
  formatKg,
  formatPercent,
  formatSignedDuration,
  formatTemp,
  formatWeightG,
  parseDuration,
  signed,
} from './format';

describe('formatDuration', () => {
  it('formats seconds as m:ss', () => {
    expect(formatDuration(0)).toBe('0:00');
    expect(formatDuration(543)).toBe('9:03');
    expect(formatDuration(600)).toBe('10:00');
    expect(formatDuration(59.6)).toBe('1:00');
  });

  it('shows a dash for missing values', () => {
    expect(formatDuration(null)).toBe(DASH);
    expect(formatDuration(undefined)).toBe(DASH);
    expect(formatDuration(Number.NaN)).toBe(DASH);
  });

  it('keeps the sign of negative values', () => {
    expect(formatDuration(-75)).toBe('-1:15');
  });
});

describe('formatSignedDuration', () => {
  it('adds an explicit sign', () => {
    expect(formatSignedDuration(12)).toBe('+0:12');
    expect(formatSignedDuration(-8)).toBe('-0:08');
  });

  it('does not sign a zero difference', () => {
    expect(formatSignedDuration(0)).toBe('0:00');
    expect(formatSignedDuration(0.2)).toBe('0:00');
  });
});

describe('parseDuration', () => {
  it('reads m:ss, h:mm:ss and plain seconds', () => {
    expect(parseDuration('9:03')).toBe(543);
    expect(parseDuration('09:03')).toBe(543);
    expect(parseDuration('1:01:05')).toBe(3665);
    expect(parseDuration('543')).toBe(543);
    expect(parseDuration(' 8:20.5 ')).toBe(500.5);
  });

  it('rejects empty and malformed text', () => {
    expect(parseDuration('')).toBeNull();
    expect(parseDuration('abc')).toBeNull();
    expect(parseDuration('9:75')).toBeNull();
    expect(parseDuration('9:3')).toBeNull();
    expect(parseDuration('-5')).toBeNull();
  });

  it('round-trips with formatDuration', () => {
    expect(parseDuration(formatDuration(612))).toBe(612);
  });
});

describe('number formatting', () => {
  it('formats percent and temperature with one decimal', () => {
    expect(formatPercent(16.437)).toBe('16.4 %');
    expect(formatTemp(209.26)).toBe('209.3 °C');
    expect(formatPercent(null)).toBe(DASH);
  });

  it('switches from grams to kilograms at 1 kg', () => {
    expect(formatWeightG(250)).toBe('250 g');
    expect(formatWeightG(5000)).toBe('5 kg');
    expect(formatWeightG(2500)).toBe('2.5 kg');
    expect(formatWeightG(null)).toBe(DASH);
  });

  it('formats kilograms', () => {
    expect(formatKg(47.4)).toBe('47.4 kg');
    expect(formatKg(7, 0)).toBe('7 kg');
  });

  it('signs differences and never shows negative zero', () => {
    expect(signed(3.24, 1, ' s')).toBe('+3.2 s');
    expect(signed(-1.26, 1)).toBe('-1.3');
    expect(signed(-0.04, 1)).toBe('0.0');
    expect(signed(0, 1)).toBe('0.0');
    expect(signed(null)).toBe(DASH);
  });
});

describe('date formatting', () => {
  it('formats dates in UTC', () => {
    expect(formatDate('2026-10-12T23:30:00Z')).toBe('12 Oct 2026');
    expect(formatDateTime('2026-10-12T08:05:00Z')).toBe('12 Oct 2026, 08:05');
    expect(formatDate(null)).toBe(DASH);
  });
});
