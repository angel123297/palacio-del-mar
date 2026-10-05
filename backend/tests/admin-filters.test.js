import test from 'node:test';
import assert from 'node:assert/strict';
import { bookingViewFilter, bookingViewSort, escapeRegex, promotionState, validatePromotionInput } from '../utils/adminFilters.js';
import { toCalendarDate } from '../utils/dates.js';

const today = toCalendarDate('2026-10-10');

test('llegadas: check-in hoy y reserva viva', () => {
  const f = bookingViewFilter('arrivals', today);
  assert.deepEqual(f.status, { $in: ['pending', 'confirmed'] });
  assert.equal(f.checkIn.$gte.toISOString(), '2026-10-10T00:00:00.000Z');
  assert.equal(f.checkIn.$lt.toISOString(), '2026-10-11T00:00:00.000Z');
});

test('en el hotel: confirmadas que ya entraron y aún no salen', () => {
  const f = bookingViewFilter('inhouse', today);
  assert.equal(f.status, 'confirmed');
  assert.equal(f.checkIn.$lte.getTime(), today.getTime());
  assert.equal(f.checkOut.$gt.getTime(), today.getTime());
});

test('próximas empiezan desde mañana; vista desconocida = todas', () => {
  assert.equal(bookingViewFilter('upcoming', today).checkIn.$gte.toISOString(), '2026-10-11T00:00:00.000Z');
  assert.deepEqual(bookingViewFilter('loquesea', today), {});
  assert.deepEqual(bookingViewSort('arrivals'), { checkIn: 1 });
  assert.deepEqual(bookingViewSort('all'), { createdAt: -1 });
});

test('escapeRegex neutraliza caracteres especiales', () => {
  assert.equal(escapeRegex('a.b*c(d)'), 'a\\.b\\*c\\(d\\)');
  assert.ok(new RegExp(escapeRegex('juan+1@x.co')).test('juan+1@x.co'));
});

test('estado de una promoción', () => {
  const p = (active, s, e) => ({ active, startDate: toCalendarDate(s), endDate: toCalendarDate(e) });
  assert.equal(promotionState(p(false, '2026-10-01', '2026-10-31'), today), 'paused');
  assert.equal(promotionState(p(true, '2026-09-01', '2026-10-09'), today), 'past');
  assert.equal(promotionState(p(true, '2026-10-11', '2026-10-20'), today), 'upcoming');
  assert.equal(promotionState(p(true, '2026-10-10', '2026-10-10'), today), 'current');
});

test('validar promoción nueva: válida y con errores', () => {
  const ok = validatePromotionInput({ title: '  Brisa  ', discountPercent: '15', startDate: '2026-11-01', endDate: '2026-11-30' });
  assert.deepEqual(ok.errors, []);
  assert.equal(ok.data.title, 'Brisa');
  assert.equal(ok.data.discountPercent, 15);

  assert.ok(validatePromotionInput({ title: 'ab', discountPercent: 15, startDate: '2026-11-01', endDate: '2026-11-30' }).errors.length);
  assert.ok(validatePromotionInput({ title: 'Brisa', discountPercent: 61, startDate: '2026-11-01', endDate: '2026-11-30' }).errors.length);
  assert.ok(validatePromotionInput({ title: 'Brisa', discountPercent: 10.5, startDate: '2026-11-01', endDate: '2026-11-30' }).errors.length);
  assert.ok(validatePromotionInput({ title: 'Brisa', discountPercent: 10, startDate: '2026-11-30', endDate: '2026-11-01' }).errors.length);
  assert.ok(validatePromotionInput({ title: 'Brisa', discountPercent: 10, startDate: '2026-02-31', endDate: '2026-03-05' }).errors.length);
  assert.ok(validatePromotionInput({ title: 'Brisa', discountPercent: 10, startDate: '2026-01-01', endDate: '2027-12-31' }).errors.length);
});

test('editar: solo se manda lo que cambia', () => {
  const existing = { title: 'Brisa', discountPercent: 10, startDate: toCalendarDate('2026-11-01'), endDate: toCalendarDate('2026-11-30') };
  const r = validatePromotionInput({ active: false }, existing);
  assert.deepEqual(r.errors, []);
  assert.equal(r.data.active, false);
  assert.equal(r.data.discountPercent, 10);
  assert.ok(validatePromotionInput({ active: 'no' }, existing).errors.length);
});
