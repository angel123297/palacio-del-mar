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

test('10 solicitudes paralelas por un tipo con 3 habitaciones: ganan exactamente 3, cada una en su slot', { skip: !uri && 'defina TEST_MONGODB_URI' }, async () => {
  await mongoose.connect(uri);
  await SuiteNight.init();
  const suite = new mongoose.Types.ObjectId();
  const nights = eachNight(toCalendarDate('2030-03-10'), toCalendarDate('2030-03-13'));
  try {
    const results = await Promise.allSettled(
      Array.from({ length: 10 }, () => SuiteNight.acquireAny(suite, new mongoose.Types.ObjectId(), nights, 3))
    );
    const ok = results.filter((r) => r.status === 'fulfilled');
    assert.equal(ok.length, 3);
    assert.deepEqual(ok.map((r) => r.value.slot).sort(), [1, 2, 3]);
    assert.equal(await SuiteNight.countDocuments({ suite }), 3 * nights.length, 'sin noches huérfanas');
  } finally {
    await SuiteNight.deleteMany({ suite });
    await mongoose.disconnect();
  }
});

test('vencimiento real: la reserva pendiente vencida pasa a expired y libera sus noches', { skip: !uri && 'defina TEST_MONGODB_URI' }, async () => {
  const { default: Booking } = await import('../models/Booking.js');
  const { expireStaleBookings } = await import('../services/bookingExpiry.js');
  await mongoose.connect(uri);
  await SuiteNight.init();
  const suite = new mongoose.Types.ObjectId();
  const vencida = new mongoose.Types.ObjectId();
  const vigente = new mongoose.Types.ObjectId();
  const pagada = new mongoose.Types.ObjectId();
  const ahora = new Date();
  const base = { user: new mongoose.Types.ObjectId(), suite, status: 'pending', paymentStatus: 'pending' };
  const noches = (y) => eachNight(toCalendarDate(`${y}-08-01`), toCalendarDate(`${y}-08-03`));
  try {
    await Booking.collection.insertMany([
      { _id: vencida, ...base, holdExpiresAt: new Date(ahora - 60000) },
      { _id: vigente, ...base, holdExpiresAt: new Date(ahora.getTime() + 600000) },
      { _id: pagada, ...base, paymentStatus: 'paid', holdExpiresAt: new Date(ahora - 60000) }
    ]);
    await SuiteNight.acquire(suite, vencida, noches(2031), 1);
    await SuiteNight.acquire(suite, vigente, noches(2032), 1);
    await SuiteNight.acquire(suite, pagada, noches(2033), 1);

    assert.equal(await expireStaleBookings(ahora), 1);
    assert.equal((await Booking.collection.findOne({ _id: vencida })).status, 'expired');
    assert.equal((await Booking.collection.findOne({ _id: vigente })).status, 'pending');
    assert.equal((await Booking.collection.findOne({ _id: pagada })).status, 'pending', 'con dinero recibido no vence');
    assert.equal(await SuiteNight.countDocuments({ booking: vencida }), 0, 'noches liberadas');
    assert.equal(await SuiteNight.countDocuments({ booking: vigente }), 2);
    assert.equal(await expireStaleBookings(ahora), 0, 'idempotente');
    // las noches liberadas se pueden volver a reservar
    await SuiteNight.acquire(suite, new mongoose.Types.ObjectId(), noches(2031), 1);
  } finally {
    await Booking.collection.deleteMany({ _id: { $in: [vencida, vigente, pagada] } });
    await SuiteNight.deleteMany({ suite });
    await mongoose.disconnect();
  }
});
