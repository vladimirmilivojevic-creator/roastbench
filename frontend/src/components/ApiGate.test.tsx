import { screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { mockApi, renderWithProviders } from '../test-utils';
import ApiGate from './ApiGate';

afterEach(() => vi.unstubAllGlobals());

describe('ApiGate', () => {
  it('shows the app as soon as the API answers', async () => {
    mockApi({ '/actuator/health': { status: 'UP' } });

    renderWithProviders(
      <ApiGate>
        <p>The app</p>
      </ApiGate>,
    );

    expect(await screen.findByText('The app')).toBeInTheDocument();
    expect(screen.queryByText(/Waking up/)).not.toBeInTheDocument();
  });

  it('explains the wait while the API is asleep and continues by itself', async () => {
    let up = false;
    mockApi({ '/actuator/health': () => (up ? { status: 'UP' } : new Response('', { status: 503 })) });

    renderWithProviders(
      <ApiGate>
        <p>The app</p>
      </ApiGate>,
    );

    expect(await screen.findByText('Waking up the demo server')).toBeInTheDocument();
    expect(screen.queryByText('The app')).not.toBeInTheDocument();

    up = true;
    await waitFor(() => expect(screen.getByText('The app')).toBeInTheDocument(), { timeout: 6000 });
  }, 10000);
});
