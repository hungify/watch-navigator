import { NavigationSession } from "../../session.js";
let session = null;
const page = {
    data: {
        distance: '0',
        distanceUnit: 'm',
        isArrived: false,
        isNavigating: false,
        statusText: 'Disconnected',
        street: 'Ready',
        turnIcon: '↑'
    },
    destroyWearEngineReceiver() {
        console.info('Wear Engine receiver destroyed');
    },
    initWearEngineReceiver() {
        console.info('Wear Engine receiver initialized');
    },
    onDestroy() {
        console.info('Watch Navigator Page onDestroy');
        if (session) {
            session.reset();
            session = null;
        }
        this.destroyWearEngineReceiver();
    },
    onHide() {
        console.info('Watch Navigator Page onHide');
    },
    onInit() {
        console.info('Watch Navigator Page onInit');
        session = new NavigationSession();
        this.syncState(session.getState());
        this.initWearEngineReceiver();
    },
    onShow() {
        console.info('Watch Navigator Page onShow');
    },
    syncState(state) {
        this.isNavigating = state.isNavigating;
        this.isArrived = state.isArrived;
        this.statusText = state.statusText;
        this.turnIcon = state.turnIcon;
        this.distance = state.distance;
        this.distanceUnit = state.distanceUnit;
        this.street = state.street;
    },
    updateNavigation(data) {
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
