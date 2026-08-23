import assert from 'node:assert/strict';
import test from 'node:test';

import { getTurnIcon, isValidNavigationPayload } from '../src/types.ts';

test('getTurnIcon maps turn directions correctly', () => {
  assert.equal(getTurnIcon('left'), '←');
  assert.equal(getTurnIcon('turn-left'), '←');
  assert.equal(getTurnIcon('right'), '→');
  assert.equal(getTurnIcon('turn-right'), '→');
  assert.equal(getTurnIcon('slight-left'), '↖');
  assert.equal(getTurnIcon('slight-right'), '↗');
  assert.equal(getTurnIcon('uturn'), '⮌');
  assert.equal(getTurnIcon('arrive'), '★');
  assert.equal(getTurnIcon('straight'), '↑');
  assert.equal(getTurnIcon('unknown'), '↑');
});

test('isValidNavigationPayload validates JSON contract correctly', () => {
  assert.equal(isValidNavigationPayload({ turn: 'left', distance_m: 100 }), true);
  assert.equal(isValidNavigationPayload({ turn: 'arrive' }), true);
  assert.equal(
    isValidNavigationPayload({ turn: 'right', distanceMeters: 50, street: 'Main St' }),
    true
  );
  assert.equal(isValidNavigationPayload({ turn: 'right', streetName: 'Second Ave' }), true);
  assert.equal(isValidNavigationPayload(null), false);
  assert.equal(isValidNavigationPayload(undefined), false);
  assert.equal(isValidNavigationPayload([]), false);
  assert.equal(isValidNavigationPayload({ turn: '' }), false);
  assert.equal(isValidNavigationPayload({ turn: '   ' }), false);
  assert.equal(isValidNavigationPayload({ turn: 123 }), false);
  assert.equal(isValidNavigationPayload({ turn: 'left', distance_m: {} }), false);
  assert.equal(isValidNavigationPayload({ turn: 'left', distance_m: NaN }), false);
  assert.equal(isValidNavigationPayload({ turn: 'left', distance_m: Infinity }), false);
  assert.equal(isValidNavigationPayload({ turn: 'left', distanceMeters: '100' }), false);
  assert.equal(isValidNavigationPayload({ turn: 'left', street: 123 }), false);
  assert.equal(isValidNavigationPayload({ turn: 'left', streetName: [] }), false);
});
