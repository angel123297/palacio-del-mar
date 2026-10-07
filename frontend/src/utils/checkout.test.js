import test from 'node:test';
import assert from 'node:assert/strict';
import { groupNights, policyText, newIdempotencyKey, paymentBalance, netPaid } from './checkout.js';

test('groupNights junta noches seguidas de la misma temporada y precio', () => {
  const lines = groupNights([
    { season: 'low', price: 100 }, { season: 'low', price: 100 }, { season: 'peak', price: 150 }, { season: 'low', price: 100 }
  ]);
  assert.deepEqual(lines, [
    { season: 'low', unitPrice: 100, nights: 2, amount: 200 },
    { season: 'peak', unitPrice: 150, nights: 1, amount: 150 },
    { season: 'low', unitPrice: 100, nights: 1, amount: 100 }
  ]);
  assert.deepEqual(groupNights(undefined), []);
});

test('policyText usa la política del servidor', () => {
  assert.match(policyText({ freeDays: 7, feePercent: 10 }), /hasta 7 días.*10 %/);
  assert.equal(policyText(null), '');
});

test('la clave de idempotencia cumple el formato que exige el servidor y es distinta cada vez', () => {
  const a = newIdempotencyKey();
  assert.match(a, /^[A-Za-z0-9_-]{8,80}$/);
  assert.notEqual(a, newIdempotencyKey());
});

test('paymentBalance: saldo neto de devoluciones (el caso que mostraba 50.000 y cobraba 150.000)', () => {
  // pagada 300.000; se quita una experiencia de 100.000 (devuelto) y se agrega otra de 150.000
  const booking = { status: 'confirmed', paymentStatus: 'partial', totalPrice: 350000, amountPaid: 300000, amountRefunded: 100000 };
  assert.deepEqual(paymentBalance(booking), { alreadyPaid: 200000, remaining: 150000 });
});

test('paymentBalance: sin devoluciones el saldo es total - pagado', () => {
  const b = { status: 'confirmed', paymentStatus: 'partial', totalPrice: 400000, amountPaid: 300000 };
  assert.deepEqual(paymentBalance(b), { alreadyPaid: 300000, remaining: 100000 });
});

test('paymentBalance: una reserva pendiente sin pagar se cobra completa', () => {
  assert.deepEqual(paymentBalance({ status: 'pending', paymentStatus: 'pending', totalPrice: 1200000, amountPaid: 0 }), { alreadyPaid: 0, remaining: 1200000 });
  // dañada por el bug antiguo (pending + partial sin dinero): también completa
  assert.deepEqual(
    paymentBalance({ status: 'pending', paymentStatus: 'partial', totalPrice: 1200000, amountPaid: 0, modificationHistory: [{ oldTotalPrice: 1000000 }] }),
    { alreadyPaid: 0, remaining: 1200000 }
  );
});

test('paymentBalance: reserva confirmada antigua sin amountPaid usa el total anterior al cambio', () => {
  const b = { status: 'confirmed', paymentStatus: 'partial', totalPrice: 400000, amountPaid: 0, modificationHistory: [{ oldTotalPrice: 300000 }] };
  assert.deepEqual(paymentBalance(b), { alreadyPaid: 300000, remaining: 100000 });
});

test('paymentBalance: nunca devuelve negativos ni falla con datos vacíos', () => {
  assert.deepEqual(paymentBalance({ paymentStatus: 'partial', status: 'confirmed', totalPrice: 100, amountPaid: 500 }), { alreadyPaid: 500, remaining: 0 });
  assert.deepEqual(paymentBalance(undefined), { alreadyPaid: 0, remaining: 0 });
});

test('netPaid: lo pagado descuenta lo devuelto; sin amountPaid se asume el total', () => {
  assert.equal(netPaid({ totalPrice: 350000, amountPaid: 450000, amountRefunded: 100000 }), 350000);
  assert.equal(netPaid({ totalPrice: 300000, amountPaid: 0 }), 300000);
  assert.equal(netPaid({ totalPrice: 300000, amountPaid: 300000, amountRefunded: 999999 }), 0);
});
