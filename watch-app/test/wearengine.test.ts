import assert from 'node:assert/strict';
import test from 'node:test';

import type { NavigationPayload } from '../src/types.ts';
import type { WearEngineDriver } from '../src/wearengine.ts';

import { decodeUtf8Bytes, WearEngineReceiver } from '../src/wearengine.ts';

class MockWearEngineDriver implements WearEngineDriver {
  public connectionHandler: ((connected: boolean) => void) | null = null;
  public connectionSubscribeCount = 0;
  public handler: ((data: unknown) => void) | null = null;
  public subscribeCount = 0;
  public unsubscribeCount = 0;

  emit(data: unknown): void {
    if (this.handler) {
      this.handler(data);
    }
  }

  emitConnectionState(connected: boolean): void {
    if (this.connectionHandler) {
      this.connectionHandler(connected);
    }
  }

  onConnectionStateChange(handler: (connected: boolean) => void): () => void {
    this.connectionHandler = handler;
    this.connectionSubscribeCount++;
    return () => {
      this.connectionHandler = null;
    };
  }

  subscribe(handler: (data: unknown) => void): () => void {
    this.handler = handler;
    this.subscribeCount++;
    return () => {
      this.unsubscribe();
    };
  }

  unsubscribe(): void {
    this.handler = null;
    this.unsubscribeCount++;
  }
}

test('WearEngineReceiver ingests valid JSON string payload', () => {
  const driver = new MockWearEngineDriver();
  const received: NavigationPayload[] = [];
  const receiver = new WearEngineReceiver(driver, payload => {
    received.push(payload);
  });
  receiver.start();

  driver.emit('{"turn":"left","distanceMeters":120,"street":"Nguyen Trai"}');

  assert.equal(received.length, 1);
  assert.equal(received[0]?.turn, 'left');
  assert.equal(received[0]?.distanceMeters, 120);
  assert.equal(received[0]?.street, 'Nguyen Trai');
});

test('WearEngineReceiver ingests object payload directly', () => {
  const driver = new MockWearEngineDriver();
  const received: NavigationPayload[] = [];
  const receiver = new WearEngineReceiver(driver, payload => {
    received.push(payload);
  });
  receiver.start();

  driver.emit({
    distanceMeters: 250,
    street: 'Tran Phu',
    turn: 'right'
  });

  assert.equal(received.length, 1);
  assert.equal(received[0]?.turn, 'right');
  assert.equal(received[0]?.distanceMeters, 250);
  assert.equal(received[0]?.street, 'Tran Phu');
});

test('WearEngineReceiver handles fallback distance_m and streetName fields', () => {
  const driver = new MockWearEngineDriver();
  const received: NavigationPayload[] = [];
  const receiver = new WearEngineReceiver(driver, payload => {
    received.push(payload);
  });
  receiver.start();

  driver.emit('{"turn":"slight_left","distance_m":80,"streetName":"Le Loi"}');

  assert.equal(received.length, 1);
  assert.equal(received[0]?.turn, 'slight_left');
  assert.equal(received[0]?.distance_m, 80);
  assert.equal(received[0]?.streetName, 'Le Loi');
});

test('WearEngineReceiver decodes Uint8Array binary UTF-8 payload', () => {
  const driver = new MockWearEngineDriver();
  const received: NavigationPayload[] = [];
  const receiver = new WearEngineReceiver(driver, payload => {
    received.push(payload);
  });
  receiver.start();

  const jsonStr = '{"turn":"sharp_right","distanceMeters":45,"street":"Pho Hue"}';
  const binaryPayload = new TextEncoder().encode(jsonStr);

  driver.emit(binaryPayload);

  assert.equal(received.length, 1);
  assert.equal(received[0]?.turn, 'sharp_right');
  assert.equal(received[0]?.distanceMeters, 45);
  assert.equal(received[0]?.street, 'Pho Hue');
});

