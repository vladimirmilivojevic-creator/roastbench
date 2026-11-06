import { defineConfig, devices } from '@playwright/test';

// The tests expect a running RoastBench with the synthetic demo data (SEED_DEMO_DATA=true):
//   docker compose up                     -> http://localhost:8080  (what CI does)
//   backend test-run + `npm run dev`      -> E2E_BASE_URL=http://localhost:5173
const baseURL = process.env.E2E_BASE_URL ?? 'http://localhost:8080';
const viewport = { width: 1440, height: 900 };

export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  timeout: 90_000,
  expect: { timeout: 15_000 },
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : [['list']],
  use: {
    baseURL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'off',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'], viewport }, testIgnore: /screenshots\.spec\.ts/ },
    { name: 'screenshots', use: { ...devices['Desktop Chrome'], viewport }, testMatch: /screenshots\.spec\.ts/ },
  ],
});
