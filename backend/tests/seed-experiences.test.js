import test from 'node:test';
import assert from 'node:assert/strict';
import Experience from '../models/Experience.js';
import { experiences } from '../seed/seedData.js';
import { addMissingExperiences } from '../scripts/seedExperiences.js';

test('las experiencias del seed son válidas según el esquema', () => {
  for (const e of experiences) {
    const err = new Experience(e).validateSync();
    assert.equal(err, undefined, `${e.name}: ${err && Object.keys(err.errors)}`);
  }
});

test('seed de experiencias: agrega solo las que faltan y no borra nada', async () => {
  const stored = [{ slug: experiences[0].slug }];
  const model = {
    exists: async (q) => (stored.some((s) => s.slug === q.slug) ? { _id: 1 } : null),
    create: async (d) => { stored.push({ slug: d.slug }); },
    deleteMany: async () => { throw new Error('no debe borrar'); }
  };
  assert.equal(await addMissingExperiences(model, experiences), experiences.length - 1);
  assert.equal(stored.length, experiences.length);
  assert.equal(await addMissingExperiences(model, experiences), 0, 'segunda ejecución: idempotente');
});
