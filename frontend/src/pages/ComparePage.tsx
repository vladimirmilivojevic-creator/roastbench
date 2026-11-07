import { Alert, Box, Button, Card, CardContent, FormControl, InputLabel, MenuItem, Select, Stack, Table, TableBody, TableCell, TableHead, TableRow, Typography } from '@mui/material';
import { useQueries } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { roastsApi } from '../api/endpoints';
import { useCompare } from '../api/hooks';
import type { CompareItem } from '../api/types';
import { ErrorAlert, Loading, PageHeader } from '../components/common';
import EChart from '../components/EChart';
import RoastChartPanel from '../components/RoastChartPanel';
import { useColorMode } from '../colorMode';
import {
  formatDate,
  formatDuration,
  formatPercent,
  formatSignedDuration,
  formatTemp,
  formatWeightG,
  signed,
} from '../domain/format';
import { selectValue } from '../domain/select';
import {
  COMPARE_COLORS,
  REFERENCE_COLOR,
  buildMultiDeviationOption,
  type ChartSeriesInput,
} from '../domain/roastChart';

function eventTime(item: CompareItem, type: string): number | null {
  return item.events.find((e) => e.type === type)?.timeSeconds ?? null;
}

export default function ComparePage() {
  const [params] = useSearchParams();
  const ids = useMemo(
    () => (params.get('ids') ?? '').split(',').map(Number).filter((n) => Number.isInteger(n) && n > 0).slice(0, 10),
    [params],
  );
  const { mode } = useColorMode();
  const [rorWindow, setRorWindow] = useState(30);
  const [referenceId, setReferenceId] = useState<number | ''>('');
  const compare = useCompare(ids, rorWindow);

  const items = compare.data?.items;
  const reference = items?.find((i) => i.roast.id === referenceId);
  const others = useMemo(() => (items ?? []).filter((i) => i.roast.id !== referenceId), [items, referenceId]);

  const comparisons = useQueries({
    queries: reference
      ? others.map((o) => ({
          queryKey: ['comparison', o.roast.id, reference.roast.id, rorWindow],
          queryFn: () => roastsApi.comparison(o.roast.id, reference.roast.id, rorWindow),
        }))
      : [],
  });

  const colorOf = (id: number) => COMPARE_COLORS[ids.indexOf(id) % COMPARE_COLORS.length];

  const series = useMemo<ChartSeriesInput[]>(() => {
    if (!items) return [];
    const list: ChartSeriesInput[] = [];
    if (reference) {
      list.push({ id: `r${reference.roast.id}`, label: reference.roast.batchCode, curve: reference.curve, color: REFERENCE_COLOR, role: 'reference' });
    }
    for (const item of others) {
      list.push({ id: `r${item.roast.id}`, label: item.roast.batchCode, curve: item.curve, color: colorOf(item.roast.id), role: 'other' });
    }
    return list;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [items, reference, others]);

  const deviationOption = useMemo(() => {
    if (!reference) return null;
    const lines = comparisons.flatMap((c, i) =>
      c.data
        ? [{ id: `d${others[i].roast.id}`, name: others[i].roast.batchCode, color: colorOf(others[i].roast.id),
            gridSeconds: c.data.result.gridSeconds, delta: c.data.result.beanTempDelta }]
        : [],
    );
    return lines.length > 0 ? buildMultiDeviationOption(lines, mode === 'dark') : null;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [comparisons.map((c) => c.dataUpdatedAt).join(','), reference, mode]);

  if (ids.length < 2) {
    return (
      <>
        <PageHeader title="Compare roasts" />
        <Alert severity="info" action={<Button component={Link} to="/roasts">Choose roasts</Button>}>
          Select two to ten roasts in the roast list and press Compare.
        </Alert>
      </>
    );
  }
  if (compare.isPending) return <Loading />;
  if (compare.isError) return <ErrorAlert error={compare.error} onRetry={() => compare.refetch()} />;

  const delta = (item: CompareItem, pick: (i: CompareItem) => number | null, format: (n: number | null) => string) => {
    if (!reference || item.roast.id === reference.roast.id) return null;
    const a = pick(item);
    const b = pick(reference);
    return a === null || b === null ? null : format(a - b);
  };

  const rows: { label: string; value: (i: CompareItem) => string; diff?: (i: CompareItem) => string | null }[] = [
    { label: 'Name', value: (i) => i.roast.name },
    { label: 'Date', value: (i) => formatDate(i.roast.roastedAt) },
    { label: 'Profile', value: (i) => i.roast.profile?.name ?? '–' },
    { label: 'Green weight', value: (i) => formatWeightG(i.roast.greenWeightG) },
    { label: 'Total time', value: (i) => formatDuration(i.metrics.totalTimeSeconds),
      diff: (i) => delta(i, (x) => x.metrics.totalTimeSeconds, formatSignedDuration) },
    { label: 'Dry end', value: (i) => formatDuration(eventTime(i, 'DRY_END')),
      diff: (i) => delta(i, (x) => eventTime(x, 'DRY_END'), formatSignedDuration) },
    { label: 'First crack', value: (i) => formatDuration(i.roast.firstCrackSeconds),
      diff: (i) => delta(i, (x) => x.roast.firstCrackSeconds, formatSignedDuration) },
    { label: 'Development time', value: (i) => formatDuration(i.roast.developmentSeconds),
      diff: (i) => delta(i, (x) => x.roast.developmentSeconds, formatSignedDuration) },
    { label: 'Development ratio (DTR)', value: (i) => formatPercent(i.metrics.developmentTimeRatioPercent),
      diff: (i) => delta(i, (x) => x.metrics.developmentTimeRatioPercent, (n) => signed(n, 1, ' pp')) },
    { label: 'Roast loss', value: (i) => formatPercent(i.metrics.roastLossPercent),
      diff: (i) => delta(i, (x) => x.metrics.roastLossPercent, (n) => signed(n, 1, ' pp')) },
    { label: 'Drop temp', value: (i) => formatTemp(i.metrics.dropBeanTemp),
      diff: (i) => delta(i, (x) => x.metrics.dropBeanTemp, (n) => signed(n, 1, ' °C')) },
  ];

  return (
    <>
      <PageHeader
        title="Compare roasts"
        subtitle={`${items!.length} roasts on one chart. All of them start at charge.`}
        actions={<Button component={Link} to="/roasts">Back to roasts</Button>}
      />

      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 2, alignItems: { sm: 'center' } }}>
        <FormControl size="small" sx={{ minWidth: 280 }}>
          <InputLabel id="reference-select">Reference roast</InputLabel>
          <Select
            labelId="reference-select"
            label="Reference roast"
            value={referenceId}
            onChange={(e) => setReferenceId(selectValue(e.target.value))}
          >
            <MenuItem value="">None</MenuItem>
            {items!.map((i) => (
              <MenuItem key={i.roast.id} value={i.roast.id}>
                {i.roast.batchCode} &middot; {i.roast.name}
              </MenuItem>
            ))}
          </Select>
        </FormControl>
        <Typography variant="body2" color="text.secondary">
          Pick a reference roast to see how the others differ from it.
        </Typography>
      </Stack>

      <RoastChartPanel
        series={series}
        rorWindow={rorWindow}
        onRorWindowChange={setRorWindow}
        allowMarks={false}
        height={500}
        label="Overlay of the selected roast curves"
      />

      {deviationOption && (
        <Card sx={{ mt: 3 }}>
          <CardContent>
            <Typography variant="h6" sx={{ mb: 1 }}>
              Bean temperature difference to {reference!.roast.batchCode}
            </Typography>
            <EChart option={deviationOption} height={260} label="Bean temperature difference to the reference roast" testId="deviation-chart" />
          </CardContent>
        </Card>
      )}

      <Card sx={{ mt: 3 }}>
        <CardContent>
          <Typography variant="h6" sx={{ mb: 1 }}>
            Statistics
          </Typography>
          <Box sx={{ overflowX: 'auto' }}>
            <Table size="small" aria-label="Roast statistics">
              <TableHead>
                <TableRow>
                  <TableCell />
                  {items!.map((i) => (
                    <TableCell key={i.roast.id} align="right">
                      <Box component="span" sx={{ display: 'inline-block', width: 10, height: 10, borderRadius: '50%', mr: 0.75, bgcolor: i.roast.id === referenceId ? REFERENCE_COLOR : colorOf(i.roast.id) }} />
                      <Link to={`/roasts/${i.roast.id}`}>{i.roast.batchCode}</Link>
                      {i.roast.id === referenceId ? ' (ref)' : ''}
                    </TableCell>
                  ))}
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.map((row) => (
                  <TableRow key={row.label}>
                    <TableCell component="th" scope="row">
                      {row.label}
                    </TableCell>
                    {items!.map((i) => {
                      const d = row.diff?.(i) ?? null;
                      return (
                        <TableCell key={i.roast.id} align="right">
                          {row.value(i)}
                          {d && (
                            <Typography component="span" variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                              {d}
                            </Typography>
                          )}
                        </TableCell>
                      );
                    })}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Box>
        </CardContent>
      </Card>
    </>
  );
}
