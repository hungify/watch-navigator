import { type Context, Hono } from 'hono';
import { cors } from 'hono/cors';
import { createFactory } from 'hono/factory';
import { zValidator } from '@hono/zod-validator';
import { fetchDirections } from './gateway.ts';
import { DirectionsQuerySchema, GoogleApiKeySchema, ServerAuthTokenSchema } from './schemas.ts';
import type { Env } from './types.ts';

const extractProvidedAuthToken = (c: Context): string | null => {
  const authHeader = c.req.header('Authorization');
  if (authHeader?.startsWith('Bearer ')) {
    return authHeader.slice(7).trim();
  }
  const apiKeyHeader = c.req.header('X-API-Key');
  return apiKeyHeader ? apiKeyHeader.trim() : null;
};

const factory = createFactory<{ Bindings: Env }>();
const app = new Hono<{ Bindings: Env }>();

app.use(
  '*',
  cors({
    origin: '*',
    allowMethods: ['GET', 'OPTIONS'],
    allowHeaders: ['Content-Type', 'Authorization', 'X-API-Key'],
  })
);

const healthHandler = (c: Context) => {
  return c.json({
    status: 'ok',
    service: 'watch-navigator-proxy',
    framework: 'hono',
    runtime: 'cloudflare-workers',
    timestamp: new Date().toISOString(),
  });
};

app.get('/health', healthHandler);
app.get('/', healthHandler);

const queryValidator = zValidator('query', DirectionsQuerySchema, (result, c) => {
  if (!result.success) {
    const firstIssue = result.error.issues[0];
    const message = firstIssue?.message || 'Invalid query parameters';
    const isInvalidMode =
      firstIssue?.path.includes('mode') ||
      firstIssue?.code === 'invalid_value' ||
      message.includes('Invalid travel mode');
    return c.json(
      {
        error: 'Bad Request',
        message: isInvalidMode ? message : `Missing required query parameters: ${message}`,
        statusCode: 400,
        details: result.error.issues,
      },
      400
    );
  }
});

const directionsHandlers = factory.createHandlers(queryValidator, async (c) => {
  const env: Env = c.env || {};

  const authTokenResult = ServerAuthTokenSchema.safeParse(env.SERVER_AUTH_TOKEN);
  if (!authTokenResult.success) {
    return c.json(
      {
        error: 'Internal Server Error',
        message: 'Server configuration error: SERVER_AUTH_TOKEN is not configured on the proxy worker.',
        statusCode: 500,
      },
      500
    );
  }

  const providedToken = extractProvidedAuthToken(c);
  if (!providedToken || providedToken !== authTokenResult.data) {
    return c.json(
      {
        error: 'Unauthorized',
        message: 'Invalid or missing authentication token in Authorization or X-API-Key header.',
        statusCode: 401,
      },
      401
    );
  }

  const googleApiKeyResult = GoogleApiKeySchema.safeParse(env.GOOGLE_DIRECTIONS_API_KEY);
  if (!googleApiKeyResult.success) {
    return c.json(
      {
        status: 'REQUEST_DENIED',
        error_message: 'Server configuration error: GOOGLE_DIRECTIONS_API_KEY is not configured on the proxy worker.',
      },
      500
    );
  }

  const query = c.req.valid('query');
  return fetchDirections(query, googleApiKeyResult.data, env.GOOGLE_API_BASE_URL);
});

app.get('/api/v1/directions', ...directionsHandlers);
app.get('/directions', ...directionsHandlers);

app.notFound((c) => {
  return c.json(
    {
      error: 'Not Found',
      message: `Endpoint ${c.req.method} ${c.req.path} not found.`,
      statusCode: 404,
    },
    404
  );
});

export default app;
