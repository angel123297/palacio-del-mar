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

import { markPaymentsRefunded } from '../controllers/bookingController.js';

test('markPaymentsRefunded ACUMULA lo devuelto: una cancelación no borra las devoluciones parciales previas', async () => {
  // Simula el documento de pago: 100.000 ya devueltos por quitar una experiencia
  const doc = { status: 'approved', refundedAmount: 100000, active: true };
  const Payment = {
    updateMany: async (_filter, update) => {
      if (update.$set) Object.assign(doc, update.$set);
      if (update.$inc) for (const [k, v] of Object.entries(update.$inc)) doc[k] = (doc[k] || 0) + v;
      if (update.$unset) for (const k of Object.keys(update.$unset)) delete doc[k];
    }
  };
  await markPaymentsRefunded('b1', 200000, Payment); // la cancelación devuelve 200.000 más
  assert.equal(doc.refundedAmount, 300000);
  assert.equal(doc.status, 'refunded');
  assert.equal(doc.active, undefined, 'libera el índice de pago activo');
});

test('markPaymentsRefunded sobre un pago sin devoluciones previas deja solo lo devuelto ahora', async () => {
  const doc = { status: 'approved', active: true }; // refundedAmount ausente
  const Payment = { updateMany: async (_f, u) => { Object.assign(doc, u.$set); for (const [k, v] of Object.entries(u.$inc)) doc[k] = (doc[k] || 0) + v; } };
  await markPaymentsRefunded('b1', 50000, Payment);
  assert.equal(doc.refundedAmount, 50000);
});
