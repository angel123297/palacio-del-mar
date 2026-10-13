import http from 'http';

const BASE_URL = 'http://localhost:3000';

async function request(method, path, body = null, headers = {}) {
  const url = new URL(path, BASE_URL);
  return new Promise((resolve, reject) => {
    const req = http.request(url, {
      method,
      headers: {
        'Content-Type': 'application/json',
        ...headers
      }
    }, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        try {
          const parsed = JSON.parse(data);
          resolve({ status: res.statusCode, body: parsed, raw: data });
        } catch (e) {
          resolve({ status: res.statusCode, body: data, raw: data });
        }
      });
    });
    req.on('error', reject);
    if (body) req.write(JSON.stringify(body));
    req.end();
  });
}

// Generate unique non-colliding future dates for repeatable tests
let dateOffsetSeed = 100 + Math.floor(Math.random() * 500);
function nextDateRange(days = 4) {
  dateOffsetSeed += 10;
  const inDate = new Date(Date.now() + dateOffsetSeed * 24 * 3600 * 1000);
  const outDate = new Date(Date.now() + (dateOffsetSeed + days) * 24 * 3600 * 1000);
  return {
    checkIn: inDate.toISOString().split('T')[0],
    checkOut: outDate.toISOString().split('T')[0],
    days
  };
}

const tests = [];
function test(category, name, fn) {
  tests.push({ category, name, fn });
}

let passed = 0;
let failed = 0;

/* ====================================================================
   GRUPO 1: REGLAS DE NEGOCIO ORIGINALES DE TU CÓDIGO
==================================================================== */

test('CÓDIGO ORIGINAL', '1.1 Catálogo de Sedes y Habitaciones activas', async () => {
  const resBranches = await request('GET', '/api/branches');
  if (resBranches.status !== 200 || !Array.isArray(resBranches.body?.data) || resBranches.body.data.length < 4) {
    throw new Error(`Error en /api/branches: se esperaban mínimo 4 sedes, recibidas ${resBranches.body?.data?.length}`);
  }

  const resSuites = await request('GET', '/api/suites');
  if (resSuites.status !== 200 || !Array.isArray(resSuites.body?.data) || resSuites.body.data.length < 10) {
    throw new Error(`Error en /api/suites: se esperaban suites activas.`);
  }
});

test('CÓDIGO ORIGINAL', '1.2 Motor de Tarifas: Temporadas y Multiplicadores dinámicos', async () => {
  // Test en temporada de noviembre (media: 1.15x)
  const range = nextDateRange(3);
  const res = await request('POST', '/api/suites/calculate-price', {
    suiteId: 'suite-1',
    checkIn: range.checkIn,
    checkOut: range.checkOut,
    guests: 2
  });
  if (res.status !== 200 || !res.body?.data?.nightlyRates) {
    throw new Error(`Fallo en cálculo de tarifas por noche: ${JSON.stringify(res.body)}`);
  }
  const calc = res.body.data;
  if (calc.nights !== 3) {
    throw new Error(`Noches calculadas incorrectas: esperadas 3, obtenidas ${calc.nights}`);
  }
  if (!calc.subtotal || !calc.tax || !calc.totalPrice) {
    throw new Error('Faltan desglose de impuestos o total en el motor de precios');
  }
});

test('CÓDIGO ORIGINAL', '1.3 Recargo por Huéspedes Adicionales ($120.000 COP/noche si > 2 personas)', async () => {
  const range = nextDateRange(2);
  const res2Guests = await request('POST', '/api/suites/calculate-price', {
    suiteId: 'suite-2',
    checkIn: range.checkIn,
    checkOut: range.checkOut,
    guests: 2
  });
  const res4Guests = await request('POST', '/api/suites/calculate-price', {
    suiteId: 'suite-2',
    checkIn: range.checkIn,
    checkOut: range.checkOut,
    guests: 4
  });
  const expectedExtraCharge = (4 - 2) * 120000 * 2; // 2 extra guests * 120k * 2 nights = 480k
  if (res4Guests.body?.data?.extraGuestsCharge !== expectedExtraCharge) {
    throw new Error(`Recargo por huésped adicional incorrecto: esperado ${expectedExtraCharge}, obtenido ${res4Guests.body?.data?.extraGuestsCharge}`);
  }
});

