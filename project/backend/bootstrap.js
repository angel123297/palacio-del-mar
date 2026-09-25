/**
 * Preparación automática de datos al arrancar el servidor (DEPLOY-002).
 *
 * El enunciado de la actividad exige que el proyecto "se pueda visualizar
 * ya en funcionamiento" con solo `docker compose up`. Antes, el catálogo
 * (suites/experiencias) y el usuario administrador solo se creaban con
 * comandos manuales aparte (`docker compose --profile seed ...` y
 * `--profile create-admin ...`), así que un `docker compose up` a secas
 * dejaba el sitio vacío y sin forma de entrar a /admin.
 *
 * Ambas funciones son idempotentes: se llaman en cada arranque del
 * contenedor, pero solo actúan si hace falta (catálogo vacío / sin ningún
 * admin), así que reiniciar el contenedor no duplica ni resetea datos.
 * Los scripts de un solo uso (seed/seedData.js, scripts/createAdmin.js)
 * siguen disponibles para recargar el catálogo o crear un admin adicional
 * a mano más adelante.
 */
import Suite from './models/Suite.js';
import User from './models/User.js';
import { insertSeedData } from './seed/seedData.js';
import { isWeakAdminPassword, upsertAdminUser } from './scripts/createAdmin.js';

export const autoSeedIfEmpty = async () => {
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
};
