import type { EventType, PhaseName } from '../api/types';

export const DASH = '–';

type Maybe = number | null | undefined;

function finite(value: Maybe): value is number {
  return value !== null && value !== undefined && Number.isFinite(value);
}

/** Seconds as m:ss, e.g. 543 -> "9:03". */
export function formatDuration(seconds: Maybe): string {
  if (!finite(seconds)) return DASH;
  const total = Math.round(Math.abs(seconds));
  const minutes = Math.floor(total / 60);
  const rest = total % 60;
  return `${seconds < 0 && total > 0 ? '-' : ''}${minutes}:${String(rest).padStart(2, '0')}`;
}

/** A difference of durations with an explicit sign, e.g. "+0:12" or "-0:08". */
export function formatSignedDuration(seconds: Maybe): string {
  if (!finite(seconds)) return DASH;
  const text = formatDuration(Math.abs(seconds));
  if (Math.round(Math.abs(seconds)) === 0) return text;
  return `${seconds > 0 ? '+' : '-'}${text}`;
}

/** Reads "m:ss", "h:mm:ss" or plain seconds. Returns null for empty or invalid text. */
export function parseDuration(text: string): number | null {
  const value = text.trim();
  if (!value) return null;
  if (/^\d+(\.\d+)?$/.test(value)) return Number(value);
  const match = /^(?:(\d+):)?(\d{1,2}):(\d{2}(?:\.\d+)?)$/.exec(value);
  if (!match) return null;
  const hours = match[1] ? Number(match[1]) : 0;
  const minutes = Number(match[2]);
  const seconds = Number(match[3]);
  if (seconds >= 60) return null;
  return hours * 3600 + minutes * 60 + seconds;
}

export function formatPercent(value: Maybe, digits = 1): string {
  return finite(value) ? `${value.toFixed(digits)} %` : DASH;
}

export function formatTemp(value: Maybe, digits = 1): string {
  return finite(value) ? `${value.toFixed(digits)} °C` : DASH;
}

export function formatRor(value: Maybe): string {
  return finite(value) ? `${value.toFixed(1)} °C/min` : DASH;
}

function trimZeros(value: number, digits: number): string {
  return String(Number(value.toFixed(digits)));
}

/** Grams for small amounts, kilograms from 1 kg: 250 -> "250 g", 5000 -> "5 kg". */
export function formatWeightG(grams: Maybe): string {
  if (!finite(grams)) return DASH;
  return grams >= 1000 ? `${trimZeros(grams / 1000, 2)} kg` : `${trimZeros(grams, 1)} g`;
}

export function formatKg(kg: Maybe, digits = 1): string {
  return finite(kg) ? `${kg.toFixed(digits)} kg` : DASH;
}

export function formatMoney(value: Maybe): string {
  return finite(value) ? `€${value.toLocaleString('en-GB', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}` : DASH;
}

export function formatDate(iso: string | null | undefined): string {
  if (!iso) return DASH;
  return new Date(iso).toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' });
}

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return DASH;
  const date = new Date(iso);
  const day = date.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' });
  const time = date.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit', timeZone: 'UTC' });
  return `${day}, ${time}`;
}

/** A number with an explicit sign and unit, e.g. signed(3.24, 1, ' °C') -> "+3.2 °C". */
export function signed(value: Maybe, digits = 1, unit = ''): string {
  if (!finite(value)) return DASH;
  const text = value.toFixed(digits);
  const isZero = Number(text) === 0;
  return `${value > 0 && !isZero ? '+' : ''}${isZero ? (0).toFixed(digits) : text}${unit}`;
}

export const PHASE_LABEL: Record<PhaseName, string> = {
  DRYING: 'Drying',
  MAILLARD: 'Maillard',
  DEVELOPMENT: 'Development',
};

export const EVENT_SHORT_LABEL: Partial<Record<EventType, string>> = {
  TURNING_POINT: 'TP',
  DRY_END: 'Dry end',
  FIRST_CRACK_START: 'FC',
  FIRST_CRACK_END: 'FC end',
  SECOND_CRACK_START: 'SC',
  SECOND_CRACK_END: 'SC end',
  DROP: 'Drop',
};

export const SOURCE_LABEL: Record<string, string> = {
  CSV: 'CSV',
  ARTISAN_ALOG: 'Artisan .alog',
  MANUAL: 'Manual',
  DEMO: 'Demo data',
};
