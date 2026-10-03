// Pruebas de la fase 0: validación de email, registro de actividad y borrado de usuarios.
// No necesitan base de datos: solo validan los modelos en memoria.
import test from 'node:test';
import assert from 'node:assert/strict';
import User from '../models/User.js';
import Booking from '../models/Booking.js';
import { isValidEmail } from '../utils/validators.js';

const nuevoUsuario = (email) =>
  new User({ name: 'Ana Prueba', email, password: 'Abcdef123456' });

test('acepta correos válidos, incluidos dominios de más de 3 letras', () => {
  for (const email of [
    'ana@hotel.com',
    'ana@hotel.co',
    'ana@mail.example.com',
    'ana@hotel.info',
    'ana@tienda.store',
    'ana@correo.travel',
    'ana.perez+reservas@gmail.com',
    'ana_perez@dominio-largo.com.co'
  ]) {
    assert.equal(isValidEmail(email), true, email);
    assert.equal(nuevoUsuario(email).validateSync(['email']), undefined, email);
  }
});

test('rechaza correos mal formados', () => {
  for (const email of [
    '', 'ana', 'ana@', '@hotel.com', 'ana@hotel', 'ana@hotel.c', 'ana@.com',
    'ana@hotel..com', 'ana hotel@x.com', 'a@b@c.com', 'ana@hotel.com ', null, undefined, 42
  ]) {
    assert.equal(isValidEmail(email), false, String(email));
  }
  assert.ok(nuevoUsuario('ana@hotel').validateSync(['email'])?.errors.email);
});

test('un correo hostil no congela el proceso (ReDoS)', () => {
  const hostiles = [
    'a@' + 'a'.repeat(40) + '.info',        // el caso que antes tardaba años
    'a@' + 'a'.repeat(60) + '.store',
    'a@' + 'a.'.repeat(120) + '!',          // muchos puntos y final inválido
    'a'.repeat(5000) + '@' + 'b'.repeat(5000), // enorme: se descarta por longitud
    '@'.repeat(5000)
  ];
  const inicio = process.hrtime.bigint();
  for (const email of hostiles) isValidEmail(email);
  const ms = Number(process.hrtime.bigint() - inicio) / 1e6;
  assert.ok(ms < 100, `tardó ${ms.toFixed(1)} ms; debería ser casi instantáneo`);
});

test('Booking valida guestEmail con el mismo validador', () => {
  const reserva = (guestEmail) => new Booking({ guestEmail }).validateSync(['guestEmail']);
  assert.equal(reserva('ana@hotel.info'), undefined);
  assert.ok(reserva('ana@hotel')?.errors.guestEmail);
});

test('logActivity acepta todas las acciones que usa el código', () => {
  const acciones = ['logout', 'profile_update', 'password_reset', 'status_change', 'user_deleted'];
  for (const action of acciones) {
    const usuario = nuevoUsuario('ana@hotel.com');
    usuario.activityLog.push({ action });
    assert.equal(usuario.validateSync(['activityLog']), undefined, action);
  }
  const usuario = nuevoUsuario('ana@hotel.com');
  usuario.activityLog.push({ action: 'accion_inventada' });
  assert.ok(usuario.validateSync(['activityLog']), 'las acciones desconocidas siguen rechazándose');
});

test('logActivity no lanza error aunque el guardado falle', async () => {
  const usuario = nuevoUsuario('ana@hotel.com');
  usuario.save = async () => { throw new Error('base de datos caída'); };
  const original = console.error;
  console.error = () => {};
  try {
    await assert.doesNotReject(usuario.logActivity('password_reset', { ok: true }, '127.0.0.1', 'test'));
  } finally {
    console.error = original;
  }
});

test('softDelete: el correo con sufijo "_deleted_<id>" sigue siendo válido', () => {
  const usuario = nuevoUsuario('ana@hotel.com');
  usuario.email = `${usuario.email}_deleted_${usuario._id}`;
  assert.equal(usuario.validateSync(['email']), undefined, usuario.email);
});

test('el hook de email duplicado rechaza el guardado con un solo next()', async () => {
  const hook = User.schema.s.hooks._pres
    .get('save')
    .map((h) => h.fn)
    .find((fn) => String(fn).includes('El email ya está registrado'));
  assert.ok(hook, 'no se encontró el hook de email duplicado');

  const originalFindOne = User.findOne;
  try {
    const usuario = nuevoUsuario('ana@hotel.com');
    usuario.isModified = (campo) => campo === 'email';

    User.findOne = async () => ({ _id: 'otro-usuario' });
    const llamadas = [];
    await hook.call(usuario, (err) => llamadas.push(err));
    assert.equal(llamadas.length, 1, 'next() debe llamarse una sola vez');
    assert.equal(llamadas[0]?.message, 'El email ya está registrado');

    User.findOne = async () => null;
    const sinDuplicado = [];
    await hook.call(usuario, (err) => sinDuplicado.push(err));
    assert.equal(sinDuplicado.length, 1);
    assert.equal(sinDuplicado[0], undefined);
  } finally {
    User.findOne = originalFindOne;
  }
});
