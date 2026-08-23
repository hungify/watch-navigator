import type { WatchPageState } from './types.ts';

import { HapticsService } from './haptics.ts';
import {
  formatDistance,
  getCanonicalTurn,
  getTurnIcon,
  isValidNavigationPayload
} from './types.ts';

const INITIAL_STATE: Readonly<WatchPageState> = Object.freeze({
  distance: '0',
  distanceUnit: 'm',
  isArrived: false,
  isNavigating: false,
  statusText: 'Disconnected',
  street: 'Ready',
  turnIcon: '/common/turn_straight.png'
});

export class NavigationSession {
  private haptics: HapticsService;
  private lastStreet: string = '';
  private lastTurn: string = '';
  private state: WatchPageState;

  constructor(
    hapticsOrInitialState?: HapticsService | Partial<WatchPageState>,
    initialState?: Partial<WatchPageState>
  ) {
    if (hapticsOrInitialState instanceof HapticsService) {
      this.haptics = hapticsOrInitialState;
      this.state = { ...INITIAL_STATE, ...initialState };
    } else if (hapticsOrInitialState && typeof hapticsOrInitialState === 'object') {
      this.haptics = new HapticsService();
      this.state = { ...INITIAL_STATE, ...hapticsOrInitialState };
    } else {
      this.haptics = new HapticsService();
      this.state = { ...INITIAL_STATE, ...initialState };
    }
  }

  getState(): WatchPageState {
    return { ...this.state };
  }

  ingest(data: unknown): boolean {
    if (!isValidNavigationPayload(data)) {
      return false;
    }

    const payload = data;
    const canonicalTurn = getCanonicalTurn(payload.turn);
    const isArrived = canonicalTurn === 'arrive';
    const rawDistance = payload.distance_m ?? payload.distanceMeters;
    const formattedDistance = formatDistance(rawDistance);
    const resolvedStreet = payload.street ?? payload.streetName ?? '';
    const turnIcon = getTurnIcon(payload.turn);

    const isFirstUpdate = !this.state.isNavigating;
    const hasPromptChanged = this.lastTurn !== canonicalTurn || this.lastStreet !== resolvedStreet;
    const isArrivalTransition = isArrived && !this.state.isArrived;

    this.state = {
      distance: formattedDistance.value,
      distanceUnit: formattedDistance.unit,
      isArrived,
      isNavigating: true,
      statusText: isArrived ? 'Arrived' : 'Navigating',
      street: resolvedStreet,
      turnIcon
    };

    if (isArrivalTransition) {
      this.haptics.vibrateArrival();
    } else if (!isArrived && (isFirstUpdate || hasPromptChanged)) {
      this.haptics.vibrateTurn();
    }

    this.lastTurn = canonicalTurn;
    this.lastStreet = resolvedStreet;

    return true;
  }

  reset(): void {
    this.state = { ...INITIAL_STATE };
    this.lastTurn = '';
    this.lastStreet = '';
  }
}
