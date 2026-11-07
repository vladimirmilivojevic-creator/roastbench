import AddIcon from '@mui/icons-material/Add';
import {
  Box,
  Button,
  Card,
  Checkbox,
  FormControl,
  FormControlLabel,
  InputLabel,
  LinearProgress,
  MenuItem,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import { useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { useInventorySummary, useLots } from '../api/hooks';
import type { StockStatus } from '../api/types';
import { CardGrid, EmptyState, ErrorAlert, Loading, MetricCard, PageHeader, StockChip } from '../components/common';
import LotFormDialog from '../components/LotFormDialog';
import { formatKg, formatMoney } from '../domain/format';
import { formatDaysLeft, statusColor, stockBarPercent } from '../domain/stock';

export default function InventoryPage() {
  const navigate = useNavigate();
  const [q, setQ] = useState('');
  const [country, setCountry] = useState('');
  const [status, setStatus] = useState<StockStatus | ''>('');
  const [includeArchived, setIncludeArchived] = useState(false);
  const [creating, setCreating] = useState(false);

  const lots = useLots({ q: q || undefined, country: country || undefined, status: status || undefined, includeArchived });
  const everyLot = useLots({ includeArchived: true });
  const summary = useInventorySummary();
  const countries = useMemo(() => [...new Set((everyLot.data ?? []).map((l) => l.country))].sort(), [everyLot.data]);

  return (
    <>
      <PageHeader
        title="Green coffee"
        subtitle="Your green coffee lots and how much is left. Every change (delivery, roast, correction) is saved as one line in a list."
        actions={
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setCreating(true)} data-testid="new-lot">
            New lot
          </Button>
        }
      />

      {summary.data && (
        <Box sx={{ mb: 3 }}>
          <CardGrid>
            <MetricCard label="In stock" value={formatKg(summary.data.totalStockKg, 1)} hint={`${summary.data.lotCount} active lots`} />
            <MetricCard label="Stock value" value={formatMoney(summary.data.stockValue)} hint="at purchase price" />
            <MetricCard label="Low stock" value={summary.data.lowStockCount} tone={summary.data.lowStockCount > 0 ? 'warning' : undefined} hint="below minimum level" testId="kpi-low" />
            <MetricCard label="Empty" value={summary.data.outOfStockCount} tone={summary.data.outOfStockCount > 0 ? 'error' : undefined} hint="no stock left" testId="kpi-out" />
          </CardGrid>
        </Box>
      )}

      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ mb: 2, alignItems: { md: 'center' } }}>
        <TextField size="small" label="Search lots" value={q} onChange={(e) => setQ(e.target.value)} sx={{ minWidth: { md: 260 } }} />
        <FormControl size="small" sx={{ minWidth: 180 }}>
          <InputLabel id="inv-country">Country</InputLabel>
          <Select labelId="inv-country" label="Country" value={country} onChange={(e) => setCountry(e.target.value)}>
            <MenuItem value="">All countries</MenuItem>
            {countries.map((c) => (
              <MenuItem key={c} value={c}>
                {c}
              </MenuItem>
            ))}
          </Select>
        </FormControl>
        <FormControl size="small" sx={{ minWidth: 160 }}>
          <InputLabel id="inv-status">Status</InputLabel>
          <Select labelId="inv-status" label="Status" value={status} onChange={(e) => setStatus(e.target.value as StockStatus | '')}>
            <MenuItem value="">Any status</MenuItem>
            <MenuItem value="OK">In stock</MenuItem>
            <MenuItem value="LOW">Low stock</MenuItem>
            <MenuItem value="OUT">Out of stock</MenuItem>
          </Select>
        </FormControl>
        <FormControlLabel control={<Checkbox checked={includeArchived} onChange={(_, v) => setIncludeArchived(v)} />} label="Show archived" />
      </Stack>

      {lots.isPending ? (
        <Loading />
      ) : lots.isError ? (
        <ErrorAlert error={lots.error} onRetry={() => lots.refetch()} />
      ) : (
        <Card>
          {lots.data.length === 0 ? (
            <EmptyState title="No lots found" action={<Button variant="contained" onClick={() => setCreating(true)}>Add a lot</Button>}>
              Add a green coffee lot, receive stock into a location and set a minimum level to get alerts.
            </EmptyState>
          ) : (
            <TableContainer>
              <Table size="small" aria-label="Green coffee lots">
                <TableHead>
                  <TableRow>
                    <TableCell>Lot</TableCell>
                    <TableCell>Origin</TableCell>
                    <TableCell>Process</TableCell>
                    <TableCell>Crop</TableCell>
                    <TableCell sx={{ minWidth: 190 }}>Stock</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell align="right">Use / day</TableCell>
                    <TableCell align="right">Lasts</TableCell>
                    <TableCell align="right">Price</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {lots.data.map((l) => (
                    <TableRow key={l.id} hover data-testid="lot-row" onClick={() => navigate(`/inventory/${l.id}`)} sx={{ cursor: 'pointer', opacity: l.archived ? 0.55 : 1 }}>
                      <TableCell>
                        <Link to={`/inventory/${l.id}`} onClick={(e) => e.stopPropagation()}>
                          <b>{l.code}</b>
                        </Link>
                        <Typography variant="body2" color="text.secondary">
                          {l.name}
                          {l.archived ? ' (archived)' : ''}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        {l.country}
                        {l.region && (
                          <Typography variant="body2" color="text.secondary">
                            {l.region}
                          </Typography>
                        )}
                      </TableCell>
                      <TableCell>{l.process ?? '–'}</TableCell>
                      <TableCell>{l.cropYear ?? '–'}</TableCell>
                      <TableCell>
                        <Tooltip title={l.minStockKg > 0 ? `Minimum level ${formatKg(l.minStockKg, 0)} (middle of the bar)` : 'No minimum level set'}>
                          <Box>
                            <Typography variant="body2" sx={{ fontWeight: 700 }}>
                              {formatKg(l.stockKg)}
                              {l.minStockKg > 0 && (
                                <Typography component="span" variant="caption" color="text.secondary">
                                  {' '}
                                  / min {formatKg(l.minStockKg, 0)}
                                </Typography>
                              )}
                            </Typography>
                            <LinearProgress
                              variant="determinate"
                              value={stockBarPercent(l.stockKg, l.minStockKg)}
                              color={statusColor(l.status)}
                              sx={{ height: 6, borderRadius: 3, mt: 0.5 }}
                              aria-label={`Stock of ${l.code}`}
                            />
                          </Box>
                        </Tooltip>
                      </TableCell>
                      <TableCell>
                        <StockChip status={l.status} />
                      </TableCell>
                      <TableCell align="right">{l.avgDailyUseKg > 0 ? formatKg(l.avgDailyUseKg, 2) : '–'}</TableCell>
                      <TableCell align="right">{formatDaysLeft(l.daysOfStockLeft)}</TableCell>
                      <TableCell align="right">{l.pricePerKg !== null ? `${formatMoney(l.pricePerKg)}/kg` : '–'}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </Card>
      )}

      {creating && <LotFormDialog lot={null} onClose={() => setCreating(false)} onSaved={(lot) => navigate(`/inventory/${lot.id}`)} />}
    </>
  );
}
