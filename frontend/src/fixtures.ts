import type { Dashboard, ImportPreview, Lot, Metrics, RoastDetail, RoastSummary } from './api/types';

// Small, hand-written API responses shared by the component tests.

export const summary: RoastSummary = {
  id: 1,
  batchNo: 1,
  batchCode: 'PR-0001',
  name: 'Guji Natural filter 2',
  roastedAt: '2026-10-05T09:00:00Z',
  machine: 'Demo 5 kg drum roaster',
  profile: { id: 1, name: 'Filter light' },
  referenceOfProfile: true,
  greenWeightG: 3000,
  roastedWeightG: 2625,
  lossPercent: 12.5,
  totalTimeSeconds: 640,
  dryEndSeconds: 256,
  firstCrackSeconds: 535,
  developmentSeconds: 105,
  dtrPercent: 16.4,
  dropBeanTemp: 209.1,
  sourceFormat: 'DEMO',
  lots: [{ lotId: 1, code: 'ETH-GUJI-25', name: 'Guji Hambela Natural', country: 'Ethiopia', greenWeightG: 3000 }],
};

export const metrics: Metrics = {
  totalTimeSeconds: 640,
  dropEstimated: false,
  chargeBeanTemp: 200,
  dropBeanTemp: 209.1,
  turningPointSeconds: 54,
  turningPointBeanTemp: 101.4,
  averageRorAfterTurningPoint: 11.1,
  phases: [
    { phase: 'DRYING', startSeconds: 0, endSeconds: 256, durationSeconds: 256, percentOfTotal: 40, startBeanTemp: 200, endBeanTemp: 150, averageRor: 14.3, estimated: false },
    { phase: 'MAILLARD', startSeconds: 256, endSeconds: 535, durationSeconds: 279, percentOfTotal: 43.6, startBeanTemp: 150, endBeanTemp: 196, averageRor: 9.9, estimated: false },
    { phase: 'DEVELOPMENT', startSeconds: 535, endSeconds: 640, durationSeconds: 105, percentOfTotal: 16.4, startBeanTemp: 196, endBeanTemp: 209.1, averageRor: 7.5, estimated: false },
  ],
  developmentTimeRatioPercent: 16.4,
  roastLossPercent: 12.5,
};

export const detail: RoastDetail = {
  summary,
  notes: null,
  sourceFilename: null,
  events: [
    { type: 'CHARGE', label: 'Charge', timeSeconds: 0, beanTemp: 200, estimated: false },
    { type: 'TURNING_POINT', label: 'Turning point', timeSeconds: 54, beanTemp: 101.4, estimated: true },
    { type: 'DRY_END', label: 'Dry end', timeSeconds: 256, beanTemp: 150, estimated: false },
    { type: 'FIRST_CRACK_START', label: 'First crack start', timeSeconds: 535, beanTemp: 196, estimated: false },
    { type: 'DROP', label: 'Drop', timeSeconds: 640, beanTemp: 209.1, estimated: false },
  ],
  metrics,
};

export const lowLot: Lot = {
  id: 4,
  code: 'KEN-NYERI-25',
  name: 'Nyeri AA Washed',
  country: 'Kenya',
  region: 'Nyeri',
  producer: null,
  variety: 'SL28, SL34',
  process: 'Washed',
  altitudeM: 1800,
  cropYear: 2025,
  supplier: null,
  pricePerKg: 17.9,
  minStockKg: 15,
  notes: null,
  archived: false,
  stockKg: 8,
  status: 'LOW',
  avgDailyUseKg: 0.13,
  daysOfStockLeft: 61,
  createdAt: '2026-09-01T00:00:00Z',
};

export const dashboard: Dashboard = {
  totalRoasts: 22,
  roastsLast30Days: 17,
  avgDtrPercentLast30Days: 16.82,
  avgLossPercentLast30Days: 14.31,
  kgRoastedLast30Days: 63.5,
  inventory: { totalStockKg: 352.4, lotCount: 7, lowStockCount: 2, outOfStockCount: 1, stockValue: 3120.5, byCountry: [] },
  alerts: [
    { lotId: 7, code: 'SUM-MAND-25', name: 'Mandheling Wet-hulled', country: 'Indonesia', stockKg: 0, minStockKg: 10, severity: 'OUT', daysOfStockLeft: null },
    { lotId: 4, code: 'KEN-NYERI-25', name: 'Nyeri AA Washed', country: 'Kenya', stockKg: 8, minStockKg: 15, severity: 'LOW', daysOfStockLeft: 61 },
  ],
  recentRoasts: [summary],
};

export const preview: ImportPreview = {
  filename: 'guji.csv',
  format: 'CSV',
  title: 'Guji test',
  beans: null,
  machine: 'Giesen W6',
  roastedAt: '2026-10-12T08:30:00Z',
  greenWeightG: 5000,
  roastedWeightG: 4275,
  warnings: ['CHARGE is not marked; the first sample is treated as charge'],
  events: detail.events,
  curve: { rorWindowSeconds: 30, timeSeconds: [0, 1, 2], beanTemp: [200, 150, 100], envTemp: null, ror: [0, 0, 0] },
  metrics,
};
