import { getTurnIcon, isValidNavigationPayload } from '../../types';
const page = {
    data: {
        isNavigating: false,
        statusText: 'Disconnected',
        turnIcon: '↑',
        distance: '0',
        street: 'Ready'
    },
    onInit() {
        console.info('Watch Navigator Page onInit');
        this.initWearEngineReceiver();
    },
    onShow() {
        console.info('Watch Navigator Page onShow');
    },
    onHide() {
        console.info('Watch Navigator Page onHide');
    },
    onDestroy() {
        console.info('Watch Navigator Page onDestroy');
        this.destroyWearEngineReceiver();
    },
    initWearEngineReceiver() {
        console.info('Wear Engine receiver initialized');
    },
    destroyWearEngineReceiver() {
        console.info('Wear Engine receiver destroyed');
    },
    updateNavigation(data) {
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
