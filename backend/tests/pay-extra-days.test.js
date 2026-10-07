// Regresión: pagar los días extra tras modificar fechas.
import test from 'node:test';
import assert from 'node:assert/strict';
import mongoose from 'mongoose';
import { createPaymentService } from '../services/paymentService.js';

const oid = () => new mongoose.Types.ObjectId();
const USER = oid();

const makeEnv = ({ booking, store, charge }) => {
  const Booking = {
    findById: async () => ({ ...booking }),
    findOneAndUpdate: async (_f, u) => ({ ...booking, ...(u.$set || {}), populate: async () => {} })
  };
  const Payment = {
    findOne: async (q) => store.find((p) => String(p.booking) === String(q.booking) &&
      (q.idempotencyKey ? p.idempotencyKey === q.idempotencyKey : q.active ? p.active === true : q.status ? p.status === q.status : true)) || null,
    find: async (q) => store.filter((p) => p.status === q.status),
    updateOne: async ({ _id }, u) => { const p = store.find((x) => x._id === _id); if (p && u.$unset?.active) delete p.active; },
    create: async (doc) => {
      if (doc.active && store.some((p) => p.active === true)) { const e = new Error('dup'); e.code = 11000; throw e; }
      if (store.some((p) => p.idempotencyKey === doc.idempotencyKey)) { const e = new Error('dup'); e.code = 11000; throw e; }
      const p = { _id: oid(), ...doc, save: async () => {} };
      store.push(p);
      return p;
    }
  };
  return createPaymentService({ Booking, Payment, provider: { name: 'sim', charge } });
};

const partialBooking = () => ({
  _id: oid(), user: USER, status: 'confirmed', paymentStatus: 'partial', amountPaid: 500000, totalPrice: 700000
});

test('días extra: el pago original ya aprobado NO bloquea el cobro del saldo', async () => {
  const booking = partialBooking();
  const store = [{ _id: oid(), booking: booking._id, idempotencyKey: 'pago-original-1', status: 'approved', amount: 500000 }];
  let charged;
  const svc = makeEnv({ booking, store, charge: async ({ amount }) => { charged = amount; return { status: 'approved', providerRef: 'R' }; } });
  const r = await svc.pay({ bookingId: booking._id, userId: USER, method: 'card', idempotencyKey: 'pago-extra-0002' });
  assert.equal(r.replay, false);
  assert.equal(charged, 200000);
  assert.equal(r.booking.paymentStatus, 'paid');
  assert.equal(store.length, 2);
});

test('días extra: pago viejo con active:true (datos antiguos) se autorrepara', async () => {
  const booking = partialBooking();
  const store = [{ _id: oid(), booking: booking._id, idempotencyKey: 'pago-original-1', status: 'approved', amount: 500000, active: true }];
  const svc = makeEnv({ booking, store, charge: async () => ({ status: 'approved', providerRef: 'R' }) });
  const r = await svc.pay({ bookingId: booking._id, userId: USER, method: 'card', idempotencyKey: 'pago-extra-0002' });
  assert.equal(r.replay, false, 'no debe tomarse como reintento del pago original');
  assert.equal(r.booking.paymentStatus, 'paid');
  assert.equal(store[0].active, undefined);
});

test('error de pasarela: reintentar con la MISMA clave no devuelve un pago fallido como éxito', async () => {
  const booking = { _id: oid(), user: USER, status: 'pending', paymentStatus: 'pending', totalPrice: 500000, holdExpiresAt: new Date(Date.now() + 600000) };
  const store = [];
  let calls = 0;
  const svc = makeEnv({ booking, store, charge: async () => { if (calls++ === 0) throw new Error('timeout'); return { status: 'approved', providerRef: 'R' }; } });
  await assert.rejects(svc.pay({ bookingId: booking._id, userId: USER, method: 'card', idempotencyKey: 'clave-mismo-0001' }), (e) => e.code === 'GATEWAY_ERROR');
  await assert.rejects(svc.pay({ bookingId: booking._id, userId: USER, method: 'card', idempotencyKey: 'clave-mismo-0001' }), (e) => e.code === 'RETRY_NEW_KEY');
  const r = await svc.pay({ bookingId: booking._id, userId: USER, method: 'card', idempotencyKey: 'clave-nueva-0002' });
  assert.equal(r.replay, false);
  assert.equal(r.booking.paymentStatus, 'paid');
});
