import type { EChartsCoreOption } from 'echarts/core';
import type { Curve, EventType, Phase, PhaseName, RoastEvent } from '../api/types';
import { EVENT_SHORT_LABEL, PHASE_LABEL, formatDuration } from './format';

// Everything in this file is plain TypeScript: it turns API data into an ECharts option and knows nothing about
// React. That keeps the chart logic unit-testable and the React layer a thin renderer.

export const COMPARE_COLORS = [
  '#c2410c',
  '#1d4ed8',
  '#15803d',
  '#7e22ce',
  '#be123c',
  '#0f766e',
  '#a16207',
  '#4338ca',
  '#0369a1',
  '#9d174d',
];
export const REFERENCE_COLOR = '#8d8d8d';

export type SeriesRole = 'main' | 'reference' | 'other';

export interface ChartSeriesInput {
  id: string;
  label: string;
  curve: Curve;
  color: string;
  role: SeriesRole;
  /** Events and phases are drawn as marks on the bean temperature line of this series. */
  events?: RoastEvent[];
  phases?: Phase[];
}

export interface ChartOptions {
  showEnv: boolean;
  showRor: boolean;
  showPhases: boolean;
  showEvents: boolean;
  dark: boolean;
}

const MARKED_EVENTS: EventType[] = [
  'TURNING_POINT',
  'DRY_END',
  'FIRST_CRACK_START',
  'FIRST_CRACK_END',
  'SECOND_CRACK_START',
  'SECOND_CRACK_END',
  'DROP',
];

const PHASE_COLORS: Record<PhaseName, string> = {
  DRYING: 'rgba(255, 193, 7, 0.10)',
  MAILLARD: 'rgba(255, 111, 0, 0.12)',
  DEVELOPMENT: 'rgba(121, 85, 72, 0.20)',
};

/**
 * The rate of rise right after charge is dominated by the probe cooling down. It is drawn from half a smoothing window
 * after the turning point, where the window no longer reaches back into the cooling part.
 */
export function rorStartSeconds(events: RoastEvent[] | undefined, windowSeconds = 30): number {
  const turningPoint = events?.find((e) => e.type === 'TURNING_POINT');
  return (turningPoint ? turningPoint.timeSeconds : 30) + windowSeconds / 2;
}

export function temperatureBounds(series: ChartSeriesInput[], includeEnv: boolean): { min: number; max: number } {
  let min = Infinity;
  let max = -Infinity;
  for (const s of series) {
    for (const v of s.curve.beanTemp) {
      min = Math.min(min, v);
      max = Math.max(max, v);
    }
    if (includeEnv && s.curve.envTemp) {
      for (const v of s.curve.envTemp) {
        min = Math.min(min, v);
        max = Math.max(max, v);
      }
    }
  }
  if (!Number.isFinite(min) || !Number.isFinite(max)) return { min: 0, max: 250 };
  return { min: Math.max(0, Math.floor((min - 10) / 10) * 10), max: Math.ceil((max + 5) / 10) * 10 };
}

function pairs(time: number[], values: number[], fromSeconds = -Infinity): [number, number][] {
  const out: [number, number][] = [];
  for (let i = 0; i < time.length; i++) {
    if (time[i] >= fromSeconds && Number.isFinite(values[i])) out.push([time[i], values[i]]);
  }
  return out;
}

function seriesName(s: ChartSeriesInput, single: boolean, kind: 'bt' | 'et' | 'ror'): string {
  const base = s.role === 'reference' ? `Reference: ${s.label}` : s.label;
  if (single && s.role !== 'reference') {
    return kind === 'bt' ? 'Bean temp (BT)' : kind === 'et' ? 'Environment (ET)' : 'RoR';
  }
  return kind === 'bt' ? base : `${base} (${kind === 'et' ? 'ET' : 'RoR'})`;
}

interface TooltipParam {
  seriesId?: string;
  seriesName?: string;
  marker?: string;
  value: [number, number] | number[];
}

