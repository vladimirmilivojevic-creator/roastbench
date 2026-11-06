import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, http, query } from './client';
import { mockApi, problem } from '../test-utils';

afterEach(() => vi.unstubAllGlobals());

describe('query', () => {
  it('keeps defined values only', () => {
    expect(query({ q: 'guji', page: 0, profileId: undefined, empty: '', flag: false, none: null })).toBe('?q=guji&page=0&flag=false');
  });

  it('is empty without parameters and encodes values', () => {
    expect(query({})).toBe('');
    expect(query({ q: 'a b&c' })).toBe('?q=a+b%26c');
  });
});

describe('http', () => {
  it('returns parsed JSON', async () => {
    mockApi({ '/api/lots/1': { id: 1, code: 'ETH-1' } });

    await expect(http.get('/api/lots/1')).resolves.toEqual({ id: 1, code: 'ETH-1' });
  });

  it('returns undefined for 204 responses', async () => {
    mockApi({ '/api/roasts/1': new Response(null, { status: 204 }) });

    await expect(http.delete('/api/roasts/1')).resolves.toBeUndefined();
  });

  it('turns problem details into an ApiError with details and field errors', async () => {
    mockApi({
      '/api/roasts/import': problem(400, 'Could not read the roast log', 'The CSV has invalid rows', {
        details: ['Line 4: invalid bean temperature'],
        errors: { name: 'must not be blank' },
      }),
    });

    const error = await http.post('/api/roasts/import').catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    const api = error as ApiError;
    expect(api.status).toBe(400);
    expect(api.title).toBe('Could not read the roast log');
    expect(api.message).toBe('The CSV has invalid rows');
    expect(api.details).toEqual(['Line 4: invalid bean temperature']);
    expect(api.fieldErrors).toEqual({ name: 'must not be blank' });
  });

  it('copes with error responses that are not JSON', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response('<html>Bad gateway</html>', { status: 502, statusText: 'Bad Gateway' })));

    const error = (await http.get('/api/x').catch((e: unknown) => e)) as ApiError;

    expect(error.status).toBe(502);
    expect(error.message).toContain('502');
  });

  it('reports a network failure as status 0', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => {
      throw new TypeError('Failed to fetch');
    }));

    const error = (await http.get('/api/x').catch((e: unknown) => e)) as ApiError;

    expect(error.status).toBe(0);
    expect(error.message).toMatch(/Cannot reach the API/);
  });

  it('sends JSON bodies and multipart uploads', async () => {
    const fetchMock = mockApi({ '/api/profiles': { id: 1 }, '/api/roasts/import': { ok: true } });

    await http.post('/api/profiles', { name: 'Filter' });
    await http.upload('/api/roasts/import', new File(['a,b'], 'x.csv'), { name: 'request', value: { name: 'x' } });

    const [, jsonInit] = fetchMock.mock.calls[0];
    expect(jsonInit?.method).toBe('POST');
    expect(jsonInit?.body).toBe('{"name":"Filter"}');
    const [, uploadInit] = fetchMock.mock.calls[1];
    const form = uploadInit?.body as FormData;
    expect((form.get('file') as File).name).toBe('x.csv');
    expect(form.get('request')).toBeInstanceOf(Blob);
  });
});
