import test from 'node:test';
import assert from 'node:assert/strict';
import Suite from '../models/Suite.js';
import Experience from '../models/Experience.js';
import Branch from '../models/Branch.js';
import Promotion from '../models/Promotion.js';
import SuiteNight from '../models/SuiteNight.js';
import { insertSeedData, experiences } from '../seed/seedData.js';

// Sustituye los métodos de los modelos por una "base de datos" en memoria y los
// restaura al terminar. No necesita MongoDB.
const withFakeDb = (preset = {}) => async (fn) => {
  const db = { branches: new Map(), suites: [], experiences: [...(preset.experiences || [])], promotions: [...(preset.promotions || [])] };
  const saved = {};
  const patch = (obj, key, impl, name) => { saved[name] = [obj, key, obj[key]]; obj[key] = impl; };

  patch(Branch, 'findOneAndUpdate', async (filter, update) => {
    if (!db.branches.has(filter.slug)) db.branches.set(filter.slug, { _id: `branch-${filter.slug}`, ...update.$setOnInsert });
    return db.branches.get(filter.slug);
  }, 'branchFind');
  patch(Suite, 'create', async (d) => { db.suites.push(d); return d; }, 'suiteCreate');
  patch(Suite, 'deleteMany', async () => { db.suites = []; }, 'suiteDel');
  patch(Experience, 'create', async (d) => {
    if (db.experiences.some((e) => e.name === d.name)) { const e = new Error('E11000 duplicate key'); e.code = 11000; throw e; }
    db.experiences.push(d); return d;
  }, 'expCreate');
  patch(Experience, 'exists', async ({ name }) => (db.experiences.some((e) => e.name === name) ? { _id: 1 } : null), 'expExists');
  patch(Experience, 'deleteMany', async () => { db.experiences = []; }, 'expDel');
  patch(Branch, 'deleteMany', async () => { db.branches = new Map(); }, 'branchDel');
  patch(SuiteNight, 'deleteMany', async () => {}, 'nightDel');
  patch(Promotion, 'create', async (d) => { db.promotions.push(d); return d; }, 'promoCreate');
  patch(Promotion, 'countDocuments', async (q) => db.promotions.filter((p) => !q?.isSample || p.isSample).length, 'promoCount');
  patch(Promotion, 'deleteMany', async () => { db.promotions = []; }, 'promoDel');
  try { return await fn(db); } finally {
    for (const [obj, key, original] of Object.values(saved)) obj[key] = original;
  }
};

test('arranque automático (clear:false) con catálogo vacío: carga todo', async () => {
  await withFakeDb()(async (db) => {
    const r = await insertSeedData({ clear: false });
    assert.equal(r.experiences, experiences.length);
    assert.equal(db.experiences.length, experiences.length);
    assert.ok(r.suites > 0 && r.promotions > 0);
  });
});

test('arranque automático con experiencias ya existentes: no duplica ni falla', async () => {
  const already = [{ name: experiences[0].name }, { name: experiences[1].name }];
  await withFakeDb({ experiences: already })(async (db) => {
    const r = await insertSeedData({ clear: false });
    assert.equal(r.experiences, experiences.length - 2, 'solo crea las que faltan');
    assert.equal(db.experiences.length, experiences.length);
  });
});

test('arranque automático con promociones de ejemplo ya cargadas: no las repite', async () => {
  await withFakeDb({ promotions: [{ isSample: true }] })(async (db) => {
    const r = await insertSeedData({ clear: false });
    assert.equal(r.promotions, 0);
    assert.equal(db.promotions.length, 1);
  });
});

test('seed manual (clear:true) recrea todo desde cero', async () => {
  await withFakeDb({ experiences: [{ name: experiences[0].name }], promotions: [{ isSample: true }] })(async (db) => {
    const r = await insertSeedData({ clear: true });
    assert.equal(r.experiences, experiences.length);
    assert.equal(db.experiences.length, experiences.length);
    assert.ok(r.promotions > 0);
  });
});