test('WearEngineReceiver decodes multi-byte UTF-8 characters properly', () => {
  const driver = new MockWearEngineDriver();
  const received: NavigationPayload[] = [];
  const receiver = new WearEngineReceiver(driver, payload => {
    received.push(payload);
  });
  receiver.start();

  const jsonStr =
    '{"turn":"left","distanceMeters":150,"street":"Đường Nguyễn Trãi, Quận Thanh Xuân"}';
  const binaryPayload = new TextEncoder().encode(jsonStr);

  driver.emit(binaryPayload);

  assert.equal(received.length, 1);
  assert.equal(received[0]?.turn, 'left');
  assert.equal(received[0]?.distanceMeters, 150);
  assert.equal(received[0]?.street, 'Đường Nguyễn Trãi, Quận Thanh Xuân');
});

test('decodeUtf8Bytes fallback correctly decodes multi-byte sequences when TextDecoder is absent', () => {
  const vietnameseSample = 'Đường Trần Phú, Phường Mộ Lao, Hà Đông 🚗';
  const encodedBytes = new TextEncoder().encode(vietnameseSample);

  // Explicitly test the manual fallback path by calling decodeUtf8Bytes directly
  const originalTextDecoder = globalThis.TextDecoder;
  try {
    // @ts-expect-error - simulate missing TextDecoder in Lite Wearable runtime
    delete globalThis.TextDecoder;
    const decoded = decodeUtf8Bytes(encodedBytes);
    assert.equal(decoded, vietnameseSample);
  } finally {
    globalThis.TextDecoder = originalTextDecoder;
  }
});

test('WearEngineReceiver decodes wrapped data envelope objects', () => {
  const driver = new MockWearEngineDriver();
  const received: NavigationPayload[] = [];
  const receiver = new WearEngineReceiver(driver, payload => {
    received.push(payload);
  });
  receiver.start();

  driver.emit({
    data: '{"turn":"uturn","distanceMeters":50,"street":"Kim Ma"}'
  });

  assert.equal(received.length, 1);
  assert.equal(received[0]?.turn, 'uturn');
  assert.equal(received[0]?.distanceMeters, 50);
  assert.equal(received[0]?.street, 'Kim Ma');
});

test('WearEngineReceiver ignores invalid or corrupted payloads', () => {
  const driver = new MockWearEngineDriver();
  const received: NavigationPayload[] = [];
  const receiver = new WearEngineReceiver(driver, payload => {
    received.push(payload);
  });
  receiver.start();

  driver.emit('invalid-json');
  driver.emit('');
  driver.emit(null);
  driver.emit(12345);
  driver.emit({ distanceMeters: 100 }); // missing turn property
  driver.emit('{"turn":""}'); // empty turn property

  assert.equal(received.length, 0);
});

test('WearEngineReceiver supports multiple listeners and unregistration', () => {
  const driver = new MockWearEngineDriver();
  const receiver = new WearEngineReceiver(driver);
  receiver.start();

  const list1: NavigationPayload[] = [];
  const list2: NavigationPayload[] = [];

  const unbind1 = receiver.onMessage(p => list1.push(p));
  const unbind2 = receiver.onMessage(p => list2.push(p));

  driver.emit('{"turn":"straight","distanceMeters":1000,"street":"Vanh Dai 3"}');

  assert.equal(list1.length, 1);
  assert.equal(list2.length, 1);

  unbind1();

  driver.emit('{"turn":"arrive","distanceMeters":0,"street":"Destination"}');

  assert.equal(list1.length, 1);
  assert.equal(list2.length, 2);

  unbind2();

  driver.emit('{"turn":"straight","distanceMeters":200}');
  assert.equal(list2.length, 2);
});

