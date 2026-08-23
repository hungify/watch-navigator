import type { NavigationSession } from './session.ts';

export type TurnType =
  | 'straight'
  | 'left'
  | 'turn-left'
  | 'sharp-left'
  | 'right'
  | 'turn-right'
  | 'sharp-right'
  | 'slight-left'
  | 'slight-right'
  | 'uturn'
  | 'arrive';

export interface NavigationPayload {
  turn: TurnType | (string & {});
  distance_m?: number;
  distanceMeters?: number;
  street?: string;
  streetName?: string;
}

export interface WatchPageState {
  isNavigating: boolean;
  statusText: string;
  turnIcon: string;
  distance: string;
  street: string;
}

export interface WatchPageIndexPage extends WatchPageState {
  data: WatchPageState;
  session: NavigationSession;
  onInit(this: WatchPageIndexPage): void;
  onShow(this: WatchPageIndexPage): void;
  onHide(this: WatchPageIndexPage): void;
  onDestroy(this: WatchPageIndexPage): void;
  initWearEngineReceiver(this: WatchPageIndexPage): void;
  destroyWearEngineReceiver(this: WatchPageIndexPage): void;
  updateNavigation(this: WatchPageIndexPage, data: unknown): void;
}

export function getTurnIcon(turn: string): string {
  const normalizedTurn = (turn || '').trim().toLowerCase().replace(/_/g, '-');
  switch (normalizedTurn) {
    case 'left':
    case 'turn-left':
    case 'sharp-left':
      return '←';
    case 'right':
    case 'turn-right':
    case 'sharp-right':
      return '→';
    case 'slight-left':
      return '↖';
    case 'slight-right':
      return '↗';
    case 'uturn':
      return '⮌';
    case 'arrive':
      return '★';
    case 'straight':
    default:
      return '↑';
  }
}

export function isValidNavigationPayload(data: unknown): data is NavigationPayload {
  if (!data || typeof data !== 'object' || Array.isArray(data)) {
    return false;
  }
  const candidate = data as Record<string, unknown>;
  return typeof candidate.turn === 'string' && candidate.turn.trim().length > 0;
}
