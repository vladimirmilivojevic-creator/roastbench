import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  List,
  ListItem,
  ListItemText,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material';
import { Link } from 'react-router';
import { useDashboard } from '../api/hooks';
import { CardGrid, EmptyState, ErrorAlert, Loading, MetricCard, PageHeader, StockChip } from '../components/common';
import { formatDate, formatDuration, formatKg, formatPercent, formatMoney } from '../domain/format';
import { formatDaysLeft } from '../domain/stock';

export default function DashboardPage() {
  const dashboard = useDashboard();

  if (dashboard.isPending) return <Loading />;
  if (dashboard.isError) return <ErrorAlert error={dashboard.error} onRetry={() => dashboard.refetch()} />;
  const d = dashboard.data;

  return (
    <>
      <PageHeader
        title="Dashboard"
        subtitle="Last 30 days in the roastery"
        actions={
          <Button variant="contained" component={Link} to="/import">
            Import a roast
          </Button>
        }
      />

      <Alert severity="info" sx={{ mb: 3 }}>
        All roasts and coffees in this demo are <b>synthetic</b>: the curves are generated to look like real drum
        roasts, they do not come from a real roaster.
      </Alert>

      <CardGrid>
        <MetricCard label="Roasts" value={d.roastsLast30Days} hint={`${d.totalRoasts} in total`} testId="kpi-roasts" />
        <MetricCard label="Avg. DTR" value={formatPercent(d.avgDtrPercentLast30Days)} hint="development time ratio, 30 days" />
        <MetricCard label="Avg. roast loss" value={formatPercent(d.avgLossPercentLast30Days)} hint="weight loss, 30 days" />
        <MetricCard label="Roasted" value={formatKg(d.kgRoastedLast30Days)} hint="green coffee, 30 days" />
        <MetricCard
          label="Coffee in stock"
          value={formatKg(d.inventory.totalStockKg, 0)}
          hint={`${d.inventory.lotCount} lots, ${formatMoney(d.inventory.stockValue)}`}
        />
        <MetricCard
          label="Low-stock alerts"
          value={d.alerts.length}
          tone={d.alerts.length > 0 ? 'warning' : 'success'}
          hint={d.inventory.outOfStockCount > 0 ? `${d.inventory.outOfStockCount} lot empty` : 'all lots above minimum'}
          testId="kpi-alerts"
        />
      </CardGrid>

      <Box sx={{ display: 'grid', gap: 3, mt: 3, gridTemplateColumns: { xs: '1fr', lg: '5fr 4fr' } }}>
        <Card>
          <CardContent>
            <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
              <Typography variant="h6">Latest roasts</Typography>
              <Button size="small" endIcon={<ArrowForwardIcon />} component={Link} to="/roasts">
                All roasts
              </Button>
            </Stack>
            {d.recentRoasts.length === 0 ? (
              <EmptyState title="No roasts yet">Import a roast log to see it here.</EmptyState>
            ) : (
              <Box sx={{ overflowX: 'auto' }}>
                <Table size="small" aria-label="Latest roasts" sx={{ '& td, & th': { whiteSpace: 'nowrap' } }}>
                  <TableHead>
                    <TableRow>
                      <TableCell>Batch</TableCell>
                      <TableCell>Name</TableCell>
                      <TableCell>Date</TableCell>
                      <TableCell align="right">Time</TableCell>
                      <TableCell align="right">DTR</TableCell>
                      <TableCell align="right">Loss</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {d.recentRoasts.map((r) => (
                      <TableRow key={r.id} hover>
                        <TableCell>
                          <Link to={`/roasts/${r.id}`}>{r.batchCode}</Link>
                        </TableCell>
                        <TableCell>{r.name}</TableCell>
                        <TableCell>{formatDate(r.roastedAt)}</TableCell>
                        <TableCell align="right">{formatDuration(r.totalTimeSeconds)}</TableCell>
                        <TableCell align="right">{formatPercent(r.dtrPercent)}</TableCell>
                        <TableCell align="right">{formatPercent(r.lossPercent)}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </Box>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardContent>
            <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
              <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                <WarningAmberIcon color={d.alerts.length ? 'warning' : 'disabled'} />
                <Typography variant="h6">Low stock</Typography>
              </Stack>
              <Button size="small" endIcon={<ArrowForwardIcon />} component={Link} to="/inventory">
                Green coffee
              </Button>
            </Stack>
            {d.alerts.length === 0 ? (
              <EmptyState title="Nothing to reorder">Every lot is at or above its minimum level.</EmptyState>
            ) : (
              <List dense disablePadding data-testid="alert-list">
                {d.alerts.map((a) => (
                  <ListItem key={a.lotId} disableGutters divider secondaryAction={<StockChip status={a.severity} />}>
                    <ListItemText
                      primary={
                        <Link to={`/inventory/${a.lotId}`}>
                          {a.code} &middot; {a.name}
                        </Link>
                      }
                      secondary={`${formatKg(a.stockKg)} left of ${formatKg(a.minStockKg, 0)} minimum · ${a.country} · lasts ${formatDaysLeft(a.daysOfStockLeft)}`}
                    />
                  </ListItem>
                ))}
              </List>
            )}
          </CardContent>
        </Card>
      </Box>
    </>
  );
}
