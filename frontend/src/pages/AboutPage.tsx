import { Alert, Box, Button, Card, CardContent, Chip, Link as MuiLink, Stack, Typography } from '@mui/material';
import { API_URL } from '../api/client';
import { PageHeader } from '../components/common';

const REPO = 'https://github.com/vladimirmilivojevic-creator/roastbench';

const TECH = [
  'Java 21',
  'Spring Boot',
  'PostgreSQL',
  'Flyway',
  'OpenAPI',
  'React',
  'TypeScript',
  'MUI',
  'ECharts',
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
      <PageHeader title="About RoastBench" subtitle="An app for roast profiles and green coffee stock" />

      <Alert severity="info" sx={{ mb: 3 }}>
        <b>All roasts in this demo are made up.</b> The curves are generated to look like real roasts. They were not
        recorded on a real roaster.
      </Alert>

      <Box sx={{ display: 'grid', gap: 3, gridTemplateColumns: { xs: '1fr', lg: '1fr 1fr' } }}>
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              What it does
            </Typography>
            <Box component="ul" sx={{ m: 0, pl: 2.5, '& li': { mb: 0.75 } }}>
              <li>Reads roast files: CSV and Artisan <code>.alog</code>.</li>
              <li>
                Calculates <b>RoR</b> (how fast the bean temperature goes up), <b>DTR</b> (how long the development
                phase is) and <b>roast loss</b> (how much weight the beans lose).
              </li>
              <li>Shows the roast on a chart and splits it into drying, Maillard and development.</li>
              <li>Compares a roast with the reference roast of its profile, or compares up to 10 roasts.</li>
              <li>
                Keeps track of your <b>green coffee</b>. A roast takes its coffee out of stock by itself. You get a
                warning when a lot is almost empty.
              </li>
            </Box>
          </CardContent>
        </Card>

        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              Made with
            </Typography>
            <Stack direction="row" sx={{ flexWrap: 'wrap', gap: 0.75, mb: 2 }}>
              {TECH.map((s) => (
                <Chip key={s} label={s} size="small" variant="outlined" />
              ))}
            </Stack>
            <Typography color="text.secondary" sx={{ mb: 2 }}>
              The backend is a Java REST API with a PostgreSQL database. The frontend is a React app. The API has
              documentation (OpenAPI), and the project has unit tests, integration tests and browser tests.
            </Typography>
            <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', rowGap: 1 }}>
              <Button variant="contained" component="a" href={REPO} target="_blank" rel="noreferrer">
                Code on GitHub
              </Button>
              <Button variant="outlined" component="a" href={swagger} target="_blank" rel="noreferrer">
                API documentation
              </Button>
              <Button variant="outlined" component="a" href={`${REPO}/tree/main/samples`} target="_blank" rel="noreferrer">
                Example roast files
              </Button>
            </Stack>
          </CardContent>
        </Card>
      </Box>

      <Typography variant="body2" color="text.secondary" sx={{ mt: 3 }}>
        Made by Vladimir Milivojevic as a portfolio project. All rights reserved, see the license in the repository.{' '}
        <MuiLink href={REPO} target="_blank" rel="noreferrer">
          github.com/vladimirmilivojevic-creator/roastbench
        </MuiLink>
      </Typography>
    </>
  );
}
