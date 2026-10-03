// Datos de contacto del formulario de reserva y su relación con la sesión.
// Funciones puras (se prueban con `npm test`).

export const contactFromUser = (user) => ({
  guestName: user?.name || '',
  guestEmail: user?.email || '',
  guestPhone: user?.phone || '',
  specialRequests: ''
});

/** Identidad de la sesión: null = invitado (sin sesión). */
export const userKey = (user) => (user ? String(user._id || user.id || user.email || 'sesion') : null);

/**
 * Qué datos de contacto deben quedar cuando cambia la sesión:
 *  - invitado → con sesión: se conserva lo que escribió y solo se rellenan los vacíos;
 *  - cierre de sesión u otro usuario: NADA del usuario anterior (se parte de cero
 *    o de los datos del nuevo usuario).
 */
export const contactAfterUserChange = (prevKey, user, contact) => {
  const nextKey = userKey(user);
  if (prevKey === nextKey) return contact;
  const fromUser = contactFromUser(user);
  if (prevKey === null) {
    return {
      ...contact,
      guestName: contact.guestName || fromUser.guestName,
      guestEmail: contact.guestEmail || fromUser.guestEmail,
      guestPhone: contact.guestPhone || fromUser.guestPhone
    };
  }
  return fromUser;
};
