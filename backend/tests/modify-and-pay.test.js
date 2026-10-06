// Test supermega minucioso para el flujo de modificación de fechas y pago de saldo pendiente (partial -> paid)
import test from 'node:test';
import assert from 'node:assert/strict';
import mongoose from 'mongoose';
import { createPaymentService, PaymentError } from '../services/paymentService.js';
import { simulatedProvider } from '../services/payments/simulatedProvider.js';

const oid = () => new mongoose.Types.ObjectId();
const USER = oid();
const KEY = 'intento-modify-0001';

test('flujo completo: reserva pagada -> modificación con aumento de precio (partial) -> pago del saldo extra (paid)', async () => {
  const bookingId = oid();
  
  // 1. Reserva inicial pagada (confirmed + paid)
  let currentBooking = {
    _id: bookingId,
    user: USER,
    status: 'confirmed',
    paymentStatus: 'paid',
    totalPrice: 300000,
    amountPaid: 300000,
    checkIn: new Date('2026-10-13'),
    checkOut: new Date('2026-10-15')
  };

  const calls = { updates: [], charges: [] };
  const store = [];

  const Booking = {
    findById: async (id) => {
      if (String(id) === String(bookingId)) return { ...currentBooking };
      return null;
    },
    findOneAndUpdate: async (filter, update) => {
      calls.updates.push({ filter, update });
      if (update.$set) {
        currentBooking = { ...currentBooking, ...update.$set };
      }
      return { ...currentBooking, populate: async () => {} };
    }
  };

  const Payment = {
    findOne: async (q) =>
      store.find((p) => String(p.booking) === String(q.booking) && p.idempotencyKey === q.idempotencyKey) || null,
    create: async (doc) => {
      const p = { _id: oid(), ...doc, save: async () => {} };
      store.push(p);
      return p;
    }
  };

  const provider = {
    name: 'simulated',
    charge: async (args) => {
      calls.charges.push(args);
      return simulatedProvider.charge(args);
    }
  };

  const service = createPaymentService({ Booking, Payment, provider });

  // 2. Simulamos la modificación de fechas: el precio sube a 400.000 y pasa a partial
  currentBooking.totalPrice = 400000;
  currentBooking.checkOut = new Date('2026-10-17'); // 2 días más
  currentBooking.paymentStatus = 'partial';

  // Verificamos estado antes de pagar el saldo pendiente
  assert.equal(currentBooking.paymentStatus, 'partial');
  assert.equal(currentBooking.totalPrice, 400000);
  assert.equal(currentBooking.amountPaid, 300000);

  // 3. El usuario paga el saldo pendiente ($100.000)
  const { payment, booking, replay } = await service.pay({
    bookingId,
    userId: USER,
    method: 'card',
    idempotencyKey: KEY
  });

  assert.equal(replay, false);
  assert.equal(payment.status, 'approved');
  assert.equal(payment.amount, 100000); // 400.000 - 300.000 = 100.000
  assert.equal(booking.paymentStatus, 'paid');
  assert.equal(booking.amountPaid, 400000);
  assert.equal(booking.status, 'confirmed');

  // Verificamos que el update filter para isPartial no falló y actualizó correctamente
  const lastUpdate = calls.updates[calls.updates.length - 1];
  assert.equal(lastUpdate.update.$set.paymentStatus, 'paid');
  assert.equal(lastUpdate.update.$set.amountPaid, 400000);
});
