import test from 'node:test';
import assert from 'node:assert/strict';
import { runRepairPartialPending } from '../migrations/repairPartialPending.js';

// Colección en memoria con el subconjunto de operadores que usa la migración.
const makeEnv = (rows, approvedFor = []) => {
  const matches = (r, f) => {
    if (f._id?.$in && !f._id.$in.some((i) => String(i) === String(r._id))) return false;
    if (f.status && r.status !== f.status) return false;
    if (f.paymentStatus && r.paymentStatus !== f.paymentStatus) return false;
    if (f.$or) {
      const noMoney = r.amountPaid === undefined || r.amountPaid === null || r.amountPaid === 0;
      if (!noMoney) return false;
    }
    return true;
  };
  const Booking = {
    collection: {
      find: (f) => ({ toArray: async () => rows.filter((r) => matches(r, f)).map((r) => ({ _id: r._id })) }),
      updateMany: async (f, u) => {
        let n = 0;
        for (const r of rows) if (matches(r, f)) { Object.assign(r, u.$set); n++; }
        return { modifiedCount: n };
      }
    }
  };
  const Payment = { distinct: async (_k, q) => approvedFor.filter((id) => q.booking.$in.includes(id)) };
  return { Booking, Payment };
};

const quiet = (fn) => async () => { const l = console.log; console.log = () => {}; try { await fn(); } finally { console.log = l; } };

test('repara solo pending+partial sin dinero y sin pagos aprobados', quiet(async () => {
  const rows = [
    { _id: 'a', status: 'pending', paymentStatus: 'partial', amountPaid: 0 },          // dañada -> se repara
    { _id: 'b', status: 'pending', paymentStatus: 'partial' },                          // sin campo -> se repara
    { _id: 'c', status: 'confirmed', paymentStatus: 'partial', amountPaid: 300000 },    // legítima -> intacta
    { _id: 'd', status: 'confirmed', paymentStatus: 'partial', amountPaid: 0 },         // confirmada -> intacta
    { _id: 'e', status: 'pending', paymentStatus: 'partial', amountPaid: 0 },           // tiene pago aprobado -> intacta
    { _id: 'f', status: 'pending', paymentStatus: 'pending', amountPaid: 0 }            // ya correcta -> intacta
  ];
  const env = makeEnv(rows, ['e']);
  assert.equal(await runRepairPartialPending(env), 2);
  const by = Object.fromEntries(rows.map((r) => [r._id, r.paymentStatus]));
  assert.deepEqual(by, { a: 'pending', b: 'pending', c: 'partial', d: 'partial', e: 'partial', f: 'pending' });
}));

test('es idempotente: la segunda ejecución no cambia nada', quiet(async () => {
  const rows = [{ _id: 'a', status: 'pending', paymentStatus: 'partial', amountPaid: 0 }];
  const env = makeEnv(rows);
  assert.equal(await runRepairPartialPending(env), 1);
  assert.equal(await runRepairPartialPending(env), 0);
}));

test('sin candidatas no consulta pagos ni modifica', quiet(async () => {
  const env = makeEnv([{ _id: 'x', status: 'confirmed', paymentStatus: 'paid', amountPaid: 100 }]);
  env.Payment.distinct = async () => { throw new Error('no debería consultarse'); };
  assert.equal(await runRepairPartialPending(env), 0);
}));