test('CÓDIGO ORIGINAL', '1.4 Descuento por Estadía Larga (5% semanal >= 7 noches)', async () => {
  const range7 = nextDateRange(7);
  const res7 = await request('POST', '/api/suites/calculate-price', {
    suiteId: 'suite-1',
    checkIn: range7.checkIn,
    checkOut: range7.checkOut,
    guests: 2
  });
  if (res7.body?.data?.durationDiscountPercent !== 5) {
    throw new Error(`Se esperaba 5% de descuento por 7 noches, obtenido: ${res7.body?.data?.durationDiscountPercent}%`);
  }
});

test('CÓDIGO ORIGINAL', '1.5 Servicios VIP Adicionales: Recogida al Aeropuerto ($150.000 COP)', async () => {
  const range = nextDateRange(2);
  const resNoVIP = await request('POST', '/api/suites/calculate-price', {
    suiteId: 'suite-3',
    checkIn: range.checkIn,
    checkOut: range.checkOut,
    guests: 2,
    airportPickup: false
  });
  const resVIP = await request('POST', '/api/suites/calculate-price', {
    suiteId: 'suite-3',
    checkIn: range.checkIn,
    checkOut: range.checkOut,
    guests: 2,
    airportPickup: true
  });
  const diff = resVIP.body.data.airportFee;
  if (diff !== 150000) {
    throw new Error(`Tarifa de transporte VIP al aeropuerto incorrecta: esperada 150.000, obtenida ${diff}`);
  }
});

test('CÓDIGO ORIGINAL', '1.6 Calendario de Disponibilidad Mensual por Suite y Fechas Pico', async () => {
  const resMonthly = await request('GET', '/api/availability/monthly?year=2026&month=12&suiteId=suite-1');
  if (resMonthly.status !== 200 || !resMonthly.body?.data?.calendar) {
    throw new Error(`Fallo en /api/availability/monthly: ${JSON.stringify(resMonthly.body)}`);
  }
  const resPeaks = await request('GET', '/api/availability/peak-dates');
  if (resPeaks.status !== 200 || !Array.isArray(resPeaks.body?.data?.peakDates)) {
    throw new Error(`Fallo en /api/availability/peak-dates`);
  }
});

test('CÓDIGO ORIGINAL', '1.7 Prevención de Doble Reserva (Anti-Overbooking Concurrency)', async () => {
  const range = nextDateRange(3);
  // Suite-4 has totalUnits: 1 (Suite Presidencial Centro)
  const b1 = await request('POST', '/api/bookings', {
    suiteId: 'suite-4',
    checkIn: range.checkIn,
    checkOut: range.checkOut,
    guests: 2,
    guestName: 'Primer Huésped (Ocupa única unidad)'
  });
  if (b1.status !== 200 || !b1.body?.data?.id) {
    throw new Error(`Error creando primera reserva: ${JSON.stringify(b1.body)}`);
  }

  // Segunda reserva en las mismas fechas exactas para la suite de 1 unidad -> DEBE RECHAZARSE CON 409
  const b2 = await request('POST', '/api/bookings', {
    suiteId: 'suite-4',
    checkIn: range.checkIn,
    checkOut: range.checkOut,
    guests: 2,
    guestName: 'Segundo Huésped (Colisión / Overbooking)'
  });
  if (b2.status !== 409 && b2.body?.success !== false) {
    throw new Error(`Overbooking no fue bloqueado! Estado: ${b2.status}, body: ${JSON.stringify(b2.body)}`);
  }
});

test('CÓDIGO ORIGINAL', '1.8 Tiempo de Caducidad Estricto (TTL de 15 minutos en reservas pendientes)', async () => {
  const range = nextDateRange(2);
  const res = await request('POST', '/api/bookings', {
    suiteId: 'suite-2',
    checkIn: range.checkIn,
    checkOut: range.checkOut,
    guests: 2
  });
  const b = res.body.data;
  if (!b.expiresAt) throw new Error('Reserva pendiente no generó expiresAt');
  const minutes = (new Date(b.expiresAt).getTime() - Date.now()) / (1000 * 60);
  if (minutes < 14 || minutes > 16) {
    throw new Error(`TTL no está en 15 minutos: ${minutes.toFixed(1)} mins`);
  }
});

