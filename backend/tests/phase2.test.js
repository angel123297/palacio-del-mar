// Fase 2: resumen por sucursal, promociones de ejemplo y arranque. No necesita base de datos.
import test from 'node:test';
import assert from 'node:assert/strict';
import mongoose from 'mongoose';
import Promotion from '../models/Promotion.js';
import { branches } from '../seed/branches.js';
import { buildSamplePromotions } from '../seed/promotions.js';
import { summarizeBranches, attachPromotions } from '../utils/branchSummary.js';
import { autoSeedIfEmpty, ensureDevAdmin } from '../bootstrap.js';

const oid = () => new mongoose.Types.ObjectId();
const b1 = { _id: oid(), slug: 'getsemani', name: 'Palacio del Mar · Getsemaní', zone: 'Getsemaní' };
const b2 = { _id: oid(), slug: 'bocagrande', name: 'Palacio del Mar · Bocagrande', zone: 'Bocagrande' };

test('summarizeBranches cuenta solo las libres y toma el precio mínimo de ellas', () => {
  const rows = summarizeBranches([
    { branch: b1, isAvailable: true, pricePerNight: 500000 },
    { branch: b1, isAvailable: true, pricePerNight: 400000 },
    { branch: b1, isAvailable: false, pricePerNight: 100000 }, // ocupada: no cuenta para "desde"
    { branch: b2, isAvailable: false, pricePerNight: 300000 },
    { branch: null, isAvailable: true, pricePerNight: 1 } // sin sucursal: se ignora
  ]);
  const g = rows.find((r) => r.slug === 'getsemani');
  const bg = rows.find((r) => r.slug === 'bocagrande');
  assert.equal(rows.length, 2);
  assert.equal(g.availableSuites, 2);
  assert.equal(g.fromPrice, 400000);
  assert.equal(bg.availableSuites, 0);
  assert.equal(bg.fromPrice, null);
});

test('attachPromotions elige el mayor descuento y deja null a quien no tiene', () => {
  const rows = summarizeBranches([{ branch: b1, isAvailable: true, pricePerNight: 1 }, { branch: b2, isAvailable: true, pricePerNight: 1 }]);
  const d = new Date();
  attachPromotions(rows, [
    { branch: b1._id, title: 'A', discountPercent: 10, startDate: d, endDate: d },
    { branch: b1._id, title: 'B', discountPercent: 25, startDate: d, endDate: d }
  ]);
  assert.equal(rows.find((r) => r.slug === 'getsemani').promotion.title, 'B');
  assert.equal(rows.find((r) => r.slug === 'bocagrande').promotion, null);
});

test('las promociones de ejemplo son válidas, una por sucursal y empiezan en el futuro o hoy', () => {
  const map = new Map(branches.map((b) => [b.slug, { _id: oid(), slug: b.slug }]));
  const now = new Date('2026-10-03T15:00:00Z');
  const promos = buildSamplePromotions(map, now);
  assert.equal(promos.length, branches.length);
  for (const p of promos) {
    assert.equal(new Promotion(p).validateSync(), undefined);
    assert.ok(p.startDate >= new Date('2026-10-03T00:00:00Z'));
    assert.ok(p.endDate > p.startDate);
    assert.equal(p.isSample, true);
  }
});

test('una promoción con fecha final anterior a la inicial se rechaza', () => {
  const err = new Promotion({
    branch: oid(), title: 'x', discountPercent: 10,
    startDate: new Date('2030-02-10'), endDate: new Date('2030-02-01')
  }).validateSync();
  assert.ok(err?.errors?.endDate);
});

test('AUTO_SEED=false no carga habitaciones', async () => {
  process.env.AUTO_SEED = 'false';
  const r = await autoSeedIfEmpty();
  assert.deepEqual(r, { seeded: false, reason: 'disabled' });
  delete process.env.AUTO_SEED;
});

test('el admin de desarrollo se ignora en producción y sin configurar', async () => {
  const saved = { ...process.env };
  process.env.DEV_ADMIN_EMAIL = 'admin@gmail.com';
  process.env.DEV_ADMIN_PASSWORD = 'admin123';
  process.env.NODE_ENV = 'production';
  assert.equal((await ensureDevAdmin()).reason, 'production');
  delete process.env.DEV_ADMIN_EMAIL;
  assert.equal((await ensureDevAdmin()).reason, 'not_configured');
  Object.assign(process.env, saved);
});
