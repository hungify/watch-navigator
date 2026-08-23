import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';

import {
  formatDistance,
  getCanonicalTurn,
  getTurnIcon,
  isValidNavigationPayload
} from '../src/types.ts';

test('getTurnIcon maps all turn directions correctly to asset paths', () => {
  // Standard turns
  assert.equal(getTurnIcon('left'), '/common/turn_left.png');
  assert.equal(getTurnIcon('turn-left'), '/common/turn_left.png');
  assert.equal(getTurnIcon('turn_left'), '/common/turn_left.png');
  assert.equal(getTurnIcon('right'), '/common/turn_right.png');
  assert.equal(getTurnIcon('turn-right'), '/common/turn_right.png');
  assert.equal(getTurnIcon('turn_right'), '/common/turn_right.png');

  // Slight turns & ramps/forks
  assert.equal(getTurnIcon('slight-left'), '/common/turn_slight_left.png');
  assert.equal(getTurnIcon('slight_left'), '/common/turn_slight_left.png');
  assert.equal(getTurnIcon('turn-slight-left'), '/common/turn_slight_left.png');
  assert.equal(getTurnIcon('ramp-left'), '/common/turn_slight_left.png');
  assert.equal(getTurnIcon('fork-left'), '/common/turn_slight_left.png');
  assert.equal(getTurnIcon('slight-right'), '/common/turn_slight_right.png');
  assert.equal(getTurnIcon('slight_right'), '/common/turn_slight_right.png');
  assert.equal(getTurnIcon('turn-slight-right'), '/common/turn_slight_right.png');
  assert.equal(getTurnIcon('ramp-right'), '/common/turn_slight_right.png');
  assert.equal(getTurnIcon('fork-right'), '/common/turn_slight_right.png');

  // Sharp turns
  assert.equal(getTurnIcon('sharp-left'), '/common/turn_sharp_left.png');
  assert.equal(getTurnIcon('sharp_left'), '/common/turn_sharp_left.png');
  assert.equal(getTurnIcon('turn-sharp-left'), '/common/turn_sharp_left.png');
  assert.equal(getTurnIcon('sharp-right'), '/common/turn_sharp_right.png');
  assert.equal(getTurnIcon('sharp_right'), '/common/turn_sharp_right.png');
  assert.equal(getTurnIcon('turn-sharp-right'), '/common/turn_sharp_right.png');

  // U-turns
  assert.equal(getTurnIcon('uturn'), '/common/turn_uturn.png');
  assert.equal(getTurnIcon('uturn-left'), '/common/turn_uturn.png');
  assert.equal(getTurnIcon('uturn_left'), '/common/turn_uturn.png');
  assert.equal(getTurnIcon('uturn-right'), '/common/turn_uturn.png');
  assert.equal(getTurnIcon('uturn_right'), '/common/turn_uturn.png');
  assert.equal(getTurnIcon('u-turn'), '/common/turn_uturn.png');

  // Roundabout & special
  assert.equal(getTurnIcon('roundabout'), '/common/turn_roundabout.png');
  assert.equal(getTurnIcon('roundabout-left'), '/common/turn_roundabout.png');
  assert.equal(getTurnIcon('roundabout-right'), '/common/turn_roundabout.png');
  assert.equal(getTurnIcon('depart'), '/common/turn_depart.png');
  assert.equal(getTurnIcon('arrive'), '/common/turn_arrive.png');
  assert.equal(getTurnIcon('destination'), '/common/turn_arrive.png');
  assert.equal(getTurnIcon('straight'), '/common/turn_straight.png');
  assert.equal(getTurnIcon('continue'), '/common/turn_straight.png');
  assert.equal(getTurnIcon('merge'), '/common/turn_straight.png');
  assert.equal(getTurnIcon('unknown'), '/common/turn_straight.png');
  assert.equal(getTurnIcon(''), '/common/turn_straight.png');
});

test('All turn icon assets exist in media resources and common directory', () => {
  const maneuvers = [
    'left',
    'right',
    'slight-left',
    'slight-right',
    'sharp-left',
    'sharp-right',
    'uturn',
    'roundabout',
    'arrive',
    'depart',
    'straight'
  ];

  for (const maneuver of maneuvers) {
    const iconPath = getTurnIcon(maneuver);
    assert.match(iconPath, /^\/common\/turn_[a-z_]+\.png$/);
    const filename = path.basename(iconPath);
    const mediaPath = path.resolve('entry/src/main/resources/base/media', filename);
    const commonPath = path.resolve('entry/src/main/js/default/common', filename);

    assert.equal(fs.existsSync(mediaPath), true, `Media asset missing: ${mediaPath}`);
    assert.equal(fs.existsSync(commonPath), true, `Common asset missing: ${commonPath}`);
  }
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
