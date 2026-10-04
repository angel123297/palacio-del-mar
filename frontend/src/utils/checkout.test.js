import test from 'node:test';
import assert from 'node:assert/strict';
import { groupNights, policyText, newIdempotencyKey } from './checkout.js';

test('groupNights junta noches seguidas de la misma temporada y precio', () => {
  const lines = groupNights([
    { season: 'low', price: 100 }, { season: 'low', price: 100 }, { season: 'peak', price: 150 }, { season: 'low', price: 100 }
  ]);
  assert.deepEqual(lines, [
    { season: 'low', unitPrice: 100, nights: 2, amount: 200 },
    { season: 'peak', unitPrice: 150, nights: 1, amount: 150 },
    { season: 'low', unitPrice: 100, nights: 1, amount: 100 }
  ]);
  assert.deepEqual(groupNights(undefined), []);
});

test('policyText usa la política del servidor', () => {
  assert.match(policyText({ freeDays: 7, feePercent: 10 }), /hasta 7 días.*10 %/);
  assert.equal(policyText(null), '');
});

test('la clave de idempotencia cumple el formato que exige el servidor y es distinta cada vez', () => {
  const a = newIdempotencyKey();
  assert.match(a, /^[A-Za-z0-9_-]{8,80}$/);
  assert.notEqual(a, newIdempotencyKey());
});
