// Paso 2: retención, vencimiento y límites. Sin base de datos (se sustituyen
// las consultas por versiones en memoria).
import test from 'node:test';
import assert from 'node:assert/strict';
import mongoose from 'mongoose';
import Booking, { BOOKING_STATUS } from '../models/Booking.js';
import SuiteNight from '../models/SuiteNight.js';
import { expireStaleBookings } from '../services/bookingExpiry.js';
import { createRateLimiter } from '../middleware/rateLimit.js';
import { holdMinutes, maxPendingPerUser, holdDeadline } from '../utils/bookingRules.js';

const hook = (needle) =>
  Booking.schema.s.hooks._pres.get('save').map((h) => h.fn).find((fn) => String(fn).includes(needle));

test('reglas: valores por defecto y variables inválidas se ignoran', () => {
  delete process.env.HOLD_MINUTES; delete process.env.MAX_PENDING_BOOKINGS;
  assert.equal(holdMinutes(), 30);
  assert.equal(maxPendingPerUser(), 3);
  process.env.HOLD_MINUTES = '45'; assert.equal(holdMinutes(), 45);
  process.env.HOLD_MINUTES = 'abc'; assert.equal(holdMinutes(), 30);
  process.env.HOLD_MINUTES = '-5'; assert.equal(holdMinutes(), 30);
  delete process.env.HOLD_MINUTES;
  const t = new Date('2030-01-01T10:00:00Z');
  assert.equal(holdDeadline(t).toISOString(), '2030-01-01T10:30:00.000Z');
});

test('estado "expired": solo se llega desde pendiente y es terminal', async () => {
  const hookTransiciones = hook('Transición de estado inválida');
  const probar = async (desde, hacia) => {
    const b = new Booking({});
    b.isNew = false;
    b._originalStatus = desde;
    b.status = hacia;
    b.isModified = (c) => c === 'status';
    let error;
    await hookTransiciones.call(b, (e) => { error = e; });
    return error;
  };
  assert.equal(await probar('pending', 'expired'), undefined);
  assert.ok(await probar('expired', 'confirmed'));
  assert.ok(await probar('confirmed', 'expired'));
  assert.ok(await probar('cancelled', 'expired'));
  assert.equal(new Booking({ status: 'expired' }).statusLabel, 'Vencida');
  assert.ok(!Booking.schema.path('status').enumValues.every((s) => s !== 'expired'));
});

test('una reserva pendiente nueva nace con hora de vencimiento; confirmar la quita', async () => {
  const h = hook('holdDeadline');
  assert.ok(h, 'no se encontró el hook de retención');
  const antes = Date.now();
  const nueva = new Booking({});
  await h.call(nueva, () => {});
  const ms = nueva.holdExpiresAt.getTime() - antes;
  assert.ok(ms >= 29.9 * 60000 && ms <= 30.1 * 60000, `retención ${ms} ms`);

  const confirmada = new Booking({ status: 'confirmed' });
  confirmada.isNew = false;
  confirmada.holdExpiresAt = new Date();
  confirmada.isModified = (c) => c === 'status';
  await h.call(confirmada, () => {});
  assert.equal(confirmada.holdExpiresAt, undefined);

  const vencida = new Booking({ status: 'expired' });
  vencida.isNew = false;
  vencida.holdExpiresAt = new Date('2030-01-01');
  vencida.isModified = (c) => c === 'status';
  await h.call(vencida, () => {});
  assert.ok(vencida.holdExpiresAt, 'expired conserva la hora como registro');
});

test('el barrendero vence solo lo vencido, libera noches y no toca lo ya pagado', async () => {
  const orig = { find: Booking.find, fou: Booking.findOneAndUpdate, release: SuiteNight.release };
  const id = () => new mongoose.Types.ObjectId();
  const [a, b] = [id(), id()];
  const liberadas = [];
  const filtrosFind = [];
  try {
    Booking.find = (f) => {
      filtrosFind.push(f);
      return { select: () => ({ limit: () => ({ lean: async () => [{ _id: a }, { _id: b }] }) }) };
    };
    // b "ya fue pagado" entre la búsqueda y el reclamo: el reclamo atómico no lo encuentra
    Booking.findOneAndUpdate = (f, upd) => ({
      populate: async () => (String(f._id) === String(a) ? { _id: a, guestEmail: null, status: upd.$set.status } : null)
    });
    SuiteNight.release = async (x) => { liberadas.push(String(x)); };

    const ahora = new Date('2030-06-01T12:00:00Z');
    const n = await expireStaleBookings(ahora);
    assert.equal(n, 1);
    assert.deepEqual(liberadas, [String(a)]);
    assert.equal(filtrosFind[0].status, 'pending');
    assert.deepEqual(filtrosFind[0].paymentStatus, { $in: ['pending', 'failed'] }, 'nunca vence una reserva con dinero recibido');
    assert.deepEqual(filtrosFind[0].holdExpiresAt, { $lte: ahora });
  } finally {
    Booking.find = orig.find; Booking.findOneAndUpdate = orig.fou; SuiteNight.release = orig.release;
  }
});

test('si liberar las noches falla, no se rompe el barrido (fallo seguro)', async () => {
  const orig = { find: Booking.find, fou: Booking.findOneAndUpdate, release: SuiteNight.release, err: console.error };
  const a = new mongoose.Types.ObjectId();
  try {
    Booking.find = () => ({ select: () => ({ limit: () => ({ lean: async () => [{ _id: a }] }) }) });
    Booking.findOneAndUpdate = () => ({ populate: async () => ({ _id: a }) });
    SuiteNight.release = async () => { throw new Error('mongo caído'); };
    console.error = () => {};
    assert.equal(await expireStaleBookings(new Date()), 1);
  } finally {
    Booking.find = orig.find; Booking.findOneAndUpdate = orig.fou; SuiteNight.release = orig.release; console.error = orig.err;
  }
});

const llamar = (mw, req) => new Promise((resolve) => {
  const res = {
    headers: {}, code: 200,
    set(k, v) { this.headers[k] = v; return this; },
    status(c) { this.code = c; return this; },
    json(b) { resolve({ code: this.code, body: b, headers: this.headers }); }
  };
  mw(req, res, () => resolve({ code: 200 }));
});

test('límite de peticiones: por usuario, con Retry-After', async () => {
  const mw = createRateLimiter({ windowMs: 60000, max: 3 });
  const ana = { user: { id: 'ana' }, ip: '1.1.1.1' };
  const luis = { user: { id: 'luis' }, ip: '1.1.1.1' };
  for (let i = 0; i < 3; i++) assert.equal((await llamar(mw, ana)).code, 200);
  const bloqueada = await llamar(mw, ana);
  assert.equal(bloqueada.code, 429);
  assert.ok(Number(bloqueada.headers['Retry-After']) >= 1);
  assert.equal((await llamar(mw, luis)).code, 200, 'otro usuario con la misma IP no se ve afectado');
  mw.reset();
  assert.equal((await llamar(mw, ana)).code, 200);
});
