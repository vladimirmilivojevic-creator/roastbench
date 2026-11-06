import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  FormControl,
  InputAdornment,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  TextField,
} from '@mui/material';
import { useState } from 'react';
import { useAdjust, useCreateLocation, useLocations, useReceive, useTransfer } from '../api/hooks';
import type { Lot, StockByLocation } from '../api/types';
import { formatKg } from '../domain/format';
import { selectValue } from '../domain/select';
import { ErrorAlert } from './common';

export type MovementKind = 'receive' | 'adjust' | 'transfer';

interface Props {
  kind: MovementKind;
  lot: Lot;
  stockByLocation: StockByLocation[];
  onClose: () => void;
}

function parsePositive(text: string): number | null {
  const n = Number(text.trim().replace(',', '.'));
  return Number.isFinite(n) && n > 0 ? n : null;
}

function parseSigned(text: string): number | null {
  const n = Number(text.trim().replace(',', '.'));
  return Number.isFinite(n) && n !== 0 ? n : null;
}

const TITLES: Record<MovementKind, string> = {
  receive: 'Receive green coffee',
  adjust: 'Adjust stock',
  transfer: 'Transfer between locations',
};

/** The three ways stock changes by hand: a delivery, a count correction, a move. Roasts book their own usage. */
export default function MovementDialog({ kind, lot, stockByLocation, onClose }: Props) {
  const locations = useLocations();
  const createLocation = useCreateLocation();
  const receive = useReceive();
  const adjust = useAdjust();
  const transfer = useTransfer();
  const [locationId, setLocationId] = useState<number | ''>('');
  const [toLocationId, setToLocationId] = useState<number | ''>('');
  const [amount, setAmount] = useState('');
  const [reason, setReason] = useState('');
  const [newLocation, setNewLocation] = useState('');

  const mutation = kind === 'receive' ? receive : kind === 'adjust' ? adjust : transfer;
  const atLocation = (id: number | '') => stockByLocation.find((s) => s.locationId === id)?.stockKg ?? 0;

  const quantity = kind === 'adjust' ? parseSigned(amount) : parsePositive(amount);
  const from = locationId === '' ? null : locationId;
  const to = toLocationId === '' ? null : toLocationId;
  const valid =
    from !== null &&
    quantity !== null &&
    (kind !== 'adjust' || reason.trim() !== '') &&
    (kind !== 'transfer' || (to !== null && to !== from));

  function save() {
    if (!valid || from === null || quantity === null) return;
    const done = { onSuccess: onClose };
    if (kind === 'receive') receive.mutate({ id: lot.id, locationId: from, quantityKg: quantity, reason: reason.trim() || undefined }, done);
    else if (kind === 'adjust') adjust.mutate({ id: lot.id, locationId: from, deltaKg: quantity, reason: reason.trim() }, done);
    else if (to !== null) transfer.mutate({ id: lot.id, fromLocationId: from, toLocationId: to, quantityKg: quantity }, done);
  }

  const locationSelect = (label: string, value: number | '', onChange: (id: number | '') => void, testId: string) => (
    <FormControl size="small" fullWidth>
      <InputLabel id={`${testId}-label`}>{label}</InputLabel>
      <Select
        labelId={`${testId}-label`}
        label={label}
        value={value}
        onChange={(e) => onChange(selectValue(e.target.value))}
        data-testid={testId}
      >
        {locations.data?.map((l) => (
          <MenuItem key={l.id} value={l.id}>
            {l.name}
            {kind !== 'receive' ? ` (${formatKg(atLocation(l.id))} of ${lot.code})` : ''}
          </MenuItem>
        ))}
      </Select>
    </FormControl>
  );

  return (
    <Dialog open onClose={mutation.isPending ? undefined : onClose} maxWidth="xs" fullWidth>
      <DialogTitle>{TITLES[kind]}</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>
          {lot.code} &middot; {lot.name}. Now {formatKg(lot.stockKg)} in stock.
        </DialogContentText>
        <Stack spacing={2}>
          {locations.data && locations.data.length === 0 && (
            <Alert
              severity="info"
              action={
                <Button color="inherit" size="small" disabled={!newLocation.trim() || createLocation.isPending} onClick={() => createLocation.mutate(newLocation.trim(), { onSuccess: () => setNewLocation('') })}>
                  Add
                </Button>
              }
            >
              <TextField variant="standard" placeholder="Name of your first location" value={newLocation} onChange={(e) => setNewLocation(e.target.value)} />
            </Alert>
          )}
          {locationSelect(kind === 'transfer' ? 'From location' : 'Location', locationId, setLocationId, 'movement-location')}
          {kind === 'transfer' && locationSelect('To location', toLocationId, setToLocationId, 'movement-to-location')}
          <TextField
            size="small"
            label={kind === 'adjust' ? 'Change (use - to remove)' : 'Quantity'}
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            slotProps={{ input: { endAdornment: <InputAdornment position="end">kg</InputAdornment> }, htmlInput: { inputMode: 'decimal', 'data-testid': 'movement-quantity' } }}
            helperText={kind === 'adjust' ? 'After a stock count, e.g. -0.6 for moisture loss' : undefined}
          />
          {kind !== 'transfer' && (
            <TextField
              size="small"
              label={kind === 'adjust' ? 'Reason (required)' : 'Reference (optional)'}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              slotProps={{ htmlInput: { maxLength: 300, 'data-testid': 'movement-reason' } }}
              placeholder={kind === 'receive' ? 'Delivery note, invoice number' : 'Stock count, spillage'}
            />
          )}
          {mutation.isError && <ErrorAlert error={mutation.error} />}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={mutation.isPending}>
          Cancel
        </Button>
        <Button variant="contained" onClick={save} disabled={!valid || mutation.isPending} data-testid="movement-save">
          Book it
        </Button>
      </DialogActions>
    </Dialog>
  );
}
