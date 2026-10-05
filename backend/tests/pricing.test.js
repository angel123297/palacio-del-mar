// Paso 1: precios únicos. Sin base de datos (funciones puras).
import test from 'node:test';
import assert from 'node:assert/strict';
import { easterSunday, holyWeek, getSeason, getSeasonCalendar, calculateSeasonalPrice } from '../utils/seasons.js';
import { quoteStay, computeTotals, totalsFromNights, longStayPercent, groupNights, summarizeQuote } from '../utils/pricing.js';
import { toCalendarDate } from '../utils/dates.js';

const d = (s) => toCalendarDate(s);
const iso = (x) => x.toISOString().slice(0, 10);

test('Pascua: fechas conocidas', () => {
  assert.equal(iso(easterSunday(2025)), '2025-04-20');
  assert.equal(iso(easterSunday(2026)), '2026-04-05');
  assert.equal(iso(easterSunday(2027)), '2027-03-28');
  assert.equal(iso(easterSunday(2038)), '2038-04-25');
  assert.deepEqual(holyWeek(2027), { name: 'Semana Santa', start: '2027-03-21', end: '2027-03-28' });
});

test('temporadas: Navidad cruza el año (antes nunca se aplicaba)', () => {
  for (const day of ['2026-12-21', '2026-12-31', '2027-01-01', '2027-01-10']) assert.equal(getSeason(d(day)), 'peak', day);
  assert.equal(getSeason(d('2027-01-11')), 'low');
  assert.equal(getSeason(d('2026-12-20')), 'high');
  assert.equal(getSeason(d('2026-12-14')), 'mid');
  assert.equal(getSeason(d('2026-12-01')), 'mid');
  assert.equal(getSeason(d('2026-06-10')), 'mid');
  assert.equal(getSeason(d('2026-06-15')), 'high');
  assert.equal(getSeason(d('2026-07-16')), 'low');
  assert.equal(getSeason(d('2026-04-05')), 'peak'); // Domingo de Resurrección 2026
  assert.equal(getSeason(d('2026-04-06')), 'low');
});

test('el calendario público sale de la misma tabla que el cobro', () => {
  const cal = getSeasonCalendar(2027);
  assert.ok(cal.peakSeason.some((r) => r.name === 'Semana Santa' && r.start === '2027-03-21'));
  assert.ok(cal.peakSeason.some((r) => r.start === '2027-12-21' && r.end === '2028-01-10'));
  // todo día marcado como pico/alto en el calendario se cobra como tal
  for (const r of cal.peakSeason) assert.equal(getSeason(d(r.start)), 'peak', r.name);
  for (const r of cal.highSeason) assert.equal(getSeason(d(r.start)), 'high', r.name);
  assert.equal(cal.holidays.length, 9);
});

test('cada noche se cobra con SU temporada', () => {
  // 27 y 28 de marzo 2027: Semana Santa (x1.5); 29: baja (x1.0)
  const q = quoteStay({ basePrice: 1000000, checkIn: d('2027-03-27'), checkOut: d('2027-03-30') });
  assert.deepEqual(q.nights.map((n) => n.price), [1500000, 1500000, 1000000]);
  assert.equal(q.lodging, 4000000);
  assert.equal(q.total, 4000000);
  assert.deepEqual(groupNights(q.nights).map((l) => [l.nights, l.unitPrice]), [[2, 1500000], [1, 1000000]]);
  assert.equal(calculateSeasonalPrice(860000, 'mid'), 989000);
});

test('descuento por estadía larga: 5% desde 5 noches, 10% desde 7, sobre alojamiento + experiencias', () => {
  assert.equal(longStayPercent(4), 0);
  assert.equal(longStayPercent(5), 5);
  assert.equal(longStayPercent(7), 10);
  const q5 = quoteStay({ basePrice: 100000, checkIn: d('2026-09-01'), checkOut: d('2026-09-06'), experiencesTotal: 50000 });
  assert.equal(q5.lodging, 500000);
  assert.equal(q5.discountType, 'long_stay');
  assert.equal(q5.discount, 27500); // 5% de 550.000
  assert.equal(q5.total, 522500);
  const q7 = quoteStay({ basePrice: 100000, checkIn: d('2026-09-01'), checkOut: d('2026-09-08') });
  assert.equal(q7.discount, 70000);
  assert.match(q7.discountReason, /7\+ noches \(10%\)/);
});

const promo = (percent, start, end, title = 'Promo') => ({
  _id: `p${percent}`, title, discountPercent: percent, startDate: d(start), endDate: d(end), active: true
});

