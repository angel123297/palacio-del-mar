// Fase 1: sucursales, datos del seed y asignación de habitaciones físicas.
// No necesita base de datos: simula la clave única (suite, slot, date) en memoria.
import test from 'node:test';
import assert from 'node:assert/strict';
import mongoose from 'mongoose';
import Branch from '../models/Branch.js';
import Suite from '../models/Suite.js';
import SuiteNight, { NightsConflictError } from '../models/SuiteNight.js';
import { branches } from '../seed/branches.js';
import { suites as baseSuites, branchOffers, buildBranchSuites } from '../seed/seedData.js';
import { eachNight, toCalendarDate } from '../utils/dates.js';

test('hay 4 sucursales válidas, con slugs únicos y coordenadas dentro de Cartagena', () => {
  assert.equal(branches.length, 4);
  assert.equal(new Set(branches.map((b) => b.slug)).size, 4);
  for (const b of branches) {
    const err = new Branch(b).validateSync();
    assert.equal(err, undefined, `${b.slug}: ${err?.message}`);
    assert.ok(b.location.lat > 10.35 && b.location.lat < 10.5, `${b.slug} lat`);
    assert.ok(b.location.lng > -75.6 && b.location.lng < -75.45, `${b.slug} lng`);
    assert.ok(b.highlights.length >= 3, `${b.slug} necesita lugares cercanos`);
  }
});

