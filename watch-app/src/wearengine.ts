import type { NavigationPayload } from './types.ts';

import { isValidNavigationPayload } from './types.ts';

export type RawMessageHandler = (data: unknown) => void;
export type WearEngineConnectionCallback = (connected: boolean) => void;
export type WearEngineMessageCallback = (payload: NavigationPayload) => void;
export const DEFAULT_INACTIVITY_TIMEOUT_MS = 15000;

export type TimerHandle = number | NodeJS.Timeout;

export interface WearEngineReceiverOptions {
  inactivityTimeoutMs?: number;
  onConnectionChange?: WearEngineConnectionCallback;
  onMessage?: WearEngineMessageCallback;
}
export interface WearEngineDriver {
  onConnectionStateChange?(handler: WearEngineConnectionCallback): () => void;
  send?(data: unknown): Promise<void> | void;
  subscribe(handler: RawMessageHandler): (() => void) | void;
  unsubscribe?(handler?: RawMessageHandler): void;
}

interface SystemP2pClient {
  on?: (event: string, callback: RawMessageHandler) => void;
  off?: (event: string, callback?: RawMessageHandler) => void;
  registerReceiver?: (options: { onSuccess: RawMessageHandler }) => void;
  subscribe?: (options: { success: RawMessageHandler }) => void;
  unregisterReceiver?: () => void;
  unsubscribe?: () => void;
}

function resolveSystemWearEngineDriver(): WearEngineDriver | null {
  try {
    const globalScope = globalThis as unknown as {
      p2p?: SystemP2pClient;
      p2pClient?: SystemP2pClient;
      require?: (module: string) => SystemP2pClient;
      wearengine?: SystemP2pClient;
    };

    let p2pModule: null | SystemP2pClient = null;

    if (globalScope.p2p && typeof globalScope.p2p === 'object') {
      p2pModule = globalScope.p2p;
    } else if (globalScope.wearengine && typeof globalScope.wearengine === 'object') {
      p2pModule = globalScope.wearengine;
    } else if (globalScope.p2pClient && typeof globalScope.p2pClient === 'object') {
      p2pModule = globalScope.p2pClient;
    } else if (typeof globalScope.require === 'function') {
      try {
        p2pModule = globalScope.require('@system.p2p');
      } catch {
        // Module '@system.p2p' not found or unsupported
      }
    }

    if (p2pModule) {
      const client = p2pModule;
      return {
        subscribe(handler: RawMessageHandler): () => void {
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

          return () => {};
        }
      };
    }
  } catch {
    // Wear Engine driver resolution failed or running outside Lite Wearable runtime
  }

  return null;
}