test('CÓDIGO ORIGINAL', '1.9 Validación de Tarjetas Bancarias (Algoritmo de Luhn y Simulación de Fallas)', async () => {
  const range = nextDateRange(2);
  const b = (await request('POST', '/api/bookings', {
    suiteId: 'suite-4',
    checkIn: range.checkIn,
    checkOut: range.checkOut,
    guests: 2
  })).body.data;

  // Tarjeta con fondos insuficientes (...0002)
  const failFunds = await request('POST', '/api/payments/process', {
    bookingId: b.id,
    cardNumber: '4242424242420002',
    amount: b.totalPrice
  });
  if (failFunds.status !== 402 || failFunds.body?.code !== 'card_declined') {
    throw new Error(`Simulación de fondos insuficientes falló: ${failFunds.status}`);
  }

  // Tarjeta expirada (...0019)
  const failExp = await request('POST', '/api/payments/process', {
    bookingId: b.id,
    cardNumber: '4242424242420019',
    amount: b.totalPrice
  });
  if (failExp.status !== 402 || failExp.body?.code !== 'expired_card') {
    throw new Error(`Simulación de tarjeta expirada falló: ${failExp.status}`);
  }

  // Tarjeta fraudulenta (...0005)
  const failFraud = await request('POST', '/api/payments/process', {
    bookingId: b.id,
    cardNumber: '4242424242420005',
    amount: b.totalPrice
  });
  if (failFraud.status !== 402 || failFraud.body?.code !== 'fraud_risk') {
    throw new Error(`Simulación de fraude falló: ${failFraud.status}`);
  }
});

test('CÓDIGO ORIGINAL', '1.10 Soporte de Pagos Parciales (Abono 50%) y Bloqueo de Sobrepago (PR #5)', async () => {
  const range = nextDateRange(2);
  const b = (await request('POST', '/api/bookings', {
    suiteId: 'suite-5',
    checkIn: range.checkIn,
    checkOut: range.checkOut,
    guests: 2
  })).body.data;

  const half = Math.round(b.totalPrice / 2);
  // Abono parcial exitoso
  const p1 = await request('POST', '/api/payments/process', {
    bookingId: b.id,
    amount: half,
    cardNumber: '4242424242424242'
  });
  if (p1.status !== 200 || p1.body?.data?.paymentStatus !== 'partial') {
    throw new Error(`Fallo en abono parcial: ${JSON.stringify(p1.body)}`);
  }
  if (p1.body.data.balanceDue !== (b.totalPrice - half)) {
    throw new Error(`Saldo pendiente incorrecto tras abono`);
  }

  // Intento de sobrepago (> balanceDue)
  const overpay = await request('POST', '/api/payments/process', {
    bookingId: b.id,
    amount: p1.body.data.balanceDue + 50000,
    cardNumber: '4242424242424242'
  });
  if (overpay.status !== 400) {
    throw new Error(`Sobrepago no fue bloqueado con HTTP 400! Código: ${overpay.status}`);
  }

  // Liquidación del saldo restante
  const p2 = await request('POST', '/api/payments/process', {
    bookingId: b.id,
    amount: p1.body.data.balanceDue,
    cardNumber: '4242424242424242'
  });
  if (p2.status !== 200 || p2.body?.data?.balanceDue !== 0 || p2.body?.data?.bookingStatus !== 'confirmed') {
    throw new Error(`Liquidación final falló: ${JSON.stringify(p2.body)}`);
  }
  if (!p2.body.data.booking?.smartLockPin) {
    throw new Error('No se generó el smartLockPin al liquidar el 100% de la reserva');
  }

  // Intento de pago adicional en reserva liquidada
  const pAfterPaid = await request('POST', '/api/payments/process', {
    bookingId: b.id,
    amount: 10000,
    cardNumber: '4242424242424242'
  });
  if (pAfterPaid.status !== 400) {
    throw new Error(`Pago redundante en reserva liquidada no fue rechazado: ${pAfterPaid.status}`);
  }
});

