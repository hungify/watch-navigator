import test from 'node:test';
import assert from 'node:assert/strict';
import app from '../src/index.ts';
import { fetchDirections } from '../src/gateway.ts';
import { EnvSchema } from '../src/schemas.ts';
import type { Env } from '../src/types.ts';

interface HealthResponse {
  status: string;
  service: string;
  framework: string;
  runtime: string;
  timestamp: string;
}

interface StatusOkResponse {
  status: string;
}

interface ErrorResponse {
  error: string;
  message: string;
  statusCode: number;
  details?: unknown;
}

interface GoogleErrorResponse {
  status: string;
  error_message: string;
}

interface DirectionsSuccessResponse {
  status: string;
  routes: Array<{
    summary: string;
    legs: Array<{
      distance: { text: string; value: number };
      duration: { text: string; value: number };
      steps: unknown[];
    }>;
  }>;
}

test('GET /health returns 200 and hono metadata', async () => {
  const res = await app.request('/health');

  assert.equal(res.status, 200);
  const data = (await res.json()) as HealthResponse;
  assert.equal(data.status, 'ok');
  assert.equal(data.framework, 'hono');
  assert.equal(data.runtime, 'cloudflare-workers');
});

test('GET / returns 200 and status ok', async () => {
  const res = await app.request('/');

  assert.equal(res.status, 200);
  const data = (await res.json()) as StatusOkResponse;
  assert.equal(data.status, 'ok');
});

test('GET /api/v1/directions returns 500 when SERVER_AUTH_TOKEN is not configured (fail closed)', async () => {
  const env: Env = {
    SERVER_AUTH_TOKEN: '',
    GOOGLE_DIRECTIONS_API_KEY: 'test-key',
  };

  const res = await app.request('/api/v1/directions?origin=21.0,105.0&destination=21.1,105.1', {}, env);
  assert.equal(res.status, 500);
  const body = (await res.json()) as ErrorResponse;
  assert.equal(body.error, 'Internal Server Error');
  assert.match(body.message, /SERVER_AUTH_TOKEN is not configured/);
});

test('GET /api/v1/directions returns 401 when token is missing or invalid', async () => {
  const env: Env = {
    SERVER_AUTH_TOKEN: 'secret-token-123',
    GOOGLE_DIRECTIONS_API_KEY: 'test-key',
  };

  const resNoAuth = await app.request('/api/v1/directions?origin=21.0,105.0&destination=21.1,105.1', {}, env);
  assert.equal(resNoAuth.status, 401);
  const bodyNoAuth = (await resNoAuth.json()) as ErrorResponse;
  assert.equal(bodyNoAuth.error, 'Unauthorized');

  const resWrongAuth = await app.request(
    '/api/v1/directions?origin=21.0,105.0&destination=21.1,105.1',
    {
      headers: { Authorization: 'Bearer wrong-token' },
    },
    env
  );
  assert.equal(resWrongAuth.status, 401);
});

test('GET /api/v1/directions returns 400 when travel mode is invalid', async () => {
  const env: Env = {
    SERVER_AUTH_TOKEN: 'secret-token-123',
    GOOGLE_DIRECTIONS_API_KEY: 'test-key',
  };

  const res = await app.request(
    '/api/v1/directions?origin=21.0,105.0&destination=21.1,105.1&mode=flying_car',
    {
      headers: { Authorization: 'Bearer secret-token-123' },
    },
    env
  );
  assert.equal(res.status, 400);
  const data = (await res.json()) as ErrorResponse;
  assert.equal(data.error, 'Bad Request');
  assert.match(data.message, /Invalid travel mode/);
});

