// Prueba de aceptación de BUG-001 contra una MongoDB REAL.
//   TEST_MONGODB_URI=mongodb://localhost:27017/palacio_test npm test
// (usa una base de datos de pruebas: crea y borra sus propios documentos)
import test from 'node:test';
import assert from 'node:assert/strict';
import mongoose from 'mongoose';
import SuiteNight, { NightsConflictError } from '../models/SuiteNight.js';
import { eachNight, toCalendarDate } from '../utils/dates.js';

const uri = process.env.TEST_MONGODB_URI;

test('20 solicitudes paralelas por las mismas noches: solo una gana', { skip: !uri && 'defina TEST_MONGODB_URI' }, async () => {
  await mongoose.connect(uri);
  await SuiteNight.init();
  const suite = new mongoose.Types.ObjectId();
  const nights = eachNight(toCalendarDate('2030-01-10'), toCalendarDate('2030-01-14'));

  try {
    const results = await Promise.allSettled(
      Array.from({ length: 20 }, () => SuiteNight.acquire(suite, new mongoose.Types.ObjectId(), nights))
    );
    const ok = results.filter((r) => r.status === 'fulfilled');
    const conflicts = results.filter((r) => r.status === 'rejected' && r.reason instanceof NightsConflictError);
    assert.equal(ok.length, 1, 'exactamente una reserva debe ganar');
    assert.equal(conflicts.length, 19);
    assert.equal(await SuiteNight.countDocuments({ suite }), nights.length, 'sin noches huérfanas de los perdedores');
  } finally {
    await SuiteNight.deleteMany({ suite });
    await mongoose.disconnect();
  }
});

test('rangos que se solapan parcialmente no pueden coexistir; adyacentes sí', { skip: !uri && 'defina TEST_MONGODB_URI' }, async () => {
  await mongoose.connect(uri);
  await SuiteNight.init();
  const suite = new mongoose.Types.ObjectId();
  const a = new mongoose.Types.ObjectId();
  try {
    await SuiteNight.acquire(suite, a, eachNight(toCalendarDate('2030-02-01'), toCalendarDate('2030-02-05')));
    await assert.rejects(
      SuiteNight.acquire(suite, new mongoose.Types.ObjectId(), eachNight(toCalendarDate('2030-02-04'), toCalendarDate('2030-02-07'))),
      NightsConflictError
    );
    // check-out del día 5 = check-in del día 5: es válido
    await SuiteNight.acquire(suite, new mongoose.Types.ObjectId(), eachNight(toCalendarDate('2030-02-05'), toCalendarDate('2030-02-07')));
    // cancelar libera
    await SuiteNight.release(a);
    await SuiteNight.acquire(suite, new mongoose.Types.ObjectId(), eachNight(toCalendarDate('2030-02-01'), toCalendarDate('2030-02-05')));
  } finally {
    await SuiteNight.deleteMany({ suite });
    await mongoose.disconnect();
  }
});
