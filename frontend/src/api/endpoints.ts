import { http, query } from './client';
import type {
  Alert,
  CompareResponse,
  Comparison,
  Curve,
  Dashboard,
  ImportPreview,
  ImportRequest,
  InventorySummary,
  LocationItem,
  Lot,
  LotRequest,
  Movement,
  PageResponse,
  Profile,
  ProfileRequest,
  RoastDetail,
  RoastListParams,
  RoastSummary,
  RoastUpdate,
  StockByLocation,
  StockStatus,
} from './types';

export const DEFAULT_ROR_WINDOW = 30;

export const roastsApi = {
  list: (p: RoastListParams) => http.get<PageResponse<RoastSummary>>(`/api/roasts${query({ ...p })}`),
  get: (id: number) => http.get<RoastDetail>(`/api/roasts/${id}`),
  curve: (id: number, window = DEFAULT_ROR_WINDOW) =>
    http.get<Curve>(`/api/roasts/${id}/curve${query({ rorWindowSeconds: window })}`),
  comparison: (id: number, referenceId?: number, window = DEFAULT_ROR_WINDOW) =>
    http.get<Comparison>(`/api/roasts/${id}/comparison${query({ referenceId, rorWindowSeconds: window })}`),
  compare: (ids: number[], window = DEFAULT_ROR_WINDOW) =>
    http.get<CompareResponse>(`/api/roasts/compare${query({ ids: ids.join(','), rorWindowSeconds: window })}`),
  preview: (file: File) => http.upload<ImportPreview>('/api/roasts/import/preview', file),
  import: (file: File, request: ImportRequest) =>
    http.upload<RoastDetail>('/api/roasts/import', file, { name: 'request', value: request }),
  update: (id: number, body: RoastUpdate) => http.patch<RoastDetail>(`/api/roasts/${id}`, body),
  assignProfile: (id: number, profileId: number | null) =>
    http.put<RoastDetail>(`/api/roasts/${id}/profile`, { profileId }),
  remove: (id: number) => http.delete(`/api/roasts/${id}`),
};

export const profilesApi = {
  list: () => http.get<Profile[]>('/api/profiles'),
  create: (body: ProfileRequest) => http.post<Profile>('/api/profiles', body),
  update: (id: number, body: ProfileRequest) => http.put<Profile>(`/api/profiles/${id}`, body),
  remove: (id: number) => http.delete(`/api/profiles/${id}`),
  setReference: (id: number, roastId: number | null) => http.put<Profile>(`/api/profiles/${id}/reference`, { roastId }),
};

export interface LotFilters {
  q?: string;
  country?: string;
  status?: StockStatus;
  includeArchived?: boolean;
}

export const inventoryApi = {
  lots: (f: LotFilters) => http.get<Lot[]>(`/api/lots${query({ ...f })}`),
  lot: (id: number) => http.get<Lot>(`/api/lots/${id}`),
  createLot: (body: LotRequest) => http.post<Lot>('/api/lots', body),
  updateLot: (id: number, body: LotRequest) => http.put<Lot>(`/api/lots/${id}`, body),
  archiveLot: (id: number) => http.post<Lot>(`/api/lots/${id}/archive`),
  restoreLot: (id: number) => http.post<Lot>(`/api/lots/${id}/restore`),
  stock: (id: number) => http.get<StockByLocation[]>(`/api/lots/${id}/stock`),
  movements: (id: number) => http.get<Movement[]>(`/api/lots/${id}/movements`),
  receive: (id: number, body: { locationId: number; quantityKg: number; reason?: string }) =>
    http.post<Lot>(`/api/lots/${id}/receipts`, body),
  adjust: (id: number, body: { locationId: number; deltaKg: number; reason: string }) =>
    http.post<Lot>(`/api/lots/${id}/adjustments`, body),
  transfer: (id: number, body: { fromLocationId: number; toLocationId: number; quantityKg: number }) =>
    http.post<Lot>(`/api/lots/${id}/transfers`, body),
  locations: () => http.get<LocationItem[]>('/api/locations'),
  createLocation: (name: string) => http.post<LocationItem>('/api/locations', { name }),
  summary: () => http.get<InventorySummary>('/api/inventory/summary'),
  alerts: () => http.get<Alert[]>('/api/inventory/alerts'),
};

export const dashboardApi = {
  get: () => http.get<Dashboard>('/api/dashboard'),
};

export const healthApi = {
  check: () => http.get<{ status: string }>('/actuator/health'),
};
