import assert from 'node:assert/strict';
import test from 'node:test';

import type { VibratorDriver } from '../src/haptics.ts';

import { HapticsService } from '../src/haptics.ts';

class MockVibratorDriver implements VibratorDriver {
  public calls: Array<{ mode: 'short' | 'long' }> = [];

  vibrate(options: { mode: 'short' | 'long' }): void {
    this.calls.push(options);
  }
}

class FaultyVibratorDriver implements VibratorDriver {
  vibrate(): void {
    throw new Error('Vibrator hardware failure');
  }
}

test('HapticsService calls short vibration for turn update', () => {
  const driver = new MockVibratorDriver();
  const haptics = new HapticsService(driver);

  haptics.vibrateTurn();
  assert.equal(driver.calls.length, 1);
  assert.equal(driver.calls[0].mode, 'short');
});

test('HapticsService calls long vibration for arrival', () => {
  const driver = new MockVibratorDriver();
  const haptics = new HapticsService(driver);

  haptics.vibrateArrival();
  assert.equal(driver.calls.length, 1);
  assert.equal(driver.calls[0].mode, 'long');
});

test('HapticsService gracefully handles errors or missing driver without throwing', () => {
  const faultyHaptics = new HapticsService(new FaultyVibratorDriver());
  assert.doesNotThrow(() => {
    faultyHaptics.vibrateTurn();
    faultyHaptics.vibrateArrival();
  });

  const nullDriverHaptics = new HapticsService(null);
  assert.doesNotThrow(() => {
    nullDriverHaptics.vibrateTurn();
    nullDriverHaptics.vibrateArrival();
  });
});
