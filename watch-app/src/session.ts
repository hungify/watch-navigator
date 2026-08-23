import type { NavigationPayload, WatchPageState } from './types.ts';

import { getTurnIcon, isValidNavigationPayload } from './types.ts';

const INITIAL_STATE: Readonly<WatchPageState> = Object.freeze({
  distance: '0',
  isNavigating: false,
  statusText: 'Disconnected',
  street: 'Ready',
  turnIcon: '↑'
});

export class NavigationSession {
  #state: WatchPageState;

  constructor(initialState?: Partial<WatchPageState>) {
    this.#state = {
      ...INITIAL_STATE,
      ...initialState
    };
  }

  getState(): WatchPageState {
    return { ...this.#state };
  }

  ingest(data: unknown): boolean {
    if (!isValidNavigationPayload(data)) {
      return false;
    }

    const payload: NavigationPayload = data;
    const isArrived = payload.turn.trim().toLowerCase() === 'arrive';
    const rawDistance = payload.distance_m ?? payload.distanceMeters ?? 0;
    let rawStreet = '';
    if (typeof payload.street === 'string') {
      rawStreet = payload.street;
    } else if (typeof payload.streetName === 'string') {
      rawStreet = payload.streetName;
    }

    this.#state = {
      distance: String(rawDistance),
      isNavigating: true,
      statusText: isArrived ? 'Arrived' : 'Navigating',
      street: rawStreet,
      turnIcon: getTurnIcon(payload.turn)
    };

    return true;
  }

  reset(): void {
    this.#state = { ...INITIAL_STATE };
  }
}
