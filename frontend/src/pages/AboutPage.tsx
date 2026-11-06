import { Alert, Box, Button, Card, CardContent, Chip, Link as MuiLink, Stack, Typography } from '@mui/material';
import { API_URL } from '../api/client';
import { PageHeader } from '../components/common';

const REPO = 'https://github.com/vladimirmilivojevic-creator/roastbench';

const STACK = [
  'Java 21',
  'Spring Boot 3',
  'PostgreSQL',
  'Flyway',
  'OpenAPI',
  'React',
  'TypeScript',
  'MUI',
  'Apache ECharts',
  'JUnit 5',
  'Testcontainers',
  'Playwright',
  'Docker',
  'GitHub Actions',
];

export default function AboutPage() {
  const swagger = `${API_URL}/swagger-ui.html`;
  return (
    <>
      <PageHeader title="About RoastBench" subtitle="A roast profile and green coffee inventory app for small roasteries" />

      <Alert severity="info" sx={{ mb: 3 }}>
        <b>Every roast in this demo is synthetic.</b> The curves are generated to behave like drum roasts (a turning
        point, a decaying rate of rise, first crack, development) so the features can be shown without a roaster. They
        are not recorded data.
      </Alert>

      <Box sx={{ display: 'grid', gap: 3, gridTemplateColumns: { xs: '1fr', lg: '1fr 1fr' } }}>
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              What it does
            </Typography>
            <Box component="ul" sx={{ m: 0, pl: 2.5, '& li': { mb: 0.75 } }}>
              <li>Imports roast logs from CSV and Artisan <code>.alog</code> files.</li>
              <li>
                Calculates the <b>rate of rise</b> (a regression slope over a smoothing window), the <b>development
                time ratio</b> (time from first crack to drop divided by total time) and the <b>roast loss</b>.
              </li>
              <li>Splits each roast into drying, Maillard and development phases and marks events on the chart.</li>
              <li>Compares a batch with the reference roast of its profile, or up to ten batches with each other.</li>
              <li>
                Tracks <b>green coffee lots</b> with origin details and an append-only stock ledger. Roasts deduct
                their green coffee automatically; lots below their minimum level raise alerts.
              </li>
            </Box>
          </CardContent>
        </Card>

        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              Under the hood
            </Typography>
            <Stack direction="row" sx={{ flexWrap: 'wrap', gap: 0.75, mb: 2 }}>
              {STACK.map((s) => (
                <Chip key={s} label={s} size="small" variant="outlined" />
              ))}
            </Stack>
            <Typography color="text.secondary" sx={{ mb: 2 }}>
              The API is documented with OpenAPI. The roast maths lives in a small framework-free Java package and the
              chart logic in framework-free TypeScript, both covered by unit tests; the API is tested against a real
              PostgreSQL with Testcontainers and the UI end to end with Playwright.
            </Typography>
            <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', rowGap: 1 }}>
              <Button variant="contained" component="a" href={REPO} target="_blank" rel="noreferrer">
                Source on GitHub
              </Button>
              <Button variant="outlined" component="a" href={swagger} target="_blank" rel="noreferrer">
                API documentation
              </Button>
              <Button variant="outlined" component="a" href={`${REPO}/tree/main/samples`} target="_blank" rel="noreferrer">
                Sample roast files
              </Button>
            </Stack>
          </CardContent>
        </Card>
      </Box>

      <Typography variant="body2" color="text.secondary" sx={{ mt: 3 }}>
        Built by Vladimir Milivojevic as a portfolio project. MIT licensed.{' '}
        <MuiLink href={REPO} target="_blank" rel="noreferrer">
          github.com/vladimirmilivojevic-creator/roastbench
        </MuiLink>
      </Typography>
    </>
  );
}
