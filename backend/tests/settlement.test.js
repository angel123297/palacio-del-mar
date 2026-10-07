// Cobrar o devolver la diferencia cuando cambia el total de una reserva ya pagada
// (días extra, experiencias agregadas o quitadas).
import test from 'node:test';
import assert from 'node:assert/strict';
import { settleTotalChange } from '../utils/settlement.js';
import { getCollectedAmount } from '../utils/pricing.js';

const paid = (over = {}) => ({ status: 'confirmed', paymentStatus: 'paid', totalPrice: 673000, amountPaid: 673000, amountRefunded: 0, ...over });

test('experiencia agregada a una reserva PAGADA: queda saldo por pagar', () => {
  const b = paid();
  const r = settleTotalChange(b, 1021000);
  assert.equal(b.paymentStatus, 'partial');
  assert.equal(r.balanceDue, 348000);
  assert.equal(b.totalPrice, 1021000);
  assert.equal(b.amountPaid, 673000, 'lo recibido no cambia');
});

test('reserva pendiente sin pagar: solo cambia el total (no queda "partial")', () => {
  const b = { status: 'pending', paymentStatus: 'pending', totalPrice: 500000, amountPaid: 0 };
  const r = settleTotalChange(b, 700000);
  assert.equal(b.paymentStatus, 'pending');
  assert.equal(b.totalPrice, 700000);
  assert.deepEqual(r, { refundAmount: 0, balanceDue: 0 });
});

test('quitar una experiencia de una reserva pagada: se devuelve la diferencia, sigue pagada', () => {
  const b = paid({ totalPrice: 1021000, amountPaid: 1021000 });
  const r = settleTotalChange(b, 673000);
  assert.equal(r.refundAmount, 348000);
  assert.equal(b.paymentStatus, 'paid');
  assert.equal(b.amountPaid, 1021000, 'amountPaid es bruto: no se recorta');
  assert.equal(b.amountRefunded, 348000);
  assert.equal(getCollectedAmount(b), 673000, 'neto en poder del hotel = nuevo total (no se descuenta dos veces)');
});

test('devolución y luego aumento: el saldo se calcula sobre lo neto', () => {
  const b = paid({ totalPrice: 1021000, amountPaid: 1021000 });
  settleTotalChange(b, 673000);                 // devuelve 348.000
  const r = settleTotalChange(b, 800000);       // sube de nuevo
  assert.equal(b.paymentStatus, 'partial');
  assert.equal(r.balanceDue, 127000);
});

test('reserva antigua "paid" sin amountPaid: se normaliza y cobra la diferencia', () => {
  const b = { paymentStatus: 'paid', totalPrice: 500000, amountPaid: 0, amountRefunded: 0 };
  const r = settleTotalChange(b, 700000);
  assert.equal(b.amountPaid, 500000);
  assert.equal(r.balanceDue, 200000);
});

test('con saldo pendiente que baja por debajo de lo cobrado: devuelve y queda pagada', () => {
  const b = { paymentStatus: 'partial', totalPrice: 900000, amountPaid: 673000, amountRefunded: 0 };
  const r = settleTotalChange(b, 600000);
  assert.equal(r.refundAmount, 73000);
  assert.equal(b.paymentStatus, 'paid');
});

test('mismo total: queda pagada, sin saldo ni reembolso', () => {
  const b = paid();
  assert.deepEqual(settleTotalChange(b, 673000), { refundAmount: 0, balanceDue: 0 });
  assert.equal(b.paymentStatus, 'paid');
});