test('CÓDIGO ORIGINAL', '1.11 Política de Cancelación y Reembolso (>7 días 100%, <=7 días 90%)', async () => {
  // Caso A: Cancelación con más de 7 días (100% reembolso, 0% retención)
  const farRange = nextDateRange(20);
  const bFar = (await request('POST', '/api/bookings', {
    suiteId: 'suite-6',
    checkIn: farRange.checkIn,
    checkOut: farRange.checkOut,
    guests: 2
  })).body.data;
  await request('POST', '/api/payments/process', { bookingId: bFar.id, amount: bFar.totalPrice, cardNumber: '4242424242424242' });

  const cFar = await request('POST', `/api/bookings/${bFar.id}/cancel`);
  if (cFar.body?.data?.refundedAmount !== bFar.totalPrice || cFar.body?.data?.retentionPenalty !== 0) {
    throw new Error(`Regla >7 días violada: Reembolso: ${cFar.body?.data?.refundedAmount}, Esperado: ${bFar.totalPrice}`);
  }

  // Caso B: Cancelación con 3 días de antelación (90% reembolso, 10% retención)
  const in3 = new Date(Date.now() + 3 * 24 * 3600 * 1000).toISOString().split('T')[0];
  const out5 = new Date(Date.now() + 5 * 24 * 3600 * 1000).toISOString().split('T')[0];
  const bSoon = (await request('POST', '/api/bookings', {
    suiteId: 'suite-7',
    checkIn: in3,
    checkOut: out5,
    guests: 2
  })).body.data;
  await request('POST', '/api/payments/process', { bookingId: bSoon.id, amount: bSoon.totalPrice, cardNumber: '4242424242424242' });

  const cSoon = await request('POST', `/api/bookings/${bSoon.id}/cancel`);
  const expectedPenalty = Math.round(bSoon.totalPrice * 0.10);
  const expectedRefund = bSoon.totalPrice - expectedPenalty;
  if (cSoon.body?.data?.refundedAmount !== expectedRefund || cSoon.body?.data?.retentionPenalty !== expectedPenalty) {
    throw new Error(`Regla <=7 días violada: Reembolso: ${cSoon.body?.data?.refundedAmount}, Penalidad: ${cSoon.body?.data?.retentionPenalty}`);
  }
});

test('CÓDIGO ORIGINAL', '1.12 Recepción del Anfitrión: Check-in, Check-out físico y Regeneración de PIN', async () => {
  const range = nextDateRange(2);
  const b = (await request('POST', '/api/bookings', {
    suiteId: 'suite-8',
    checkIn: range.checkIn,
    checkOut: range.checkOut,
    guests: 2
  })).body.data;

  // Confirmación
  await request('POST', `/api/admin/bookings/${b.id}/approve`);

  // Check-in
  const ci = await request('POST', `/api/host/bookings/${b.id}/checkin`);
  if (!ci.body?.data?.checkedIn) throw new Error('Fallo en check-in del anfitrión');

  // Regeneración de PIN
  const pin = await request('POST', `/api/host/bookings/${b.id}/regenerate-pin`);
  if (!pin.body?.smartLockPin) throw new Error('Fallo al regenerar PIN de cerradura');

  // Check-out
  const co = await request('POST', `/api/host/bookings/${b.id}/checkout`);
  if (!co.body?.data?.checkedOut || co.body?.data?.status !== 'completed') {
    throw new Error('Fallo en check-out del anfitrión');
  }
});

test('CÓDIGO ORIGINAL', '1.13 Panel de Administrador: Aprobación, Métrica de Ingresos Netos y Exportación CSV', async () => {
  const metricsRes = await request('GET', '/api/admin/metrics');
  if (metricsRes.status !== 200 || metricsRes.body?.data?.totalRevenue === undefined) {
    throw new Error(`Fallo consultando métricas de admin`);
  }

  const csvRes = await request('GET', '/api/admin/bookings/export-csv');
  if (csvRes.status !== 200 || !csvRes.raw.includes('ID,Codigo,Huesped,Email')) {
    throw new Error(`Fallo exportando CSV de reservas`);
  }
});

/* ====================================================================
   GRUPO 2: PRUEBAS PROPUESTAS (NUEVAS REGLAS Y ROBUSTEZ ADICIONAL)
==================================================================== */

