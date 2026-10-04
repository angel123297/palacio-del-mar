// Pago contra Mongo REAL. Se salta sin TEST_MONGODB_URI.
import test from 'node:test';
import assert from 'node:assert/strict';
import mongoose from 'mongoose';

const uri = process.env.TEST_MONGODB_URI;
const opts = { skip: !uri && 'defina TEST_MONGODB_URI' };

const setup = async () => {
  const { default: Booking } = await import('../models/Booking.js');
  const { default: Payment } = await import('../models/Payment.js');
  const { createPaymentService } = await import('../services/paymentService.js');
  const { simulatedProvider } = await import('../services/payments/simulatedProvider.js');
  const { expireStaleBookings } = await import('../services/bookingExpiry.js');
  await mongoose.connect(uri);
  await Payment.init();
  const service = createPaymentService({ Booking, Payment, provider: simulatedProvider });
  return { Booking, Payment, service, expireStaleBookings };
};

const insertPending = async (Booking, holdOffsetMs) => {
  const _id = new mongoose.Types.ObjectId();
  const user = new mongoose.Types.ObjectId();
  await Booking.collection.insertOne({
    _id, user, suite: new mongoose.Types.ObjectId(), status: 'pending', paymentStatus: 'pending',
    totalPrice: 750000, holdExpiresAt: new Date(Date.now() + holdOffsetMs)
  });
  return { _id, user };
};

test('pago real: confirma, quita la retención y el barrido de vencimiento ya no la toca', opts, async () => {
  const { Booking, Payment, service, expireStaleBookings } = await setup();
  const { _id, user } = await insertPending(Booking, 10 * 60000);
  try {
    const { payment } = await service.pay({ bookingId: _id, userId: user, method: 'nequi', idempotencyKey: 'clave-real-0001' });
    assert.equal(payment.status, 'approved');
    const doc = await Booking.collection.findOne({ _id });
    assert.equal(doc.status, 'confirmed');
    assert.equal(doc.paymentStatus, 'paid');
    assert.equal(doc.amountPaid, 750000);
    assert.equal(doc.holdExpiresAt, undefined);
    assert.equal(await expireStaleBookings(new Date(Date.now() + 3600000)), 0, 'pagada: no vence');
  } finally {
    await Booking.collection.deleteOne({ _id });
    await Payment.deleteMany({ booking: _id });
    await mongoose.disconnect();
  }
});

test('pago real: 5 envíos simultáneos con la misma clave crean UN solo pago', opts, async () => {
  const { Booking, Payment, service } = await setup();
  const { _id, user } = await insertPending(Booking, 10 * 60000);
  try {
    const results = await Promise.allSettled(
      Array.from({ length: 5 }, () => service.pay({ bookingId: _id, userId: user, method: 'card', idempotencyKey: 'clave-real-0002' }))
    );
    assert.equal(results.filter((r) => r.status === 'fulfilled').length, 5, 'ninguna falla: repiten el resultado');
    assert.equal(await Payment.countDocuments({ booking: _id }), 1);
    assert.equal(await Payment.countDocuments({ booking: _id, status: 'approved' }), 1);
    assert.equal((await Booking.collection.findOne({ _id })).status, 'confirmed');
  } finally {
    await Booking.collection.deleteOne({ _id });
    await Payment.deleteMany({ booking: _id });
    await mongoose.disconnect();
  }
});

test('pago real: si el barrido la venció primero, no se puede pagar', opts, async () => {
  const { Booking, Payment, service, expireStaleBookings } = await setup();
  const { _id, user } = await insertPending(Booking, -1000);
  try {
    assert.equal(await expireStaleBookings(new Date()), 1);
    await assert.rejects(
      service.pay({ bookingId: _id, userId: user, method: 'card', idempotencyKey: 'clave-real-0003' }),
      (e) => e.code === 'HOLD_EXPIRED'
    );
    assert.equal((await Booking.collection.findOne({ _id })).status, 'expired');
    assert.equal(await Payment.countDocuments({ booking: _id }), 0);
  } finally {
    await Booking.collection.deleteOne({ _id });
    await Payment.deleteMany({ booking: _id });
    await mongoose.disconnect();
  }
});
