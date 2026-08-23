import assert from 'node:assert/strict';
import test from 'node:test';

import type { WatchPageIndexPage } from '../src/types.ts';

import page from '../src/pages/index/index.ts';

function createPageInstance(): WatchPageIndexPage {
  const instance = {
    ...page,
    data: { ...page.data },
    distance: page.data.distance,
    distanceUnit: page.data.distanceUnit,
    isArrived: page.data.isArrived,
    isNavigating: page.data.isNavigating,
    statusText: page.data.statusText,
    street: page.data.street,
    turnIcon: page.data.turnIcon
  };

  return instance as unknown as WatchPageIndexPage;
}

test('Watch index page initializes with idle state', () => {
  const p = createPageInstance();
  p.onInit();

  assert.equal(p.isNavigating, false);
  assert.equal(p.isArrived, false);
  assert.equal(p.statusText, 'Disconnected');
  assert.equal(p.turnIcon, '/common/turn_straight.png');
  assert.equal(p.distance, '0');
  assert.equal(p.distanceUnit, 'm');
  assert.equal(p.street, 'Ready');
});

test('Watch index page delegates updateNavigation and syncs properties', () => {
  const p = createPageInstance();
  p.onInit();

  p.updateNavigation({
    distance_m: 120,
    street: 'Tran Phu',
    turn: 'left'
  });

  assert.equal(p.isNavigating, true);
  assert.equal(p.isArrived, false);
  assert.equal(p.statusText, 'Navigating');
  assert.equal(p.turnIcon, '/common/turn_left.png');
  assert.equal(p.distance, '120');
  assert.equal(p.distanceUnit, 'm');
  assert.equal(p.street, 'Tran Phu');
});

test('Watch index page synchronizes arrival state on arrive maneuver', () => {
  const p = createPageInstance();
  p.onInit();

  p.updateNavigation({
    distance_m: 0,
    street: 'Keangnam Tower',
    turn: 'arrive'
  });

  assert.equal(p.isNavigating, true);
  assert.equal(p.isArrived, true);
  assert.equal(p.statusText, 'Arrived');
  assert.equal(p.turnIcon, '/common/turn_arrive.png');
  assert.equal(p.street, 'Keangnam Tower');
});

test('Watch index page ignores invalid navigation payloads', () => {
  const p = createPageInstance();
  p.onInit();

  p.updateNavigation({
    distance_m: 300,
    street: 'Pham Hung',
    turn: 'turn-right'
  });

  // Now send invalid payload
  p.updateNavigation(null);
  p.updateNavigation({ turn: '' });

  assert.equal(p.isNavigating, true);
  assert.equal(p.turnIcon, '/common/turn_right.png');
  assert.equal(p.distance, '300');
  assert.equal(p.street, 'Pham Hung');
});

test('Watch index page onDestroy cleans up session and receiver', () => {
  let receiverDestroyed = false;
  const p = createPageInstance();
  p.destroyWearEngineReceiver = () => {
    receiverDestroyed = true;
  };
  p.onInit();

  p.updateNavigation({
    distance_m: 200,
    street: 'Lang Ha',
    turn: 'right'
  });

  p.onDestroy();
  assert.equal(receiverDestroyed, true);
});
