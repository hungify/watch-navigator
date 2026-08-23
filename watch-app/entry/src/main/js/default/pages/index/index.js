import { NavigationSession } from "../../session.js";
const session = new NavigationSession();
const page = {
    data: session.getState(),
    session,
    onDestroy() {
        console.info('Watch Navigator Page onDestroy');
        this.session.reset();
        this.destroyWearEngineReceiver();
    },
    destroyWearEngineReceiver() {
        console.info('Wear Engine receiver destroyed');
    },
    initWearEngineReceiver() {
        console.info('Wear Engine receiver initialized');
    },
    onHide() {
        console.info('Watch Navigator Page onHide');
    },
    onInit() {
        console.info('Watch Navigator Page onInit');
        this.initWearEngineReceiver();
    },
    onShow() {
        console.info('Watch Navigator Page onShow');
    },
    updateNavigation(data) {
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
