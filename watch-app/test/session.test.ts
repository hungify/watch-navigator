import assert from 'node:assert/strict';
import test from 'node:test';

import { NavigationSession } from '../src/session.ts';

test('NavigationSession initializes with default disconnected state', () => {
  const session = new NavigationSession();
  const state = session.getState();

  assert.deepEqual(state, {
    distance: '0',
    isNavigating: false,
    statusText: 'Disconnected',
    street: 'Ready',
    turnIcon: '↑'
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

test('NavigationSession reset() restores default disconnected state', () => {
  const session = new NavigationSession();
  session.reset();

  assert.deepEqual(session.getState(), {
    distance: '0',
    isNavigating: false,
    statusText: 'Disconnected',
    street: 'Ready',
    turnIcon: '↑'
  });
});

test('NavigationSession ingest() updates state on valid payload', () => {
  const session = new NavigationSession();
  const success = session.ingest({
    distance_m: 120,
    street: 'Nguyen Trai',
    turn: 'left'
  });

  assert.equal(success, true);
  assert.deepEqual(session.getState(), {
    distance: '120',
    isNavigating: true,
    statusText: 'Navigating',
    street: 'Nguyen Trai',
    turnIcon: '←'
  });
});

test('NavigationSession normalizes distance from distance_m or distanceMeters', () => {
  const session = new NavigationSession();

  session.ingest({ distance_m: 350, turn: 'straight' });
  assert.equal(session.getState().distance, '350');

  session.ingest({ distanceMeters: 50, turn: 'right' });
  assert.equal(session.getState().distance, '50');

  session.ingest({ turn: 'straight' });
  assert.equal(session.getState().distance, '0');
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

test('NavigationSession resolves turn icons with normalization', () => {
  const session = new NavigationSession();

  session.ingest({ turn: 'TURN_LEFT' });
  assert.equal(session.getState().turnIcon, '←');

  session.ingest({ turn: 'turn-right' });
  assert.equal(session.getState().turnIcon, '→');

  session.ingest({ turn: 'slight_left' });
  assert.equal(session.getState().turnIcon, '↖');

  session.ingest({ turn: 'slight-right' });
  assert.equal(session.getState().turnIcon, '↗');

  session.ingest({ turn: 'uturn' });
  assert.equal(session.getState().turnIcon, '⮌');

  session.ingest({ turn: 'straight' });
  assert.equal(session.getState().turnIcon, '↑');
});

test('NavigationSession rejects invalid payloads and preserves existing state', () => {
  const session = new NavigationSession();

  // Setup an active navigation state first
  session.ingest({
    distance_m: 100,
    street: 'Nguyen Trai',
    turn: 'left'
  });

  const activeState = session.getState();

  const invalidPayloads: unknown[] = [
    null,
    undefined,
    123,
    'invalid',
    true,
    [],
    {},
    { distance_m: 100 },
    { turn: '' },
    { turn: '   ' },
    { turn: 123 },
    { turn: null },
    { turn: undefined },
    { turn: {} }
  ];

  for (const payload of invalidPayloads) {
    const result = session.ingest(payload);
    assert.equal(result, false, `Expected payload to be rejected: ${JSON.stringify(payload)}`);
    assert.deepEqual(
      session.getState(),
      activeState,
      'State should remain untouched on invalid ingest'
    );
  }
});

test('NavigationSession handles arrival step and full lifecycle state transitions', () => {
  const session = new NavigationSession();

  // 1. Initial State: Disconnected
  assert.equal(session.getState().statusText, 'Disconnected');
  assert.equal(session.getState().isNavigating, false);

  // 2. Disconnected -> Navigating
  session.ingest({
    distance_m: 500,
    street: 'Giai Phong',
    turn: 'straight'
  });
  assert.equal(session.getState().statusText, 'Navigating');
  assert.equal(session.getState().isNavigating, true);
  assert.equal(session.getState().turnIcon, '↑');
  assert.equal(session.getState().distance, '500');
  assert.equal(session.getState().street, 'Giai Phong');

  // 3. Navigating -> Next Step
  session.ingest({
    distance_m: 50,
    street: 'Dai Co Viet',
    turn: 'right'
  });
  assert.equal(session.getState().statusText, 'Navigating');
  assert.equal(session.getState().turnIcon, '→');
  assert.equal(session.getState().distance, '50');
  assert.equal(session.getState().street, 'Dai Co Viet');

  // 4. Navigating -> Arrived
  session.ingest({
    distance_m: 0,
    street: 'Destination',
    turn: 'arrive'
  });
  assert.equal(session.getState().statusText, 'Arrived');
  assert.equal(session.getState().isNavigating, true);
  assert.equal(session.getState().turnIcon, '★');
  assert.equal(session.getState().distance, '0');
  assert.equal(session.getState().street, 'Destination');

  // 5. Arrived -> Disconnected via reset()
  session.reset();
  assert.deepEqual(session.getState(), {
    distance: '0',
    isNavigating: false,
    statusText: 'Disconnected',
    street: 'Ready',
    turnIcon: '↑'
  });
});

test('NavigationSession handles arrival case-insensitively', () => {
  const session = new NavigationSession();

  session.ingest({ turn: 'ARRIVE', distance_m: 0, street: 'Home' });
  assert.equal(session.getState().statusText, 'Arrived');
  assert.equal(session.getState().turnIcon, '★');

  session.ingest({ turn: 'Arrive', distance_m: 0, street: 'Office' });
  assert.equal(session.getState().statusText, 'Arrived');
  assert.equal(session.getState().turnIcon, '★');
});
