import {
  getTurnIcon,
  isValidNavigationPayload,
  WatchPageIndexPage,
  WatchPageState
} from '../../types';

const page = {
  data: {
    isNavigating: false,
    statusText: 'Disconnected',
    turnIcon: '↑',
    distance: '0',
    street: 'Ready'
  } as WatchPageState,

  onInit(this: WatchPageIndexPage): void {
    console.info('Watch Navigator Page onInit');
    this.initWearEngineReceiver();
  },

  onShow(this: WatchPageIndexPage): void {
    console.info('Watch Navigator Page onShow');
  },

  onHide(this: WatchPageIndexPage): void {
    console.info('Watch Navigator Page onHide');
  },

  onDestroy(this: WatchPageIndexPage): void {
    console.info('Watch Navigator Page onDestroy');
    this.destroyWearEngineReceiver();
  },

  initWearEngineReceiver(this: WatchPageIndexPage): void {
    console.info('Wear Engine receiver initialized');
  },

  destroyWearEngineReceiver(this: WatchPageIndexPage): void {
    console.info('Wear Engine receiver destroyed');
  },

  updateNavigation(this: WatchPageIndexPage, data: unknown): void {
    if (!isValidNavigationPayload(data)) {
      return;
    }

    const payload = data;
    this.isNavigating = true;
    this.statusText = payload.turn.toLowerCase() === 'arrive' ? 'Arrived' : 'Navigating';
    this.distance = String(payload.distance_m ?? payload.distanceMeters ?? 0);
    this.street = typeof payload.street === 'string' ? payload.street : '';
    this.turnIcon = getTurnIcon(payload.turn);
  }
};

export default page;
