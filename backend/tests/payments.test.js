// Centro de pago simulado: sin base de datos (modelos falsos en memoria).
import test from 'node:test';
import assert from 'node:assert/strict';
import mongoose from 'mongoose';
import { createPaymentService, PaymentError } from '../services/paymentService.js';
import { simulatedProvider } from '../services/payments/simulatedProvider.js';
import { assertPaymentsConfig, PAYMENT_METHODS } from '../services/payments/index.js';
import { calculateCancellation, CANCELLATION_POLICY } from '../utils/pricing.js';

const oid = () => new mongoose.Types.ObjectId();
const USER = oid();
const KEY = 'intento-0001-abcdef';

const makeBooking = (over = {}) => ({
  _id: oid(), user: USER, status: 'pending', paymentStatus: 'pending', totalPrice: 500000,
  holdExpiresAt: new Date(Date.now() + 10 * 60000), guestEmail: 'a@b.co', ...over
});

const harness = ({ booking, claim = true, provider } = {}) => {
  const calls = { charges: [], updates: [], notified: [] };
  const store = [];
  const Booking = {
    findById: async () => ({ ...booking }), // copia: simula una lectura nueva de la base
    findOneAndUpdate: async (filter, update) => {
      calls.updates.push({ filter, update });
      return claim ? { ...booking, ...update.$set, populate: async () => {} } : null;
    }
  };
  const Payment = {
    findOne: async (q) =>
      store.find((p) => String(p.booking) === String(q.booking) &&
        (q.idempotencyKey ? p.idempotencyKey === q.idempotencyKey : q.status ? p.status === q.status : q.active ? p.active === true : true)) || null,
    create: async (doc) => {
      if (store.some((p) => String(p.booking) === String(doc.booking) && p.idempotencyKey === doc.idempotencyKey)) {
        const e = new Error('duplicado'); e.code = 11000; throw e;
      }
      if (doc.active && store.some((p) => String(p.booking) === String(doc.booking) && p.active === true)) {
        const e = new Error('ya hay un pago activo'); e.code = 11000; throw e;
      }
      const p = { _id: oid(), ...doc, save: async () => {} };
      store.push(p);
      return p;
    }
  };
  const spy = provider || {
    name: 'simulated',
    charge: async (args) => { calls.charges.push(args); return simulatedProvider.charge(args); }
  };
  const service = createPaymentService({ Booking, Payment, provider: spy, notify: async (x) => { calls.notified.push(x); } });
  return { service, calls, store };
};

const pay = (service, booking, extra = {}) =>
  service.pay({ bookingId: booking._id, userId: USER, method: 'card', idempotencyKey: KEY, ...extra });

test('simulación: SIEMPRE aprueba, con cualquier método y sin datos de tarjeta', async () => {
  for (const m of PAYMENT_METHODS) {
    const r = await simulatedProvider.charge({ amount: 1000, method: m.id });
    assert.equal(r.status, 'approved');
    assert.match(r.providerRef, /^SIM-[0-9A-F]{10}$/);
  }
  const b = makeBooking();
  const { service, calls } = harness({ booking: b });
  await pay(service, b);
  assert.deepEqual(Object.keys(calls.charges[0]).sort(), ['amount', 'bookingId', 'idempotencyKey', 'method']);
});

test('configuración: simulado no se permite en producción ni hay modos desconocidos', () => {
  assert.equal(assertPaymentsConfig({ NODE_ENV: 'development' }), 'simulated');
  assert.equal(assertPaymentsConfig({}), 'simulated');
  assert.throws(() => assertPaymentsConfig({ NODE_ENV: 'production' }), /NODE_ENV=production/);
  assert.throws(() => assertPaymentsConfig({ PAYMENTS_MODE: 'wompi' }), /no tiene proveedor/);
});

test('pago aprobado: confirma la reserva de forma atómica, quita la retención y emite comprobante', async () => {
  const b = makeBooking();
  const { service, calls } = harness({ booking: b });
  const { payment, booking, replay } = await pay(service, b, { method: 'pse' });

  assert.equal(replay, false);
  assert.equal(payment.status, 'approved');
  assert.equal(payment.amount, 500000);
  assert.match(payment.receiptNumber, /^PM-\d{8}-[0-9A-F]{6}$/);
  assert.equal(booking.status, 'confirmed');
  assert.equal(booking.paymentStatus, 'paid');

  const { filter, update } = calls.updates[0];
  assert.equal(filter.status, 'pending');                    // solo si sigue pendiente
  assert.ok(filter.$or.some((c) => c.holdExpiresAt?.$gt));   // y la retención sigue vigente
  assert.deepEqual(update.$unset, { holdExpiresAt: 1 });
  assert.equal(update.$set.amountPaid, 500000);
  assert.equal(update.$set.paymentMethod, 'pse');
  assert.equal(calls.notified.length, 1);
});

test('idempotencia: el mismo intento repetido (doble clic) cobra y confirma UNA sola vez', async () => {
  const b = makeBooking();
  const { service, calls, store } = harness({ booking: b });
  const first = await pay(service, b);
  const second = await pay(service, b);
  assert.equal(second.replay, true);
  assert.equal(second.payment._id, first.payment._id);
  assert.equal(calls.charges.length, 1);
  assert.equal(calls.updates.length, 1);
  assert.equal(store.length, 1);
});

test('retención vencida: no se cobra nada', async () => {
  const b = makeBooking({ holdExpiresAt: new Date(Date.now() - 1000) });
  const { service, calls } = harness({ booking: b });
  await assert.rejects(pay(service, b), (e) => e instanceof PaymentError && e.status === 409 && e.code === 'HOLD_EXPIRED');
  assert.equal(calls.charges.length, 0);
});