function escapeHtml(text: string): string {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

export function tooltipFormatter(raw: TooltipParam | TooltipParam[]): string {
  const params = Array.isArray(raw) ? raw : [raw];
  if (params.length === 0) return '';
  const seconds = Number(params[0].value[0]);
  let html = `<div style="font-weight:600;margin-bottom:4px">${formatDuration(seconds)}</div>`;
  for (const p of params) {
    const value = Number(p.value[1]);
    const unit = p.seriesId?.endsWith('-ror') ? ' °C/min' : ' °C';
    html += `<div>${p.marker ?? ''} ${escapeHtml(p.seriesName ?? '')}: <b>${value.toFixed(1)}${unit}</b></div>`;
  }
  return html;
}

export function buildRoastChartOption(series: ChartSeriesInput[], options: ChartOptions): EChartsCoreOption {
  const single = series.filter((s) => s.role !== 'reference').length <= 1;
  const text = options.dark ? '#d7ccc8' : '#4e342e';
  const line = options.dark ? 'rgba(255,255,255,0.12)' : 'rgba(0,0,0,0.08)';
  const markColor = options.dark ? '#bcaaa4' : '#6d4c41';
  const temp = temperatureBounds(series, options.showEnv);
  const maxTime = Math.max(60, ...series.map((s) => s.curve.timeSeconds[s.curve.timeSeconds.length - 1] ?? 0));

  let rorMax = 20;
  const echartsSeries: Record<string, unknown>[] = [];
  // With several roasts the legend lists one entry per roast (the ET and RoR lines share its colour).
  const legendNames: string[] = [];

  for (const s of series) {
    const isReference = s.role === 'reference';
    const base = { showSymbol: false, silent: false, emphasis: { focus: 'series' }, animation: false };

    const bt: Record<string, unknown> = {
      ...base,
      id: `${s.id}-bt`,
      name: seriesName(s, single, 'bt'),
      type: 'line',
      yAxisIndex: 0,
      data: pairs(s.curve.timeSeconds, s.curve.beanTemp),
      lineStyle: { width: isReference ? 3.5 : 2.2, color: s.color, opacity: isReference ? 0.5 : 1 },
      itemStyle: { color: s.color },
      z: isReference ? 1 : 3,
    };
    legendNames.push(bt.name as string);

    if (options.showEvents && s.events && s.events.length > 0) {
      bt.markLine = {
        silent: true,
        symbol: ['none', 'none'],
        animation: false,
        label: { show: true, position: 'end', color: text, fontSize: 11, distance: 4 },
        // Neighbouring events (first crack end and drop, say) sit close together: stagger their labels.
        data: s.events
          .filter((e) => MARKED_EVENTS.includes(e.type))
          .map((e, i) => ({
            xAxis: e.timeSeconds,
            label: {
              formatter: `${e.estimated ? '~' : ''}${EVENT_SHORT_LABEL[e.type] ?? e.label}`,
              offset: [0, i % 2 === 0 ? 0 : -15],
            },
            lineStyle: { type: e.estimated ? 'dotted' : 'dashed', color: markColor, width: 1 },
          })),
      };
    }
    if (options.showPhases && s.phases && s.phases.length > 0) {
      bt.markArea = {
        silent: true,
        animation: false,
        label: { show: true, position: 'insideTop', color: text, fontSize: 11 },
        data: s.phases.map((p) => [
          {
            name: `${PHASE_LABEL[p.phase]} ${p.percentOfTotal.toFixed(0)} %`,
            xAxis: p.startSeconds,
            itemStyle: { color: PHASE_COLORS[p.phase] },
          },
          { xAxis: p.endSeconds },
        ]),
      };
    }
    echartsSeries.push(bt);

    if (options.showEnv && s.curve.envTemp && !isReference) {
      const etName = seriesName(s, single, 'et');
      if (single) legendNames.push(etName);
      echartsSeries.push({
        ...base,
        id: `${s.id}-et`,
        name: etName,
        type: 'line',
        yAxisIndex: 0,
        data: pairs(s.curve.timeSeconds, s.curve.envTemp),
        lineStyle: { width: 1.3, color: s.color, type: 'dashed', opacity: 0.7 },
        itemStyle: { color: s.color },
        z: 2,
      });
    }

    if (options.showRor) {
      const from = rorStartSeconds(s.events, s.curve.rorWindowSeconds);
      const data = pairs(s.curve.timeSeconds, s.curve.ror, from);
      for (const [, v] of data) rorMax = Math.max(rorMax, v);
      const rorName = seriesName(s, single, 'ror');
      if (single) legendNames.push(rorName);
      echartsSeries.push({
        ...base,
        id: `${s.id}-ror`,
        name: rorName,
        type: 'line',
        yAxisIndex: 1,
        data,
        lineStyle: { width: isReference ? 2.5 : 1.5, color: s.color, opacity: isReference ? 0.4 : 0.8 },
        itemStyle: { color: s.color },
        z: 2,
      });
    }
  }

  return {
    animation: false,
    textStyle: { color: text },
    grid: { left: 58, right: options.showRor ? 58 : 24, top: 80, bottom: 84 },
    legend: { top: 6, type: 'scroll', data: legendNames, textStyle: { color: text } },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross', label: { formatter: (p: { value: number; axisDimension: string }) =>
        p.axisDimension === 'x' ? formatDuration(p.value) : Number(p.value).toFixed(1) } },
      formatter: tooltipFormatter,
    },
    xAxis: {
      type: 'value',
      min: 0,
      max: Math.ceil(maxTime / 60) * 60,
      minInterval: 30,
      name: 'Time (m:ss)',
      nameLocation: 'middle',
      nameGap: 30,
      nameTextStyle: { color: text },
      axisLabel: { color: text, formatter: (v: number) => formatDuration(v) },
      axisLine: { lineStyle: { color: text } },
      splitLine: { show: true, lineStyle: { color: line } },
    },
    yAxis: [
      {
        type: 'value',
        name: '°C',
        min: temp.min,
        max: temp.max,
        nameTextStyle: { color: text },
        axisLabel: { color: text },
        splitLine: { lineStyle: { color: line } },
      },
      {
        type: 'value',
        name: 'RoR °C/min',
        show: options.showRor,
        min: 0,
        max: Math.ceil(rorMax / 5) * 5,
        nameTextStyle: { color: text },
        axisLabel: { color: text },
        splitLine: { show: false },
      },
    ],
    dataZoom: [
      { type: 'inside', xAxisIndex: 0, filterMode: 'none' },
      { type: 'slider', xAxisIndex: 0, height: 18, bottom: 10, filterMode: 'none', textStyle: { color: text } },
    ],
    series: echartsSeries,
  };
}