test('GET /api/v1/directions succeeds with valid Bearer token or X-API-Key', async () => {
  const originalFetch = globalThis.fetch;
  let capturedUrl: string | null = null;

  globalThis.fetch = async (input: RequestInfo | URL, init?: RequestInit) => {
    capturedUrl = input.toString();
    return new Response(
      JSON.stringify({
        status: 'OK',
        routes: [
          {
            summary: 'Mocked Route',
            legs: [{ distance: { text: '3 km', value: 3000 }, duration: { text: '5 min', value: 300 }, steps: [] }],
          },
        ],
      }),
      {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }
    );
  };

  const env: Env = {
    SERVER_AUTH_TOKEN: 'secret-token-123',
    GOOGLE_DIRECTIONS_API_KEY: 'google-secret-key',
  };

  try {
    const resBearer = await app.request(
      '/api/v1/directions?origin=21.0285,105.8542&destination=place_id:ChIJ123&mode=driving',
      {
        headers: { Authorization: 'Bearer secret-token-123' },
      },
      env
    );
    assert.equal(resBearer.status, 200);
    const dataBearer = (await resBearer.json()) as DirectionsSuccessResponse;
    assert.equal(dataBearer.status, 'OK');
    assert.equal(dataBearer.routes[0].summary, 'Mocked Route');

    assert.ok(capturedUrl);
    const parsedUrlBearer = new URL(capturedUrl);
    assert.equal(parsedUrlBearer.origin, 'https://maps.googleapis.com');
    assert.equal(parsedUrlBearer.pathname, '/maps/api/directions/json');
    assert.equal(parsedUrlBearer.searchParams.get('origin'), '21.0285,105.8542');
    assert.equal(parsedUrlBearer.searchParams.get('destination'), 'place_id:ChIJ123');
    assert.equal(parsedUrlBearer.searchParams.get('mode'), 'driving');
    assert.equal(parsedUrlBearer.searchParams.get('key'), 'google-secret-key');

    capturedUrl = null;
    const resApiKey = await app.request(
      '/api/v1/directions?origin=21.0285,105.8542&destination=place_id:ChIJ123&mode=driving',
      {
        headers: { 'X-API-Key': 'secret-token-123' },
      },
      env
    );
    assert.equal(resApiKey.status, 200);
    assert.ok(capturedUrl);
    const parsedUrlApiKey = new URL(capturedUrl);
    assert.equal(parsedUrlApiKey.searchParams.get('key'), 'google-secret-key');
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test('GET /api/v1/directions returns 400 when origin or destination is missing', async () => {
  const env: Env = {
    SERVER_AUTH_TOKEN: 'secret-token-123',
    GOOGLE_DIRECTIONS_API_KEY: 'test-key',
  };

  const res = await app.request(
    '/api/v1/directions?origin=21.0,105.0',
    {
      headers: { Authorization: 'Bearer secret-token-123' },
    },
    env
  );
  assert.equal(res.status, 400);
  const data = (await res.json()) as ErrorResponse;
  assert.equal(data.error, 'Bad Request');
});

test('GET /api/v1/directions returns 500 when GOOGLE_DIRECTIONS_API_KEY is not configured', async () => {
  const env: Env = {
    SERVER_AUTH_TOKEN: 'secret-token-123',
    GOOGLE_DIRECTIONS_API_KEY: '',
  };

  const res = await app.request(
    '/api/v1/directions?origin=21.0,105.0&destination=21.1,105.1',
    {
      headers: { Authorization: 'Bearer secret-token-123' },
    },
    env
  );
  assert.equal(res.status, 500);
  const data = (await res.json()) as GoogleErrorResponse;
  assert.equal(data.status, 'REQUEST_DENIED');
});

test('GET /api/v1/directions rejects non-Google upstream URL origin', async () => {
  const env: Env = {
    SERVER_AUTH_TOKEN: 'secret-token-123',
    GOOGLE_DIRECTIONS_API_KEY: 'test-key',
    GOOGLE_API_BASE_URL: 'https://insecure-api.example.com',
  };

  const res = await app.request(
    '/api/v1/directions?origin=21.0,105.0&destination=21.1,105.1',
    {
      headers: { Authorization: 'Bearer secret-token-123' },
    },
    env
  );
  assert.equal(res.status, 500);
  const data = (await res.json()) as ErrorResponse;
  assert.equal(data.error, 'Internal Server Error');
  assert.match(data.message, /Upstream Directions API must use https:\/\/maps\.googleapis\.com/);
});

test('OPTIONS /api/v1/directions returns 204 with CORS headers', async () => {
  const res = await app.request('/api/v1/directions', { method: 'OPTIONS' });
  assert.equal(res.status, 204);
  assert.equal(res.headers.get('Access-Control-Allow-Origin'), '*');
});

test('GET /unknown-endpoint returns 404', async () => {
  const res = await app.request('/unknown-path');
  assert.equal(res.status, 404);
});

test('GET /api/v1/directions returns 502 UNKNOWN_ERROR on upstream network error / abort', async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => {
    throw new Error('fetch failed');
  };

  const env: Env = {
    SERVER_AUTH_TOKEN: 'secret-token-123',
    GOOGLE_DIRECTIONS_API_KEY: 'test-key',
  };

  try {
    const res = await app.request(
      '/api/v1/directions?origin=21.0,105.0&destination=21.1,105.1',
      {
        headers: { Authorization: 'Bearer secret-token-123' },
      },
      env
    );
    assert.equal(res.status, 502);
    const data = (await res.json()) as GoogleErrorResponse;
    assert.equal(data.status, 'UNKNOWN_ERROR');
    assert.match(data.error_message, /Failed to connect to Google Directions API/);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test('EnvSchema validates valid environment and rejects empty or insecure values', () => {
  const valid = EnvSchema.safeParse({
    SERVER_AUTH_TOKEN: 'my-token',
    GOOGLE_DIRECTIONS_API_KEY: 'my-key',
    GOOGLE_API_BASE_URL: 'https://maps.googleapis.com',
  });
  assert.equal(valid.success, true);

  const missingToken = EnvSchema.safeParse({
    SERVER_AUTH_TOKEN: '',
    GOOGLE_DIRECTIONS_API_KEY: 'my-key',
  });
  assert.equal(missingToken.success, false);

  const insecureUrl = EnvSchema.safeParse({
    SERVER_AUTH_TOKEN: 'my-token',
    GOOGLE_DIRECTIONS_API_KEY: 'my-key',
    GOOGLE_API_BASE_URL: 'http://insecure.example.com',
  });
  assert.equal(insecureUrl.success, false);

  const nonGoogleOrigin = EnvSchema.safeParse({
    SERVER_AUTH_TOKEN: 'my-token',
    GOOGLE_DIRECTIONS_API_KEY: 'my-key',
    GOOGLE_API_BASE_URL: 'https://evil.example.com',
  });
  assert.equal(nonGoogleOrigin.success, false);
});

test('fetchDirections directly executes upstream request and validates security', async () => {
  const originalFetch = globalThis.fetch;
  let capturedInput: string | null = null;

  globalThis.fetch = async (input: RequestInfo | URL) => {
    capturedInput = input.toString();
    return new Response(JSON.stringify({ status: 'OK', routes: [] }), { status: 200 });
  };

  try {
    const res = await fetchDirections(
      { origin: '21.0,105.0', destination: '21.1,105.1', mode: 'driving' },
      'my-key'
    );
    assert.equal(res.status, 200);
    assert.ok(capturedInput);
    assert.match(capturedInput, /https:\/\/maps\.googleapis\.com\/maps\/api\/directions\/json/);

    const insecureRes = await fetchDirections(
      { origin: '21.0,105.0', destination: '21.1,105.1', mode: 'driving' },
      'my-key',
      'http://insecure.example.com'
    );
    assert.equal(insecureRes.status, 500);
    const body = (await insecureRes.json()) as ErrorResponse;
    assert.match(body.message, /Upstream Directions API must use https:\/\/maps\.googleapis\.com/);
  } finally {
    globalThis.fetch = originalFetch;
  }
});
