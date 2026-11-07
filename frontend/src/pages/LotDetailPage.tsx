import ArchiveOutlinedIcon from '@mui/icons-material/ArchiveOutlined';
import EditIcon from '@mui/icons-material/Edit';
import UnarchiveOutlinedIcon from '@mui/icons-material/UnarchiveOutlined';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material';
import { useState } from 'react';
import { Link, useParams } from 'react-router';
import { useArchiveLot, useLot, useLotMovements, useLotStock } from '../api/hooks';
import type { MovementType } from '../api/types';
import { CardGrid, ErrorAlert, Loading, MetricCard, PageHeader, StockChip } from '../components/common';
import LotFormDialog from '../components/LotFormDialog';
import MovementDialog, { type MovementKind } from '../components/MovementDialogs';
import { formatDateTime, formatKg, formatMoney } from '../domain/format';
import { formatDaysLeft, shortfallKg } from '../domain/stock';

const TYPE_COLOR: Record<MovementType, 'success' | 'default' | 'warning' | 'info' | 'error'> = {
  RECEIPT: 'success',
  ROAST_CONSUMPTION: 'default',
  ADJUSTMENT: 'warning',
  TRANSFER_IN: 'info',
  TRANSFER_OUT: 'info',
  ROAST_REVERSAL: 'success',
};

