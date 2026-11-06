import {
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  InputAdornment,
  Stack,
  TextField,
} from '@mui/material';
import { useState, type ChangeEvent, type ComponentProps } from 'react';
import { ApiError } from '../api/client';
import { useCreateLot, useUpdateLot } from '../api/hooks';
import type { Lot, LotRequest } from '../api/types';
import { ErrorAlert } from './common';

function numberOrUndefined(text: string): number | undefined {
  const trimmed = text.trim();
  if (trimmed === '') return undefined;
  const n = Number(trimmed.replace(',', '.'));
  return Number.isFinite(n) ? n : undefined;
}

interface Props {
  lot: Lot | null;
  onClose: () => void;
  onSaved?: (lot: Lot) => void;
}

/** Create or edit a green coffee lot: origin, producer, process and the minimum stock level for alerts. */
export default function LotFormDialog({ lot, onClose, onSaved }: Props) {
  const [v, setV] = useState({
    code: lot?.code ?? '',
    name: lot?.name ?? '',
    country: lot?.country ?? '',
    region: lot?.region ?? '',
    producer: lot?.producer ?? '',
    variety: lot?.variety ?? '',
    process: lot?.process ?? '',
    altitudeM: lot?.altitudeM?.toString() ?? '',
    cropYear: lot?.cropYear?.toString() ?? '',
    supplier: lot?.supplier ?? '',
    pricePerKg: lot?.pricePerKg?.toString() ?? '',
    minStockKg: lot ? String(lot.minStockKg) : '',
    notes: lot?.notes ?? '',
  });
  const create = useCreateLot();
  const update = useUpdateLot();
  const mutation = lot ? update : create;
  const set = (key: keyof typeof v) => (e: ChangeEvent<HTMLInputElement>) => setV((s) => ({ ...s, [key]: e.target.value }));
  const fieldErrors = mutation.error instanceof ApiError ? mutation.error.fieldErrors : {};

  function save() {
    const body: LotRequest = {
      code: v.code.trim(),
      name: v.name.trim(),
      country: v.country.trim(),
      region: v.region.trim() || undefined,
      producer: v.producer.trim() || undefined,
      variety: v.variety.trim() || undefined,
      process: v.process.trim() || undefined,
      altitudeM: numberOrUndefined(v.altitudeM),
      cropYear: numberOrUndefined(v.cropYear),
      supplier: v.supplier.trim() || undefined,
      pricePerKg: numberOrUndefined(v.pricePerKg),
      minStockKg: numberOrUndefined(v.minStockKg) ?? 0,
      notes: v.notes.trim() || undefined,
    };
    const done = (saved: Lot) => {
      onSaved?.(saved);
      onClose();
    };
    if (lot) update.mutate({ id: lot.id, body }, { onSuccess: done });
    else create.mutate(body, { onSuccess: done });
  }

  const field = (key: keyof typeof v, label: string, extra: Partial<ComponentProps<typeof TextField>> = {}) => (
    <TextField
      size="small"
      label={label}
      value={v[key]}
      onChange={set(key)}
      error={Boolean(fieldErrors[key])}
      helperText={fieldErrors[key]}
      {...extra}
    />
  );

  return (
    <Dialog open onClose={mutation.isPending ? undefined : onClose} maxWidth="md" fullWidth>
      <DialogTitle>{lot ? `Edit ${lot.code}` : 'New green coffee lot'}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: '1fr 2fr' } }}>
            {field('code', 'Lot code', { required: true, autoFocus: true, slotProps: { htmlInput: { maxLength: 40, 'data-testid': 'lot-code' } } })}
            {field('name', 'Name', { required: true, slotProps: { htmlInput: { maxLength: 150, 'data-testid': 'lot-name' } } })}
          </Box>
          <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: 'repeat(3, 1fr)' } }}>
            {field('country', 'Country', { required: true, slotProps: { htmlInput: { 'data-testid': 'lot-country' } } })}
            {field('region', 'Region')}
            {field('producer', 'Producer / farm')}
            {field('variety', 'Variety')}
            {field('process', 'Process')}
            {field('altitudeM', 'Altitude', { slotProps: { input: { endAdornment: <InputAdornment position="end">m</InputAdornment> } } })}
            {field('cropYear', 'Crop year')}
            {field('supplier', 'Supplier')}
            {field('pricePerKg', 'Price', { slotProps: { input: { endAdornment: <InputAdornment position="end">&euro;/kg</InputAdornment> } } })}
          </Box>
          {field('minStockKg', 'Minimum stock level', {
            helperText: fieldErrors.minStockKg ?? 'You get a low-stock alert when stock falls below this',
            slotProps: { input: { endAdornment: <InputAdornment position="end">kg</InputAdornment> }, htmlInput: { 'data-testid': 'lot-min' } },
          })}
          {field('notes', 'Notes', { multiline: true, minRows: 2 })}
          {mutation.isError && <ErrorAlert error={mutation.error} />}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={mutation.isPending}>
          Cancel
        </Button>
        <Button variant="contained" onClick={save} disabled={!v.code.trim() || !v.name.trim() || !v.country.trim() || mutation.isPending} data-testid="lot-save">
          Save lot
        </Button>
      </DialogActions>
    </Dialog>
  );
}
