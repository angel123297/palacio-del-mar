// Archivo de calendario (.ics, iCalendar RFC 5545) de una reserva. Sin dependencias.
const pad = (n) => String(n).padStart(2, '0');

// Colombia no tiene horario de verano: la hora local es siempre UTC-5.
const BOGOTA_OFFSET_HOURS = 5;

/** 'YYYY-MM-DD' (o ISO) + 'HH:MM' hora de Bogotá -> 'YYYYMMDDTHHMMSSZ' (UTC). */
export const toIcsUtc = (dateValue, hhmm = '12:00') => {
  const day = new Date(dateValue).toISOString().slice(0, 10);
  const [h, m] = String(hhmm).split(':').map(Number);
  const d = new Date(`${day}T00:00:00Z`);
  d.setUTCHours((h || 0) + BOGOTA_OFFSET_HOURS, m || 0, 0, 0);
  return d.toISOString().replace(/[-:]/g, '').replace(/\.\d{3}/, '');
};

const escapeText = (t = '') => String(t).replace(/\\/g, '\\\\').replace(/;/g, '\;').replace(/,/g, '\\,').replace(/\r?\n/g, '\\n');

// Las líneas no pueden pasar de 75 bytes: se continúan en la siguiente con un espacio
const fold = (line) => {
  const bytes = new TextEncoder().encode(line);
  if (bytes.length <= 75) return line;
  const out = [];
  let cur = '';
  let curBytes = 0;
  for (const ch of line) {
    const b = new TextEncoder().encode(ch).length;
    if (curBytes + b > (out.length ? 74 : 75)) { out.push(cur); cur = ''; curBytes = 0; }
    cur += ch; curBytes += b;
  }
  out.push(cur);
  return out.join('\r\n ');
};

/** Evento de estadía: del check-in al check-out, con aviso un día antes. */
export const buildIcs = ({ booking, branch, now = new Date() }) => {
  const hotel = branch?.name || 'Palacio del Mar';
  const code = String(booking._id).slice(-8).toUpperCase();
  const description = [
    `Reserva #${code}`,
    booking.suite?.name ? `Habitación: ${booking.suite.name}` : null,
    `Huéspedes: ${booking.guests}`,
    branch?.phone ? `Teléfono: ${branch.phone}` : null
  ].filter(Boolean).join('\n');

  const lines = [
    'BEGIN:VCALENDAR',
    'VERSION:2.0',
    'PRODID:-//Palacio del Mar//Reservas//ES',
    'CALSCALE:GREGORIAN',
    'METHOD:PUBLISH',
    'BEGIN:VEVENT',
    `UID:reserva-${booking._id}@palaciodelmar`,
    `DTSTAMP:${now.toISOString().replace(/[-:]/g, '').replace(/\.\d{3}/, '')}`,
    `DTSTART:${toIcsUtc(booking.checkIn, branch?.checkInTime || '15:00')}`,
    `DTEND:${toIcsUtc(booking.checkOut, branch?.checkOutTime || '12:00')}`,
    `SUMMARY:${escapeText(`Estadía en ${hotel}`)}`,
    branch?.address ? `LOCATION:${escapeText(`${hotel}, ${branch.address}`)}` : null,
    `DESCRIPTION:${escapeText(description)}`,
    'BEGIN:VALARM',
    'ACTION:DISPLAY',
    'TRIGGER:-P1D',
    `DESCRIPTION:${escapeText(`Mañana llegas a ${hotel}`)}`,
    'END:VALARM',
    'END:VEVENT',
    'END:VCALENDAR'
  ].filter(Boolean);

  return `${lines.map(fold).join('\r\n')}\r\n`;
};

export const downloadIcs = (booking, branch) => {
  const blob = new Blob([buildIcs({ booking, branch })], { type: 'text/calendar;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `palacio-del-mar-${String(booking._id).slice(-8)}.ics`;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
};
