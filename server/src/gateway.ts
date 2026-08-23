import { DEFAULT_GOOGLE_MAPS_HOST, GoogleApiBaseUrlSchema } from './schemas.ts';
import type { DirectionsQueryParams } from './types.ts';

export const FETCH_TIMEOUT_MS = 8000;
export const USER_AGENT = 'WatchNavigator-HonoProxy/1.0';
export const DIRECTIONS_PATH = '/maps/api/directions/json';

export async function fetchDirections(
  query: DirectionsQueryParams,
  apiKey: string,
  baseUrl: string = DEFAULT_GOOGLE_MAPS_HOST
): Promise<Response> {
  const baseUrlResult = GoogleApiBaseUrlSchema.safeParse(baseUrl);
  if (!baseUrlResult.success) {
    const issue = baseUrlResult.error.issues[0];
    const isSecurity = issue?.message.includes(DEFAULT_GOOGLE_MAPS_HOST) || issue?.message.includes('HTTPS');
    return new Response(
      JSON.stringify({
        error: 'Internal Server Error',
        message: isSecurity ? issue.message : 'Server configuration error: Invalid GOOGLE_API_BASE_URL.',
        statusCode: 500,
      }),
      {
        status: 500,
        headers: { 'Content-Type': 'application/json' },
      }
    );
  }

  const googleUrl = new URL(DIRECTIONS_PATH, baseUrlResult.data);
  googleUrl.searchParams.set('origin', query.origin);
  googleUrl.searchParams.set('destination', query.destination);
  googleUrl.searchParams.set('mode', query.mode);
  googleUrl.searchParams.set('key', apiKey);

  try {
    const googleResponse = await fetch(googleUrl.toString(), {
      method: 'GET',
      headers: {
        Accept: 'application/json',
        'User-Agent': USER_AGENT,
      },
      signal: AbortSignal.timeout(FETCH_TIMEOUT_MS),
    });

    const responseBody = await googleResponse.text();
    return new Response(responseBody, {
      status: googleResponse.status,
      headers: {
        'Content-Type': googleResponse.headers.get('content-type') || 'application/json',
      },
    });
  } catch (err: unknown) {
    const errorMessage = err instanceof Error ? err.message : 'Network error';
    return new Response(
      JSON.stringify({
        status: 'UNKNOWN_ERROR',
        error_message: `Failed to connect to Google Directions API: ${errorMessage}`,
      }),
      {
        status: 502,
        headers: { 'Content-Type': 'application/json' },
      }
    );
  }
}
