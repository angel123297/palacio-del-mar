// Regresión: un id de experiencia inválido ("fx-3") debe dar 400, no 500.
import test from 'node:test';
import assert from 'node:assert/strict';
import { calculatePrice } from '../controllers/suiteController.js';

const call = async (body) => {
  let status = 200; let payload;
  const res = { status(c) { status = c; return this; }, json(b) { payload = b; return this; } };
  await calculatePrice({ body }, res);
  return { status, payload };
};
const base = { suiteId: '6ac573f4a43af3f2e0a6e4e1', checkIn: '2026-12-01', checkOut: '2026-12-03', includeExperiences: true };

test('calculate-price: id de experiencia inválido -> 400 con mensaje claro', async () => {
  const { status, payload } = await call({ ...base, experienceIds: ['fx-3'] });
  assert.equal(status, 400);
  assert.match(payload.message, /experiencias seleccionadas no es válida/);
});

test('calculate-price: mezcla de ids válidos e inválidos también -> 400', async () => {
  const { status } = await call({ ...base, experienceIds: ['6ac573f4a43af3f2e0a6e4e2', 'fx-1'] });
  assert.equal(status, 400);
});
