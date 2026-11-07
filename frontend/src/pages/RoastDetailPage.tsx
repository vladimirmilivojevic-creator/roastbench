import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlined';
import EditIcon from '@mui/icons-material/Edit';
import StarBorderIcon from '@mui/icons-material/StarBorder';
import StarIcon from '@mui/icons-material/Star';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material';
import { useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import {
  useAssignProfile,
  useComparison,
  useCurve,
  useDeleteRoast,
  useProfiles,
  useRoast,
  useSetReference,
  useUpdateRoast,
} from '../api/hooks';
import { CardGrid, ConfirmDialog, ErrorAlert, Loading, MetricCard, PageHeader } from '../components/common';
import EChart from '../components/EChart';
import EditEventsDialog from '../components/EditEventsDialog';
import RoastChartPanel from '../components/RoastChartPanel';
import { useColorMode } from '../colorMode';
import {
  PHASE_LABEL,
  SOURCE_LABEL,
  formatDateTime,
  formatDuration,
  formatPercent,
  formatRor,
  formatSignedDuration,
  formatTemp,
  formatWeightG,
  signed,
} from '../domain/format';
import { optionalId } from '../domain/select';
import {
  COMPARE_COLORS,
  REFERENCE_COLOR,
  buildDeviationChartOption,
  type ChartSeriesInput,
} from '../domain/roastChart';

export default function RoastDetailPage() {
  const id = Number(useParams().id);
  const navigate = useNavigate();
  const { mode } = useColorMode();
  const [rorWindow, setRorWindow] = useState(30);
  const [editEvents, setEditEvents] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [notes, setNotes] = useState<string | null>(null);

  const detail = useRoast(id);
  const curve = useCurve(id, rorWindow);
  const profiles = useProfiles();
  const assign = useAssignProfile();
  const setReference = useSetReference();
  const update = useUpdateRoast();
  const remove = useDeleteRoast();

  const profile = profiles.data?.find((p) => p.id === detail.data?.summary.profile?.id);
  const reference = profile?.referenceRoast ?? null;
  const hasReference = reference !== null && reference.id !== id;
  const referenceCurve = useCurve(hasReference ? reference.id : undefined, rorWindow);
  const comparison = useComparison(hasReference ? id : undefined, reference?.id, rorWindow);

  const series = useMemo<ChartSeriesInput[]>(() => {
    if (!detail.data || !curve.data) return [];
    const list: ChartSeriesInput[] = [];
    if (hasReference && referenceCurve.data && reference) {
      list.push({ id: 'ref', label: reference.batchCode, curve: referenceCurve.data, color: REFERENCE_COLOR, role: 'reference' });
    }
    list.push({
      id: 'main',
      label: detail.data.summary.batchCode,
      curve: curve.data,
      color: COMPARE_COLORS[0],
      role: 'main',
      events: detail.data.events,
      phases: detail.data.metrics.phases,
    });
    return list;
  }, [detail.data, curve.data, hasReference, referenceCurve.data, reference]);

  const deviationOption = useMemo(
    () =>
      comparison.data
        ? buildDeviationChartOption(comparison.data.result.gridSeconds, comparison.data.result.beanTempDelta, mode === 'dark')
        : null,
    [comparison.data, mode],
  );

  if (detail.isPending) return <Loading />;
  if (detail.isError) return <ErrorAlert error={detail.error} onRetry={() => detail.refetch()} />;

  const { summary, metrics, events } = detail.data;
  const isReference = summary.referenceOfProfile;
  const dev = metrics.phases.find((p) => p.phase === 'DEVELOPMENT');
  const dryEnd = events.find((e) => e.type === 'DRY_END');
  const cmp = comparison.data?.result;
  const notesValue = notes ?? detail.data.notes ?? '';
  const notesChanged = notes !== null && notes !== (detail.data.notes ?? '');

  return (
    <>
      <PageHeader
        title={`${summary.batchCode} · ${summary.name}`}
        subtitle={
          <>
            {formatDateTime(summary.roastedAt)}
            {summary.machine ? ` · ${summary.machine}` : ''} &middot;{' '}
            <Chip size="small" label={SOURCE_LABEL[summary.sourceFormat]} variant="outlined" />
          </>
        }
        actions={
          <>
            {summary.profile && (
              <Button
                variant={isReference ? 'contained' : 'outlined'}
                color="warning"
                startIcon={isReference ? <StarIcon /> : <StarBorderIcon />}
                disabled={setReference.isPending}
                onClick={() => setReference.mutate({ profileId: summary.profile!.id, roastId: isReference ? null : summary.id })}
              >
                {isReference ? 'Reference roast' : 'Set as reference'}
              </Button>
            )}
            <Button color="error" startIcon={<DeleteOutlineIcon />} onClick={() => setConfirmDelete(true)}>
              Delete
            </Button>
          </>
        }
      />

      {(setReference.isError || assign.isError || update.isError) && (
        <Box sx={{ mb: 2 }}>
          <ErrorAlert error={setReference.error ?? assign.error ?? update.error} />
        </Box>
      )}

      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 3, alignItems: { sm: 'center' } }}>
        <FormControl size="small" sx={{ minWidth: 240 }}>
          <InputLabel id="profile-select">Profile</InputLabel>
          <Select
            labelId="profile-select"
            label="Profile"
            value={summary.profile?.id ?? ''}
            onChange={(e) => assign.mutate({ id: summary.id, profileId: optionalId(e.target.value) ?? null })}
          >
            <MenuItem value="">No profile</MenuItem>
            {profiles.data?.map((p) => (
              <MenuItem key={p.id} value={p.id}>
                {p.name}
              </MenuItem>
            ))}
          </Select>
        </FormControl>
        <Box sx={{ display: 'flex', gap: 0.75, flexWrap: 'wrap', alignItems: 'center' }}>
          {summary.lots.map((l) => (
            <Chip
              key={l.lotId}
              component={Link}
              to={`/inventory/${l.lotId}`}
              clickable
              label={`${l.code} · ${formatWeightG(l.greenWeightG)}`}
            />
          ))}
        </Box>
      </Stack>

      <CardGrid min={150}>
        <MetricCard label="Total time" value={formatDuration(metrics.totalTimeSeconds)} hint={metrics.dropEstimated ? 'drop not marked: last sample' : 'charge to drop'} />
        <MetricCard label="Dry end" value={formatDuration(dryEnd?.timeSeconds)} hint={dryEnd?.estimated ? 'estimated at 150 °C' : formatTemp(dryEnd?.beanTemp)} />
        <MetricCard label="First crack" value={formatDuration(summary.firstCrackSeconds)} hint="start" />
        <MetricCard label="Development time" value={formatDuration(dev?.durationSeconds)} hint="first crack to drop" />
        <MetricCard label="Development ratio" value={formatPercent(metrics.developmentTimeRatioPercent)} hint="DTR" testId="metric-dtr" />
        <MetricCard label="Roast loss" value={formatPercent(metrics.roastLossPercent)} hint={`${formatWeightG(summary.greenWeightG)} → ${formatWeightG(summary.roastedWeightG)}`} testId="metric-loss" />
        <MetricCard label="Drop temp" value={formatTemp(metrics.dropBeanTemp)} hint={`charge ${formatTemp(metrics.chargeBeanTemp, 0)}`} />
        <MetricCard label="Turning point" value={formatDuration(metrics.turningPointSeconds)} hint={formatTemp(metrics.turningPointBeanTemp)} />
        <MetricCard label="Avg. RoR after TP" value={metrics.averageRorAfterTurningPoint.toFixed(1)} hint={'°C per minute'} />
      </CardGrid>

      <Box sx={{ mt: 3 }}>
        {series.length === 0 ? (
          <Loading label="Loading curve" />
        ) : (
          <RoastChartPanel
            series={series}
            rorWindow={rorWindow}
            onRorWindowChange={setRorWindow}
            label={`Roast curve of ${summary.batchCode}`}
          />
        )}
      </Box>

      <Box sx={{ display: 'grid', gap: 3, mt: 3, gridTemplateColumns: { xs: '1fr', xl: '1fr 1fr' } }}>
        <Card>
          <CardContent>
            <Typography variant="h6" sx={{ mb: 1 }}>
              Phases
            </Typography>
            <Box sx={{ overflowX: 'auto' }}>
              <Table size="small" aria-label="Roast phases" sx={{ '& td, & th': { whiteSpace: 'nowrap' } }}>
                <TableHead>
                  <TableRow>
                    <TableCell>Phase</TableCell>
                    <TableCell align="right">Time</TableCell>
                    <TableCell align="right">Share</TableCell>
                    <TableCell align="right">Temp</TableCell>
                    <TableCell align="right">Avg. RoR</TableCell>
                    {cmp && <TableCell align="right">vs reference</TableCell>}
                  </TableRow>
                </TableHead>
                <TableBody>
                  {metrics.phases.map((p) => {
                    const delta = cmp?.phases.find((d) => d.phase === p.phase);
                    return (
                      <TableRow key={p.phase}>
                        <TableCell>
                          {PHASE_LABEL[p.phase]} {p.estimated && <Chip size="small" label="estimated" />}
                        </TableCell>
                        <TableCell align="right">{formatDuration(p.durationSeconds)}</TableCell>
                        <TableCell align="right">{formatPercent(p.percentOfTotal, 0)}</TableCell>
                        <TableCell align="right">
                          {p.startBeanTemp.toFixed(0)} &rarr; {p.endBeanTemp.toFixed(0)} &deg;C
                        </TableCell>
                        <TableCell align="right">{formatRor(p.averageRor)}</TableCell>
                        {cmp && (
                          <TableCell align="right">
                            {delta ? `${formatSignedDuration(delta.durationDeltaSeconds)} · ${signed(delta.averageRorDelta, 1)} RoR` : '–'}
                          </TableCell>
                        )}
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </Box>
          </CardContent>
        </Card>

        <Card>
          <CardContent>
            <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
              <Typography variant="h6">Events</Typography>
              <Button size="small" startIcon={<EditIcon />} onClick={() => setEditEvents(true)}>
                Edit events
              </Button>
            </Stack>
            <Box sx={{ overflowX: 'auto' }}>
              <Table size="small" aria-label="Roast events">
                <TableHead>
                  <TableRow>
                    <TableCell>Event</TableCell>
                    <TableCell align="right">Time</TableCell>
                    <TableCell align="right">Bean temp</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {events.map((e) => (
                    <TableRow key={e.type}>
                      <TableCell>
                        {e.label} {e.estimated && <Chip size="small" label="estimated" />}
                      </TableCell>
                      <TableCell align="right">{formatDuration(e.timeSeconds)}</TableCell>
                      <TableCell align="right">{formatTemp(e.beanTemp)}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Box>
          </CardContent>
        </Card>
      </Box>

      {summary.profile && (
        <Card sx={{ mt: 3 }}>
          <CardContent>
            <Typography variant="h6" sx={{ mb: 1 }}>
              Compared with the reference of &ldquo;{summary.profile.name}&rdquo;
            </Typography>
            {!reference ? (
              <Alert severity="info">
                This profile has no reference roast yet. Open your best roast and press &ldquo;Set as reference&rdquo;.
              </Alert>
            ) : reference.id === id ? (
              <Alert severity="info">This roast is the reference. The other roasts of this profile are compared with it.</Alert>
            ) : comparison.isPending ? (
              <Loading />
            ) : comparison.isError ? (
              <ErrorAlert error={comparison.error} />
            ) : cmp ? (
              <>
                <Typography color="text.secondary" sx={{ mb: 2 }}>
                  Reference: <Link to={`/roasts/${reference.id}`}>{reference.batchCode}</Link> {reference.name}. Both
                  roasts start at charge. We compare the first {formatDuration(cmp.overlapSeconds)}.
                </Typography>
                <CardGrid min={150}>
                  <MetricCard label="Average gap" value={formatTemp(cmp.rmse)} hint="typical temperature difference" testId="cmp-rmse" />
                  <MetricCard label="Hotter or colder" value={signed(cmp.meanSignedDeviation, 1, ' °C')} hint="+ means hotter than the reference" />
                  <MetricCard label="Biggest gap" value={formatTemp(cmp.maxAbsDeviation)} hint={`at ${formatDuration(cmp.maxDeviationAtSeconds)}`} />
                  <MetricCard label="Dry end" value={formatSignedDuration(cmp.dryEndDeltaSeconds)} hint="vs reference" />
                  <MetricCard label="First crack" value={formatSignedDuration(cmp.firstCrackDeltaSeconds)} hint="vs reference" />
                  <MetricCard label="Total time" value={formatSignedDuration(cmp.dropDeltaSeconds)} hint={`drop ${signed(cmp.dropBeanTempDelta, 1, ' °C')}`} />
                  <MetricCard label="DTR" value={signed(cmp.dtrDeltaPercentPoints, 1, ' pp')} hint="percentage points" />
                  <MetricCard label="Roast loss" value={signed(cmp.roastLossDeltaPercentPoints, 1, ' pp')} hint="percentage points" />
                </CardGrid>
                {deviationOption && (
                  <Box sx={{ mt: 2 }}>
                    <Typography variant="subtitle2" sx={{ mb: 0.5 }}>
                      Bean temperature difference over time
                    </Typography>
                    <EChart option={deviationOption} height={220} label="Bean temperature difference to the reference roast" testId="deviation-chart" />
                  </Box>
                )}
              </>
            ) : null}
          </CardContent>
        </Card>
      )}

      <Card sx={{ mt: 3 }}>
        <CardContent>
          <Typography variant="h6" sx={{ mb: 1 }}>
            Notes
          </Typography>
          <TextField
            multiline
            minRows={2}
            fullWidth
            placeholder="Cupping notes, adjustments, what to change next time"
            value={notesValue}
            onChange={(e) => setNotes(e.target.value)}
            slotProps={{ htmlInput: { maxLength: 4000, 'aria-label': 'Notes' } }}
          />
          <Button
            sx={{ mt: 1 }}
            variant="outlined"
            disabled={!notesChanged || update.isPending}
            onClick={() => update.mutate({ id, body: { notes: notesValue } }, { onSuccess: () => setNotes(null) })}
          >
            Save notes
          </Button>
          {detail.data.sourceFilename && (
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
              Imported from {detail.data.sourceFilename}
            </Typography>
          )}
        </CardContent>
      </Card>

      {editEvents && <EditEventsDialog open roastId={id} events={events} onClose={() => setEditEvents(false)} />}

      <ConfirmDialog
        open={confirmDelete}
        title={`Delete ${summary.batchCode}?`}
        confirmLabel="Delete roast"
        destructive
        busy={remove.isPending}
        onClose={() => setConfirmDelete(false)}
        onConfirm={() => remove.mutate(id, { onSuccess: () => navigate('/roasts') })}
      >
        The roast and its curve are removed and the green coffee it used goes back into stock.
      </ConfirmDialog>
    </>
  );
}
