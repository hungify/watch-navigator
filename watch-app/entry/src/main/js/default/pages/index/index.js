import { NavigationSession } from "../../session.js";
import { DEFAULT_INACTIVITY_TIMEOUT_MS, WearEngineReceiver } from "../../wearengine.js";
let session = null;
let receiver = null;
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
    },
    destroyWearEngineReceiver() {
        if (receiver) {
            receiver.stop();
            receiver = null;
        }
        console.info('Wear Engine receiver destroyed');
    },
    initWearEngineReceiver(driver) {
        if (receiver) {
            receiver.stop();
        }
        receiver = new WearEngineReceiver(driver, {
            inactivityTimeoutMs: DEFAULT_INACTIVITY_TIMEOUT_MS,
            onConnectionChange: (connected) => {
                this.onConnectionChange(connected);
            },
            onMessage: (payload) => {
                this.updateNavigation(payload);
            }
        });
        receiver.start();
        console.info('Wear Engine receiver initialized');
    },
    onConnectionChange(connected) {
        if (!session) {
            session = new NavigationSession();
        }
        if (connected) {
            session.handleConnect();
        }
        else {
            session.handleDisconnect();
        }
        this.syncState(session.getState());
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
        this.isConnected = state.isConnected;
        this.hasConnectionWarning = state.hasConnectionWarning;
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
