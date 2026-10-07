/**
 * Agrega las experiencias de ejemplo que FALTEN, sin borrar nada.
 *
 * `npm run seed` limpia suites, sucursales, experiencias, noches y promociones
 * antes de sembrar; este script es la alternativa segura cuando solo faltan las
 * experiencias (el sitio muestra datos de respaldo con ids "fx-…" que no se pueden
 * reservar cuando la colección está vacía). Es idempotente: si ya existen, no hace nada.
 *
 * Uso:  docker compose exec backend npm run seed:experiences
 */
// Primero: así .env ya está cargado cuando los demás módulos leen process.env al importarse
import 'dotenv/config';
import connectDB, { closeConnection } from '../database/db.js';
import Experience from '../models/Experience.js';
import { experiences } from '../seed/seedData.js';
import { isMainModule } from '../utils/isMain.js';


/** Inserta las experiencias del seed cuyo slug todavía no existe. Devuelve cuántas creó. */
export const addMissingExperiences = async (model = Experience, seed = experiences) => {
  let created = 0;
  for (const data of seed) {
    // El slug se genera al validar (pre('validate')); si el seed ya lo trae, se usa para comparar
    const exists = data.slug ? await model.exists({ slug: data.slug }) : await model.exists({ name: data.name });
    if (exists) continue;
    await model.create(data);
    created += 1;
  }
  return created;
};

const run = async () => {
  try {
    await connectDB();
    const before = await Experience.countDocuments();
    const created = await addMissingExperiences();
    console.log(`✅ Experiencias: ${before} existentes, ${created} agregadas, ${before + created} en total.`);
    await closeConnection();
    process.exit(0);
  } catch (error) {
    console.error('❌ Error:', error.message);
    process.exit(1);
  }
};

if (isMainModule(import.meta.url)) run();
