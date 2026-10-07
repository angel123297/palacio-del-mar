import test from 'node:test';
import assert from 'node:assert/strict';
import { recordPartialRefund } from '../controllers/bookingController.js';

test('suma lo devuelto al pago aprobado más reciente SIN cambiar su estado', async () => {
  const calls = [];
  const Payment = { findOneAndUpdate: async (...a) => { calls.push(a); } };
  await recordPartialRefund('b1', 100000, Payment);
  const [filter, update, options] = calls[0];
  assert.deepEqual(filter, { booking: 'b1', status: 'approved' });
  assert.equal(update.$inc.refundedAmount, 100000);
  assert.ok(update.$set.refundedAt instanceof Date);
  assert.equal(update.$set.status, undefined, 'no debe marcar el pago como refunded');
  assert.equal(update.$unset, undefined, 'no debe liberar el índice de pago activo');
  assert.deepEqual(options, { sort: { approvedAt: -1 } });
});

test('un fallo al registrar no rompe la operación', async () => {
  const Payment = { findOneAndUpdate: async () => { throw new Error('mongo caído'); } };
  const e = console.error; console.error = () => {};
  try { await assert.doesNotReject(() => recordPartialRefund('b1', 5, Payment)); } finally { console.error = e; }
});
