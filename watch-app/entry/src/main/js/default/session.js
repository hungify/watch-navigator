import { HapticsService } from "./haptics.js";
import { formatDistance, getCanonicalTurn, getTurnIcon, isValidNavigationPayload } from "./types.js";
const INITIAL_STATE = Object.freeze({
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
    constructor(hapticsOrInitialState, initialState) {
        this.lastStreet = '';
        this.lastTurn = '';
        if (hapticsOrInitialState instanceof HapticsService) {
            this.haptics = hapticsOrInitialState;
            this.state = { ...INITIAL_STATE, ...initialState };
        }
        else if (hapticsOrInitialState && typeof hapticsOrInitialState === 'object') {
            this.haptics = new HapticsService();
            this.state = { ...INITIAL_STATE, ...hapticsOrInitialState };
        }
        else {
            this.haptics = new HapticsService();
            this.state = { ...INITIAL_STATE, ...initialState };
        }
    }
    getState() {
        return { ...this.state };
    }
    ingest(data) {
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
        }
        else if (!isArrived && (isFirstUpdate || hasPromptChanged)) {
            this.haptics.vibrateTurn();
        }
        this.lastTurn = canonicalTurn;
        this.lastStreet = resolvedStreet;
        return true;
    }
    handleConnect() {
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
    handleDisconnect() {
        this.state = {
            ...this.state,
            hasConnectionWarning: this.state.isNavigating,
            isConnected: false,
            statusText: 'Disconnected'
        };
    }
    handleReconnect() {
        this.handleConnect();
    }
    isOfflineCached() {
        return this.state.isNavigating && !this.state.isConnected;
    }
    reset() {
        this.state = { ...INITIAL_STATE };
        this.lastTurn = '';
        this.lastStreet = '';
    }
}