export default function LotDetailPage() {
  const id = Number(useParams().id);
  const lot = useLot(id);
  const stock = useLotStock(id);
  const movements = useLotMovements(id);
  const archive = useArchiveLot();
  const [dialog, setDialog] = useState<MovementKind | null>(null);
  const [editing, setEditing] = useState(false);

  if (lot.isPending) return <Loading />;
  if (lot.isError) return <ErrorAlert error={lot.error} onRetry={() => lot.refetch()} />;
  const l = lot.data;
  const shortfall = shortfallKg(l);

  return (
    <>
      <PageHeader
        title={`${l.code} · ${l.name}`}
        subtitle={[l.country, l.region, l.producer].filter(Boolean).join(' · ')}
        actions={
          <>
            <Button variant="contained" disabled={l.archived} onClick={() => setDialog('receive')} data-testid="receive">
              Receive
            </Button>
            <Button variant="outlined" disabled={l.archived} onClick={() => setDialog('adjust')} data-testid="adjust">
              Adjust
            </Button>
            <Button variant="outlined" disabled={l.archived} onClick={() => setDialog('transfer')}>
              Transfer
            </Button>
            <Button startIcon={<EditIcon />} onClick={() => setEditing(true)}>
              Edit
            </Button>
            <Button
              color="inherit"
              startIcon={l.archived ? <UnarchiveOutlinedIcon /> : <ArchiveOutlinedIcon />}
              onClick={() => archive.mutate({ id, archived: !l.archived })}
              disabled={archive.isPending}
            >
              {l.archived ? 'Restore' : 'Archive'}
            </Button>
          </>
        }
      />

      {l.archived && <Alert severity="info" sx={{ mb: 2 }}>This lot is archived: no new stock movements. Its history is kept.</Alert>}
      {archive.isError && <Box sx={{ mb: 2 }}><ErrorAlert error={archive.error} /></Box>}
      {l.status !== 'OK' && !l.archived && l.minStockKg > 0 && (
        <Alert severity={l.status === 'OUT' ? 'error' : 'warning'} sx={{ mb: 2 }} data-testid="lot-alert">
          {l.status === 'OUT' ? 'This lot is empty. ' : 'Stock is below the minimum level. '}
          Order at least {formatKg(shortfall)} to get back to {formatKg(l.minStockKg, 0)}.
        </Alert>
      )}

      <CardGrid>
        <MetricCard label="In stock" value={formatKg(l.stockKg, 2)} hint={<StockChip status={l.status} />} testId="lot-stock" />
        <MetricCard label="Minimum level" value={l.minStockKg > 0 ? formatKg(l.minStockKg, 0) : '–'} hint="low-stock alert below this" />
        <MetricCard label="Use per day" value={l.avgDailyUseKg > 0 ? formatKg(l.avgDailyUseKg, 2) : '–'} hint="average of the last 30 days" />
        <MetricCard label="Lasts about" value={formatDaysLeft(l.daysOfStockLeft)} hint="at the current pace" />
        <MetricCard label="Stock value" value={l.pricePerKg !== null ? formatMoney(l.stockKg * l.pricePerKg) : '–'} hint={l.pricePerKg !== null ? `${formatMoney(l.pricePerKg)}/kg` : 'no price set'} />
      </CardGrid>

      <Box sx={{ display: 'grid', gap: 3, mt: 3, gridTemplateColumns: { xs: '1fr', lg: '1fr 2fr' } }}>
        <Stack spacing={3}>
          <Card>
            <CardContent>
              <Typography variant="h6" sx={{ mb: 1 }}>
                Origin
              </Typography>
              <Table size="small" aria-label="Origin details">
                <TableBody>
                  {[
                    ['Country', l.country],
                    ['Region', l.region],
                    ['Producer', l.producer],
                    ['Variety', l.variety],
                    ['Process', l.process],
                    ['Altitude', l.altitudeM ? `${l.altitudeM} m` : null],
                    ['Crop year', l.cropYear],
                    ['Supplier', l.supplier],
                  ].map(([label, value]) => (
                    <TableRow key={String(label)}>
                      <TableCell component="th" scope="row" sx={{ color: 'text.secondary', border: 0, pl: 0 }}>
                        {label}
                      </TableCell>
                      <TableCell sx={{ border: 0 }}>{value ?? '–'}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
              {l.notes && (
                <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                  {l.notes}
                </Typography>
              )}
              <Button size="small" sx={{ mt: 1 }} component={Link} to={`/roasts?lotId=${l.id}`}>
                Roasts that used this lot
              </Button>
            </CardContent>
          </Card>

          <Card>
            <CardContent>
              <Typography variant="h6" sx={{ mb: 1 }}>
                Stock by location
              </Typography>
              {stock.isPending ? (
                <Loading />
              ) : stock.data && stock.data.length > 0 ? (
                <Table size="small" aria-label="Stock by location">
                  <TableBody>
                    {stock.data.map((s) => (
                      <TableRow key={s.locationId}>
                        <TableCell sx={{ pl: 0 }}>{s.locationName}</TableCell>
                        <TableCell align="right" sx={{ fontWeight: 700 }}>
                          {formatKg(s.stockKg, 2)}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              ) : (
                <Typography color="text.secondary">Nothing in stock at any location.</Typography>
              )}
            </CardContent>
          </Card>
        </Stack>

        <Card>
          <CardContent>
            <Typography variant="h6" sx={{ mb: 0.5 }}>
              Stock ledger
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
              Every change is a new line. Nothing is edited or deleted. Newest first. Balance = what was left after that line.
            </Typography>
            {movements.isPending ? (
              <Loading />
            ) : movements.isError ? (
              <ErrorAlert error={movements.error} />
            ) : (
              <Box sx={{ overflowX: 'auto' }}>
                <Table size="small" aria-label="Stock ledger">
                  <TableHead>
                    <TableRow>
                      <TableCell>Date</TableCell>
                      <TableCell>Movement</TableCell>
                      <TableCell>Location</TableCell>
                      <TableCell>Note</TableCell>
                      <TableCell align="right">Quantity</TableCell>
                      <TableCell align="right">Balance</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {movements.data.map((m) => (
                      <TableRow key={m.id} data-testid="movement-row">
                        <TableCell sx={{ whiteSpace: 'nowrap' }}>{formatDateTime(m.occurredAt)}</TableCell>
                        <TableCell>
                          <Chip size="small" color={TYPE_COLOR[m.type]} variant={m.type === 'ROAST_CONSUMPTION' ? 'outlined' : 'filled'} label={m.typeLabel} />
                        </TableCell>
                        <TableCell>{m.locationName}</TableCell>
                        <TableCell>
                          {m.roastId ? <Link to={`/roasts/${m.roastId}`}>{m.reason ?? 'Roast'}</Link> : (m.reason ?? '–')}
                        </TableCell>
                        <TableCell align="right" sx={{ color: m.quantityKg < 0 ? 'error.main' : 'success.main', fontWeight: 700 }}>
                          {m.quantityKg > 0 ? '+' : ''}
                          {m.quantityKg.toFixed(3)} kg
                        </TableCell>
                        <TableCell align="right">{m.balanceKg.toFixed(3)} kg</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </Box>
            )}
          </CardContent>
        </Card>
      </Box>

      {dialog && <MovementDialog kind={dialog} lot={l} stockByLocation={stock.data ?? []} onClose={() => setDialog(null)} />}
      {editing && <LotFormDialog lot={l} onClose={() => setEditing(false)} />}
    </>
  );
}