function decodeSequence(bytes: Uint8Array, start: number): [string, number] {
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

function decodeFallbackUtf8(bytes: Uint8Array): string {
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

export function decodeUtf8Bytes(bytes: Uint8Array): string {
  if (typeof TextDecoder !== 'undefined') {
    return new TextDecoder('utf-8').decode(bytes);
  }
  return decodeFallbackUtf8(bytes);
}

function decodeRawData(raw: unknown): unknown {
  if (typeof raw === 'string') {
    const trimmed = raw.trim();
    if (trimmed.startsWith('{') && trimmed.endsWith('}')) {
      try {
        return JSON.parse(trimmed) as unknown;
      } catch {
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
    const obj = raw as Record<string, unknown>;
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
  private cleanupConnectionSubscription: (() => void) | null = null;
  private cleanupSubscription: (() => void) | null = null;
  private connectionListeners: Set<WearEngineConnectionCallback> = new Set();
  private driver: WearEngineDriver | null;
  private inactivityTimeoutMs: number = 0;
  private isConnected: boolean = false;
  private listeners: Set<WearEngineMessageCallback> = new Set();
  private watchdogTimer: null | TimerHandle = null;
  constructor(
    driver?: null | WearEngineDriver,
    onMessageOrCallbackOrOptions?: WearEngineMessageCallback | WearEngineReceiverOptions,
    options?: WearEngineReceiverOptions
  ) {
    if (driver !== undefined) {
      this.driver = driver;
    } else {
      this.driver = resolveSystemWearEngineDriver();
    }

    let opts: undefined | WearEngineReceiverOptions = options;
    if (typeof onMessageOrCallbackOrOptions === 'function') {
      this.listeners.add(onMessageOrCallbackOrOptions);
    } else if (onMessageOrCallbackOrOptions && typeof onMessageOrCallbackOrOptions === 'object') {
      opts = onMessageOrCallbackOrOptions;
    }

    if (opts) {
      if (opts.inactivityTimeoutMs && opts.inactivityTimeoutMs > 0) {
        this.inactivityTimeoutMs = opts.inactivityTimeoutMs;
      }
      if (opts.onMessage) {
        this.listeners.add(opts.onMessage);
      }
      if (opts.onConnectionChange) {
        this.connectionListeners.add(opts.onConnectionChange);
      }
    }
  }

  getIsConnected(): boolean {
    return this.isConnected;
  }

  handleRawMessage(raw: unknown): boolean {
    const candidate = decodeRawData(raw);

    // Check for explicit disconnect or connection event payloads
    if (candidate && typeof candidate === 'object') {
      const obj = candidate as Record<string, unknown>;
      if (obj.event === 'disconnect' || obj.type === 'disconnect' || obj.connected === false) {
        this.notifyConnectionChange(false);
        return true;
      }
      if (obj.event === 'connect' || obj.type === 'connect' || obj.connected === true) {
        this.notifyConnectionChange(true);
        if (!isValidNavigationPayload(candidate)) {
          return true;
        }
      }
    }

    const payload = this.parsePayload(candidate);
    if (!payload) {
      return false;
    }

    this.notifyConnectionChange(true);
    for (const listener of this.listeners) {
      try {
        listener(payload);
      } catch (err) {
        console.warn('Error in WearEngine listener:', err);
      }
    }
    return true;
  }

  isListening(): boolean {
    return this.cleanupSubscription !== null;
  }

  onConnectionChange(callback: WearEngineConnectionCallback): () => void {
    this.connectionListeners.add(callback);
    return () => {
      this.connectionListeners.delete(callback);
    };
  }

  onMessage(callback: WearEngineMessageCallback): () => void {
    this.listeners.add(callback);
    return () => {
      this.listeners.delete(callback);
    };
  }

  setConnected(connected: boolean): void {
    this.notifyConnectionChange(connected);
  }

  start(): void {
    if (this.cleanupSubscription || !this.driver) {
      return;
    }

    const boundHandler = (data: unknown) => {
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

    if (typeof this.driver.onConnectionStateChange === 'function') {
      const connCleanup = this.driver.onConnectionStateChange((connected: boolean) => {
        this.notifyConnectionChange(connected);
      });
      if (typeof connCleanup === 'function') {
        this.cleanupConnectionSubscription = connCleanup;
      }
    }
  }

  stop(): void {
    this.clearWatchdog();
    if (this.cleanupSubscription) {
      try {
        this.cleanupSubscription();
      } catch (err) {
        console.warn('Error unsubscribing WearEngine driver:', err);
      }
      this.cleanupSubscription = null;
    }
    if (this.cleanupConnectionSubscription) {
      try {
        this.cleanupConnectionSubscription();
      } catch (err) {
        console.warn('Error unsubscribing WearEngine connection driver:', err);
      }
      this.cleanupConnectionSubscription = null;
    }
  }

  private clearWatchdog(): void {
    if (this.watchdogTimer !== null) {
      clearTimeout(this.watchdogTimer);
      this.watchdogTimer = null;
    }
  }

  private notifyConnectionChange(connected: boolean): void {
    if (connected) {
      this.resetWatchdog();
    } else {
      this.clearWatchdog();
    }

    if (this.isConnected === connected) {
      return;
    }
    this.isConnected = connected;
    for (const listener of this.connectionListeners) {
      try {
        listener(connected);
      } catch (err) {
        console.warn('Error in WearEngine connection listener:', err);
      }
    }
  }

  private parsePayload(raw: unknown): NavigationPayload | null {
    return isValidNavigationPayload(raw) ? raw : null;
  }

  private resetWatchdog(): void {
    this.clearWatchdog();
    if (this.inactivityTimeoutMs > 0) {
      this.watchdogTimer = setTimeout(() => {
        this.notifyConnectionChange(false);
      }, this.inactivityTimeoutMs);
    }
  }
}
