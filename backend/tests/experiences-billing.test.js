// Regresión: agregar/quitar experiencias en una reserva existente debe cobrar/devolver.
import test from 'node:test';
import assert from 'node:assert/strict';
import mongoose from 'mongoose';
import Booking from '../models/Booking.js';
import Experience from '../models/Experience.js';
import { addExperienceToBooking, removeExperienceFromBooking } from '../controllers/bookingController.js';
import { createPaymentService } from '../services/paymentService.js';

const PRICES = { palenque: 389000, muralla: 184000, islas: 348000 };
Experience.findById = async (id) => (PRICES[id] ? { _id: id, name: id, price: PRICES[id], available: true } : null);
Experience.find = async (q) => (q._id.$in || []).map((id) => ({ _id: id, price: PRICES[id] }));

const makeBooking = (over = {}) => {
  const b = {
    _id: 'b1', user: { toString: () => 'u1' }, isModifiable: true,
    status: 'confirmed', paymentStatus: 'paid',
    checkIn: new Date('2026-10-23T00:00:00Z'), checkOut: new Date('2026-10-24T00:00:00Z'),
    subtotal: 100000, totalPrice: 673000, amountPaid: 673000, amountRefunded: 0,
    experiences: ['palenque', 'muralla'],
    save: async () => {}, populate: async () => {},
    ...over
  };
  Booking.findById = async () => b;
  return b;
};
const call = async (fn, req) => {
  let status = 200; let payload;
  const res = { status(c) { status = c; return this; }, json(p) { payload = p; return this; } };
  await fn(req, res);
  return { status, payload };
};
const asUser = { id: 'u1', role: 'user' };

test('agregar una experiencia a una reserva PAGADA genera saldo por pagar (antes quedaba "pagado")', async () => {
  const b = makeBooking();
  const { status, payload } = await call(addExperienceToBooking, { params: { id: 'b1' }, body: { experienceId: 'islas' }, user: asUser });
  assert.equal(status, 200);
  assert.equal(b.totalPrice, 1021000);
  assert.equal(b.paymentStatus, 'partial');
  assert.equal(payload.data.balanceDue, 348000);
  assert.equal(payload.data.needsPayment, true);
});

test('agregar una experiencia a una reserva PENDIENTE solo sube el total', async () => {
  const b = makeBooking({ status: 'pending', paymentStatus: 'pending', amountPaid: 0, totalPrice: 500000 });
  const { payload } = await call(addExperienceToBooking, { params: { id: 'b1' }, body: { experienceId: 'islas' }, user: asUser });
  assert.equal(b.paymentStatus, 'pending');
  assert.equal(payload.data.balanceDue, 0);
  assert.equal(b.totalPrice, 100000 + 389000 + 184000 + 348000);
});

test('quitar una experiencia de una reserva pagada registra el reembolso', async () => {
  const b = makeBooking({ experiences: ['palenque', 'muralla', 'islas'], totalPrice: 1021000, amountPaid: 1021000 });
  const { payload } = await call(removeExperienceFromBooking, { params: { id: 'b1', experienceId: 'islas' }, user: asUser });
  assert.equal(b.totalPrice, 673000);
  assert.equal(b.paymentStatus, 'paid');
  assert.equal(b.amountRefunded, 348000);
  assert.equal(payload.settlement.refundAmount, 348000);
});

// ---- pay(): el saldo se calcula sobre lo neto ----
const oid = () => new mongoose.Types.ObjectId();
const payHarness = (booking) => {
  let update;
  const Bk = { findById: async () => ({ ...booking }), findOneAndUpdate: async (_f, u) => { update = u; return { ...booking, ...u.$set, populate: async () => {} }; } };
  const store = [];
  const Pay = { findOne: async () => null, find: async () => [], updateOne: async () => {}, create: async (d) => { const p = { _id: oid(), ...d, save: async () => {} }; store.push(p); return p; } };
  let charged;
  const svc = createPaymentService({ Booking: Bk, Payment: Pay, provider: { name: 's', charge: async ({ amount }) => { charged = amount; return { status: 'approved', providerRef: 'R' }; } } });
  return { svc, get charged() { return charged; }, get update() { return update; } };
};
const USER = oid();

test('pay(): tras un reembolso por cambio de precio, el saldo usa lo NETO cobrado', async () => {
  // pagó 700.000, se devolvieron 200.000 (neto 500.000) y el total ahora es 600.000
  const booking = { _id: oid(), user: USER, status: 'confirmed', paymentStatus: 'partial', amountPaid: 700000, amountRefunded: 200000, totalPrice: 600000 };
  const h = payHarness(booking);
  await h.svc.pay({ bookingId: booking._id, userId: USER, method: 'card', idempotencyKey: 'clave-neto-000001' });
  assert.equal(h.charged, 100000);
  assert.equal(h.update.$set.amountPaid, 800000, 'bruto = total + devuelto, para que el neto sea el total');
});

test('pay(): una reserva pendiente que nunca se pagó se cobra COMPLETA aunque diga "partial"', async () => {
  const booking = { _id: oid(), user: USER, status: 'pending', paymentStatus: 'partial', amountPaid: 0, totalPrice: 700000,
    holdExpiresAt: new Date(Date.now() + 600000), modificationHistory: [{ oldTotalPrice: 500000 }] };
  const h = payHarness(booking);
  await h.svc.pay({ bookingId: booking._id, userId: USER, method: 'card', idempotencyKey: 'clave-pend-0000001' });
  assert.equal(h.charged, 700000);
});
