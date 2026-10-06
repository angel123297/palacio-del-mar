import test from 'node:test';
import assert from 'node:assert/strict';
import { contactFromUser, contactAfterUserChange, userKey } from './contact.js';

const ana = { _id: 'a1', name: 'Ana Pérez', email: 'ana@correo.com', phone: '3001112233' };
const luis = { _id: 'l2', name: 'Luis Gómez', email: 'luis@correo.com', phone: '3004445566' };

test('al cerrar sesión no queda ningún dato del usuario anterior', () => {
  const contact = { ...contactFromUser(ana), specialRequests: 'Cuna' };
  assert.deepEqual(contactAfterUserChange(userKey(ana), null, contact), contactFromUser(null));
});

test('al entrar otro usuario se usan SOLO sus datos, no los del anterior', () => {
  const out = contactAfterUserChange(userKey(ana), luis, contactFromUser(ana));
  assert.deepEqual(out, contactFromUser(luis));
});

test('invitado que se registra conserva lo que escribió y completa lo vacío', () => {
  const typed = { guestName: 'María', guestEmail: '', guestPhone: '', specialRequests: 'Piso alto' };
  const out = contactAfterUserChange(null, luis, typed);
  assert.equal(out.guestName, 'María');
  assert.equal(out.guestEmail, 'luis@correo.com');
  assert.equal(out.specialRequests, 'Piso alto');
});

test('sin cambio de sesión no se toca nada', () => {
  const c = { guestName: 'x', guestEmail: 'y', guestPhone: 'z', specialRequests: '' };
  assert.equal(contactAfterUserChange(userKey(ana), ana, c), c);
  assert.equal(contactAfterUserChange(null, null, c), c);
});
