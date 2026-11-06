import { describe, expect, it } from 'vitest';
import { optionalId, selectValue } from './select';

describe('select helpers', () => {
  it('turn the empty option into "no value"', () => {
    expect(selectValue('')).toBe('');
    expect(selectValue(null)).toBe('');
    expect(selectValue(undefined)).toBe('');
    expect(optionalId('')).toBeUndefined();
  });

  it('turn ids into numbers', () => {
    expect(selectValue('12')).toBe(12);
    expect(selectValue(7)).toBe(7);
    expect(optionalId('3')).toBe(3);
  });
});
