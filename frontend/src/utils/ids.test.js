import test from 'node:test';
import assert from 'node:assert/strict';
import { isObjectId } from './ids.js';

test('acepta un ObjectId de 24 hex', () => {
  assert.equal(isObjectId('6ac573f4a43af3f2e0a6e4e1'), true);
  assert.equal(isObjectId('6AC573F4A43AF3F2E0A6E4E1'), true);
});

test('rechaza ids de respaldo y valores raros', () => {
  for (const v of ['fx-3', '', null, undefined, 123, '6ac573f4a43af3f2e0a6e4e', '6ac573f4a43af3f2e0a6e4e1z']) {
    assert.equal(isObjectId(v), false, String(v));
  }
});
