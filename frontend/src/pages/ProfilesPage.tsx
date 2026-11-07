import AddIcon from '@mui/icons-material/Add';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlined';
import EditIcon from '@mui/icons-material/Edit';
import StarIcon from '@mui/icons-material/Star';
import {
  Box,
  Button,
  Card,
  CardActions,
  CardContent,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import { useState } from 'react';
import { Link } from 'react-router';
import { useCreateProfile, useDeleteProfile, useProfiles, useUpdateProfile } from '../api/hooks';
import type { Profile } from '../api/types';
import { ApiError } from '../api/client';
import { ConfirmDialog, EmptyState, ErrorAlert, Loading, PageHeader } from '../components/common';
import { formatDuration, formatPercent } from '../domain/format';

function ProfileDialog({ profile, onClose }: { profile: Profile | null; onClose: () => void }) {
  const [name, setName] = useState(profile?.name ?? '');
  const [description, setDescription] = useState(profile?.description ?? '');
  const create = useCreateProfile();
  const update = useUpdateProfile();
  const mutation = profile ? update : create;

  function save() {
    const body = { name: name.trim(), description: description.trim() || undefined };
    if (profile) update.mutate({ id: profile.id, body }, { onSuccess: onClose });
    else create.mutate(body, { onSuccess: onClose });
  }

  const fieldError = mutation.error instanceof ApiError ? mutation.error.fieldErrors.name : undefined;

  return (
    <Dialog open onClose={mutation.isPending ? undefined : onClose} maxWidth="sm" fullWidth>
      <DialogTitle>{profile ? 'Edit profile' : 'New profile'}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <TextField autoFocus label="Name" required value={name} onChange={(e) => setName(e.target.value)} error={Boolean(fieldError)} helperText={fieldError} slotProps={{ htmlInput: { maxLength: 120 } }} />
          <TextField label="Description" multiline minRows={2} value={description} onChange={(e) => setDescription(e.target.value)} slotProps={{ htmlInput: { maxLength: 2000 } }} />
          {mutation.isError && <ErrorAlert error={mutation.error} />}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={mutation.isPending}>
          Cancel
        </Button>
        <Button variant="contained" onClick={save} disabled={!name.trim() || mutation.isPending}>
          Save
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export default function ProfilesPage() {
  const profiles = useProfiles();
  const remove = useDeleteProfile();
  const [editing, setEditing] = useState<Profile | 'new' | null>(null);
  const [deleting, setDeleting] = useState<Profile | null>(null);

  return (
    <>
      <PageHeader
        title="Profiles"
        subtitle="A profile is a recipe for one coffee. Mark your best roast as the reference roast to aim for."
        actions={
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setEditing('new')}>
            New profile
          </Button>
        }
      />

      {profiles.isPending ? (
        <Loading />
      ) : profiles.isError ? (
        <ErrorAlert error={profiles.error} onRetry={() => profiles.refetch()} />
      ) : profiles.data.length === 0 ? (
        <EmptyState title="No profiles yet" action={<Button variant="contained" onClick={() => setEditing('new')}>Create a profile</Button>}>
          Create a profile, assign roasts to it and mark the best one as its reference.
        </EmptyState>
      ) : (
        <Box sx={{ display: 'grid', gap: 2.5, gridTemplateColumns: { xs: '1fr', md: 'repeat(2, 1fr)', xl: 'repeat(3, 1fr)' } }}>
          {profiles.data.map((p) => (
            <Card key={p.id} data-testid="profile-card" sx={{ display: 'flex', flexDirection: 'column' }}>
              <CardContent sx={{ flexGrow: 1 }}>
                <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'flex-start' }}>
                  <Typography variant="h6">{p.name}</Typography>
                  <Box sx={{ whiteSpace: 'nowrap' }}>
                    <Tooltip title="Edit">
                      <IconButton size="small" aria-label={`Edit ${p.name}`} onClick={() => setEditing(p)}>
                        <EditIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                    <Tooltip title="Delete">
                      <IconButton size="small" aria-label={`Delete ${p.name}`} onClick={() => setDeleting(p)}>
                        <DeleteOutlineIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  </Box>
                </Stack>
                {p.description && (
                  <Typography color="text.secondary" sx={{ mt: 0.5, mb: 1.5 }}>
                    {p.description}
                  </Typography>
                )}
                <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 1, mt: 1 }}>
                  {[
                    ['Roasts', String(p.roastCount)],
                    ['Avg. time', formatDuration(p.avgTotalTimeSeconds)],
                    ['Avg. DTR', formatPercent(p.avgDtrPercent)],
                    ['Avg. loss', formatPercent(p.avgLossPercent)],
                  ].map(([label, value]) => (
                    <Box key={label}>
                      <Typography variant="caption" color="text.secondary">
                        {label}
                      </Typography>
                      <Typography sx={{ fontWeight: 700 }}>{value}</Typography>
                    </Box>
                  ))}
                </Box>
                <Stack direction="row" spacing={0.75} sx={{ mt: 2, alignItems: 'center' }}>
                  <StarIcon fontSize="small" sx={{ color: p.referenceRoast ? 'warning.main' : 'action.disabled' }} />
                  {p.referenceRoast ? (
                    <Typography variant="body2">
                      Reference: <Link to={`/roasts/${p.referenceRoast.id}`}>{p.referenceRoast.batchCode}</Link> {p.referenceRoast.name}
                    </Typography>
                  ) : (
                    <Typography variant="body2" color="text.secondary">
                      No reference roast yet
                    </Typography>
                  )}
                </Stack>
              </CardContent>
              <CardActions>
                <Button size="small" component={Link} to={`/roasts?profileId=${p.id}`}>
                  Show roasts
                </Button>
              </CardActions>
            </Card>
          ))}
        </Box>
      )}

      {editing && <ProfileDialog profile={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />}
      <ConfirmDialog
        open={deleting !== null}
        title={`Delete profile ${deleting?.name ?? ''}?`}
        confirmLabel="Delete profile"
        destructive
        busy={remove.isPending}
        onClose={() => setDeleting(null)}
        onConfirm={() => deleting && remove.mutate(deleting.id, { onSuccess: () => setDeleting(null) })}
      >
        Its roasts are kept, they just no longer belong to a profile.
      </ConfirmDialog>
    </>
  );
}
