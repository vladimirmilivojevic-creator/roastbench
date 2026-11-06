import { screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { dashboard } from '../fixtures';
import { mockApi, renderWithProviders } from '../test-utils';
import DashboardPage from './DashboardPage';

afterEach(() => vi.unstubAllGlobals());

describe('DashboardPage', () => {
  it('shows the roast statistics of the last 30 days', async () => {
    mockApi({ '/api/dashboard': dashboard });

    renderWithProviders(<DashboardPage />);

    const roasts = await screen.findByTestId('kpi-roasts');
    expect(within(roasts).getByText('17')).toBeInTheDocument();
    expect(within(roasts).getByText('22 in total')).toBeInTheDocument();
    expect(screen.getByText('16.8 %')).toBeInTheDocument();
    expect(screen.getByText('14.3 %')).toBeInTheDocument();
    expect(screen.getByText('63.5 kg')).toBeInTheDocument();
  });

  it('lists the low-stock alerts with severity and projection', async () => {
    mockApi({ '/api/dashboard': dashboard });

    renderWithProviders(<DashboardPage />);

    const list = await screen.findByTestId('alert-list');
    const items = within(list).getAllByRole('listitem');
    expect(items).toHaveLength(2);
    expect(within(items[0]).getByText('Out of stock')).toBeInTheDocument();
    expect(within(items[0]).getByRole('link', { name: /SUM-MAND-25/ })).toHaveAttribute('href', '/inventory/7');
    expect(within(items[1]).getByText('Low stock')).toBeInTheDocument();
    expect(within(items[1]).getByText(/8\.0 kg left of 15 kg minimum/)).toBeInTheDocument();
    expect(within(items[1]).getByText(/lasts 61 days/)).toBeInTheDocument();
    expect(within(screen.getByTestId('kpi-alerts')).getByText('2')).toBeInTheDocument();
  });

  it('links the latest roasts to their detail page', async () => {
    mockApi({ '/api/dashboard': dashboard });

    renderWithProviders(<DashboardPage />);

    const link = await screen.findByRole('link', { name: 'PR-0001' }); // batch code, never wrapped
    expect(link).toHaveAttribute('href', '/roasts/1');
    expect(screen.getByText('Guji Natural filter 2')).toBeInTheDocument();
    expect(screen.getByText('10:40')).toBeInTheDocument();
  });

  it('says so when nothing needs reordering', async () => {
    mockApi({ '/api/dashboard': { ...dashboard, alerts: [] } });

    renderWithProviders(<DashboardPage />);

    expect(await screen.findByText('Nothing to reorder')).toBeInTheDocument();
  });

  it('shows the API problem and lets the user retry', async () => {
    const fetchMock = mockApi({ '/api/dashboard': { title: 'Server error', detail: 'Database is down' } }, 500);

    renderWithProviders(<DashboardPage />);

    expect(await screen.findByText('Database is down')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalled();
  });

  it('warns that the data is synthetic', async () => {
    mockApi({ '/api/dashboard': dashboard });

    renderWithProviders(<DashboardPage />);

    expect(await screen.findByText(/synthetic/)).toBeInTheDocument();
  });
});
