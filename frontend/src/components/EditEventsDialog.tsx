import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  Stack,
  TextField,
} from '@mui/material';
import { useState } from 'react';
import { useUpdateRoast } from '../api/hooks';
import type { EventType, RoastEvent } from '../api/types';
import { formatDuration, parseDuration } from '../domain/format';
import { ErrorAlert } from './common';

const EDITABLE: { type: EventType; label: string }[] = [
  { type: 'TURNING_POINT', label: 'Turning point' },
  { type: 'DRY_END', label: 'Dry end' },
  { type: 'FIRST_CRACK_START', label: 'First crack start' },
  { type: 'FIRST_CRACK_END', label: 'First crack end' },
  { type: 'SECOND_CRACK_START', label: 'Second crack start' },
  { type: 'SECOND_CRACK_END', label: 'Second crack end' },
  { type: 'DROP', label: 'Drop' },
];

interface Props {
  open: boolean;
  roastId: number;
  events: RoastEvent[];
  onClose: () => void;
}

/** Edit the marked events (m:ss from charge). Everything derived from them is recalculated by the API. */
export default function EditEventsDialog({ open, roastId, events, onClose }: Props) {
  const marked = new Map(events.filter((e) => !e.estimated).map((e) => [e.type, e.timeSeconds]));
  const [values, setValues] = useState<Record<string, string>>(() =>
    Object.fromEntries(EDITABLE.map(({ type }) => [type, marked.has(type) ? formatDuration(marked.get(type)) : ''])),
  );
  const [errors, setErrors] = useState<Record<string, string>>({});
  const update = useUpdateRoast();

  function save() {
    const changes: Partial<Record<EventType, number | null>> = {};
    const nextErrors: Record<string, string> = {};
    for (const { type } of EDITABLE) {
      const text = values[type] ?? '';
      const original = marked.get(type);
      if (text.trim() === '') {
        if (original !== undefined) changes[type] = null;
        continue;
      }
      const seconds = parseDuration(text);
      if (seconds === null) {
        nextErrors[type] = 'Use m:ss, for example 8:45';
      } else if (original === undefined || Math.abs(seconds - original) > 0.5) {
        changes[type] = seconds;
      }
    }
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) return;
    if (Object.keys(changes).length === 0) {
      onClose();
      return;
    }
    update.mutate({ id: roastId, body: { events: changes } }, { onSuccess: onClose });
  }

  return (
    <Dialog open={open} onClose={update.isPending ? undefined : onClose} maxWidth="xs" fullWidth>
      <DialogTitle>Edit events</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>
          Times are counted from charge (m:ss). Leave a field empty to remove the event. Phases, development time
          ratio and the chart are recalculated.
        </DialogContentText>
        <Stack spacing={2}>
          {EDITABLE.map(({ type, label }) => (
            <TextField
              key={type}
              size="small"
              label={label}
              placeholder="m:ss"
              value={values[type] ?? ''}
              onChange={(e) => setValues((v) => ({ ...v, [type]: e.target.value }))}
              error={Boolean(errors[type])}
              helperText={errors[type]}
              slotProps={{ htmlInput: { 'data-testid': `event-${type}` } }}
            />
          ))}
          {update.isError && <ErrorAlert error={update.error} />}
          {!marked.has('TURNING_POINT') && (
            <Alert severity="info">The turning point is detected automatically unless you set it.</Alert>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={update.isPending}>
          Cancel
        </Button>
        <Button variant="contained" onClick={save} disabled={update.isPending}>
          Save
        </Button>
      </DialogActions>
    </Dialog>
  );
}
