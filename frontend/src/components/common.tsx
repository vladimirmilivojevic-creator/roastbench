import {
  Alert,
  AlertTitle,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  Stack,
  Typography,
} from '@mui/material';
import type { ReactNode } from 'react';
import { ApiError } from '../api/client';
import type { StockStatus } from '../api/types';
import { statusColor, statusLabel } from '../domain/stock';

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: ReactNode; actions?: ReactNode }) {
  return (
    <Stack
      direction={{ xs: 'column', sm: 'row' }}
      spacing={2}
      sx={{ mb: 3, alignItems: { sm: 'center' }, justifyContent: 'space-between' }}
    >
      <Box>
        <Typography variant="h4" component="h1">
          {title}
        </Typography>
        {subtitle && (
          <Typography color="text.secondary" sx={{ mt: 0.5 }}>
            {subtitle}
          </Typography>
        )}
      </Box>
      {actions && <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', rowGap: 1 }}>{actions}</Stack>}
    </Stack>
  );
}

export function MetricCard({
  label,
  value,
  hint,
  tone,
  testId,
}: {
  label: string;
  value: ReactNode;
  hint?: ReactNode;
  tone?: 'warning' | 'error' | 'success';
  testId?: string;
}) {
  return (
    <Card data-testid={testId} sx={tone ? { borderColor: `${tone}.main` } : undefined}>
      <CardContent sx={{ py: 1.75, '&:last-child': { pb: 1.75 } }}>
        <Typography variant="caption" color="text.secondary" sx={{ textTransform: 'uppercase', letterSpacing: 0.6 }}>
          {label}
        </Typography>
        <Typography variant="h5" sx={{ mt: 0.25 }} color={tone ? `${tone}.main` : undefined}>
          {value}
        </Typography>
        {hint && (
          <Typography variant="caption" color="text.secondary">
            {hint}
          </Typography>
        )}
      </CardContent>
    </Card>
  );
}

/** Responsive grid of cards: as many columns as fit at the given minimum width. */
export function CardGrid({ children, min = 170 }: { children: ReactNode; min?: number }) {
  return (
    <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: `repeat(auto-fill, minmax(${min}px, 1fr))` }}>
      {children}
    </Box>
  );
}

export function Loading({ label = 'Loading' }: { label?: string }) {
  return (
    <Box sx={{ display: 'grid', placeItems: 'center', py: 8 }} role="status" aria-label={label}>
      <CircularProgress />
    </Box>
  );
}

export function ErrorAlert({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const api = error instanceof ApiError ? error : null;
  return (
    <Alert severity="error" action={onRetry && <Button color="inherit" size="small" onClick={onRetry}>Retry</Button>}>
      <AlertTitle>{api?.title ?? 'Something went wrong'}</AlertTitle>
      {api?.message ?? (error instanceof Error ? error.message : 'Unknown error')}
      {api && api.details.length > 0 && (
        <Box component="ul" sx={{ m: 0, mt: 1, pl: 2.5 }}>
          {api.details.map((d) => (
            <li key={d}>{d}</li>
          ))}
        </Box>
      )}
    </Alert>
  );
}

export function EmptyState({ title, children, action }: { title: string; children?: ReactNode; action?: ReactNode }) {
  return (
    <Box sx={{ textAlign: 'center', py: 6, px: 2 }}>
      <Typography variant="h6">{title}</Typography>
      {children && (
        <Typography color="text.secondary" sx={{ mt: 0.5 }}>
          {children}
        </Typography>
      )}
      {action && <Box sx={{ mt: 2 }}>{action}</Box>}
    </Box>
  );
}

export function StockChip({ status, size = 'small' }: { status: StockStatus; size?: 'small' | 'medium' }) {
  return <Chip size={size} color={statusColor(status)} variant={status === 'OK' ? 'outlined' : 'filled'} label={statusLabel(status)} />;
}

export function ConfirmDialog({
  open,
  title,
  children,
  confirmLabel = 'Confirm',
  destructive,
  busy,
  onConfirm,
  onClose,
}: {
  open: boolean;
  title: string;
  children: ReactNode;
  confirmLabel?: string;
  destructive?: boolean;
  busy?: boolean;
  onConfirm: () => void;
  onClose: () => void;
}) {
  return (
    <Dialog open={open} onClose={busy ? undefined : onClose} maxWidth="xs" fullWidth>
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        <DialogContentText component="div">{children}</DialogContentText>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={busy}>
          Cancel
        </Button>
        <Button onClick={onConfirm} color={destructive ? 'error' : 'primary'} variant="contained" disabled={busy}>
          {confirmLabel}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
