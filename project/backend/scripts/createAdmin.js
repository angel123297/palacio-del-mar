/**
 * Crea (o actualiza) el usuario administrador inicial.
 *
 * Antes de este script no había NINGUNA forma de crear un admin: el
 * registro público (/api/auth/register) siempre crea usuarios con
 * role: 'guest', y no existía ningún endpoint ni script para promover a
 * alguien a administrador. Sin este script, el panel de administración
 * era inalcanzable incluso para el dueño del hotel.
 *
 * Uso:
 *   npm run create-admin
 *   (lee ADMIN_NAME, ADMIN_EMAIL, ADMIN_PASSWORD del .env)
 *
 * Con Docker Compose:
 *   docker compose --profile create-admin run --rm create-admin
 */
import dotenv from 'dotenv';
import connectDB, { closeConnection } from '../database/db.js';
import User from '../models/User.js';

dotenv.config();

/** ¿Es una contraseña de administrador débil o de ejemplo? Reutilizado por bootstrap.js. */
export const isWeakAdminPassword = (password) => {
  const knownWeak = ['admin1234', 'cambiaesta123', 'password', 'admin', '12345678', 'cambia_esto'];
  return (
    !password ||
    password.length < 12 ||
    !/[A-Za-z]/.test(password) ||
    !/[0-9]/.test(password) ||
    knownWeak.includes(password.toLowerCase())
  );
};

/**
 * Crea el usuario admin si no existe, o promueve a admin uno existente con
 * ese email. Requiere una conexión de Mongoose ya abierta (no conecta ni
 * desconecta): la usan tanto este script de CLI como el arranque
 * automático del servidor (ver ../bootstrap.js).
 */
export const upsertAdminUser = async ({ name = 'Administrador', email, password }) => {
  let user = await User.findOne({ email: email.toLowerCase() }).select('+password');

  if (user) {
    user.role = 'admin';
    user.status = 'active';
    user.emailVerified = true;
    user.password = password; // se re-hashea en el pre('save') del modelo
    await user.save();
    return { user, created: false };
  }

  user = new User({
    name,
    email: email.toLowerCase(),
    password,
    role: 'admin',
    status: 'active',
    emailVerified: true
  });
  await user.save();
  return { user, created: true };
};

/**
 * Punto de entrada cuando se ejecuta como script:
 *   npm run create-admin
 *   docker compose --profile create-admin run --rm create-admin
 * (lee ADMIN_NAME, ADMIN_EMAIL, ADMIN_PASSWORD del .env). El arranque
 * normal del servidor ya crea el primer administrador automáticamente si
 * no existe ninguno (ver bootstrap.js); este script sirve para crear uno
 * adicional o promover a un usuario existente más adelante.
 */
const run = async () => {
  const name = process.env.ADMIN_NAME || 'Administrador';
  const email = process.env.ADMIN_EMAIL;
  const password = process.env.ADMIN_PASSWORD;

  if (!email || !password) {
    console.error('❌ Faltan ADMIN_EMAIL y/o ADMIN_PASSWORD en las variables de entorno.');
    process.exit(1);
  }

  if (isWeakAdminPassword(password)) {
    console.error('❌ ADMIN_PASSWORD es débil o es un valor de ejemplo: usa 12+ caracteres con letras y números, único para este despliegue.');
    process.exit(1);
  }

  await connectDB();
  const { user, created } = await upsertAdminUser({ name, email, password });
  console.log(created ? `✅ Administrador creado: ${user.email}` : `✅ Usuario existente "${user.email}" actualizado a administrador.`);

  await closeConnection();
  process.exit(0);
};

if (import.meta.url === `file://${process.argv[1]}`) {
  run().catch((error) => {
    console.error('❌ Error creando el administrador:', error.message);
    process.exit(1);
  });
}
