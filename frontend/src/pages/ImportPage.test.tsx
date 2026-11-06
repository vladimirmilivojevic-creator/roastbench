import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { detail, lowLot, preview } from '../fixtures';
import { mockApi, problem, renderWithProviders } from '../test-utils';
import ImportPage from './ImportPage';

// The chart needs a canvas, which jsdom does not have.
vi.mock('../components/EChart', () => ({ default: ({ label }: { label: string }) => <div data-testid="echart">{label}</div> }));

afterEach(() => vi.unstubAllGlobals());

function api(overrides: Record<string, unknown> = {}) {
  return mockApi({
    '/api/profiles': [],
    '/api/lots': [lowLot],
    '/api/roasts/import/preview': preview,
    '/api/roasts/import': detail,
    ...overrides,
  });
}

const csv = () => new File(['Time,BT\n0,200\n'], 'guji.csv', { type: 'text/csv' });

describe('ImportPage', () => {
  it('previews a file: metrics, warnings and the chart appear before anything is saved', async () => {
    const fetchMock = api();
    renderWithProviders(<ImportPage />, { route: '/import' });

    await userEvent.upload(screen.getByTestId('file-input'), csv());

    expect(await screen.findByTestId('echart')).toBeInTheDocument();
    expect(within(screen.getByTestId('preview-dtr')).getByText('16.4 %')).toBeInTheDocument();
    expect(screen.getByText(/CHARGE is not marked/)).toBeInTheDocument();
    expect(screen.getByLabelText(/^Name/)).toHaveValue('Guji test');
    expect(screen.getByLabelText('Machine')).toHaveValue('Giesen W6');
    const saved = fetchMock.mock.calls.filter(([url, init]) => String(url).endsWith('/api/roasts/import') && init?.method === 'POST');
    expect(saved).toHaveLength(0);
  });

  it('sends the details with the file when the roast is imported', async () => {
    const fetchMock = api();
    renderWithProviders(<ImportPage />, { route: '/import' });
    await userEvent.upload(screen.getByTestId('file-input'), csv());
    await screen.findByTestId('echart');

    fireEvent.change(screen.getByLabelText(/^Name/), { target: { value: 'My roast' } });
    await userEvent.click(screen.getByRole('button', { name: 'Import roast' }));

    await waitFor(() => {
      const call = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/api/roasts/import'));
      expect(call).toBeDefined();
    });
    const [, init] = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/api/roasts/import'))!;
    const form = init?.body as FormData;
    expect((form.get('file') as File).name).toBe('guji.csv');
    const request = JSON.parse(await (form.get('request') as Blob).text());
    expect(request.name).toBe('My roast');
    expect(request.machine).toBe('Giesen W6');
    expect(request.roastedWeightG).toBe(4275);
    expect(request.lots).toBeUndefined();
  });

  it('refuses to import while a green coffee row is incomplete', async () => {
    api();
    renderWithProviders(<ImportPage />, { route: '/import' });
    await userEvent.upload(screen.getByTestId('file-input'), csv());
    await screen.findByTestId('echart');

    await userEvent.click(screen.getByRole('button', { name: 'Add green coffee lot' }));

    expect(screen.getByText(/Choose a lot for every green coffee row/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Import roast' })).toBeDisabled();
  });

  it('checks that the roasted weight does not exceed the green weight', async () => {
    api();
    renderWithProviders(<ImportPage />, { route: '/import' });
    await userEvent.upload(screen.getByTestId('file-input'), csv());
    await screen.findByTestId('echart');

    fireEvent.change(screen.getByLabelText('Roasted weight'), { target: { value: '6000' } });

    expect(screen.getByText(/cannot be larger than the green weight/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Import roast' })).toBeDisabled();
  });

  it('shows what is wrong with an unreadable file', async () => {
    api({
      '/api/roasts/import/preview': problem(400, 'Could not read the roast log', 'The CSV has invalid rows', {
        details: ['Line 4: invalid bean temperature \'abc\''],
      }),
    });
    renderWithProviders(<ImportPage />, { route: '/import' });

    await userEvent.upload(screen.getByTestId('file-input'), csv());

    expect(await screen.findByText('The CSV has invalid rows')).toBeInTheDocument();
    expect(screen.getByText(/Line 4: invalid bean temperature/)).toBeInTheDocument();
    expect(screen.queryByTestId('echart')).not.toBeInTheDocument();
  });
});
