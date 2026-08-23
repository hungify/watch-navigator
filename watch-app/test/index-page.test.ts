import assert from 'node:assert/strict';
import test from 'node:test';

import page from '../src/pages/index/index.ts';

test('page initial data is synchronized with NavigationSession initial state', () => {
  assert.deepEqual(page.data, {
    distance: '0',
    isNavigating: false,
    statusText: 'Disconnected',
    street: 'Ready',
    turnIcon: '↑'
  });
});

test('page updateNavigation updates UI properties via session.ingest', () => {
  const mockPageContext = {
    ...page,
    distance: '0',
    isNavigating: false,
    statusText: 'Disconnected',
    street: 'Ready',
    turnIcon: '↑'
  };

  mockPageContext.updateNavigation({
    distance_m: 180,
    street: 'Kim Ma',
    turn: 'turn-left'
  });

  assert.equal(mockPageContext.isNavigating, true);
  assert.equal(mockPageContext.statusText, 'Navigating');
  assert.equal(mockPageContext.distance, '180');
  assert.equal(mockPageContext.street, 'Kim Ma');
  assert.equal(mockPageContext.turnIcon, '←');

  // Verify arrival
  mockPageContext.updateNavigation({
    distance_m: 0,
    street: 'Kim Ma',
    turn: 'arrive'
  });

  assert.equal(mockPageContext.isNavigating, true);
  assert.equal(mockPageContext.statusText, 'Arrived');
  assert.equal(mockPageContext.distance, '0');
  assert.equal(mockPageContext.turnIcon, '★');
});

test('page updateNavigation ignores invalid payloads without updating UI properties', () => {
  const mockPageContext = {
    ...page,
    distance: '100',
    isNavigating: true,
    statusText: 'Navigating',
    street: 'Old Street',
    turnIcon: '←'
  };

  mockPageContext.updateNavigation(null);
  assert.equal(mockPageContext.distance, '100');
  assert.equal(mockPageContext.statusText, 'Navigating');

  mockPageContext.updateNavigation({ invalid: true });
  assert.equal(mockPageContext.distance, '100');
  assert.equal(mockPageContext.statusText, 'Navigating');
});

test('page onDestroy resets session and cleans up', () => {
  let receiverDestroyed = false;
  const mockPageContext = {
    ...page,
    destroyWearEngineReceiver() {
      receiverDestroyed = true;
    }
  };

  // Ingest valid navigation data first
  mockPageContext.session.ingest({
    distance_m: 200,
    street: 'Lang Ha',
    turn: 'right'
  });
  assert.equal(mockPageContext.session.getState().isNavigating, true);

  // Call onDestroy
  mockPageContext.onDestroy();
  assert.equal(receiverDestroyed, true);
  assert.equal(mockPageContext.session.getState().isNavigating, false);
  assert.equal(mockPageContext.session.getState().statusText, 'Disconnected');
});
