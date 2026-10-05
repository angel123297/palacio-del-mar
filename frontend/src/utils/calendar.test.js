import { test } from 'node:test';
import assert from 'node:assert/strict';
import { buildIcs, toIcsUtc } from './calendar.js';

const booking = { _id: 'abc123def456', checkIn: '2026-12-20T00:00:00.000Z', checkOut: '2026-12-23T00:00:00.000Z', guests: 2, suite: { name: 'Suite, Mar; Norte' } };
const branch = { name: 'Palacio del Mar · Getsemaní', address: 'Calle 10 #10-10', checkInTime: '15:00', checkOutTime: '12:00' };

test('ics: las horas de Bogotá (UTC-5) se pasan a UTC', () => {
  assert.equal(toIcsUtc('2026-12-20T00:00:00.000Z', '15:00'), '20261220T200000Z');
  assert.equal(toIcsUtc('2026-12-23', '12:00'), '20261223T170000Z');
});

test('ics: estructura válida, CRLF, texto escapado y aviso el día anterior', () => {
  const ics = buildIcs({ booking, branch, now: new Date('2026-10-01T10:00:00Z') });
  assert.ok(ics.startsWith('BEGIN:VCALENDAR\r\n') && ics.endsWith('END:VCALENDAR\r\n'));
  assert.match(ics, /DTSTART:20261220T200000Z/);
  assert.match(ics, /DTEND:20261223T170000Z/);
  assert.match(ics, /TRIGGER:-P1D/);
  assert.match(ics, /Suite\\, Mar\; Norte/);
  assert.ok(ics.split('\r\n').every((l) => new TextEncoder().encode(l).length <= 75), 'líneas de máximo 75 bytes');
});