test('las sucursales no están encimadas en el mapa (>1 km entre sí)', () => {
  const km = (a, b) => {
    const R = 6371, rad = (x) => (x * Math.PI) / 180;
    const dLat = rad(b.lat - a.lat), dLng = rad(b.lng - a.lng);
    const h = Math.sin(dLat / 2) ** 2 + Math.cos(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.sin(dLng / 2) ** 2;
    return 2 * R * Math.asin(Math.sqrt(h));
  };
  for (let i = 0; i < branches.length; i++)
    for (let j = i + 1; j < branches.length; j++)
      assert.ok(km(branches[i].location, branches[j].location) > 0.5, `${branches[i].slug} vs ${branches[j].slug}`);
});

test('el seed genera suites válidas, con slug y (sucursal, nombre) únicos', () => {
  const bySlug = new Map(branches.map((b) => [b.slug, { ...b, _id: new mongoose.Types.ObjectId() }]));
  const docs = buildBranchSuites(bySlug);
  assert.equal(docs.length, Object.values(branchOffers).reduce((n, o) => n + o.rooms.length, 0));
  assert.equal(new Set(docs.map((d) => d.slug)).size, docs.length, 'slugs repetidos');
  assert.equal(new Set(docs.map((d) => `${d.branch}|${d.name}`)).size, docs.length);
  for (const d of docs) {
    const suite = new Suite(d);
    const err = suite.validateSync();
    assert.equal(err, undefined, `${d.slug}: ${err?.message}`);
    assert.ok(d.totalUnits >= 1);
    assert.ok(d.basePrice > 0 && d.basePrice % 10000 === 0);
  }
  // cada tipo del catálogo base existe en alguna sucursal
  for (const base of baseSuites) assert.ok(docs.some((d) => d.name === base.name), base.name);
});

test('los índices del modelo: único (suite, slot, date) y (branch, name)', () => {
  const night = SuiteNight.schema.indexes().find(([k]) => k.suite && k.slot && k.date);
  assert.ok(night?.[1].unique, 'falta el índice único por habitación');
  assert.ok(!SuiteNight.schema.indexes().some(([k, o]) => o.unique && k.suite && !k.slot), 'el índice único antiguo (suite,date) no debe seguir');
  const suiteIdx = Suite.schema.indexes();
  assert.ok(suiteIdx.some(([k, o]) => k.branch && k.name && o.unique));
  assert.ok(!suiteIdx.some(([k, o]) => o.unique && Object.keys(k).length === 1 && k.name), 'name ya no puede ser único global');
});

// --- Simulación en memoria de la clave única ---------------------------------
const instalarFalsa = () => {
  const tomadas = new Set();
  const originales = { create: SuiteNight.create, deleteMany: SuiteNight.deleteMany, distinct: SuiteNight.distinct };
  const clave = (d) => `${d.suite}|${d.slot}|${new Date(d.date).getTime()}`;
  SuiteNight.create = async (d) => {
    await new Promise((r) => setImmediate(r)); // permite intercalar solicitudes
    if (tomadas.has(clave(d))) { const e = new Error('E11000'); e.code = 11000; throw e; }
    tomadas.add(clave(d));
    return d;
  };
  SuiteNight.deleteMany = async (f) => { for (const date of f.date.$in) tomadas.delete(clave({ suite: f.suite, slot: f.slot, date })); };
  SuiteNight.distinct = async (_campo, f) => {
    const out = new Set();
    for (const k of tomadas) {
      const [s, slot, t] = k.split('|');
      if (s === String(f.suite) && f.date.$in.some((d) => d.getTime() === Number(t))) out.add(Number(slot));
    }
    return [...out];
  };
  return { tomadas, restaurar: () => Object.assign(SuiteNight, originales) };
};

test('acquireAny reparte reservas en distintas habitaciones y rechaza cuando se llenan', async () => {
  const f = instalarFalsa();
  try {
    const suite = new mongoose.Types.ObjectId();
    const noches = eachNight(toCalendarDate('2030-03-01'), toCalendarDate('2030-03-04'));
    const resultados = await Promise.allSettled(
      Array.from({ length: 6 }, () => SuiteNight.acquireAny(suite, new mongoose.Types.ObjectId(), noches, 3))
    );
    const ok = resultados.filter((r) => r.status === 'fulfilled');
    const fallos = resultados.filter((r) => r.status === 'rejected' && r.reason instanceof NightsConflictError);
    assert.equal(ok.length, 3, 'con 3 habitaciones caben exactamente 3 reservas');
    assert.equal(fallos.length, 3);
    assert.deepEqual(ok.map((r) => r.value.slot).sort(), [1, 2, 3]);
    assert.equal(f.tomadas.size, 3 * noches.length, 'sin noches huérfanas de las rechazadas');
    assert.deepEqual(await SuiteNight.freeSlots(suite, noches, 3), []);
    assert.deepEqual(await SuiteNight.freeSlots(suite, noches, 4), [4], 'si se agrega una habitación, aparece libre');
  } finally {
    f.restaurar();
  }
});

test('una reserva parcialmente solapada usa otra habitación; las adyacentes comparten la misma', async () => {
  const f = instalarFalsa();
  try {
    const suite = new mongoose.Types.ObjectId();
    const a = await SuiteNight.acquireAny(suite, new mongoose.Types.ObjectId(), eachNight(toCalendarDate('2030-04-01'), toCalendarDate('2030-04-05')), 2);
    const b = await SuiteNight.acquireAny(suite, new mongoose.Types.ObjectId(), eachNight(toCalendarDate('2030-04-03'), toCalendarDate('2030-04-07')), 2);
    const c = await SuiteNight.acquireAny(suite, new mongoose.Types.ObjectId(), eachNight(toCalendarDate('2030-04-05'), toCalendarDate('2030-04-08')), 2);
    assert.equal(a.slot, 1);
    assert.equal(b.slot, 2, 'se solapa con a → otra habitación');
    assert.equal(c.slot, 1, 'empieza el día que a se va → reutiliza la habitación 1');
    await assert.rejects(
      SuiteNight.acquireAny(suite, new mongoose.Types.ObjectId(), eachNight(toCalendarDate('2030-04-04'), toCalendarDate('2030-04-06')), 2),
      NightsConflictError
    );
  } finally {
    f.restaurar();
  }
});

test('acquire en un slot fijo conflicta solo con ese slot (extender una reserva)', async () => {
  const f = instalarFalsa();
  try {
    const suite = new mongoose.Types.ObjectId();
    await SuiteNight.acquire(suite, new mongoose.Types.ObjectId(), eachNight(toCalendarDate('2030-05-05'), toCalendarDate('2030-05-06')), 1);
    await SuiteNight.acquire(suite, new mongoose.Types.ObjectId(), eachNight(toCalendarDate('2030-05-05'), toCalendarDate('2030-05-06')), 2);
    await assert.rejects(
      SuiteNight.acquire(suite, new mongoose.Types.ObjectId(), eachNight(toCalendarDate('2030-05-05'), toCalendarDate('2030-05-06')), 1),
      NightsConflictError
    );
  } finally {
    f.restaurar();
  }
});