test('WearEngineReceiver lifecycle start and stop manages driver subscription', () => {
  const driver = new MockWearEngineDriver();
  const receiver = new WearEngineReceiver(driver);

  assert.equal(driver.subscribeCount, 0);
  assert.equal(receiver.isListening(), false);

  receiver.start();
  assert.equal(driver.subscribeCount, 1);
  assert.equal(receiver.isListening(), true);

  // Calling start again when already listening is a no-op
  receiver.start();
  assert.equal(driver.subscribeCount, 1);

  receiver.stop();
  assert.equal(driver.unsubscribeCount, 1);
  assert.equal(receiver.isListening(), false);

  // Calling stop again is a no-op
  receiver.stop();
  assert.equal(driver.unsubscribeCount, 1);
});

test('WearEngineReceiver notifies connection listeners when receiving messages', () => {
  const driver = new MockWearEngineDriver();
  const connectionEvents: boolean[] = [];
  const receiver = new WearEngineReceiver(driver, {
    onConnectionChange: connected => {
      connectionEvents.push(connected);
    }
  });
  receiver.start();

  assert.equal(receiver.getIsConnected(), false);
  driver.emit('{"turn":"straight","distanceMeters":100}');

  assert.equal(receiver.getIsConnected(), true);
  assert.deepEqual(connectionEvents, [true]);

  // Subsequent message when already connected should not re-trigger duplicate event
  driver.emit('{"turn":"left","distanceMeters":80}');
  assert.deepEqual(connectionEvents, [true]);
});

test('WearEngineReceiver handles explicit disconnect and connect event payloads', () => {
  const driver = new MockWearEngineDriver();
  const connectionEvents: boolean[] = [];
  const receiver = new WearEngineReceiver(driver, {
    onConnectionChange: connected => {
      connectionEvents.push(connected);
    }
  });
  receiver.start();

  // Message connects
  driver.emit('{"turn":"right","distanceMeters":50}');
  assert.equal(receiver.getIsConnected(), true);

  // Disconnect event arrives
  driver.emit({ event: 'disconnect' });
  assert.equal(receiver.getIsConnected(), false);
  assert.deepEqual(connectionEvents, [true, false]);

  // Reconnect event arrives
  driver.emit({ event: 'connect' });
  assert.equal(receiver.getIsConnected(), true);
  assert.deepEqual(connectionEvents, [true, false, true]);
});

test('WearEngineReceiver integrates with driver onConnectionStateChange', () => {
  const driver = new MockWearEngineDriver();
  const connectionEvents: boolean[] = [];
  const receiver = new WearEngineReceiver(driver);
  receiver.onConnectionChange(connected => {
    connectionEvents.push(connected);
  });
  receiver.start();
  assert.equal(driver.connectionSubscribeCount, 1);

  driver.emitConnectionState(true);
  assert.equal(receiver.getIsConnected(), true);
  assert.deepEqual(connectionEvents, [true]);

  driver.emitConnectionState(false);
  assert.equal(receiver.getIsConnected(), false);
  assert.deepEqual(connectionEvents, [true, false]);

  receiver.stop();
});

test('WearEngineReceiver triggers inactivity watchdog when message stream stalls', async () => {
  const driver = new MockWearEngineDriver();
  const connectionEvents: boolean[] = [];
  let onDisconnected: () => void = () => {};
  const disconnected = new Promise<void>(resolve => {
    onDisconnected = resolve;
  });

  const receiver = new WearEngineReceiver(driver, {
    inactivityTimeoutMs: 10,
    onConnectionChange: connected => {
      connectionEvents.push(connected);
      if (!connected) {
        onDisconnected();
      }
    }
  });
  receiver.start();

  driver.emit('{"turn":"left","distanceMeters":200}');
  assert.equal(receiver.getIsConnected(), true);
  assert.deepEqual(connectionEvents, [true]);

  // Await the real disconnection signal emitted by the watchdog
  await disconnected;

  assert.equal(receiver.getIsConnected(), false);
  assert.deepEqual(connectionEvents, [true, false]);

  // Packet resumes
  driver.emit('{"turn":"left","distanceMeters":150}');
  assert.equal(receiver.getIsConnected(), true);
  assert.deepEqual(connectionEvents, [true, false, true]);

  receiver.stop();
});
