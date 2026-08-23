import type { WatchPageIndexPage, WatchPageState } from '../../types.ts';
import type { WearEngineDriver } from '../../wearengine.ts';

import { NavigationSession } from '../../session.ts';
import { WearEngineReceiver } from '../../wearengine.ts';

let session: NavigationSession | null = null;
let receiver: WearEngineReceiver | null = null;

const page = {
  data: {
    distance: '0',
    distanceUnit: 'm',
    isArrived: false,
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
    receiver = new WearEngineReceiver(driver, payload => {
      this.updateNavigation(payload);
    });
    receiver.start();
    console.info('Wear Engine receiver initialized');
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
