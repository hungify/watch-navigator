import type { z } from 'zod';
import type { DirectionsQuerySchema, EnvSchema, TravelModeEnum } from './schemas.ts';

export type TravelMode = z.infer<typeof TravelModeEnum>;
export type DirectionsQueryParams = z.infer<typeof DirectionsQuerySchema>;
export type ValidatedEnv = z.infer<typeof EnvSchema>;

export interface Env {
  SERVER_AUTH_TOKEN?: string;
  GOOGLE_DIRECTIONS_API_KEY?: string;
  GOOGLE_API_BASE_URL?: string;
}
