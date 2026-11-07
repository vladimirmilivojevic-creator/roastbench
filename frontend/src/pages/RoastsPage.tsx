import CompareArrowsIcon from '@mui/icons-material/CompareArrows';
import StarIcon from '@mui/icons-material/Star';
import {
  Box,
  Button,
  Card,
  Checkbox,
  Chip,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  TableSortLabel,
  TextField,
  Tooltip,
} from '@mui/material';
import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { useLots, useProfiles, useRoasts } from '../api/hooks';
import { EmptyState, ErrorAlert, Loading, PageHeader } from '../components/common';
import { formatDate, formatDuration, formatPercent, formatTemp, formatWeightG } from '../domain/format';
import { optionalId } from '../domain/select';

const MAX_COMPARE = 10;

const COLUMNS: { key: string; label: string; sort?: string; align?: 'right' }[] = [
  { key: 'batch', label: 'Batch', sort: 'batchNo' },
  { key: 'name', label: 'Name', sort: 'name' },
  { key: 'date', label: 'Date', sort: 'roastedAt' },
  { key: 'profile', label: 'Profile' },
  { key: 'lots', label: 'Green coffee' },
  { key: 'green', label: 'Green', align: 'right' },
  { key: 'time', label: 'Time', sort: 'totalTime', align: 'right' },
  { key: 'dtr', label: 'DTR', sort: 'dtr', align: 'right' },
  { key: 'loss', label: 'Loss', sort: 'loss', align: 'right' },
  { key: 'drop', label: 'Drop', sort: 'dropTemp', align: 'right' },
];

