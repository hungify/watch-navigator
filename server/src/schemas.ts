import { z } from 'zod';

export const DEFAULT_GOOGLE_MAPS_HOST = 'https://maps.googleapis.com';

export const TravelModeEnum = z.enum(['driving', 'walking', 'bicycling', 'transit'], {
  message: 'Invalid travel mode. Supported modes are: driving, walking, bicycling, transit.',
});

export const DirectionsQuerySchema = z.object({
  origin: z.string({ message: 'origin parameter is required' }).trim().min(1, 'origin parameter is required'),
  destination: z
    .string({ message: 'destination parameter is required' })
    .trim()
    .min(1, 'destination parameter is required'),
  mode: TravelModeEnum.default('driving'),
});

export const ServerAuthTokenSchema = z
  .string({ message: 'SERVER_AUTH_TOKEN is required' })
  .trim()
  .min(1, 'SERVER_AUTH_TOKEN cannot be empty');

export const GoogleApiKeySchema = z
  .string({ message: 'GOOGLE_DIRECTIONS_API_KEY is required' })
  .trim()
  .min(1, 'GOOGLE_DIRECTIONS_API_KEY cannot be empty');

export const GoogleApiBaseUrlSchema = z
  .string()
  .trim()
  .url('Invalid GOOGLE_API_BASE_URL')
  .refine(
    (val) => {
      try {
        const url = new URL(val);
        return url.origin === DEFAULT_GOOGLE_MAPS_HOST;
      } catch {
        return false;
      }
    },
    { message: `Security error: Upstream Directions API must use ${DEFAULT_GOOGLE_MAPS_HOST}.` }
  );

export const EnvSchema = z.object({
  SERVER_AUTH_TOKEN: ServerAuthTokenSchema,
  GOOGLE_DIRECTIONS_API_KEY: GoogleApiKeySchema,
  GOOGLE_API_BASE_URL: GoogleApiBaseUrlSchema.optional(),
});