export interface DeviationSeries {
  id: string;
  name: string;
  color: string;
  gridSeconds: number[];
  delta: number[];
}

/** Several roasts' bean temperature differences to one reference roast in a single chart. */
export function buildMultiDeviationOption(series: DeviationSeries[], dark: boolean): EChartsCoreOption {
  const text = dark ? '#d7ccc8' : '#4e342e';
  const line = dark ? 'rgba(255,255,255,0.12)' : 'rgba(0,0,0,0.08)';
  const largest = Math.max(0, ...series.flatMap((s) => s.delta.map((d) => Math.abs(d))));
  const limit = Math.max(5, Math.ceil(largest / 5) * 5);
  const maxTime = Math.max(60, ...series.map((s) => s.gridSeconds[s.gridSeconds.length - 1] ?? 0));
  return {
    animation: false,
    textStyle: { color: text },
    grid: { left: 58, right: 24, top: series.length > 1 ? 40 : 20, bottom: 40 },
    legend: series.length > 1 ? { top: 6, type: 'scroll', textStyle: { color: text } } : undefined,
    tooltip: {
      trigger: 'axis',
      formatter: (raw: TooltipParam | TooltipParam[]) => {
        const params = Array.isArray(raw) ? raw : [raw];
        if (params.length === 0) return '';
        let html = `<div style="font-weight:600;margin-bottom:4px">${formatDuration(Number(params[0].value[0]))}</div>`;
        for (const p of params) {
          const value = Number(p.value[1]);
          html += `<div>${p.marker ?? ''} ${escapeHtml(p.seriesName ?? '')}: <b>${value > 0 ? '+' : ''}${value.toFixed(1)} °C</b></div>`;
        }
        return html;
      },
    },
    xAxis: {
      type: 'value',
      min: 0,
      max: maxTime,
      minInterval: 30,
      axisLabel: { color: text, formatter: (v: number) => formatDuration(v) },
      splitLine: { show: true, lineStyle: { color: line } },
    },
    yAxis: {
      type: 'value',
      name: 'Δ °C',
      min: -limit,
      max: limit,
      nameTextStyle: { color: text },
      axisLabel: { color: text },
      splitLine: { lineStyle: { color: line } },
    },
    series: series.map((s, i) => ({
      id: s.id,
      name: s.name,
      type: 'line',
      showSymbol: false,
      animation: false,
      data: s.gridSeconds.map((t, k) => [t, s.delta[k]]),
      lineStyle: { width: 1.8, color: s.color },
      itemStyle: { color: s.color },
      areaStyle: series.length === 1 ? { color: s.color, opacity: 0.18 } : undefined,
      markLine: i === 0
        ? { silent: true, symbol: 'none', label: { show: false }, data: [{ yAxis: 0 }], lineStyle: { color: text, width: 1, opacity: 0.6 } }
        : undefined,
    })),
  };
}