test('promoción: descuenta solo las noches que cubre (fin inclusive)', () => {
  const q = quoteStay({
    basePrice: 100000, checkIn: d('2026-09-01'), checkOut: d('2026-09-05'), // noches 1,2,3,4
    promotions: [promo(20, '2026-09-03', '2026-09-04', 'Septiembre')]
  });
  assert.deepEqual(q.nights.map((n) => n.promoPercent), [0, 0, 20, 20]);
  assert.equal(q.discountType, 'promotion');
  assert.equal(q.discount, 40000);
  assert.equal(q.total, 360000);
  assert.match(q.discountReason, /Septiembre.*-20% en 2 de 4 noches/);
});

test('NO se acumulan: gana el descuento que más ahorra', () => {
  // 7 noches: estadía larga 10% = 70.000; promo 5% en todas = 35.000 -> gana estadía larga
  const baja = quoteStay({
    basePrice: 100000, checkIn: d('2026-09-01'), checkOut: d('2026-09-08'),
    promotions: [promo(5, '2026-09-01', '2026-09-30')]
  });
  assert.equal(baja.discountType, 'long_stay');
  assert.equal(baja.discount, 70000);
  // promo 30% en todas = 210.000 -> gana la promoción
  const alta = quoteStay({
    basePrice: 100000, checkIn: d('2026-09-01'), checkOut: d('2026-09-08'),
    promotions: [promo(30, '2026-09-01', '2026-09-30')]
  });
  assert.equal(alta.discountType, 'promotion');
  assert.equal(alta.total, 490000);
});

test('si dos promociones cubren la misma noche se usa la mayor', () => {
  const q = quoteStay({
    basePrice: 100000, checkIn: d('2026-09-01'), checkOut: d('2026-09-02'),
    promotions: [promo(10, '2026-09-01', '2026-09-30', 'A'), promo(25, '2026-09-01', '2026-09-01', 'B')]
  });
  assert.equal(q.nights[0].promoPercent, 25);
  assert.equal(q.total, 75000);
});

test('una promoción inactiva o de otras fechas no se aplica', () => {
  const q = quoteStay({
    basePrice: 100000, checkIn: d('2026-09-01'), checkOut: d('2026-09-03'),
    promotions: [{ ...promo(40, '2026-09-01', '2026-09-30'), active: false }, promo(40, '2026-10-01', '2026-10-05')]
  });
  assert.equal(q.discount, 0);
  assert.equal(q.total, 200000);
});

test('recalcular con experiencias da lo mismo que cotizar con ellas desde el inicio', () => {
  const promotions = [promo(15, '2026-09-02', '2026-09-04')];
  const base = quoteStay({ basePrice: 120000, checkIn: d('2026-09-01'), checkOut: d('2026-09-06'), promotions });
  const conExp = quoteStay({ basePrice: 120000, checkIn: d('2026-09-01'), checkOut: d('2026-09-06'), experiencesTotal: 90000, promotions });
  // lo guardado en la reserva (noches) + experiencias nuevas
  const recalculado = totalsFromNights(base.nights, 90000);
  assert.equal(recalculado.total, conExp.total);
  assert.equal(recalculado.discount, conExp.discount);
});

test('reservas anteriores al desglose: se recalculan sobre su subtotal', () => {
  const t = computeTotals({ lodging: 700000, nightsCount: 7, experiencesTotal: 100000 });
  assert.equal(t.discount, 80000);
  assert.equal(t.total, 720000);
});

test('totales siempre enteros y nunca negativos', () => {
  const q = quoteStay({
    basePrice: 333333, checkIn: d('2026-09-01'), checkOut: d('2026-09-04'),
    promotions: [promo(33, '2026-09-01', '2026-09-30')]
  });
  assert.ok(Number.isInteger(q.total) && Number.isInteger(q.discount));
  assert.ok(q.total >= 0);
});

test('resumen del detalle de habitación: total, descuento y líneas coherentes con la cotización', () => {
  const q = quoteStay({ basePrice: 1000000, checkIn: toCalendarDate('2027-03-01'), checkOut: toCalendarDate('2027-03-08') });
  const r = summarizeQuote(q, groupNights(q.nights));
  assert.equal(r.nights, 7);
  assert.equal(r.totalPrice, q.total);
  assert.ok(r.totalPrice > 0 && r.totalPrice < r.lodging, 'estadía larga descuenta');
  assert.equal(r.lines.reduce((s, l) => s + l.amount, 0), r.lodging);
});
