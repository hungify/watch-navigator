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
  hasConnectionWarning: false,
  isArrived: false,
  isConnected: false,
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
    if (canonicalTurn === 'stop') {
      this.reset();
      return true;
    }
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
      hasConnectionWarning: false,
      isArrived,
      isConnected: true,
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
  handleConnect(): void {
    let status = 'Connected';
    if (this.state.isNavigating) {
      status = this.state.isArrived ? 'Arrived' : 'Navigating';
    }
    this.state = {
      ...this.state,
      hasConnectionWarning: false,
      isConnected: true,
      statusText: status
    };
  }

  handleDisconnect(): void {
    this.state = {
      ...this.state,
      hasConnectionWarning: this.state.isNavigating,
      isConnected: false,
      statusText: 'Disconnected'
    };
  }

  handleReconnect(): void {
    this.handleConnect();
  }

  isOfflineCached(): boolean {
    return this.state.isNavigating && !this.state.isConnected;
  }

  reset(): void {
    this.state = { ...INITIAL_STATE };
    this.lastTurn = '';
    this.lastStreet = '';
  }
}
