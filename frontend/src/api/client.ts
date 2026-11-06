/** Base URL of the API. Empty in development and in Docker (same origin); set at build time for GitHub Pages. */
export const API_URL: string = (import.meta.env.VITE_API_URL ?? '').replace(/\/$/, '');

/** An RFC 9457 problem response from the API, or a network failure. */
export class ApiError extends Error {
  readonly status: number;
  readonly title: string;
  readonly details: string[];
  readonly fieldErrors: Record<string, string>;

  constructor(status: number, title: string, message: string, details: string[] = [], fieldErrors: Record<string, string> = {}) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.title = title;
    this.details = details;
    this.fieldErrors = fieldErrors;
  }
}

interface Problem {
  title?: string;
  detail?: string;
  details?: string[];
  errors?: Record<string, string>;
}

async function parseError(response: Response): Promise<ApiError> {
  let problem: Problem = {};
  try {
    problem = (await response.json()) as Problem;
  } catch {
    // not JSON (e.g. a proxy error page)
  }
  const title = problem.title ?? response.statusText ?? 'Request failed';
  const message = problem.detail ?? `${response.status} ${title}`;
  return new ApiError(response.status, title, message, problem.details ?? [], problem.errors ?? {});
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`${API_URL}${path}`, init);
  } catch {
    throw new ApiError(0, 'Network error', 'Cannot reach the API. Check your connection and try again.');
  }
  if (!response.ok) {
    throw await parseError(response);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

function jsonInit(method: string, body?: unknown): RequestInit {
  return {
    method,
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  };
}

export const http = {
  get: <T>(path: string) => request<T>(path, { headers: { Accept: 'application/json' } }),
  post: <T>(path: string, body?: unknown) => request<T>(path, jsonInit('POST', body)),
  put: <T>(path: string, body?: unknown) => request<T>(path, jsonInit('PUT', body)),
  patch: <T>(path: string, body?: unknown) => request<T>(path, jsonInit('PATCH', body)),
  delete: <T = void>(path: string) => request<T>(path, { method: 'DELETE' }),
  upload: <T>(path: string, file: File, jsonPart?: { name: string; value: unknown }) => {
    const form = new FormData();
    form.append('file', file, file.name);
    if (jsonPart) {
      form.append(jsonPart.name, new Blob([JSON.stringify(jsonPart.value)], { type: 'application/json' }));
    }
    return request<T>(path, { method: 'POST', body: form });
  },
};

/** Builds a query string from defined values only. */
export function query(params: Record<string, string | number | boolean | undefined | null>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, String(value));
    }
  }
  const text = search.toString();
  return text ? `?${text}` : '';
}
