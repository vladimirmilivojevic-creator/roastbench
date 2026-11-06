import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  DEFAULT_ROR_WINDOW,
  dashboardApi,
  healthApi,
  inventoryApi,
  profilesApi,
  roastsApi,
  type LotFilters,
} from './endpoints';
import type { ImportRequest, LotRequest, ProfileRequest, RoastListParams, RoastUpdate } from './types';

// ---- queries --------------------------------------------------------------------------------------------------

export const useHealth = () =>
  useQuery({
    queryKey: ['health'],
    queryFn: healthApi.check,
    retry: false,
    refetchInterval: (query) => (query.state.data ? false : 3000),
  });

export const useDashboard = () => useQuery({ queryKey: ['dashboard'], queryFn: dashboardApi.get });

export const useRoasts = (params: RoastListParams) =>
  useQuery({ queryKey: ['roasts', params], queryFn: () => roastsApi.list(params), placeholderData: keepPreviousData });

export const useRoast = (id: number | undefined) =>
  useQuery({ queryKey: ['roast', id], queryFn: () => roastsApi.get(id!), enabled: id !== undefined });

export const useCurve = (id: number | undefined, window = DEFAULT_ROR_WINDOW) =>
  useQuery({
    queryKey: ['curve', id, window],
    queryFn: () => roastsApi.curve(id!, window),
    enabled: id !== undefined,
    placeholderData: keepPreviousData,
  });

export const useComparison = (id: number | undefined, referenceId: number | undefined, window = DEFAULT_ROR_WINDOW) =>
  useQuery({
    queryKey: ['comparison', id, referenceId, window],
    queryFn: () => roastsApi.comparison(id!, referenceId, window),
    enabled: id !== undefined && referenceId !== undefined && id !== referenceId,
    placeholderData: keepPreviousData,
  });

export const useCompare = (ids: number[], window = DEFAULT_ROR_WINDOW) =>
  useQuery({
    queryKey: ['compare', ids, window],
    queryFn: () => roastsApi.compare(ids, window),
    enabled: ids.length > 0,
    placeholderData: keepPreviousData,
  });

export const useProfiles = () => useQuery({ queryKey: ['profiles'], queryFn: profilesApi.list });

export const useLots = (filters: LotFilters) =>
  useQuery({ queryKey: ['lots', filters], queryFn: () => inventoryApi.lots(filters), placeholderData: keepPreviousData });

export const useLot = (id: number) => useQuery({ queryKey: ['lot', id], queryFn: () => inventoryApi.lot(id) });

export const useLotStock = (id: number) =>
  useQuery({ queryKey: ['lot-stock', id], queryFn: () => inventoryApi.stock(id) });

export const useLotMovements = (id: number) =>
  useQuery({ queryKey: ['lot-movements', id], queryFn: () => inventoryApi.movements(id) });

export const useLocations = () => useQuery({ queryKey: ['locations'], queryFn: inventoryApi.locations });

export const useInventorySummary = () => useQuery({ queryKey: ['inventory-summary'], queryFn: inventoryApi.summary });

export const useAlerts = () => useQuery({ queryKey: ['alerts'], queryFn: inventoryApi.alerts });

// ---- mutations ------------------------------------------------------------------------------------------------

/** Every change can affect stock, metrics and lists at once, so a mutation refreshes all cached data. */
function useRefreshingMutation<TVariables, TResult>(fn: (variables: TVariables) => Promise<TResult>) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: () => client.invalidateQueries({ predicate: (q) => q.queryKey[0] !== 'health' }),
  });
}

export const useImportRoast = () =>
  useRefreshingMutation(({ file, request }: { file: File; request: ImportRequest }) => roastsApi.import(file, request));

export const usePreviewImport = () => useMutation({ mutationFn: (file: File) => roastsApi.preview(file) });

export const useUpdateRoast = () =>
  useRefreshingMutation(({ id, body }: { id: number; body: RoastUpdate }) => roastsApi.update(id, body));

export const useAssignProfile = () =>
  useRefreshingMutation(({ id, profileId }: { id: number; profileId: number | null }) =>
    roastsApi.assignProfile(id, profileId));

export const useDeleteRoast = () => useRefreshingMutation((id: number) => roastsApi.remove(id));

export const useCreateProfile = () => useRefreshingMutation((body: ProfileRequest) => profilesApi.create(body));

export const useUpdateProfile = () =>
  useRefreshingMutation(({ id, body }: { id: number; body: ProfileRequest }) => profilesApi.update(id, body));

export const useDeleteProfile = () => useRefreshingMutation((id: number) => profilesApi.remove(id));

export const useSetReference = () =>
  useRefreshingMutation(({ profileId, roastId }: { profileId: number; roastId: number | null }) =>
    profilesApi.setReference(profileId, roastId));

export const useCreateLot = () => useRefreshingMutation((body: LotRequest) => inventoryApi.createLot(body));

export const useUpdateLot = () =>
  useRefreshingMutation(({ id, body }: { id: number; body: LotRequest }) => inventoryApi.updateLot(id, body));

export const useArchiveLot = () =>
  useRefreshingMutation(({ id, archived }: { id: number; archived: boolean }) =>
    archived ? inventoryApi.archiveLot(id) : inventoryApi.restoreLot(id));

export const useReceive = () =>
  useRefreshingMutation(({ id, ...body }: { id: number; locationId: number; quantityKg: number; reason?: string }) =>
    inventoryApi.receive(id, body));

export const useAdjust = () =>
  useRefreshingMutation(({ id, ...body }: { id: number; locationId: number; deltaKg: number; reason: string }) =>
    inventoryApi.adjust(id, body));

export const useTransfer = () =>
  useRefreshingMutation(
    ({ id, ...body }: { id: number; fromLocationId: number; toLocationId: number; quantityKg: number }) =>
      inventoryApi.transfer(id, body),
  );

export const useCreateLocation = () => useRefreshingMutation((name: string) => inventoryApi.createLocation(name));
