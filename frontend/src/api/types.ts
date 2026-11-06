// Shapes of the RoastBench REST API. The contract is documented in docs/openapi.json.

export type EventType =
  | 'CHARGE'
  | 'TURNING_POINT'
  | 'DRY_END'
  | 'FIRST_CRACK_START'
  | 'FIRST_CRACK_END'
  | 'SECOND_CRACK_START'
  | 'SECOND_CRACK_END'
  | 'DROP'
  | 'COOL';

export type SourceFormat = 'CSV' | 'ARTISAN_ALOG' | 'MANUAL' | 'DEMO';
export type StockStatus = 'OK' | 'LOW' | 'OUT';
export type PhaseName = 'DRYING' | 'MAILLARD' | 'DEVELOPMENT';
export type MovementType =
  | 'RECEIPT'
  | 'ROAST_CONSUMPTION'
  | 'ADJUSTMENT'
  | 'TRANSFER_IN'
  | 'TRANSFER_OUT'
  | 'ROAST_REVERSAL';

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface ProfileRef {
  id: number;
  name: string;
}

export interface LotUsage {
  lotId: number;
  code: string;
  name: string;
  country: string;
  greenWeightG: number;
}

export interface RoastSummary {
  id: number;
  batchNo: number;
  batchCode: string;
  name: string;
  roastedAt: string;
  machine: string | null;
  profile: ProfileRef | null;
  referenceOfProfile: boolean;
  greenWeightG: number | null;
  roastedWeightG: number | null;
  lossPercent: number | null;
  totalTimeSeconds: number;
  dryEndSeconds: number | null;
  firstCrackSeconds: number | null;
  developmentSeconds: number | null;
  dtrPercent: number | null;
  dropBeanTemp: number;
  sourceFormat: SourceFormat;
  lots: LotUsage[];
}

export interface RoastEvent {
  type: EventType;
  label: string;
  timeSeconds: number;
  beanTemp: number | null;
  estimated: boolean;
}

export interface Phase {
  phase: PhaseName;
  startSeconds: number;
  endSeconds: number;
  durationSeconds: number;
  percentOfTotal: number;
  startBeanTemp: number;
  endBeanTemp: number;
  averageRor: number;
  estimated: boolean;
}

export interface Metrics {
  totalTimeSeconds: number;
  dropEstimated: boolean;
  chargeBeanTemp: number;
  dropBeanTemp: number;
  turningPointSeconds: number;
  turningPointBeanTemp: number;
  averageRorAfterTurningPoint: number;
  phases: Phase[];
  developmentTimeRatioPercent: number | null;
  roastLossPercent: number | null;
}

export interface RoastDetail {
  summary: RoastSummary;
  notes: string | null;
  sourceFilename: string | null;
  events: RoastEvent[];
  metrics: Metrics;
}

export interface Curve {
  rorWindowSeconds: number;
  timeSeconds: number[];
  beanTemp: number[];
  envTemp: number[] | null;
  ror: number[];
}

export interface CompareItem {
  roast: RoastSummary;
  curve: Curve;
  metrics: Metrics;
  events: RoastEvent[];
}

export interface CompareResponse {
  items: CompareItem[];
}

export interface PhaseDelta {
  phase: PhaseName;
  durationDeltaSeconds: number;
  averageRorDelta: number;
}

export interface ComparisonResult {
  overlapSeconds: number;
  gridSeconds: number[];
  beanTempDelta: number[];
  meanAbsDeviation: number;
  maxAbsDeviation: number;
  maxDeviationAtSeconds: number;
  rmse: number;
  meanSignedDeviation: number;
  dryEndDeltaSeconds: number | null;
  firstCrackDeltaSeconds: number | null;
  dropDeltaSeconds: number;
  dropBeanTempDelta: number;
  dtrDeltaPercentPoints: number | null;
  roastLossDeltaPercentPoints: number | null;
  phases: PhaseDelta[];
}

export interface Comparison {
  roastId: number;
  referenceId: number;
  rorWindowSeconds: number;
  result: ComparisonResult;
}

export interface ImportPreview {
  filename: string;
  format: SourceFormat;
  title: string | null;
  beans: string | null;
  machine: string | null;
  roastedAt: string | null;
  greenWeightG: number | null;
  roastedWeightG: number | null;
  warnings: string[];
  events: RoastEvent[];
  curve: Curve;
  metrics: Metrics;
}

export interface LotWeight {
  lotId: number;
  greenWeightG: number;
}

export interface ImportRequest {
  name?: string;
  machine?: string;
  roastedAt?: string;
  profileId?: number;
  notes?: string;
  greenWeightG?: number;
  roastedWeightG?: number;
  lots?: LotWeight[];
}

export interface RoastUpdate {
  name?: string;
  machine?: string;
  notes?: string;
  roastedWeightG?: number;
  events?: Partial<Record<EventType, number | null>>;
}

export interface Lot {
  id: number;
  code: string;
  name: string;
  country: string;
  region: string | null;
  producer: string | null;
  variety: string | null;
  process: string | null;
  altitudeM: number | null;
  cropYear: number | null;
  supplier: string | null;
  pricePerKg: number | null;
  minStockKg: number;
  notes: string | null;
  archived: boolean;
  stockKg: number;
  status: StockStatus;
  avgDailyUseKg: number;
  daysOfStockLeft: number | null;
  createdAt: string;
}

export interface LotRequest {
  code: string;
  name: string;
  country: string;
  region?: string;
  producer?: string;
  variety?: string;
  process?: string;
  altitudeM?: number;
  cropYear?: number;
  supplier?: string;
  pricePerKg?: number;
  minStockKg?: number;
  notes?: string;
}

export interface LocationItem {
  id: number;
  name: string;
}

export interface StockByLocation {
  locationId: number;
  locationName: string;
  stockKg: number;
}

export interface Movement {
  id: number;
  lotId: number;
  locationId: number;
  locationName: string;
  type: MovementType;
  typeLabel: string;
  quantityKg: number;
  balanceKg: number;
  roastId: number | null;
  reason: string | null;
  occurredAt: string;
}

export interface Alert {
  lotId: number;
  code: string;
  name: string;
  country: string;
  stockKg: number;
  minStockKg: number;
  severity: StockStatus;
  daysOfStockLeft: number | null;
}

export interface CountryStock {
  country: string;
  stockKg: number;
}

export interface InventorySummary {
  totalStockKg: number;
  lotCount: number;
  lowStockCount: number;
  outOfStockCount: number;
  stockValue: number;
  byCountry: CountryStock[];
}

export interface ReferenceRoast {
  id: number;
  batchCode: string;
  name: string;
}

export interface Profile {
  id: number;
  name: string;
  description: string | null;
  referenceRoast: ReferenceRoast | null;
  roastCount: number;
  avgDtrPercent: number | null;
  avgLossPercent: number | null;
  avgTotalTimeSeconds: number | null;
}

export interface ProfileRequest {
  name: string;
  description?: string;
}

export interface Dashboard {
  totalRoasts: number;
  roastsLast30Days: number;
  avgDtrPercentLast30Days: number | null;
  avgLossPercentLast30Days: number | null;
  kgRoastedLast30Days: number;
  inventory: InventorySummary;
  alerts: Alert[];
  recentRoasts: RoastSummary[];
}

export interface RoastListParams {
  q?: string;
  profileId?: number;
  lotId?: number;
  sort?: string;
  direction?: 'asc' | 'desc';
  page?: number;
  size?: number;
}
