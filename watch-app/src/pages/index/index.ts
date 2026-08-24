import type { NavigationPayload, WatchPageIndexPage, WatchPageState } from '../../types.ts';
import type { WearEngineDriver } from '../../wearengine.ts';

import { NavigationSession } from '../../session.ts';
import { DEFAULT_INACTIVITY_TIMEOUT_MS, WearEngineReceiver } from '../../wearengine.ts';

let session: NavigationSession | null = null;
let receiver: WearEngineReceiver | null = null;

const page = {
  data: {
    distance: '0',
    distanceUnit: 'm',
    hasConnectionWarning: false,
    isArrived: false,
    isConnected: false,
    isNavigating: false,
    statusText: 'Disconnected',
    street: 'Ready',
    turnIcon: '/common/turn_straight.png'
  } as WatchPageState,

  destroyWearEngineReceiver(this: WatchPageIndexPage): void {
    if (receiver) {
      receiver.stop();
      receiver = null;
    }
    console.info('Wear Engine receiver destroyed');
  },

  initWearEngineReceiver(this: WatchPageIndexPage, driver?: null | WearEngineDriver): void {
    if (receiver) {
      receiver.stop();
    }
    receiver = new WearEngineReceiver(driver, {
      inactivityTimeoutMs: DEFAULT_INACTIVITY_TIMEOUT_MS,
      onConnectionChange: (connected: boolean) => {
        this.onConnectionChange(connected);
      },
      onMessage: (payload: NavigationPayload) => {
        this.updateNavigation(payload);
      }
    });
    receiver.start();
    console.info('Wear Engine receiver initialized');
  },
  onConnectionChange(this: WatchPageIndexPage, connected: boolean): void {
    if (!session) {
      session = new NavigationSession();
    }
    if (connected) {
      session.handleConnect();
    } else {
      session.handleDisconnect();
    }
    this.syncState(session.getState());
  },

  onDestroy(this: WatchPageIndexPage): void {
    console.info('Watch Navigator Page onDestroy');
    if (session) {
      session.reset();
      session = null;
    }
    this.destroyWearEngineReceiver();
  },

  onHide(this: WatchPageIndexPage): void {
    console.info('Watch Navigator Page onHide');
  },

  onInit(this: WatchPageIndexPage): void {
    console.info('Watch Navigator Page onInit');
    session = new NavigationSession();
    this.syncState(session.getState());
    this.initWearEngineReceiver();
  },

  onShow(this: WatchPageIndexPage): void {
    console.info('Watch Navigator Page onShow');
  },

  syncState(this: WatchPageIndexPage, state: WatchPageState): void {
    this.isNavigating = state.isNavigating;
    this.isArrived = state.isArrived;
    this.isConnected = state.isConnected;
    this.hasConnectionWarning = state.hasConnectionWarning;
    this.statusText = state.statusText;
    this.turnIcon = state.turnIcon;
    this.distance = state.distance;
    this.distanceUnit = state.distanceUnit;
    this.street = state.street;
  },

  updateNavigation(this: WatchPageIndexPage, data: unknown): void {
    if (!session) {
      session = new NavigationSession();
    }
    const accepted = session.ingest(data);
    if (accepted) {
      this.syncState(session.getState());
    }
  }
};

export default page;
