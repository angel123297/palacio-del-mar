/**
 * Migración de la Fase 1 (sucursales + habitaciones físicas).
 *
 * Se ejecuta en cada arranque y es IDEMPOTENTE: si no hay nada que migrar no
 * toca nada. Sirve tanto para una base nueva como para una base creada con la
 * versión anterior (suites sin sucursal, noches sin "slot").
 *
 * Orden (importante):
 *   1. Crear las sucursales que falten.
 *   2. Asignar las suites antiguas a "Centro Histórico".
 *   3. Copiar sucursal y habitación (slot 1) a reservas y noches antiguas.
 *   4. Crear el índice único NUEVO (suite, slot, date) y confirmar que existe.
 *   5. Solo entonces borrar los índices ANTIGUOS (suite, date) y (name):
 *      si se borraran antes, habría una ventana sin protección contra la
 *      doble reserva; si se dejaran, no cabrían 2 habitaciones del mismo
 *      tipo en la misma noche ni el mismo nombre en dos sucursales.
 */
import Branch from '../models/Branch.js';
import Suite from '../models/Suite.js';
import Booking from '../models/Booking.js';
import SuiteNight from '../models/SuiteNight.js';
import { ensureBranches } from '../seed/branches.js';

const DEFAULT_BRANCH_SLUG = 'centro-historico';
const NEW_NIGHT_INDEX = 'suite_1_slot_1_date_1';
const OLD_NIGHT_INDEX = 'suite_1_date_1';
const OLD_SUITE_NAME_INDEX = 'name_1';

const missing = (field) => ({ $or: [{ [field]: { $exists: false } }, { [field]: null }] });

const indexNames = async (model) => {
  try {
    return (await model.collection.indexes()).map((i) => i.name);
  } catch (err) {
    if (err.code === 26 || /ns does not exist|NamespaceNotFound/i.test(err.message)) return [];
    throw err;
  }
};

const dropIndexIfExists = async (model, name) => {
  if (!(await indexNames(model)).includes(name)) return false;
  try {
    await model.collection.dropIndex(name);
    return true;
  } catch (err) {
    if (err.code === 27 || err.code === 26) return false; // ya no existe
    throw err;
  }
};

export const runBranchMigration = async () => {
  const report = { branches: 0, suites: 0, bookings: 0, nights: 0, droppedIndexes: [] };

  // 1. Sucursales
  const before = await Branch.countDocuments();
  const branches = await ensureBranches();
  report.branches = (await Branch.countDocuments()) - before;
  const defaultBranch = branches.get(DEFAULT_BRANCH_SLUG);

  // 2. Suites sin sucursal → Centro Histórico
  const suiteRes = await Suite.collection.updateMany(missing('branch'), { $set: { branch: defaultBranch._id } });
  report.suites = suiteRes.modifiedCount;

  // 3a. Reservas sin sucursal: se toma la de su suite
  const orphanSuiteIds = await Booking.collection.distinct('suite', missing('branch'));
  for (const suiteId of orphanSuiteIds) {
    const suite = await Suite.collection.findOne({ _id: suiteId }, { projection: { branch: 1 } });
    if (!suite?.branch) continue; // suite borrada: la reserva queda como estaba
    const res = await Booking.collection.updateMany({ suite: suiteId, ...missing('branch') }, { $set: { branch: suite.branch } });
    report.bookings += res.modifiedCount;
  }
  await Booking.collection.updateMany({ unitSlot: { $exists: false } }, { $set: { unitSlot: 1 } });

  // 3b. Noches antiguas: ocupaban la única habitación → slot 1
  const nightRes = await SuiteNight.collection.updateMany({ slot: { $exists: false } }, { $set: { slot: 1 } });
  report.nights = nightRes.modifiedCount;

  // 4. Índices nuevos (Mongoose los crea con init) y comprobación
  await SuiteNight.init();
  await Suite.init();
  if (!(await indexNames(SuiteNight)).includes(NEW_NIGHT_INDEX)) {
    // Fallo seguro: NO se borra el índice antiguo si el nuevo no existe.
    throw new Error(`No se pudo crear el índice ${NEW_NIGHT_INDEX}; migración detenida sin borrar índices antiguos`);
  }

  // 5. Índices antiguos
  if (await dropIndexIfExists(SuiteNight, OLD_NIGHT_INDEX)) report.droppedIndexes.push(`SuiteNight.${OLD_NIGHT_INDEX}`);
  if (await dropIndexIfExists(Suite, OLD_SUITE_NAME_INDEX)) report.droppedIndexes.push(`Suite.${OLD_SUITE_NAME_INDEX}`);

  const changed = report.branches || report.suites || report.bookings || report.nights || report.droppedIndexes.length;
  if (changed) {
    console.log(
      `[Migración] sucursales nuevas: ${report.branches}, suites asignadas: ${report.suites}, ` +
      `reservas actualizadas: ${report.bookings}, noches actualizadas: ${report.nights}` +
      (report.droppedIndexes.length ? `, índices antiguos eliminados: ${report.droppedIndexes.join(', ')}` : '')
    );
  }
  return report;
};
