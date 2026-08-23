var __classPrivateFieldSet = (this && this.__classPrivateFieldSet) || function (receiver, state, value, kind, f) {
    if (kind === "m") throw new TypeError("Private method is not writable");
    if (kind === "a" && !f) throw new TypeError("Private accessor was defined without a setter");
    if (typeof state === "function" ? receiver !== state || !f : !state.has(receiver)) throw new TypeError("Cannot write private member to an object whose class did not declare it");
    return (kind === "a" ? f.call(receiver, value) : f ? f.value = value : state.set(receiver, value)), value;
};
var __classPrivateFieldGet = (this && this.__classPrivateFieldGet) || function (receiver, state, kind, f) {
    if (kind === "a" && !f) throw new TypeError("Private accessor was defined without a getter");
    if (typeof state === "function" ? receiver !== state || !f : !state.has(receiver)) throw new TypeError("Cannot read private member from an object whose class did not declare it");
    return kind === "m" ? f : kind === "a" ? f.call(receiver) : f ? f.value : state.get(receiver);
};
var _NavigationSession_state;
import { getTurnIcon, isValidNavigationPayload } from "./types.js";
const INITIAL_STATE = Object.freeze({
    distance: '0',
    isNavigating: false,
    statusText: 'Disconnected',
    street: 'Ready',
    turnIcon: '↑'
});
export class NavigationSession {
    constructor(initialState) {
        _NavigationSession_state.set(this, void 0);
        __classPrivateFieldSet(this, _NavigationSession_state, {
            ...INITIAL_STATE,
            ...initialState
        }, "f");
    }
    getState() {
        return { ...__classPrivateFieldGet(this, _NavigationSession_state, "f") };
    }
    ingest(data) {
        if (!isValidNavigationPayload(data)) {
            return false;
        }
        const payload = data;
        const isArrived = payload.turn.trim().toLowerCase() === 'arrive';
        const rawDistance = payload.distance_m ?? payload.distanceMeters ?? 0;
        let rawStreet = '';
        if (typeof payload.street === 'string') {
            rawStreet = payload.street;
        }
        else if (typeof payload.streetName === 'string') {
            rawStreet = payload.streetName;
        }
        __classPrivateFieldSet(this, _NavigationSession_state, {
            distance: String(rawDistance),
            isNavigating: true,
            statusText: isArrived ? 'Arrived' : 'Navigating',
            street: rawStreet,
            turnIcon: getTurnIcon(payload.turn)
        }, "f");
        return true;
    }
    reset() {
        __classPrivateFieldSet(this, _NavigationSession_state, { ...INITIAL_STATE }, "f");
    }
}
_NavigationSession_state = new WeakMap();
