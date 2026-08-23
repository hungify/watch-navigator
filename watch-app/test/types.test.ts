import assert from 'node:assert/strict';
import test from 'node:test';

import {
  formatDistance,
  getCanonicalTurn,
  getTurnIcon,
  isValidNavigationPayload
} from '../src/types.ts';

test('getTurnIcon maps all turn directions correctly', () => {
  // Standard turns
  assert.equal(getTurnIcon('left'), '←');
  assert.equal(getTurnIcon('turn-left'), '←');
  assert.equal(getTurnIcon('turn_left'), '←');
  assert.equal(getTurnIcon('right'), '→');
  assert.equal(getTurnIcon('turn-right'), '→');
  assert.equal(getTurnIcon('turn_right'), '→');

  // Slight turns & ramps/forks
  assert.equal(getTurnIcon('slight-left'), '↖');
  assert.equal(getTurnIcon('slight_left'), '↖');
  assert.equal(getTurnIcon('turn-slight-left'), '↖');
  assert.equal(getTurnIcon('ramp-left'), '↖');
  assert.equal(getTurnIcon('fork-left'), '↖');
  assert.equal(getTurnIcon('slight-right'), '↗');
  assert.equal(getTurnIcon('slight_right'), '↗');
  assert.equal(getTurnIcon('turn-slight-right'), '↗');
  assert.equal(getTurnIcon('ramp-right'), '↗');
  assert.equal(getTurnIcon('fork-right'), '↗');

  // Sharp turns
  assert.equal(getTurnIcon('sharp-left'), '↰');
  assert.equal(getTurnIcon('sharp_left'), '↰');
  assert.equal(getTurnIcon('turn-sharp-left'), '↰');
  assert.equal(getTurnIcon('sharp-right'), '↱');
  assert.equal(getTurnIcon('sharp_right'), '↱');
  assert.equal(getTurnIcon('turn-sharp-right'), '↱');

  // U-turns
  assert.equal(getTurnIcon('uturn'), '⮌');
  assert.equal(getTurnIcon('uturn-left'), '⮌');
  assert.equal(getTurnIcon('uturn_left'), '⮌');
  assert.equal(getTurnIcon('uturn-right'), '⮌');
  assert.equal(getTurnIcon('uturn_right'), '⮌');
  assert.equal(getTurnIcon('u-turn'), '⮌');

  // Roundabout & special
  assert.equal(getTurnIcon('roundabout'), '⟳');
  assert.equal(getTurnIcon('roundabout-left'), '⟳');
  assert.equal(getTurnIcon('roundabout-right'), '⟳');
  assert.equal(getTurnIcon('depart'), '↑');
  assert.equal(getTurnIcon('arrive'), '★');
  assert.equal(getTurnIcon('destination'), '★');
  assert.equal(getTurnIcon('straight'), '↑');
  assert.equal(getTurnIcon('continue'), '↑');
  assert.equal(getTurnIcon('merge'), '↑');
  assert.equal(getTurnIcon('unknown'), '↑');
  assert.equal(getTurnIcon(''), '↑');
});
test('getCanonicalTurn normalizes aliases to canonical maneuver names', () => {
  // Left aliases
  assert.equal(getCanonicalTurn('left'), 'left');
  assert.equal(getCanonicalTurn('turn-left'), 'left');
  assert.equal(getCanonicalTurn('turn_left'), 'left');

  // Right aliases
  assert.equal(getCanonicalTurn('right'), 'right');
  assert.equal(getCanonicalTurn('turn-right'), 'right');
  assert.equal(getCanonicalTurn('turn_right'), 'right');

  // Slight left aliases & ramps/forks
  assert.equal(getCanonicalTurn('slight-left'), 'slight-left');
  assert.equal(getCanonicalTurn('slight_left'), 'slight-left');
  assert.equal(getCanonicalTurn('turn-slight-left'), 'slight-left');
  assert.equal(getCanonicalTurn('ramp-left'), 'ramp-left');
  assert.equal(getCanonicalTurn('fork-left'), 'fork-left');

  // Slight right aliases & ramps/forks
  assert.equal(getCanonicalTurn('slight-right'), 'slight-right');
  assert.equal(getCanonicalTurn('slight_right'), 'slight-right');
  assert.equal(getCanonicalTurn('turn-slight-right'), 'slight-right');
  assert.equal(getCanonicalTurn('ramp-right'), 'ramp-right');
  assert.equal(getCanonicalTurn('fork-right'), 'fork-right');

  // Sharp turns
  assert.equal(getCanonicalTurn('sharp-left'), 'sharp-left');
  assert.equal(getCanonicalTurn('sharp_left'), 'sharp-left');
  assert.equal(getCanonicalTurn('turn-sharp-left'), 'sharp-left');
  assert.equal(getCanonicalTurn('sharp-right'), 'sharp-right');
  assert.equal(getCanonicalTurn('sharp_right'), 'sharp-right');
  assert.equal(getCanonicalTurn('turn-sharp-right'), 'sharp-right');

  // U-turns
  assert.equal(getCanonicalTurn('uturn'), 'uturn-left');
  assert.equal(getCanonicalTurn('uturn-left'), 'uturn-left');
  assert.equal(getCanonicalTurn('uturn_left'), 'uturn-left');
  assert.equal(getCanonicalTurn('u-turn'), 'uturn-left');
  assert.equal(getCanonicalTurn('uturn-right'), 'uturn-right');
  assert.equal(getCanonicalTurn('uturn_right'), 'uturn-right');

  // Roundabout
  assert.equal(getCanonicalTurn('roundabout'), 'roundabout');
  assert.equal(getCanonicalTurn('roundabout-left'), 'roundabout-left');
  assert.equal(getCanonicalTurn('roundabout_left'), 'roundabout-left');
  assert.equal(getCanonicalTurn('roundabout-right'), 'roundabout-right');
  assert.equal(getCanonicalTurn('roundabout_right'), 'roundabout-right');

  // Arrival and other
  assert.equal(getCanonicalTurn('arrive'), 'arrive');
  assert.equal(getCanonicalTurn('destination'), 'arrive');
  assert.equal(getCanonicalTurn('depart'), 'depart');
  assert.equal(getCanonicalTurn('straight'), 'straight');
  assert.equal(getCanonicalTurn('continue'), 'continue');
  assert.equal(getCanonicalTurn('merge'), 'merge');
  assert.equal(getCanonicalTurn('custom-maneuver'), 'custom-maneuver');
  assert.equal(getCanonicalTurn(''), 'straight');
});

