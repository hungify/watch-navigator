import assert from 'node:assert/strict';
import test from 'node:test';

import type { VibratorDriver } from '../src/haptics.ts';

import { HapticsService } from '../src/haptics.ts';
import { NavigationSession } from '../src/session.ts';

class MockVibratorDriver implements VibratorDriver {
  public calls: Array<{ mode: 'short' | 'long' }> = [];

  vibrate(options: { mode: 'short' | 'long' }): void {
    this.calls.push(options);
  }
}

test('NavigationSession initializes with default disconnected state', () => {
  const session = new NavigationSession();
  const state = session.getState();

  assert.deepEqual(state, {
    distance: '0',
    distanceUnit: 'm',
    isArrived: false,
    isNavigating: false,
    statusText: 'Disconnected',
    street: 'Ready',
    turnIcon: '/common/turn_straight.png'
  });
});

test('NavigationSession getState() returns an immutable copy', () => {
  const session = new NavigationSession();
  const state1 = session.getState();
  state1.statusText = 'Mutated';
  state1.isNavigating = true;

  const state2 = session.getState();
  assert.equal(state2.statusText, 'Disconnected');
  assert.equal(state2.isNavigating, false);
});

test('NavigationSession ingests valid navigation payload and triggers turn haptic', () => {
  const driver = new MockVibratorDriver();
  const haptics = new HapticsService(driver);
  const session = new NavigationSession(haptics);

  const success = session.ingest({
    distance_m: 150,
    street: 'Nguyen Trai',
    turn: 'left'
  });

  assert.equal(success, true);
  assert.deepEqual(session.getState(), {
    distance: '150',
    distanceUnit: 'm',
    isArrived: false,
    isNavigating: true,
    statusText: 'Navigating',
    street: 'Nguyen Trai',
    turnIcon: '/common/turn_left.png'
  });
  assert.equal(driver.calls.length, 1);
  assert.equal(driver.calls[0].mode, 'short');
});

test('NavigationSession formats kilometer distances properly', () => {
  const session = new NavigationSession();

  const success = session.ingest({
    distanceMeters: 2400,
    streetName: 'Lang Ha',
    turn: 'straight'
  });

  assert.equal(success, true);
  const state = session.getState();
  assert.equal(state.distance, '2.4');
  assert.equal(state.distanceUnit, 'km');
  assert.equal(state.street, 'Lang Ha');
});

test('NavigationSession normalizes street name from street or streetName', () => {
  const session = new NavigationSession();

  session.ingest({ street: 'Nguyen Trai', turn: 'left' });
  assert.equal(session.getState().street, 'Nguyen Trai');

  session.ingest({ streetName: 'Khuat Duy Tien', turn: 'right' });
  assert.equal(session.getState().street, 'Khuat Duy Tien');

  session.ingest({ turn: 'straight' });
  assert.equal(session.getState().street, '');
});

test('NavigationSession transitions to Arrived state and triggers long arrival vibration', () => {
  const driver = new MockVibratorDriver();
  const haptics = new HapticsService(driver);
  const session = new NavigationSession(haptics);

  // First step
  session.ingest({
    distance_m: 50,
    street: 'Tran Duy Hung',
    turn: 'slight-right'
  });
  assert.equal(driver.calls.length, 1);
  assert.equal(driver.calls[0].mode, 'short');

  // Arrival step
  const success = session.ingest({
    distance_m: 0,
    street: 'Vincom Center',
    turn: 'arrive'
  });

  assert.equal(success, true);
  assert.deepEqual(session.getState(), {
    distance: '0',
    distanceUnit: 'm',
    isArrived: true,
    isNavigating: true,
    statusText: 'Arrived',
    street: 'Vincom Center',
    turnIcon: '/common/turn_arrive.png'
  });

  assert.equal(driver.calls.length, 2);
  assert.equal(driver.calls[1].mode, 'long');
});

test('NavigationSession rejects invalid or malformed payloads without altering state', () => {
  const session = new NavigationSession();
  session.ingest({
    distance_m: 200,
    street: 'Khai Sanh',
    turn: 'turn-right'
  });
  const snapshotBefore = session.getState();

  assert.equal(session.ingest(null), false);
  assert.equal(session.ingest(undefined), false);
  assert.equal(session.ingest('invalid'), false);
  assert.equal(session.ingest({ turn: '' }), false);
  assert.equal(session.ingest({ turn: '   ' }), false);
  assert.equal(session.ingest({ turn: 123 }), false);
  assert.equal(session.ingest({ turn: 'left', distance_m: {} }), false);
  assert.equal(session.ingest({ turn: 'left', distance_m: NaN }), false);
  assert.equal(session.ingest({ turn: 'left', distance_m: Infinity }), false);
  assert.equal(session.ingest({ turn: 'left', distanceMeters: '100' }), false);
  assert.equal(session.ingest({ turn: 'left', street: 12345 }), false);
  assert.equal(session.ingest({ turn: 'left', streetName: [] }), false);

  assert.deepEqual(session.getState(), snapshotBefore);
});

