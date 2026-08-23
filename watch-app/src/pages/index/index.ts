import type { WatchPageIndexPage } from '../../types.ts';

import { NavigationSession } from '../../session.ts';

const session = new NavigationSession();

const page = {
  data: session.getState(),
  session,

  onDestroy(this: WatchPageIndexPage): void {
    console.info('Watch Navigator Page onDestroy');
    this.session.reset();
    this.destroyWearEngineReceiver();
  },

  destroyWearEngineReceiver(this: WatchPageIndexPage): void {
    console.info('Wear Engine receiver destroyed');
  },

  initWearEngineReceiver(this: WatchPageIndexPage): void {
    console.info('Wear Engine receiver initialized');
  },

  onHide(this: WatchPageIndexPage): void {
    console.info('Watch Navigator Page onHide');
  },

  onInit(this: WatchPageIndexPage): void {
    console.info('Watch Navigator Page onInit');
    this.initWearEngineReceiver();
  },

  onShow(this: WatchPageIndexPage): void {
    console.info('Watch Navigator Page onShow');
  },

  updateNavigation(this: WatchPageIndexPage, data: unknown): void {
    if (!this.session.ingest(data)) {
      return;
    }

    const state = this.session.getState();
    this.distance = state.distance;
    this.isNavigating = state.isNavigating;
    this.statusText = state.statusText;
    this.street = state.street;
    this.turnIcon = state.turnIcon;
  }
};

export default page;
