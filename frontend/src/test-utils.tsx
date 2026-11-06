import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import type { ReactElement } from 'react';
import { MemoryRouter } from 'react-router';
import { vi } from 'vitest';
import { ColorModeProvider } from './colorMode';

export function renderWithProviders(ui: ReactElement, { route = '/' }: { route?: string } = {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <ColorModeProvider>
        <MemoryRouter initialEntries={[route]}>{ui}</MemoryRouter>
      </ColorModeProvider>
    </QueryClientProvider>,
  );
}

type Handler = unknown | ((request: { path: string; init?: RequestInit }) => unknown);

/** Replaces fetch with canned responses. Keys are path prefixes; the longest matching prefix wins. */
export function mockApi(handlers: Record<string, Handler>, status = 200) {
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input).replace(/^https?:\/\/[^/]+/, '');
    const key = Object.keys(handlers)
      .filter((k) => path.startsWith(k))
      .sort((a, b) => b.length - a.length)[0];
    if (!key) {
      return new Response(JSON.stringify({ title: 'Not found', detail: `No mock for ${path}` }), { status: 404 });
    }
    const handler = handlers[key];
    const body = typeof handler === 'function' ? handler({ path, init }) : handler;
    if (body instanceof Response) return body;
    return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

export function problem(status: number, title: string, detail: string, extra: Record<string, unknown> = {}) {
  return new Response(JSON.stringify({ title, detail, status, ...extra }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  });
}