export default function RoastsPage() {
  const [params, setParams] = useSearchParams();
  const navigate = useNavigate();
  const [selected, setSelected] = useState<number[]>([]);

  const q = params.get('q') ?? '';
  const profileId = params.get('profileId') ? Number(params.get('profileId')) : undefined;
  const lotId = params.get('lotId') ? Number(params.get('lotId')) : undefined;
  const sort = params.get('sort') ?? 'roastedAt';
  const direction = (params.get('direction') ?? 'desc') as 'asc' | 'desc';
  const page = Number(params.get('page') ?? 0);
  const size = Number(params.get('size') ?? 20);

  const [search, setSearch] = useState(q);
  useEffect(() => {
    const timer = window.setTimeout(() => {
      if (search !== q) update({ q: search || undefined, page: undefined });
    }, 300);
    return () => window.clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [search]);

  function update(changes: Record<string, string | number | undefined>) {
    const next = new URLSearchParams(params);
    for (const [key, value] of Object.entries(changes)) {
      if (value === undefined || value === '') next.delete(key);
      else next.set(key, String(value));
    }
    setParams(next, { replace: true });
  }

  const roasts = useRoasts({ q: q || undefined, profileId, lotId, sort, direction, page, size });
  const profiles = useProfiles();
  const lots = useLots({ includeArchived: true });

  function toggleSort(key: string) {
    if (sort === key) update({ direction: direction === 'asc' ? 'desc' : 'asc', page: undefined });
    else update({ sort: key, direction: key === 'name' ? 'asc' : 'desc', page: undefined });
  }

  function toggleSelected(id: number) {
    setSelected((current) =>
      current.includes(id) ? current.filter((x) => x !== id) : current.length < MAX_COMPARE ? [...current, id] : current,
    );
  }

  const hasFilters = Boolean(q || profileId || lotId);

  return (
    <>
      <PageHeader
        title="Roasts"
        subtitle="All your roasts. Click one to open it. Tick 2 to 10 roasts to compare them."
        actions={
          <>
            <Tooltip title={selected.length < 2 ? `Select 2 to ${MAX_COMPARE} roasts to compare` : ''}>
              <span>
                <Button
                  variant="outlined"
                  startIcon={<CompareArrowsIcon />}
                  disabled={selected.length < 2}
                  onClick={() => navigate(`/compare?ids=${selected.join(',')}`)}
                >
                  Compare{selected.length > 0 ? ` (${selected.length})` : ''}
                </Button>
              </span>
            </Tooltip>
            <Button variant="contained" component={Link} to="/import">
              Import roast
            </Button>
          </>
        }
      />

      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ mb: 2 }}>
        <TextField
          size="small"
          label="Search name or machine"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          sx={{ minWidth: { md: 280 } }}
        />
        <FormControl size="small" sx={{ minWidth: 200 }}>
          <InputLabel id="filter-profile">Profile</InputLabel>
          <Select
            labelId="filter-profile"
            label="Profile"
            value={profileId ?? ''}
            onChange={(e) => update({ profileId: optionalId(e.target.value), page: undefined })}
          >
            <MenuItem value="">All profiles</MenuItem>
            {profiles.data?.map((p) => (
              <MenuItem key={p.id} value={p.id}>
                {p.name}
              </MenuItem>
            ))}
          </Select>
        </FormControl>
        <FormControl size="small" sx={{ minWidth: 220 }}>
          <InputLabel id="filter-lot">Green coffee</InputLabel>
          <Select
            labelId="filter-lot"
            label="Green coffee"
            value={lotId ?? ''}
            onChange={(e) => update({ lotId: optionalId(e.target.value), page: undefined })}
          >
            <MenuItem value="">All lots</MenuItem>
            {lots.data?.map((l) => (
              <MenuItem key={l.id} value={l.id}>
                {l.code} &middot; {l.name}
              </MenuItem>
            ))}
          </Select>
        </FormControl>
        {hasFilters && (
          <Button
            onClick={() => {
              setSearch('');
              setParams(new URLSearchParams(), { replace: true });
            }}
          >
            Clear filters
          </Button>
        )}
      </Stack>

      {roasts.isPending ? (
        <Loading />
      ) : roasts.isError ? (
        <ErrorAlert error={roasts.error} onRetry={() => roasts.refetch()} />
      ) : (
        <Card>
          {roasts.data.items.length === 0 ? (
            <EmptyState
              title={hasFilters ? 'No roasts match these filters' : 'No roasts yet'}
              action={
                <Button component={Link} to="/import" variant="contained">
                  Import a roast log
                </Button>
              }
            >
              {hasFilters ? 'Try removing a filter.' : 'Import a CSV or Artisan .alog file to get started.'}
            </EmptyState>
          ) : (
            <>
              <TableContainer>
                <Table size="small" aria-label="Roasts">
                  <TableHead>
                    <TableRow>
                      <TableCell padding="checkbox" />
                      {COLUMNS.map((c) => (
                        <TableCell key={c.key} align={c.align} sortDirection={sort === c.sort ? direction : false}>
                          {c.sort ? (
                            <TableSortLabel active={sort === c.sort} direction={sort === c.sort ? direction : 'asc'} onClick={() => toggleSort(c.sort!)}>
                              {c.label}
                            </TableSortLabel>
                          ) : (
                            c.label
                          )}
                        </TableCell>
                      ))}
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {roasts.data.items.map((r) => (
                      <TableRow key={r.id} hover selected={selected.includes(r.id)} data-testid="roast-row">
                        <TableCell padding="checkbox">
                          <Checkbox
                            checked={selected.includes(r.id)}
                            disabled={!selected.includes(r.id) && selected.length >= MAX_COMPARE}
                            onChange={() => toggleSelected(r.id)}
                            slotProps={{ input: { 'aria-label': `Select ${r.batchCode} for comparison` } }}
                          />
                        </TableCell>
                        <TableCell sx={{ whiteSpace: 'nowrap' }}>
                          <Link to={`/roasts/${r.id}`}>{r.batchCode}</Link>
                          {r.referenceOfProfile && (
                            <Tooltip title={`Reference roast of ${r.profile?.name ?? 'its profile'}`}>
                              <StarIcon fontSize="inherit" sx={{ ml: 0.5, color: 'warning.main', verticalAlign: 'text-bottom' }} />
                            </Tooltip>
                          )}
                        </TableCell>
                        <TableCell>{r.name}</TableCell>
                        <TableCell sx={{ whiteSpace: 'nowrap' }}>{formatDate(r.roastedAt)}</TableCell>
                        <TableCell>
                          {r.profile ? <Chip size="small" variant="outlined" label={r.profile.name} /> : '–'}
                        </TableCell>
                        <TableCell>
                          <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap' }}>
                            {r.lots.length === 0 && '–'}
                            {r.lots.map((l) => (
                              <Chip key={l.lotId} size="small" label={l.code} component={Link} to={`/inventory/${l.lotId}`} clickable />
                            ))}
                          </Box>
                        </TableCell>
                        <TableCell align="right">{formatWeightG(r.greenWeightG)}</TableCell>
                        <TableCell align="right">{formatDuration(r.totalTimeSeconds)}</TableCell>
                        <TableCell align="right">{formatPercent(r.dtrPercent)}</TableCell>
                        <TableCell align="right">{formatPercent(r.lossPercent)}</TableCell>
                        <TableCell align="right">{formatTemp(r.dropBeanTemp)}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
              <TablePagination
                component="div"
                count={roasts.data.totalItems}
                page={roasts.data.page}
                rowsPerPage={roasts.data.size}
                rowsPerPageOptions={[10, 20, 50]}
                onPageChange={(_, p) => update({ page: p === 0 ? undefined : p })}
                onRowsPerPageChange={(e) => update({ size: Number(e.target.value), page: undefined })}
              />
            </>
          )}
        </Card>
      )}
    </>
  );
}
