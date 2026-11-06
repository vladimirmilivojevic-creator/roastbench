import CoffeeIcon from '@mui/icons-material/Coffee';
import { Box, Button, LinearProgress, Stack, Typography } from '@mui/material';
import { useEffect, useState, type ReactNode } from 'react';
import { useHealth } from '../api/hooks';

/**
 * Free hosting puts the API to sleep after a few idle minutes and wakes it on the first request, which can take up
 * to a minute. The static site loads instantly, so this gate explains the wait instead of showing broken pages.
 */
export default function ApiGate({ children }: { children: ReactNode }) {
  const health = useHealth();
  const [seconds, setSeconds] = useState(0);

  useEffect(() => {
    if (health.data) return;
    const timer = window.setInterval(() => setSeconds((s) => s + 1), 1000);
    return () => window.clearInterval(timer);
  }, [health.data]);

  if (health.data) return <>{children}</>;

  return (
    <Box
      role="status"
      aria-live="polite"
      sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', p: 3, textAlign: 'center' }}
    >
      <Stack spacing={2.5} sx={{ maxWidth: 460, width: '100%', alignItems: 'center' }}>
        <CoffeeIcon color="primary" sx={{ fontSize: 56 }} />
        <Typography variant="h5">Waking up the demo server</Typography>
        <Typography color="text.secondary">
          {seconds < 6
            ? 'Connecting to the RoastBench API…'
            : 'The API runs on a free host that sleeps when nobody is using it. Starting it takes up to a minute; this page continues by itself.'}
        </Typography>
        <LinearProgress sx={{ width: '100%' }} />
        <Typography variant="caption" color="text.secondary">
          {seconds}s
        </Typography>
        {seconds > 120 && (
          <Button variant="outlined" onClick={() => window.location.reload()}>
            Reload
          </Button>
        )}
      </Stack>
    </Box>
  );
}
