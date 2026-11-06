import { describe, expect, it } from 'vitest';
import type { Curve, Phase, RoastEvent } from '../api/types';
import {
  buildDeviationChartOption,
  buildRoastChartOption,
  rorStartSeconds,
  temperatureBounds,
  tooltipFormatter,
  type ChartOptions,
  type ChartSeriesInput,
} from './roastChart';

function curve(): Curve {
  const time = Array.from({ length: 11 }, (_, i) => i * 60);
  return {
    rorWindowSeconds: 30,
    timeSeconds: time,
    beanTemp: time.map((t) => (t < 60 ? 200 - t : 100 + 0.2 * (t - 60))),
    envTemp: time.map(() => 240),
    ror: time.map((t) => (t < 60 ? -80 : 12)),
  };
}

const events: RoastEvent[] = [
  { type: 'CHARGE', label: 'Charge', timeSeconds: 0, beanTemp: 200, estimated: false },
  { type: 'TURNING_POINT', label: 'Turning point', timeSeconds: 60, beanTemp: 100, estimated: true },
  { type: 'DRY_END', label: 'Dry end', timeSeconds: 300, beanTemp: 148, estimated: false },
  { type: 'DROP', label: 'Drop', timeSeconds: 600, beanTemp: 208, estimated: false },
];

const phases: Phase[] = [
  { phase: 'DRYING', startSeconds: 0, endSeconds: 300, durationSeconds: 300, percentOfTotal: 50, startBeanTemp: 200,
    endBeanTemp: 148, averageRor: 12, estimated: false },
  { phase: 'MAILLARD', startSeconds: 300, endSeconds: 600, durationSeconds: 300, percentOfTotal: 50, startBeanTemp: 148,
    endBeanTemp: 208, averageRor: 12, estimated: false },
];

const all: ChartOptions = { showEnv: true, showRor: true, showPhases: true, showEvents: true, dark: false };

function main(extra: Partial<ChartSeriesInput> = {}): ChartSeriesInput {
  return { id: 'r1', label: 'PR-0001', curve: curve(), color: '#c2410c', role: 'main', events, phases, ...extra };
}

type Series = { id: string; name: string; data: [number, number][]; yAxisIndex: number; markLine?: { data: unknown[] };
  markArea?: { data: unknown[] } };

function seriesOf(option: unknown): Series[] {
  return (option as { series: Series[] }).series;
}