test('NavigationSession reset restores initial state cleanly', () => {
  const session = new NavigationSession();
  session.ingest({
    distance_m: 0,
    street: 'Destination Point',
    turn: 'arrive'
  });

  session.reset();

  assert.deepEqual(session.getState(), {
    distance: '0',
    distanceUnit: 'm',
    isArrived: false,
    isNavigating: false,
    statusText: 'Disconnected',
    street: 'Ready',
    turnIcon: '/common/turn_straight.png'
  });
});

test('NavigationSession resets to initial state on terminal stop payload without triggering haptics', () => {
  const driver = new MockVibratorDriver();
  const haptics = new HapticsService(driver);
  const session = new NavigationSession(haptics);

  session.ingest({ distanceMeters: 150, street: 'Nguyen Trai', turn: 'left' });
  assert.equal(session.getState().isNavigating, true);
  assert.equal(driver.calls.length, 1);

  const accepted = session.ingest({ distanceMeters: 0, street: '', turn: 'stop' });
  assert.equal(accepted, true);
  assert.deepEqual(session.getState(), {
    distance: '0',
    distanceUnit: 'm',
    isArrived: false,
    isNavigating: false,
    statusText: 'Disconnected',
    street: 'Ready',
    turnIcon: '/common/turn_straight.png'
  });
  // No additional vibration triggered on stop
  assert.equal(driver.calls.length, 1);
});

test('NavigationSession handles arrival case-insensitively', () => {
  const session = new NavigationSession();

  session.ingest({ distance_m: 0, street: 'Home', turn: 'ARRIVE' });
  assert.equal(session.getState().statusText, 'Arrived');
  assert.equal(session.getState().turnIcon, '/common/turn_arrive.png');

  session.ingest({ distance_m: 0, street: 'Office', turn: 'Arrive' });
  assert.equal(session.getState().statusText, 'Arrived');
  assert.equal(session.getState().turnIcon, '/common/turn_arrive.png');
});

test('NavigationSession triggers arrival vibration only once across consecutive arrival updates', () => {
  const driver = new MockVibratorDriver();
  const haptics = new HapticsService(driver);
  const session = new NavigationSession(haptics);

  session.ingest({ street: 'Hanoi Opera House', turn: 'arrive' });
  session.ingest({ street: 'Hanoi Opera House', turn: 'arrive' });
  session.ingest({ street: 'Hanoi Opera House', turn: 'destination' });

  assert.equal(driver.calls.length, 1);
  assert.equal(driver.calls[0].mode, 'long');
});

test('NavigationSession does not re-trigger turn vibration for synonymous turn aliases on same street', () => {
  const driver = new MockVibratorDriver();
  const haptics = new HapticsService(driver);
  const session = new NavigationSession(haptics);

  session.ingest({ distance_m: 100, street: 'Hai Ba Trung', turn: 'left' });
  assert.equal(driver.calls.length, 1);

  // Equivalent alias 'turn_left' with same street should not re-trigger vibration
  session.ingest({ distance_m: 80, street: 'Hai Ba Trung', turn: 'turn_left' });
  assert.equal(driver.calls.length, 1);

  // Changing maneuver to 'right' SHOULD trigger new vibration
  session.ingest({ distance_m: 50, street: 'Ba Trieu', turn: 'turn_right' });
  assert.equal(driver.calls.length, 2);
  assert.equal(driver.calls[1].mode, 'short');
});

test('NavigationSession triggers second turn vibration for distinct maneuvers sharing icon and street', () => {
  const driver = new MockVibratorDriver();
  const haptics = new HapticsService(driver);
  const session = new NavigationSession(haptics);

  session.ingest({ distance_m: 100, street: 'Roundabout Square', turn: 'roundabout-left' });
  assert.equal(driver.calls.length, 1);
  assert.equal(driver.calls[0].mode, 'short');

  // Same icon (/common/turn_roundabout.png) and same street, but distinct maneuver 'roundabout-right' MUST trigger second vibration
  session.ingest({ distance_m: 80, street: 'Roundabout Square', turn: 'roundabout-right' });
  assert.equal(driver.calls.length, 2);
  assert.equal(driver.calls[1].mode, 'short');
});

test('NavigationSession triggers second turn vibration for distinct maneuvers sharing icon and street', () => {
  const driver = new MockVibratorDriver();
  const haptics = new HapticsService(driver);
  const session = new NavigationSession(haptics);

  session.ingest({ turn: 'roundabout-left', distance_m: 100, street: 'Roundabout Square' });
  assert.equal(driver.calls.length, 1);
  assert.equal(driver.calls[0].mode, 'short');

  // Same icon (/common/turn_roundabout.png) and same street, but distinct maneuver 'roundabout-right' MUST trigger second vibration
  session.ingest({ turn: 'roundabout-right', distance_m: 80, street: 'Roundabout Square' });
  assert.equal(driver.calls.length, 2);
  assert.equal(driver.calls[1].mode, 'short');
});
