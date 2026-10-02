import test from 'node:test';
import assert from 'node:assert/strict';
import { toCalendarDate, calculateNights, eachNight, todayCalendarDate } from '../utils/dates.js';
import { calculateCancellation, getCollectedAmount } from '../utils/pricing.js';

test('fechas: YYYY-MM-DD es medianoche UTC y las noches son exactas', () => {
  const ci = toCalendarDate('2026-03-07');
  const co = toCalendarDate('2026-03-10'); // incluye cambio de horario en EE.UU.
  assert.equal(ci.toISOString(), '2026-03-07T00:00:00.000Z');
  assert.equal(calculateNights(ci, co), 3);
  assert.deepEqual(eachNight(ci, co).map((d) => d.toISOString().slice(0, 10)), ['2026-03-07', '2026-03-08', '2026-03-09']);
});

test('fechas: normalizar es idempotente y rechaza fechas imposibles', () => {
  const d = toCalendarDate('2026-12-31');
  assert.equal(toCalendarDate(d).getTime(), d.getTime());
  assert.equal(toCalendarDate('2026-02-31'), null);
  assert.equal(toCalendarDate('basura'), null);
});

test('fechas: un instante cerca de medianoche usa el día del hotel (Bogotá, UTC-5)', () => {
  // 2026-06-10 03:30 UTC = 2026-06-09 22:30 en Bogotá
  assert.equal(toCalendarDate('2026-06-10T03:30:00Z').toISOString().slice(0, 10), '2026-06-09');
  assert.equal(todayCalendarDate(new Date('2026-06-10T03:30:00Z')).toISOString().slice(0, 10), '2026-06-09');
});

test('reembolso: reserva sin pagar no genera reembolso', () => {
  const r = calculateCancellation({ totalPrice: 1000, paymentStatus: 'pending' }, 30);
  assert.equal(r.refundAmount, 0);
  assert.equal(r.refundStatus, 'none');
});

test('reembolso: pagada con más de 7 días devuelve todo', () => {
  const r = calculateCancellation({ totalPrice: 1000, paymentStatus: 'paid', amountPaid: 1000 }, 20);
  assert.equal(r.refundAmount, 1000);
  assert.equal(r.cancellationFee, 0);
});

test('reembolso: pagada con menos de 7 días retiene 10%', () => {
  const r = calculateCancellation({ totalPrice: 1000, paymentStatus: 'paid', amountPaid: 1000 }, 3);
  assert.equal(r.cancellationFee, 100);
  assert.equal(r.refundAmount, 900);
});

test('reembolso: pago parcial nunca devuelve más de lo cobrado', () => {
  const r = calculateCancellation({ totalPrice: 1000, paymentStatus: 'partial', amountPaid: 50 }, 3);
  assert.equal(r.cancellationFee, 50); // penalización limitada a lo cobrado
  assert.equal(r.refundAmount, 0);
});

test('reembolso: compatibilidad con reservas antiguas marcadas paid sin amountPaid', () => {
  assert.equal(getCollectedAmount({ totalPrice: 800, paymentStatus: 'paid', amountPaid: 0 }), 800);
});

test('reembolso: idempotente — lo ya reembolsado se descuenta', () => {
  const r = calculateCancellation({ totalPrice: 1000, paymentStatus: 'paid', amountPaid: 1000, amountRefunded: 1000 }, 30);
  assert.equal(r.refundAmount, 0);
});