test('si la reserva venció MIENTRAS se pagaba: pago anulado y reserva sin confirmar', async () => {
  const b = makeBooking();
  const { service, store } = harness({ booking: b, claim: false });
  await assert.rejects(pay(service, b), (e) => e.code === 'HOLD_EXPIRED' && e.status === 409);
  assert.equal(store[0].status, 'void');
});

test('reservas ajenas, vencidas o ya pagadas', async () => {
  const mine = makeBooking();
  let h = harness({ booking: mine });
  await assert.rejects(h.service.pay({ bookingId: mine._id, userId: oid(), method: 'card', idempotencyKey: KEY }),
    (e) => e.status === 404);

  const expired = makeBooking({ status: 'expired' });
  h = harness({ booking: expired });
  await assert.rejects(pay(h.service, expired), (e) => e.code === 'HOLD_EXPIRED');

  const cancelled = makeBooking({ status: 'cancelled' });
  h = harness({ booking: cancelled });
  await assert.rejects(pay(h.service, cancelled), (e) => e.code === 'NOT_PAYABLE');

  const paid = makeBooking({ status: 'confirmed', paymentStatus: 'paid' });
  h = harness({ booking: paid });
  await assert.rejects(pay(h.service, paid), (e) => e.code === 'ALREADY_PAID');
  h.store.push({ _id: oid(), booking: paid._id, status: 'approved', idempotencyKey: 'otra-clave-0001' });
  const again = await pay(h.service, paid, { idempotencyKey: 'clave-nueva-0002' });
  assert.equal(again.replay, true);
  assert.equal(h.calls.charges.length, 0);
});

test('entradas inválidas: método y clave de idempotencia', async () => {
  const b = makeBooking();
  const { service, calls } = harness({ booking: b });
  await assert.rejects(pay(service, b, { method: 'bitcoin' }), (e) => e.status === 400 && e.code === 'INVALID_METHOD');
  await assert.rejects(pay(service, b, { idempotencyKey: 'corta' }), (e) => e.status === 400 && e.code === 'INVALID_KEY');
  assert.equal(calls.charges.length, 0);
});

test('camino de rechazo (para la pasarela real): no confirma la reserva', async () => {
  const b = makeBooking();
  const declined = { name: 'fake', charge: async () => ({ status: 'declined', message: 'Fondos insuficientes' }) };
  const { service, calls, store } = harness({ booking: b, provider: declined });
  await assert.rejects(pay(service, b), (e) => e.status === 402 && e.code === 'DECLINED');
  assert.equal(calls.updates.length, 0);
  assert.equal(store[0].status, 'declined');
});

test('la política de cancelación sale de una sola constante', () => {
  const booking = { paymentStatus: 'paid', totalPrice: 1000000, amountPaid: 1000000 };
  assert.deepEqual(CANCELLATION_POLICY, { freeDays: 7, feePercent: 10 });
  assert.equal(calculateCancellation(booking, CANCELLATION_POLICY.freeDays).cancellationFee, 0);
  assert.equal(calculateCancellation(booking, CANCELLATION_POLICY.freeDays - 1).cancellationFee, 100000);
});

test('dos intentos con claves DISTINTAS: el segundo no cobra (un solo pago activo por reserva)', async () => {
  const b = makeBooking();
  const { service, calls, store } = harness({ booking: b });
  // un primer intento quedó "en proceso" (otra pestaña, otro dispositivo)
  store.push({ _id: oid(), booking: b._id, idempotencyKey: 'otra-clave-0001', status: 'processing', active: true });
  await assert.rejects(() => pay(service, b), (e) => e instanceof PaymentError && e.code === 'PAYMENT_IN_PROGRESS');
  assert.equal(calls.charges.length, 0, 'no se cobró');
});

test('un pago rechazado libera el cupo: se puede reintentar', async () => {
  const b = makeBooking();
  const decline = { name: 'simulated', charge: async () => ({ status: 'declined', message: 'no' }) };
  const { service, store } = harness({ booking: b, provider: decline });
  await assert.rejects(() => pay(service, b), (e) => e.code === 'DECLINED');
  assert.notEqual(store[0].active, true);
});

test('si el total cambió mientras se pagaba: no se confirma y se avisa del nuevo total', async () => {
  const b = makeBooking();
  const mutating = { name: 'simulated', charge: async (a) => { b.totalPrice = 900000; return simulatedProvider.charge(a); } };
  const { service, calls, store } = harness({ booking: b, claim: false, provider: mutating });
  await assert.rejects(() => pay(service, b), (e) => e.code === 'PRICE_CHANGED');
  assert.equal(store[0].status, 'void');
  assert.equal(store[0].active, undefined);
  assert.equal(calls.updates.length, 1);
});

test('excepción en provider.charge marca el pago como declined y libera el cupo', async () => {
  const b = makeBooking();
  const failing = { name: 'simulated', charge: async () => { throw new Error('Timeout de red'); } };
  const { service, store } = harness({ booking: b, provider: failing });
  await assert.rejects(() => pay(service, b), (e) => e.status === 502 && e.code === 'GATEWAY_ERROR');
  assert.equal(store[0].status, 'declined');
  assert.equal(store[0].active, undefined);
});

test('la confirmación atómica exige el mismo total que se cobró', async () => {
  const b = makeBooking();
  const { service, calls } = harness({ booking: b });
  await pay(service, b);
  assert.equal(calls.updates[0].filter.totalPrice, 500000);
});
