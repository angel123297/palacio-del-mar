import { test } from 'node:test';
import assert from 'node:assert/strict';
import { pickPromotion } from './promos.js';

const p = (id, startDate, endDate, discountPercent) => ({ id, title: id, startDate, endDate, discountPercent });

test('banner: elige la vigente de mayor descuento', () => {
  const r = pickPromotion([p('a', '2026-10-01', '2026-10-31', 10), p('b', '2026-10-02', '2026-10-20', 20)], '2026-10-05');
  assert.equal(r.promo.id, 'b'); assert.equal(r.live, true);
});
test('banner: sin vigentes muestra la próxima; sin promociones, nada', () => {
  const r = pickPromotion([p('c', '2026-12-01', '2026-12-10', 15), p('d', '2026-11-01', '2026-11-10', 5)], '2026-10-05');
  assert.equal(r.promo.id, 'd'); assert.equal(r.live, false);
  assert.equal(pickPromotion([p('x', '2026-01-01', '2026-01-05', 5)], '2026-10-05'), null);
});