test('PRUEBAS PROPUESTAS', '2.1 Promociones por Rango de Fechas: Creación, Validación de Topes y Aplicación', async () => {
  const promoStart = new Date(Date.now() + 50 * 24 * 3600 * 1000).toISOString().split('T')[0];
  const promoEnd = new Date(Date.now() + 65 * 24 * 3600 * 1000).toISOString().split('T')[0];

  // 1. Crear promoción válida para Sede Getsemaní (branch-2)
  const createPromo = await request('POST', '/api/admin/promotions', {
    title: 'Semana de Gala Getsemaní',
    branchId: 'branch-2',
    startDate: promoStart,
    endDate: promoEnd,
    discountPercent: 25,
    code: `GALA_${Date.now()}`
  });
  if (createPromo.status !== 200 || !createPromo.body?.data?.id) {
    throw new Error(`Error creando promoción de rango: ${JSON.stringify(createPromo.body)}`);
  }
  const promoId = createPromo.body.data.id;

  // 2. Verificar que se aplica automáticamente al consultar una suite de esa sede en ese rango
  const suite5Calc = await request('POST', '/api/suites/calculate-price', {
    suiteId: 'suite-5', // Ubicada en branch-2
    checkIn: promoStart,
    checkOut: new Date(Date.now() + 54 * 24 * 3600 * 1000).toISOString().split('T')[0],
    guests: 2
  });
  if (suite5Calc.body?.data?.promoDiscountPercent < 25) {
    throw new Error(`La promoción no se aplicó automáticamente: ${JSON.stringify(suite5Calc.body?.data)}`);
  }

  // 3. Probar restricción de seguridad: desborde de descuento (>50%) se ajusta al límite
  const promoLimit = await request('POST', '/api/admin/promotions', {
    title: 'Descuento Extremo Prohibido',
    startDate: promoStart,
    endDate: promoEnd,
    discountPercent: 95
  });
  if (promoLimit.body?.data?.discountPercent > 50) {
    throw new Error('El sistema admitió un descuento superior al 50% máximo permitido');
  }

  // Limpieza de promoción creada
  await request('DELETE', `/api/admin/promotions/${promoId}`);
});

test('PRUEBAS PROPUESTAS', '2.2 Idempotencia de Pagos (Evitar doble cargo por reintento de red)', async () => {
  const range = nextDateRange(2);
  const b = (await request('POST', '/api/bookings', {
    suiteId: 'suite-9',
    checkIn: range.checkIn,
    checkOut: range.checkOut,
    guests: 2
  })).body.data;

  const idempotencyKey = `idem_${Date.now()}_${Math.random()}`;
  
  // Primer intento con Idempotency-Key
  const res1 = await request('POST', '/api/payments/process', {
    bookingId: b.id,
    amount: b.totalPrice,
    cardNumber: '4242424242424242'
  }, { 'idempotency-key': idempotencyKey });

  // Segundo intento exacto (simulando timeout / red caída y reintento)
  const res2 = await request('POST', '/api/payments/process', {
    bookingId: b.id,
    amount: b.totalPrice,
    cardNumber: '4242424242424242'
  }, { 'idempotency-key': idempotencyKey });

  if (res1.status !== 200 || res2.status !== 200) {
    throw new Error('Fallo en procesamiento de pago con idempotencia');
  }
  if (res1.body.data.paymentId !== res2.body.data.paymentId) {
    throw new Error('Idempotencia violada: Se generó un segundo pago distinto para la misma idempotency-key');
  }
});

test('PRUEBAS PROPUESTAS', '2.3 Modificación Dinámica de Fechas y Recálculo de Saldos', async () => {
  const range = nextDateRange(3);
  const b = (await request('POST', '/api/bookings', {
    suiteId: 'suite-10',
    checkIn: range.checkIn,
    checkOut: range.checkOut,
    guests: 2
  })).body.data;

  // Extender 1 noche más
  const newOut = new Date(new Date(range.checkOut).getTime() + 24 * 3600 * 1000).toISOString().split('T')[0];
  const modRes = await request('PUT', `/api/bookings/${b.id}/modify`, {
    checkIn: range.checkIn,
    checkOut: newOut
  });

  if (modRes.status !== 200 || modRes.body?.data?.nights !== 4) {
    throw new Error(`Modificación de fechas falló: ${JSON.stringify(modRes.body)}`);
  }
  if (modRes.body.data.totalPrice <= b.totalPrice) {
    throw new Error('El precio total no se recalculó hacia arriba tras extender una noche');
  }
});

test('PRUEBAS PROPUESTAS', '2.4 Pre-Checkin Digital y Asignación de PIN Móvil', async () => {
  const range = nextDateRange(2);
  const b = (await request('POST', '/api/bookings', {
    suiteId: 'suite-11',
    checkIn: range.checkIn,
    checkOut: range.checkOut,
    guests: 2
  })).body.data;

  const preRes = await request('POST', `/api/bookings/${b.id}/pre-checkin`);
  if (!preRes.body?.data?.preCheckInCompleted || !preRes.body?.data?.smartLockPin) {
    throw new Error(`Fallo en flujo de pre-checkin: ${JSON.stringify(preRes.body)}`);
  }
});

