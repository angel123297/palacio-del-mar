/**
 * Preparación automática de datos al arrancar el servidor (DEPLOY-002).
 *
 * El proyecto debe poder verse funcionando con solo `docker compose up`:
 * sin este paso, el catálogo (suites/experiencias) estaría vacío y no habría
 * ningún usuario para entrar a /admin.
 *
 * Ambas funciones son idempotentes: se llaman en cada arranque del
 * contenedor, pero solo actúan si hace falta (catálogo vacío / sin ningún
 * admin), así que reiniciar el contenedor no duplica ni resetea datos.
 * Los scripts de un solo uso (seed/seedData.js, scripts/createAdmin.js)
 * siguen disponibles para recargar el catálogo o crear un admin adicional
 * a mano más adelante.
 */
import bcrypt from 'bcryptjs';
import Suite from './models/Suite.js';
import User from './models/User.js';
import { insertSeedData } from './seed/seedData.js';
import { isWeakAdminPassword, upsertAdminUser } from './scripts/createAdmin.js';

export const autoSeedIfEmpty = async () => {
  // AUTO_SEED=false: no cargar habitaciones solas (se cargan a mano con `npm run seed`)
  if (String(process.env.AUTO_SEED).toLowerCase() === 'false') {
    console.log('[Bootstrap] AUTO_SEED=false: no se cargan habitaciones automáticamente.');
    return { seeded: false, reason: 'disabled' };
  }
  const existing = await Suite.countDocuments();
  if (existing > 0) {
    return { seeded: false };
  }
  console.log('[Bootstrap] Catálogo vacío: cargando suites y experiencias de ejemplo...');
  const result = await insertSeedData({ clear: false }); // ya está vacío; no hace falta borrar
  console.log(`[Bootstrap] ✅ ${result.suites} suites y ${result.experiences} experiencias cargadas.`);
  return { seeded: true, ...result };
};

export const autoCreateAdminIfMissing = async () => {
  const existingAdmin = await User.findOne({ role: 'admin' });
  if (existingAdmin) {
    return { created: false };
  }

  const email = process.env.ADMIN_EMAIL;
  const password = process.env.ADMIN_PASSWORD;
  const name = process.env.ADMIN_NAME || 'Administrador';

  if (!email || !password) {
    console.warn('[Bootstrap] ⚠️ No hay ningún administrador y faltan ADMIN_EMAIL/ADMIN_PASSWORD: no se pudo crear uno automáticamente.');
    return { created: false, reason: 'missing_env' };
  }
  if (isWeakAdminPassword(password)) {
    console.warn('[Bootstrap] ⚠️ ADMIN_PASSWORD es débil: no se creó el administrador automáticamente. Define una contraseña de 12+ caracteres con letras y números en el .env, o crea uno con "npm run create-admin".');
    return { created: false, reason: 'weak_password' };
  }

  const { user } = await upsertAdminUser({ name, email, password });
  console.log(`[Bootstrap] ✅ Administrador creado automáticamente: ${user.email}`);
  return { created: true };
};

/**
 * Administrador de DESARROLLO con credenciales simples (DEV_ADMIN_EMAIL /
 * DEV_ADMIN_PASSWORD) para revisar el panel en local. Se asegura en cada
 * arranque y es independiente del admin "real" de arriba.
 *
 * La contraseña simple no pasa la validación del modelo (exige mayúscula),
 * así que se crea con una temporal válida y luego se guarda el hash bcrypt
 * directamente. Se ignora si NODE_ENV=production.
 */
export const ensureDevAdmin = async () => {
  const email = process.env.DEV_ADMIN_EMAIL?.trim().toLowerCase();
  const password = process.env.DEV_ADMIN_PASSWORD;
  if (!email || !password) return { created: false, reason: 'not_configured' };
  if (process.env.NODE_ENV === 'production') {
    console.warn('[Bootstrap] ⚠️ DEV_ADMIN_* se ignora porque NODE_ENV=production.');
    return { created: false, reason: 'production' };
  }

  let user = await User.findOne({ email }).select('+password');
  const created = !user;
  if (!user) {
    user = new User({
      name: process.env.DEV_ADMIN_NAME || 'Admin Local',
      email,
      password: 'Temporal-Dev-1', // válida para el modelo; se reemplaza abajo
      role: 'admin',
      status: 'active',
      emailVerified: true
    });
    await user.save();
  }

  const set = { role: 'admin', status: 'active', emailVerified: true };
  if (created || !(await bcrypt.compare(password, user.password))) {
    set.password = await bcrypt.hash(password, 12);
  }
  await User.collection.updateOne({ _id: user._id }, { $set: set });
  console.log(`[Bootstrap] ✅ Admin de desarrollo listo: ${email}${created ? ' (creado)' : ''}`);
  return { created };
};

/**
 * Los clientes que se registran en la web tienen teléfono (profile.phone) y el
 * formulario de reserva y el perfil lo usan. Los administradores se crean por
 * otro camino y no lo traían, así que al usar el sitio de clientes tenían que
 * escribirlo siempre. Aquí se completa SOLO si falta (nunca pisa uno ya guardado).
 */
export const ensureAdminPhones = async () => {
  const pairs = [
    [process.env.ADMIN_EMAIL, process.env.ADMIN_PHONE],
    [process.env.DEV_ADMIN_EMAIL, process.env.DEV_ADMIN_PHONE]
  ];
  let updated = 0;
  for (const [rawEmail, phone] of pairs) {
    const email = rawEmail?.trim().toLowerCase();
    if (!email || !phone) continue;
    const res = await User.collection.updateOne(
      { email, role: 'admin', $or: [{ 'profile.phone': { $exists: false } }, { 'profile.phone': null }, { 'profile.phone': '' }] },
      { $set: { 'profile.phone': String(phone).trim() } }
    );
    updated += res.modifiedCount;
  }
  if (updated) console.log(`[Bootstrap] Teléfono completado en ${updated} administrador(es).`);
  return { updated };
};

/** Ejecuta ambos pasos; un fallo en uno no debe tumbar el arranque del servidor. */
export const runStartupBootstrap = async () => {
  try {
    await autoSeedIfEmpty();
  } catch (err) {
    console.error('[Bootstrap] ❌ No se pudo cargar el catálogo de ejemplo:', err.message);
  }
  try {
    await autoCreateAdminIfMissing();
  } catch (err) {
    console.error('[Bootstrap] ❌ No se pudo crear el administrador automático:', err.message);
  }
  try {
    await ensureDevAdmin();
  } catch (err) {
    console.error('[Bootstrap] ❌ No se pudo crear el admin de desarrollo:', err.message);
  }
  try {
    await ensureAdminPhones();
  } catch (err) {
    console.error('[Bootstrap] ❌ No se pudo completar el perfil de los administradores:', err.message);
  }
};