/** Bean temperature difference to a reference roast over time (positive: hotter than the reference). */
export function buildDeviationChartOption(
  gridSeconds: number[],
  delta: number[],
  dark: boolean,
  color = '#c2410c',
): EChartsCoreOption {
  const text = dark ? '#d7ccc8' : '#4e342e';
  const line = dark ? 'rgba(255,255,255,0.12)' : 'rgba(0,0,0,0.08)';
  const limit = Math.max(5, Math.ceil(Math.max(...delta.map((d) => Math.abs(d)), 0) / 5) * 5);
  return {
    animation: false,
    textStyle: { color: text },
    grid: { left: 58, right: 24, top: 20, bottom: 40 },
    tooltip: {
      trigger: 'axis',
      formatter: (raw: TooltipParam | TooltipParam[]) => {
        const p = Array.isArray(raw) ? raw[0] : raw;
        const value = Number(p.value[1]);
        return `<div style="font-weight:600">${formatDuration(Number(p.value[0]))}</div>` +
          `<div>Difference: <b>${value > 0 ? '+' : ''}${value.toFixed(1)} °C</b></div>`;
      },
    },
    xAxis: {
      type: 'value',
      min: 0,
      max: gridSeconds.length ? gridSeconds[gridSeconds.length - 1] : 60,
      minInterval: 30,
      axisLabel: { color: text, formatter: (v: number) => formatDuration(v) },
      splitLine: { show: true, lineStyle: { color: line } },
    },
    yAxis: {
      type: 'value',
      name: 'Δ °C',
      min: -limit,
      max: limit,
      nameTextStyle: { color: text },
      axisLabel: { color: text },
      splitLine: { lineStyle: { color: line } },
    },
    series: [
      {
        id: 'deviation',
        name: 'Difference to reference',
        type: 'line',
        showSymbol: false,
        animation: false,
        data: gridSeconds.map((t, i) => [t, delta[i]]),
        lineStyle: { width: 1.8, color },
        areaStyle: { color, opacity: 0.18 },
        markLine: { silent: true, symbol: 'none', label: { show: false }, data: [{ yAxis: 0 }],
          lineStyle: { color: text, width: 1, opacity: 0.6 } },
      },
    ],
  };
}