test('getCanonicalTurn normalizes aliases to canonical maneuver names', () => {
  // Left aliases
  assert.equal(getCanonicalTurn('left'), 'left');
  assert.equal(getCanonicalTurn('turn-left'), 'left');
  assert.equal(getCanonicalTurn('turn_left'), 'left');

  // Right aliases
  assert.equal(getCanonicalTurn('right'), 'right');
  assert.equal(getCanonicalTurn('turn-right'), 'right');
  assert.equal(getCanonicalTurn('turn_right'), 'right');

  // Slight left aliases & ramps/forks
  assert.equal(getCanonicalTurn('slight-left'), 'slight-left');
  assert.equal(getCanonicalTurn('slight_left'), 'slight-left');
  assert.equal(getCanonicalTurn('turn-slight-left'), 'slight-left');
  assert.equal(getCanonicalTurn('ramp-left'), 'ramp-left');
  assert.equal(getCanonicalTurn('fork-left'), 'fork-left');

  // Slight right aliases & ramps/forks
  assert.equal(getCanonicalTurn('slight-right'), 'slight-right');
  assert.equal(getCanonicalTurn('slight_right'), 'slight-right');
  assert.equal(getCanonicalTurn('turn-slight-right'), 'slight-right');
  assert.equal(getCanonicalTurn('ramp-right'), 'ramp-right');
  assert.equal(getCanonicalTurn('fork-right'), 'fork-right');

  // Sharp turns
  assert.equal(getCanonicalTurn('sharp-left'), 'sharp-left');
  assert.equal(getCanonicalTurn('sharp_left'), 'sharp-left');
  assert.equal(getCanonicalTurn('turn-sharp-left'), 'sharp-left');
  assert.equal(getCanonicalTurn('sharp-right'), 'sharp-right');
  assert.equal(getCanonicalTurn('sharp_right'), 'sharp-right');
  assert.equal(getCanonicalTurn('turn-sharp-right'), 'sharp-right');

  // U-turns
  assert.equal(getCanonicalTurn('uturn'), 'uturn-left');
  assert.equal(getCanonicalTurn('uturn-left'), 'uturn-left');
  assert.equal(getCanonicalTurn('uturn_left'), 'uturn-left');
  assert.equal(getCanonicalTurn('u-turn'), 'uturn-left');
  assert.equal(getCanonicalTurn('uturn-right'), 'uturn-right');
  assert.equal(getCanonicalTurn('uturn_right'), 'uturn-right');

  // Roundabout
  assert.equal(getCanonicalTurn('roundabout'), 'roundabout');
  assert.equal(getCanonicalTurn('roundabout-left'), 'roundabout-left');
  assert.equal(getCanonicalTurn('roundabout_left'), 'roundabout-left');
  assert.equal(getCanonicalTurn('roundabout-right'), 'roundabout-right');
  assert.equal(getCanonicalTurn('roundabout_right'), 'roundabout-right');

  // Arrival and other
  assert.equal(getCanonicalTurn('arrive'), 'arrive');
  assert.equal(getCanonicalTurn('destination'), 'arrive');
  assert.equal(getCanonicalTurn('depart'), 'depart');
  assert.equal(getCanonicalTurn('straight'), 'straight');
  assert.equal(getCanonicalTurn('continue'), 'continue');
  assert.equal(getCanonicalTurn('merge'), 'merge');
  assert.equal(getCanonicalTurn('custom-maneuver'), 'custom-maneuver');
  assert.equal(getCanonicalTurn(''), 'straight');
});

test('formatDistance formats meters and kilometers properly', () => {
  // Meters (< 1000m)
  assert.deepEqual(formatDistance(0), { value: '0', unit: 'm' });
  assert.deepEqual(formatDistance(50), { value: '50', unit: 'm' });
  assert.deepEqual(formatDistance(120), { value: '120', unit: 'm' });
  assert.deepEqual(formatDistance(950), { value: '950', unit: 'm' });
  assert.deepEqual(formatDistance(999), { value: '999', unit: 'm' });

  // Kilometers (>= 1000m)
  assert.deepEqual(formatDistance(1000), { value: '1.0', unit: 'km' });
  assert.deepEqual(formatDistance(1250), { value: '1.3', unit: 'km' });
  assert.deepEqual(formatDistance(1500), { value: '1.5', unit: 'km' });
  assert.deepEqual(formatDistance(12340), { value: '12.3', unit: 'km' });

  // String inputs
  assert.deepEqual(formatDistance('350'), { value: '350', unit: 'm' });
  assert.deepEqual(formatDistance('2400'), { value: '2.4', unit: 'km' });

  // Edge cases & invalid inputs
  assert.deepEqual(formatDistance(), { value: '0', unit: 'm' });
  assert.deepEqual(formatDistance(-50), { value: '0', unit: 'm' });
  assert.deepEqual(formatDistance(NaN), { value: '0', unit: 'm' });
  assert.deepEqual(formatDistance('invalid'), { value: '0', unit: 'm' });
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
