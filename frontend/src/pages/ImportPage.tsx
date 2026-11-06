import CloudUploadIcon from '@mui/icons-material/CloudUpload';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlined';
import {
  Alert,
  AlertTitle,
  Autocomplete,
  Box,
  Button,
  Card,
  CardContent,
  FormControl,
  IconButton,
  InputAdornment,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  Step,
  StepLabel,
  Stepper,
  TextField,
  Typography,
} from '@mui/material';
import { useMemo, useState, type DragEvent } from 'react';
import { useNavigate } from 'react-router';
import { useImportRoast, useLots, usePreviewImport, useProfiles } from '../api/hooks';
import type { ImportPreview, ImportRequest, Lot } from '../api/types';
import { CardGrid, ErrorAlert, MetricCard, PageHeader } from '../components/common';
import RoastChartPanel from '../components/RoastChartPanel';
import { SOURCE_LABEL, formatDuration, formatKg, formatPercent, formatTemp, formatWeightG } from '../domain/format';
import { COMPARE_COLORS } from '../domain/roastChart';
import { selectValue } from '../domain/select';

interface LotRow {
  key: number;
  lot: Lot | null;
  grams: string;
}

function toLocalInput(iso: string | null): string {
  const date = iso ? new Date(iso) : new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function baseName(filename: string): string {
  const dot = filename.lastIndexOf('.');
  return dot > 0 ? filename.slice(0, dot) : filename;
}

export default function ImportPage() {
  const navigate = useNavigate();
  const preview = usePreviewImport();
  const importer = useImportRoast();
  const profiles = useProfiles();
  const lots = useLots({});

  const [file, setFile] = useState<File | null>(null);
  const [dragging, setDragging] = useState(false);
  const [rorWindow, setRorWindow] = useState(30);
  const [name, setName] = useState('');
  const [machine, setMachine] = useState('');
  const [roastedAt, setRoastedAt] = useState('');
  const [profileId, setProfileId] = useState<number | ''>('');
  const [notes, setNotes] = useState('');
  const [roastedWeight, setRoastedWeight] = useState('');
  const [rows, setRows] = useState<LotRow[]>([]);

  const data: ImportPreview | undefined = preview.data;
  const step = importer.isPending ? 2 : data ? 1 : 0;

  function choose(selected: File | undefined) {
    if (!selected) return;
    setFile(selected);
    preview.mutate(selected, {
      onSuccess: (p) => {
        setName(p.title ?? baseName(selected.name));
        setMachine(p.machine ?? '');
        setRoastedAt(toLocalInput(p.roastedAt));
        setRoastedWeight(p.roastedWeightG ? String(p.roastedWeightG) : '');
        setRows([]);
        setProfileId('');
        setNotes('');
      },
    });
  }

  function onDrop(e: DragEvent) {
    e.preventDefault();
    setDragging(false);
    choose(e.dataTransfer.files[0]);
  }

  const lotSum = rows.reduce((sum, r) => sum + (Number(r.grams) || 0), 0);
  const greenWeight = rows.length > 0 ? lotSum : (data?.greenWeightG ?? null);
  const roastedNumber = roastedWeight.trim() === '' ? null : Number(roastedWeight);

  const problems = useMemo(() => {
    const list: string[] = [];
    if (!name.trim()) list.push('Give the roast a name.');
    if (roastedNumber !== null && (!Number.isFinite(roastedNumber) || roastedNumber <= 0)) list.push('Roasted weight must be a positive number of grams.');
    if (roastedNumber !== null && greenWeight !== null && roastedNumber > greenWeight) list.push('Roasted weight cannot be larger than the green weight.');
    const used = new Set<number>();
    for (const r of rows) {
      if (!r.lot) list.push('Choose a lot for every green coffee row, or remove the row.');
      else if (used.has(r.lot.id)) list.push(`${r.lot.code} is listed twice.`);
      else used.add(r.lot.id);
      if (!(Number(r.grams) > 0)) list.push('Enter the weight in grams for every green coffee row.');
    }
    return [...new Set(list)];
  }, [name, roastedNumber, greenWeight, rows]);

  function submit() {
    if (!file || problems.length > 0) return;
    const request: ImportRequest = {
      name: name.trim(),
      machine: machine.trim() || undefined,
      roastedAt: roastedAt ? new Date(roastedAt).toISOString() : undefined,
      profileId: profileId === '' ? undefined : profileId,
      notes: notes.trim() || undefined,
      roastedWeightG: roastedNumber ?? undefined,
      lots: rows.length > 0 ? rows.map((r) => ({ lotId: r.lot!.id, greenWeightG: Number(r.grams) })) : undefined,
    };
    importer.mutate({ file, request }, { onSuccess: (roast) => navigate(`/roasts/${roast.summary.id}`) });
  }

  const addRow = () =>
    setRows((current) => [...current, { key: Date.now() + current.length, lot: null, grams: current.length === 0 && data?.greenWeightG ? String(data.greenWeightG) : '' }]);

  return (
    <>
      <PageHeader title="Import a roast" subtitle="CSV or Artisan .alog. Check the result before it is saved." />

      <Stepper activeStep={step} alternativeLabel sx={{ mb: 3 }}>
        {['Choose file', 'Check the curve', 'Details and green coffee'].map((label) => (
          <Step key={label}>
            <StepLabel>{label}</StepLabel>
          </Step>
        ))}
      </Stepper>

      <Card
        onDragOver={(e) => {
          e.preventDefault();
          setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={onDrop}
        sx={{ borderStyle: 'dashed', borderWidth: 2, borderColor: dragging ? 'primary.main' : 'divider', bgcolor: dragging ? 'action.hover' : undefined }}
      >
        <CardContent sx={{ textAlign: 'center', py: 4 }}>
          <CloudUploadIcon color="primary" sx={{ fontSize: 44 }} />
          <Typography variant="h6">{file ? file.name : 'Drop a roast log here'}</Typography>
          <Typography color="text.secondary" sx={{ mb: 2 }}>
            Artisan .alog files and CSV with time, bean temperature and optional environment temperature and events.
            Sample files are in the <code>samples/</code> folder of the repository.
          </Typography>
          <Button variant="contained" component="label" disabled={preview.isPending}>
            {preview.isPending ? 'Reading…' : file ? 'Choose another file' : 'Choose file'}
            <input
              hidden
              type="file"
              accept=".csv,.tsv,.txt,.alog"
              data-testid="file-input"
              onChange={(e) => {
                choose(e.target.files?.[0]);
                e.target.value = '';
              }}
            />
          </Button>
        </CardContent>
      </Card>

      {preview.isError && (
        <Box sx={{ mt: 2 }}>
          <ErrorAlert error={preview.error} />
        </Box>
      )}

      {data && (
        <>
          {data.warnings.length > 0 && (
            <Alert severity="warning" sx={{ mt: 3 }}>
              <AlertTitle>Things to know about this file</AlertTitle>
              <Box component="ul" sx={{ m: 0, pl: 2.5 }}>
                {data.warnings.map((w) => (
                  <li key={w}>{w}</li>
                ))}
              </Box>
            </Alert>
          )}

          <Box sx={{ mt: 3 }}>
            <CardGrid min={150}>
              <MetricCard label="Format" value={SOURCE_LABEL[data.format]} hint={`${data.curve.timeSeconds.length} samples`} />
              <MetricCard label="Total time" value={formatDuration(data.metrics.totalTimeSeconds)} />
              <MetricCard label="Development ratio" value={formatPercent(data.metrics.developmentTimeRatioPercent)} hint="DTR" testId="preview-dtr" />
              <MetricCard label="Roast loss" value={formatPercent(data.metrics.roastLossPercent)} hint={data.greenWeightG ? `${formatWeightG(data.greenWeightG)} in the file` : 'no weights in the file'} />
              <MetricCard label="Drop temp" value={formatTemp(data.metrics.dropBeanTemp)} />
              <MetricCard label="Turning point" value={formatDuration(data.metrics.turningPointSeconds)} hint={formatTemp(data.metrics.turningPointBeanTemp)} />
            </CardGrid>
          </Box>

          <Box sx={{ mt: 3 }}>
            <RoastChartPanel
              series={[{ id: 'preview', label: name || 'Imported roast', curve: data.curve, color: COMPARE_COLORS[0], role: 'main', events: data.events, phases: data.metrics.phases }]}
              rorWindow={rorWindow}
              onRorWindowChange={setRorWindow}
              label="Preview of the imported roast curve"
              height={400}
            />
          </Box>

          <Card sx={{ mt: 3 }}>
            <CardContent>
              <Typography variant="h6" sx={{ mb: 2 }}>
                Details
              </Typography>
              <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' } }}>
                <TextField label="Name" required value={name} onChange={(e) => setName(e.target.value)} slotProps={{ htmlInput: { maxLength: 150, 'data-testid': 'roast-name' } }} />
                <TextField label="Roast date and time" type="datetime-local" value={roastedAt} onChange={(e) => setRoastedAt(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} />
                <TextField label="Machine" value={machine} onChange={(e) => setMachine(e.target.value)} slotProps={{ htmlInput: { maxLength: 120 } }} />
                <FormControl>
                  <InputLabel id="import-profile">Profile</InputLabel>
                  <Select labelId="import-profile" label="Profile" value={profileId} onChange={(e) => setProfileId(selectValue(e.target.value))}>
                    <MenuItem value="">No profile</MenuItem>
                    {profiles.data?.map((p) => (
                      <MenuItem key={p.id} value={p.id}>
                        {p.name}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
                <TextField
                  label="Roasted weight"
                  value={roastedWeight}
                  onChange={(e) => setRoastedWeight(e.target.value)}
                  slotProps={{ input: { endAdornment: <InputAdornment position="end">g</InputAdornment> }, htmlInput: { inputMode: 'decimal' } }}
                  helperText="Used for roast loss"
                />
                <TextField label="Notes" value={notes} onChange={(e) => setNotes(e.target.value)} multiline minRows={1} slotProps={{ htmlInput: { maxLength: 4000 } }} />
              </Box>

              <Typography variant="h6" sx={{ mt: 4, mb: 0.5 }}>
                Green coffee used
              </Typography>
              <Typography color="text.secondary" sx={{ mb: 2 }}>
                Link the lots this roast used. The weights are deducted from stock when you import; add several lots
                for a blend. Skip it to import the curve only.
              </Typography>
              <Stack spacing={2}>
                {rows.map((row) => {
                  const kg = (Number(row.grams) || 0) / 1000;
                  const short = row.lot !== null && kg > row.lot.stockKg;
                  return (
                    <Stack key={row.key} direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ alignItems: { sm: 'flex-start' } }}>
                      <Autocomplete
                        sx={{ flex: 1, minWidth: 260 }}
                        options={lots.data ?? []}
                        value={row.lot}
                        onChange={(_, lot) => setRows((c) => c.map((r) => (r.key === row.key ? { ...r, lot } : r)))}
                        getOptionLabel={(l) => `${l.code} · ${l.name}`}
                        isOptionEqualToValue={(a, b) => a.id === b.id}
                        renderOption={(props, l) => (
                          <li {...props} key={l.id}>
                            {l.code} &middot; {l.name}&nbsp;<Typography component="span" color="text.secondary">({formatKg(l.stockKg)} in stock)</Typography>
                          </li>
                        )}
                        renderInput={(p) => <TextField {...p} label="Lot" size="small" />}
                      />
                      <TextField
                        size="small"
                        label="Green weight"
                        value={row.grams}
                        onChange={(e) => setRows((c) => c.map((r) => (r.key === row.key ? { ...r, grams: e.target.value } : r)))}
                        slotProps={{ input: { endAdornment: <InputAdornment position="end">g</InputAdornment> }, htmlInput: { inputMode: 'decimal', 'aria-label': 'Green weight in grams' } }}
                        error={short}
                        helperText={short ? `Only ${formatKg(row.lot!.stockKg)} in stock` : row.lot ? `${formatKg(row.lot.stockKg)} in stock` : ' '}
                        sx={{ width: { sm: 200 } }}
                      />
                      <IconButton aria-label="Remove lot" onClick={() => setRows((c) => c.filter((r) => r.key !== row.key))}>
                        <DeleteOutlineIcon />
                      </IconButton>
                    </Stack>
                  );
                })}
                <Box>
                  <Button onClick={addRow} disabled={(lots.data ?? []).length === 0}>
                    Add green coffee lot
                  </Button>
                  {rows.length > 0 && (
                    <Typography component="span" color="text.secondary" sx={{ ml: 2 }}>
                      Total {formatWeightG(lotSum)}
                    </Typography>
                  )}
                </Box>
              </Stack>

              {problems.length > 0 && (
                <Alert severity="warning" sx={{ mt: 3 }}>
                  {problems.join(' ')}
                </Alert>
              )}
              {importer.isError && (
                <Box sx={{ mt: 2 }}>
                  <ErrorAlert error={importer.error} />
                </Box>
              )}
              <Stack direction="row" spacing={2} sx={{ mt: 3 }}>
                <Button variant="contained" size="large" disabled={problems.length > 0 || importer.isPending} onClick={submit}>
                  {importer.isPending ? 'Importing…' : 'Import roast'}
                </Button>
                <Button size="large" onClick={() => navigate('/roasts')} disabled={importer.isPending}>
                  Cancel
                </Button>
              </Stack>
            </CardContent>
          </Card>
        </>
      )}
    </>
  );
}
