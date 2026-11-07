import { Box, Card, CardContent, FormControlLabel, Stack, Switch, ToggleButton, ToggleButtonGroup, Typography } from '@mui/material';
import { useMemo, useState } from 'react';
import { useColorMode } from '../colorMode';
import { buildRoastChartOption, type ChartSeriesInput } from '../domain/roastChart';
import EChart from './EChart';

const WINDOWS = [15, 30, 60];

interface Props {
  series: ChartSeriesInput[];
  rorWindow: number;
  onRorWindowChange: (seconds: number) => void;
  /** Phase areas and event lines need a single main roast. */
  allowMarks?: boolean;
  height?: number;
  label: string;
}

/** The interactive roast chart with its display switches. Zoom with the wheel or the slider; hover for values. */
export default function RoastChartPanel({ series, rorWindow, onRorWindowChange, allowMarks = true, height = 460, label }: Props) {
  const { mode } = useColorMode();
  const [showEnv, setShowEnv] = useState(true);
  const [showRor, setShowRor] = useState(true);
  const [showPhases, setShowPhases] = useState(true);
  const [showEvents, setShowEvents] = useState(true);

  const option = useMemo(
    () => buildRoastChartOption(series, { showEnv, showRor, showPhases: allowMarks && showPhases, showEvents: allowMarks && showEvents, dark: mode === 'dark' }),
    [series, showEnv, showRor, showPhases, showEvents, allowMarks, mode],
  );

  return (
    <Card>
      <CardContent>
        <Stack direction="row" sx={{ flexWrap: 'wrap', columnGap: 2.5, rowGap: 0.5, alignItems: 'center', mb: 1 }}>
          <FormControlLabel control={<Switch size="small" checked={showEnv} onChange={(_, v) => setShowEnv(v)} />} label="Environment temp" />
          <FormControlLabel control={<Switch size="small" checked={showRor} onChange={(_, v) => setShowRor(v)} />} label="Rate of rise" />
          {allowMarks && (
            <>
              <FormControlLabel control={<Switch size="small" checked={showPhases} onChange={(_, v) => setShowPhases(v)} />} label="Phases" />
              <FormControlLabel control={<Switch size="small" checked={showEvents} onChange={(_, v) => setShowEvents(v)} />} label="Events" />
            </>
          )}
          <Box sx={{ flexGrow: 1 }} />
          {showRor && (
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
              <Typography variant="caption" color="text.secondary">
                RoR window
              </Typography>
              <ToggleButtonGroup
                size="small"
                exclusive
                value={rorWindow}
                onChange={(_, v: number | null) => v !== null && onRorWindowChange(v)}
                aria-label="Rate of rise smoothing window"
              >
                {WINDOWS.map((w) => (
                  <ToggleButton key={w} value={w} sx={{ px: 1.25, py: 0.25 }}>
                    {w}s
                  </ToggleButton>
                ))}
              </ToggleButtonGroup>
            </Stack>
          )}
        </Stack>
        <EChart option={option} height={height} label={label} testId="roast-chart" />
        <Typography variant="caption" color="text.secondary">
          Scroll to zoom. Drag the bar below to move. A dotted line (~) is a guess, it was not marked in the file.
        </Typography>
      </CardContent>
    </Card>
  );
}