describe('buildRoastChartOption', () => {
  it('draws bean temperature, environment temperature and rate of rise for a single roast', () => {
    const series = seriesOf(buildRoastChartOption([main()], all));

    expect(series.map((s) => s.id)).toEqual(['r1-bt', 'r1-et', 'r1-ror']);
    expect(series.map((s) => s.name)).toEqual(['Bean temp (BT)', 'Environment (ET)', 'RoR']);
    expect(series.map((s) => s.yAxisIndex)).toEqual([0, 0, 1]);
  });

  it('leaves out environment temperature and rate of rise when switched off', () => {
    const option = buildRoastChartOption([main()], { ...all, showEnv: false, showRor: false });

    expect(seriesOf(option).map((s) => s.id)).toEqual(['r1-bt']);
    expect((option as { yAxis: { show: boolean }[] }).yAxis[1].show).toBe(false);
  });

  it('starts the rate of rise half a smoothing window after the turning point', () => {
    const ror = seriesOf(buildRoastChartOption([main()], all)).find((s) => s.id === 'r1-ror')!;

    // turning point at 60 s + 15 s: the first sample at or after 75 s (samples are a minute apart in this fixture)
    expect(ror.data[0][0]).toBe(120);
    expect(ror.data.every(([, v]) => v === 12)).toBe(true);
  });

  it('falls back to 30 seconds when no turning point is known', () => {
    expect(rorStartSeconds(undefined)).toBe(45);
    expect(rorStartSeconds(undefined, 60)).toBe(60);
    expect(rorStartSeconds(events)).toBe(75);
  });

  it('lists every line in the legend for one roast and one entry per roast for several', () => {
    const one = (buildRoastChartOption([main()], all) as { legend: { data: string[] } }).legend.data;
    const reference = main({ id: 'ref', label: 'PR-0002', role: 'reference', color: '#8d8d8d', events: undefined, phases: undefined });
    const other = main({ id: 'r2', label: 'PR-0003', role: 'other', color: '#1d4ed8', events: undefined, phases: undefined });
    const many = (buildRoastChartOption([reference, main(), other], all) as { legend: { data: string[] } }).legend.data;

    expect(one).toEqual(['Bean temp (BT)', 'Environment (ET)', 'RoR']);
    expect(many).toEqual(['Reference: PR-0002', 'PR-0001', 'PR-0003']);
  });

  it('marks events and phases on the bean temperature line only', () => {
    const [bt, et, ror] = seriesOf(buildRoastChartOption([main()], all));

    expect(bt.markLine!.data).toHaveLength(3); // TP, dry end, drop (charge is the axis origin)
    expect(bt.markArea!.data).toHaveLength(2);
    expect(et.markLine).toBeUndefined();
    expect(ror.markArea).toBeUndefined();
  });

  it('marks estimated events with a tilde', () => {
    const [bt] = seriesOf(buildRoastChartOption([main()], all));
    const labels = (bt.markLine!.data as { label: { formatter: string } }[]).map((d) => d.label.formatter);

    expect(labels).toEqual(['~TP', 'Dry end', 'Drop']);
  });

  it('hides event lines and phase areas when switched off', () => {
    const [bt] = seriesOf(buildRoastChartOption([main()], { ...all, showEvents: false, showPhases: false }));

    expect(bt.markLine).toBeUndefined();
    expect(bt.markArea).toBeUndefined();
  });

  it('names several roasts by label and draws the reference behind them without environment temperature', () => {
    const reference = main({ id: 'ref', label: 'PR-0002', role: 'reference', color: '#8d8d8d', events: undefined, phases: undefined });
    const other = main({ id: 'r2', label: 'PR-0003', role: 'other', color: '#1d4ed8', events: undefined, phases: undefined });
    const series = seriesOf(buildRoastChartOption([reference, main(), other], all));

    expect(series.map((s) => s.name)).toEqual([
      'Reference: PR-0002', 'Reference: PR-0002 (RoR)',
      'PR-0001', 'PR-0001 (ET)', 'PR-0001 (RoR)',
      'PR-0003', 'PR-0003 (ET)', 'PR-0003 (RoR)',
    ]);
  });

  it('sizes the axes to the data', () => {
    const option = buildRoastChartOption([main()], all) as {
      xAxis: { max: number };
      yAxis: { min: number; max: number }[];
    };

    expect(option.xAxis.max).toBe(600);
    expect(option.yAxis[0].min).toBeLessThanOrEqual(90);
    expect(option.yAxis[0].max).toBeGreaterThanOrEqual(240);
    expect(option.yAxis[1].min).toBe(0);
  });
});

describe('temperatureBounds', () => {
  it('rounds outwards to 10 degrees and ignores environment temperature when asked', () => {
    expect(temperatureBounds([main()], false)).toEqual({ min: 90, max: 220 });
    expect(temperatureBounds([main()], true).max).toBe(250);
  });

  it('has a default for empty input', () => {
    expect(temperatureBounds([], true)).toEqual({ min: 0, max: 250 });
  });
});

describe('tooltipFormatter', () => {
  it('shows the time and one line per series with units', () => {
    const html = tooltipFormatter([
      { seriesId: 'r1-bt', seriesName: 'Bean temp (BT)', marker: '*', value: [543, 196.04] },
      { seriesId: 'r1-ror', seriesName: 'RoR', marker: '*', value: [543, 8.25] },
    ]);

    expect(html).toContain('9:03');
    expect(html).toContain('196.0 °C');
    expect(html).toContain('8.3 °C/min');
  });

  it('escapes series names', () => {
    const html = tooltipFormatter([{ seriesId: 'x-bt', seriesName: '<img onerror=x>', value: [1, 2] }]);

    expect(html).not.toContain('<img');
    expect(html).toContain('&lt;img');
  });

  it('copes with an empty list', () => {
    expect(tooltipFormatter([])).toBe('');
  });
});

describe('buildDeviationChartOption', () => {
  it('draws the difference on a symmetric axis', () => {
    const option = buildDeviationChartOption([0, 1, 2], [0, 3.2, -7.4], false) as {
      yAxis: { min: number; max: number };
      series: { data: [number, number][] }[];
    };

    expect(option.yAxis.min).toBe(-10);
    expect(option.yAxis.max).toBe(10);
    expect(option.series[0].data).toEqual([[0, 0], [1, 3.2], [2, -7.4]]);
  });

  it('keeps a minimum range for a flat line', () => {
    const option = buildDeviationChartOption([0, 1], [0, 0], true) as { yAxis: { max: number } };

    expect(option.yAxis.max).toBe(5);
  });
});