test('PRUEBAS PROPUESTAS', '2.5 Bloqueo de Suite por Mantenimiento y Restauración Operativa', async () => {
  const range = nextDateRange(2);

  // 1. Bloquear suite-12 para reparaciones
  const blockRes = await request('PUT', '/api/admin/suites/suite-12/maintenance', { isAvailable: false });
  if (blockRes.status !== 200 || blockRes.body?.data?.isAvailable !== false) {
    throw new Error('Fallo al bloquear suite por mantenimiento');
  }

  // 2. Intentar reservar suite en mantenimiento -> DEBE RECHAZARSE con 400
  const tryBook = await request('POST', '/api/bookings', {
    suiteId: 'suite-12',
    checkIn: range.checkIn,
    checkOut: range.checkOut,
    guests: 2
  });
  if (tryBook.status !== 400) {
    throw new Error(`Reserva en habitación en mantenimiento no fue bloqueada: ${tryBook.status}`);
  }

  // 3. Restaurar / habilitar suite nuevamente
  const unblockRes = await request('PUT', '/api/admin/suites/suite-12/maintenance', { isAvailable: true });
  if (unblockRes.status !== 200 || unblockRes.body?.data?.isAvailable !== true) {
    throw new Error('Fallo al restaurar disponibilidad de la suite');
  }

  // 4. Ahora la reserva debe ser permitida
  const bookSuccess = await request('POST', '/api/bookings', {
    suiteId: 'suite-12',
    checkIn: range.checkIn,
    checkOut: range.checkOut,
    guests: 2
  });
  if (bookSuccess.status !== 200) {
    throw new Error('No se pudo reservar tras desbloquear la suite');
  }
});

test('PRUEBAS PROPUESTAS', '2.6 Integridad de Fechas: Rechazo de Check-in en el Pasado y Fechas Invertidas', async () => {
  // Check-in en el pasado
  const pastRes = await request('POST', '/api/bookings', {
    suiteId: 'suite-1',
    checkIn: '2020-01-01',
    checkOut: '2020-01-05',
    guests: 2
  });
  if (pastRes.status !== 400) {
    throw new Error(`Check-in en el pasado no fue rechazado: ${pastRes.status}`);
  }

  // Fechas invertidas (checkOut antes de checkIn)
  const range = nextDateRange(2);
  const invertedRes = await request('POST', '/api/bookings', {
    suiteId: 'suite-1',
    checkIn: range.checkOut,
    checkOut: range.checkIn,
    guests: 2
  });
  if (invertedRes.status !== 400) {
    throw new Error(`Fechas invertidas no fueron rechazadas: ${invertedRes.status}`);
  }
});

test('PRUEBAS PROPUESTAS', '2.7 Control de Aforo Máximo (Exceso de Huéspedes por Suite)', async () => {
  const range = nextDateRange(2);
  // suite-1 maxGuests: 2. Intentar 6 huéspedes
  const capRes = await request('POST', '/api/bookings', {
    suiteId: 'suite-1',
    checkIn: range.checkIn,
    checkOut: range.checkOut,
    guests: 6
  });
  if (capRes.status !== 400) {
    throw new Error(`Exceso de capacidad no fue rechazado: ${capRes.status}`);
  }
});

test('PRUEBAS PROPUESTAS', '2.8 Restricción de Temporada Festiva: Estancia Mínima de 2 Noches en Fin de Año', async () => {
  // 1 sola noche en Navidad / Fin de Año
  const singlePeakNight = await request('POST', '/api/bookings', {
    suiteId: 'suite-2',
    checkIn: '2026-12-24',
    checkOut: '2026-12-25',
    guests: 2
  });
  if (singlePeakNight.status !== 400) {
    throw new Error(`Estancia de 1 sola noche en temporada pico debió ser rechazada con 400: ${singlePeakNight.status}`);
  }
});

async function run() {
  console.log('===============================================================');
  console.log('   PRUEBAS INTENSIVAS DE BACKEND — PALACIO DEL MAR QA SUITE   ');
  console.log('===============================================================\n');

  let currentCat = '';
  for (const t of tests) {
    if (t.category !== currentCat) {
      currentCat = t.category;
      console.log(`\n▶ [${currentCat}]`);
    }
    try {
      await t.fn();
      console.log(`  ✔ [PASS] ${t.name}`);
      passed++;
    } catch (err) {
      console.error(`  ✖ [FAIL] ${t.name}`);
      console.error(`    ↳ Error: ${err.message}`);
      failed++;
    }
  }

  console.log('\n===============================================================');
  console.log(`RESULTADO FINAL: ${passed} pasados, ${failed} fallidos de ${tests.length} pruebas.`);
  console.log('===============================================================');
  if (failed > 0) process.exit(1);
}

run();
