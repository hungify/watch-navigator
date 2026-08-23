import { isValidNavigationPayload } from "./types.js";
function resolveSystemWearEngineDriver() {
    try {
        const globalScope = globalThis;
        let p2pModule = null;
        if (globalScope.p2p && typeof globalScope.p2p === 'object') {
            p2pModule = globalScope.p2p;
        }
        else if (globalScope.wearengine && typeof globalScope.wearengine === 'object') {
            p2pModule = globalScope.wearengine;
        }
        else if (globalScope.p2pClient && typeof globalScope.p2pClient === 'object') {
            p2pModule = globalScope.p2pClient;
        }
        else if (typeof globalScope.require === 'function') {
            try {
                p2pModule = globalScope.require('@system.p2p');
            }
            catch {
                // Module '@system.p2p' not found or unsupported
            }
        }
        if (p2pModule) {
            const client = p2pModule;
            return {
                subscribe(handler) {
                    if (typeof client.subscribe === 'function') {
                        client.subscribe({ success: handler });
                        return () => {
                            if (typeof client.unsubscribe === 'function') {
                                client.unsubscribe();
                            }
                        };
                    }
                    if (typeof client.registerReceiver === 'function') {
                        client.registerReceiver({ onSuccess: handler });
                        return () => {
                            if (typeof client.unregisterReceiver === 'function') {
                                client.unregisterReceiver();
                            }
                        };
                    }
                    if (typeof client.on === 'function') {
                        client.on('message', handler);
                        return () => {
                            if (typeof client.off === 'function') {
                                client.off('message', handler);
                            }
                        };
                    }
                    return () => { };
                }
            };
        }
    }
    catch {
        // Wear Engine driver resolution failed or running outside Lite Wearable runtime
    }
    return null;
}
function decodeSequence(bytes, start) {
    const c1 = bytes[start];
    if (c1 > 0xbf && c1 < 0xe0) {
        const c2 = bytes[start + 1] ?? 0;
        return [String.fromCharCode(((c1 & 0x1f) << 6) | (c2 & 0x3f)), 1];
    }
    if (c1 > 0xdf && c1 < 0xf0) {
        const c2 = bytes[start + 1] ?? 0;
        const c3 = bytes[start + 2] ?? 0;
        return [String.fromCharCode(((c1 & 0x0f) << 12) | ((c2 & 0x3f) << 6) | (c3 & 0x3f)), 2];
    }
    const c2 = bytes[start + 1] ?? 0;
    const c3 = bytes[start + 2] ?? 0;
    const c4 = bytes[start + 3] ?? 0;
    let cp = ((c1 & 0x07) << 18) | ((c2 & 0x3f) << 12) | ((c3 & 0x3f) << 6) | (c4 & 0x3f);
    cp -= 0x10000;
    return [String.fromCharCode((cp >> 10) | 0xd800, (cp & 0x3ff) | 0xdc00), 3];
}
function decodeFallbackUtf8(bytes) {
    let out = '';
    let i = 0;
    while (i < bytes.length) {
        const c1 = bytes[i++];
        if (c1 < 0x80) {
            out += String.fromCharCode(c1);
            continue;
        }
        const [char, advance] = decodeSequence(bytes, i - 1);
        out += char;
        i += advance;
    }
    return out;
}
export function decodeUtf8Bytes(bytes) {
    if (typeof TextDecoder !== 'undefined') {
        return new TextDecoder('utf-8').decode(bytes);
    }
    return decodeFallbackUtf8(bytes);
}
function decodeRawData(raw) {
    if (typeof raw === 'string') {
        const trimmed = raw.trim();
        if (trimmed.startsWith('{') && trimmed.endsWith('}')) {
            try {
                return JSON.parse(trimmed);
            }
            catch {
                return null;
            }
        }
        return null;
    }
    if (raw instanceof Uint8Array) {
        return decodeRawData(decodeUtf8Bytes(raw));
    }
    if (raw instanceof ArrayBuffer) {
        return decodeRawData(decodeUtf8Bytes(new Uint8Array(raw)));
    }
    if (raw && typeof raw === 'object') {
        const obj = raw;
        if (obj.data !== undefined) {
            return decodeRawData(obj.data);
        }
        if (obj.message !== undefined) {
            return decodeRawData(obj.message);
        }
        return obj;
    }
    return null;
}
export class WearEngineReceiver {
    constructor(driver, onMessageCallback) {
        this.cleanupSubscription = null;
        this.listeners = new Set();
        if (driver !== undefined) {
            this.driver = driver;
        }
        else {
            this.driver = resolveSystemWearEngineDriver();
        }
        if (onMessageCallback) {
            this.listeners.add(onMessageCallback);
        }
    }
    handleRawMessage(raw) {
        const payload = this.parsePayload(raw);
        if (!payload) {
            return false;
        }
        for (const listener of this.listeners) {
            try {
                listener(payload);
            }
            catch (err) {
                console.warn('Error in WearEngine listener:', err);
            }
        }
        return true;
    }
    isListening() {
        return this.cleanupSubscription !== null;
    }
    onMessage(callback) {
        this.listeners.add(callback);
        return () => {
            this.listeners.delete(callback);
        };
    }
    start() {
        if (this.cleanupSubscription || !this.driver) {
            return;
        }
        const boundHandler = (data) => {
            this.handleRawMessage(data);
        };
        const cleanup = this.driver.subscribe(boundHandler);
        this.cleanupSubscription =
            typeof cleanup === 'function'
                ? cleanup
                : () => {
                    if (this.driver && typeof this.driver.unsubscribe === 'function') {
                        this.driver.unsubscribe(boundHandler);
                    }
                };
    }
    stop() {
        if (this.cleanupSubscription) {
            try {
                this.cleanupSubscription();
            }
            catch (err) {
                console.warn('Error unsubscribing WearEngine driver:', err);
            }
            this.cleanupSubscription = null;
        }
    }
    parsePayload(raw) {
        const candidate = decodeRawData(raw);
        return isValidNavigationPayload(candidate) ? candidate : null;
    }
}
