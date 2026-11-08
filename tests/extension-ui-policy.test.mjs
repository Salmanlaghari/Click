import assert from 'node:assert/strict';
import test from 'node:test';
import {
  EXTENSION_TABS,
  isApplyExtensionActionAvailable,
} from '../src/components/extensionUiPolicy.js';

test('extension preview does not expose a userscripts surface', () => {
  assert.deepEqual(EXTENSION_TABS, ['extensions', 'trackers']);
  assert.equal(EXTENSION_TABS.includes('userscripts'), false);
});

test('applying an extension requires a host action handler', () => {
  assert.equal(isApplyExtensionActionAvailable(undefined), false);
  assert.equal(isApplyExtensionActionAvailable(null), false);
  assert.equal(isApplyExtensionActionAvailable(() => {}), true);
});
