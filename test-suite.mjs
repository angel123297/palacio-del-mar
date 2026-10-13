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

function getFutureDate(daysAhead) {
  const d = new Date(Date.now() + daysAhead * 24 * 3600 * 1000);
  return d.toISOString().split('T')[0];
}

const tests = [];
function test(name, fn) {
  tests.push({ name, fn });
}

let passed = 0;
let failed = 0;

test('1. Backend Health and Branches check', async () => {
  const res = await request('GET', '/api/branches');
  if (res.status !== 200 || !res.body?.data || res.body.data.length === 0) {
    throw new Error(`Branches endpoint failed: ${JSON.stringify(res.body)}`);
  }
});

test('2. Suite Availability and Calculation with dates', async () => {
  const checkIn = getFutureDate(40);
  const checkOut = getFutureDate(44);
  const res = await request('POST', '/api/suites/calculate-price', {
    suiteId: 'suite-1',
    checkIn,
    checkOut,
    guests: 2
  });
  if (res.status !== 200 || !res.body?.data?.totalPrice) {
    throw new Error(`Calculate price failed: ${JSON.stringify(res.body)}`);
  }
  if (res.body.data.nights !== 4) {
    throw new Error(`Expected 4 nights, got ${res.body.data.nights}`);
  }
});

test('3. Promotion Creation & Date Validation (Admin & Host)', async () => {
  const pStart = getFutureDate(10);
  const pEnd = getFutureDate(30);
  const validPromo = {
    title: 'Promoción Escapada Romántica Getsemaní',
    branchId: 'branch-2',
    startDate: pStart,
    endDate: pEnd,
    discountPercent: 20,
    code: `ROMANCE_${Date.now()}`
  };
  const resValid = await request('POST', '/api/admin/promotions', validPromo);
  if (resValid.status !== 200 || !resValid.body?.data?.id) {
    throw new Error(`Failed to create valid promotion: ${JSON.stringify(resValid.body)}`);
  }
  const promoId = resValid.body.data.id;

  // Test 3b: Verify price calculation automatically applies promotion for suite-5 (branch-2)
  const calcRes = await request('POST', '/api/suites/calculate-price', {
    suiteId: 'suite-5', // in branch-2
    checkIn: getFutureDate(15),
    checkOut: getFutureDate(18),
    guests: 2
  });
  if (!calcRes.body?.data?.promoDiscountPercent || calcRes.body.data.promoDiscountPercent < 20) {
    throw new Error(`Promotion was not applied automatically: ${JSON.stringify(calcRes.body.data)}`);
  }

  // Test 3c: Invalid promotion with discount > 50% must be capped
  const cappedPromo = {
    title: 'Promoción Ilegal 80% OFF',
    branchId: 'branch-1',
    startDate: pStart,
    endDate: pEnd,
    discountPercent: 80
  };
  const resCapped = await request('POST', '/api/admin/promotions', cappedPromo);
  if (resCapped.body?.data?.discountPercent > 50) {
    throw new Error(`Discount exceeded 50% limit: ${resCapped.body.data.discountPercent}`);
  }

  // Cleanup promo
  await request('DELETE', `/api/admin/promotions/${promoId}`);
});

test('4. Guest Booking Creation with 15-minute TTL', async () => {
  const checkIn = getFutureDate(50);
  const checkOut = getFutureDate(54);
  const bookingData = {
    suiteId: 'suite-1',
    checkIn,
    checkOut,
    guests: 2,
    guestName: 'Carlos Mendoza',
    guestEmail: 'carlos.qa@test.com',
    guestPhone: '+57 310 999 8877'
  };
  const res = await request('POST', '/api/bookings', bookingData);
  if (res.status !== 200 || !res.body?.data?.id) {
    throw new Error(`Failed to create booking: ${JSON.stringify(res.body)}`);
  }
  const b = res.body.data;
  if (!b.expiresAt) {
    throw new Error('Booking did not receive an expiresAt TTL');
  }
  const expDiff = (new Date(b.expiresAt).getTime() - Date.now()) / (1000 * 60);
  if (expDiff < 14 || expDiff > 16) {
    throw new Error(`Expected ~15 minute TTL, got ${expDiff.toFixed(1)} minutes`);
  }
  if (b.status !== 'pending' || b.paymentStatus !== 'pending') {
    throw new Error(`Expected pending status, got status=${b.status}, paymentStatus=${b.paymentStatus}`);
  }

  // Cancel booking to free slot
  await request('POST', `/api/bookings/${b.id}/cancel`);
});

test('5. Partial Payment (50% Deposit) and Overpayment Protection', async () => {
  const checkIn = getFutureDate(60);
  const checkOut = getFutureDate(64);
  const createRes = await request('POST', '/api/bookings', {
    suiteId: 'suite-2',
    checkIn,
    checkOut,
    guests: 2,
    guestName: 'Elena Ramos',
    guestEmail: 'elena.qa@test.com'
  });
  if (createRes.status !== 200 || !createRes.body?.data?.id) {
    throw new Error(`Failed to create booking: ${JSON.stringify(createRes.body)}`);
  }
  const booking = createRes.body.data;
  const initialTotal = booking.totalPrice;
  const halfAmount = Math.round(initialTotal / 2);

  // 5b. Pay 50% deposit
  const pay1 = await request('POST', '/api/payments/process', {
    bookingId: booking.id,
    amount: halfAmount,
    method: 'CREDIT_CARD',
    cardNumber: '4242424242424242',
    cardDetails: { name: 'Elena Ramos', last4: '4242' }
  });
  if (pay1.status !== 200 || pay1.body?.data?.balanceDue !== (initialTotal - halfAmount)) {
    throw new Error(`Partial payment calculation failed: ${JSON.stringify(pay1.body)}`);
  }
  if (pay1.body.data.paymentStatus !== 'partial') {
    throw new Error(`Expected paymentStatus 'partial', got ${pay1.body.data.paymentStatus}`);
  }

  // 5c. Try Overpayment (> balanceDue) -> MUST FAIL with 400
  const overpayAmount = pay1.body.data.balanceDue + 50000;
  const overpayRes = await request('POST', '/api/payments/process', {
    bookingId: booking.id,
    amount: overpayAmount,
    method: 'CREDIT_CARD',
    cardNumber: '4242424242424242'
  });
  if (overpayRes.status !== 400) {
    throw new Error(`Overpayment was not blocked! HTTP status: ${overpayRes.status}, body: ${JSON.stringify(overpayRes.body)}`);
  }

  // 5d. Pay remaining balance
  const remainingAmount = pay1.body.data.balanceDue;
  const pay2 = await request('POST', '/api/payments/process', {
    bookingId: booking.id,
    amount: remainingAmount,
    method: 'CREDIT_CARD',
    cardNumber: '4242424242424242',
    cardDetails: { name: 'Elena Ramos', last4: '4242' }
  });
  if (pay2.status !== 200 || pay2.body?.data?.balanceDue !== 0) {
    throw new Error(`Final balance payment failed: ${JSON.stringify(pay2.body)}`);
  }
  if (pay2.body.data.paymentStatus !== 'paid' || pay2.body.data.bookingStatus !== 'confirmed') {
    throw new Error(`Booking was not fully confirmed after balance reached 0: ${JSON.stringify(pay2.body.data)}`);
  }
  if (!pay2.body.data.booking?.smartLockPin) {
    throw new Error('SmartLockPin was not generated upon full payment completion');
  }

  // 5e. Try paying again once already paid in full -> MUST FAIL with 400
  const payAgain = await request('POST', '/api/payments/process', {
    bookingId: booking.id,
    amount: 10000,
    method: 'CREDIT_CARD',
    cardNumber: '4242424242424242'
  });
  if (payAgain.status !== 400) {
    throw new Error(`Payment on already-paid booking was not rejected: ${payAgain.status}`);
  }

  // Cleanup
  await request('POST', `/api/bookings/${booking.id}/cancel`);
});

test('6. Cancellation Policy: > 7 days (100% refund) and <= 7 days (90% refund)', async () => {
  // 6a. Booking > 7 days ahead (checkIn in 25 days)
  const futureCheckIn = getFutureDate(25);
  const futureCheckOut = getFutureDate(28);
  
  const b1 = (await request('POST', '/api/bookings', {
    suiteId: 'suite-3',
    checkIn: futureCheckIn,
    checkOut: futureCheckOut,
    guests: 2
  })).body?.data;

  if (!b1 || !b1.id) throw new Error('Failed to create booking 1 for cancellation test');

  // Pay in full
  await request('POST', '/api/payments/process', {
    bookingId: b1.id,
    amount: b1.totalPrice,
    cardNumber: '4242424242424242'
  });

  // Cancel > 7 days ahead
  const cancel1 = await request('POST', `/api/bookings/${b1.id}/cancel`, { reason: 'Test cancel > 7 days' });
  if (cancel1.status !== 200) {
    throw new Error(`Cancel failed: ${JSON.stringify(cancel1.body)}`);
  }
  if (cancel1.body.data.refundedAmount !== b1.totalPrice || cancel1.body.data.retentionPenalty !== 0) {
    throw new Error(`100% refund policy violated: refunded=${cancel1.body.data.refundedAmount}, penalty=${cancel1.body.data.retentionPenalty}`);
  }

  // 6b. Booking <= 7 days ahead (checkIn in 3 days)
  const soonCheckIn = getFutureDate(3);
  const soonCheckOut = getFutureDate(5);

  const b2 = (await request('POST', '/api/bookings', {
    suiteId: 'suite-4',
    checkIn: soonCheckIn,
    checkOut: soonCheckOut,
    guests: 2
  })).body?.data;

  if (!b2 || !b2.id) throw new Error('Failed to create booking 2 for cancellation test');

  await request('POST', '/api/payments/process', {
    bookingId: b2.id,
    amount: b2.totalPrice,
    cardNumber: '4242424242424242'
  });

  const cancel2 = await request('POST', `/api/bookings/${b2.id}/cancel`, { reason: 'Test cancel <= 7 days' });
  const expectedPenalty = Math.round(b2.totalPrice * 0.10);
  const expectedRefund = b2.totalPrice - expectedPenalty;
  if (cancel2.body.data.refundedAmount !== expectedRefund || cancel2.body.data.retentionPenalty !== expectedPenalty) {
    throw new Error(`90% refund policy violated: refunded=${cancel2.body.data.refundedAmount} (expected ${expectedRefund}), penalty=${cancel2.body.data.retentionPenalty} (expected ${expectedPenalty})`);
  }
});

test('7. Admin Booking Approval & PIN Assignment', async () => {
  const b = (await request('POST', '/api/bookings', {
    suiteId: 'suite-5',
    checkIn: getFutureDate(70),
    checkOut: getFutureDate(73),
    guests: 2
  })).body?.data;

  if (!b || !b.id) {
    throw new Error('Failed to create booking for approval test');
  }

  const approveRes = await request('POST', `/api/admin/bookings/${b.id}/approve`);
  if (approveRes.status !== 200) {
    throw new Error(`Approve booking failed: ${JSON.stringify(approveRes.body)}`);
  }
  const updated = approveRes.body.data;
  if (updated.status !== 'confirmed') {
    throw new Error(`Status was not changed to confirmed: ${updated.status}`);
  }
  if (!updated.smartLockPin) {
    throw new Error('smartLockPin was not assigned upon approval');
  }

  // Cleanup
  await request('POST', `/api/bookings/${b.id}/cancel`);
});

test('8. Host Reception: Check-in, Check-out & PIN Regeneration', async () => {
  const b = (await request('POST', '/api/bookings', {
    suiteId: 'suite-6',
    checkIn: getFutureDate(80),
    checkOut: getFutureDate(83),
    guests: 1
  })).body?.data;

  if (!b || !b.id) throw new Error('Failed to create booking for host reception test');

  // Confirm booking
  await request('POST', `/api/admin/bookings/${b.id}/approve`);

  // Check-in
  const ciRes = await request('POST', `/api/host/bookings/${b.id}/checkin`);
  if (ciRes.status !== 200 || !ciRes.body?.data?.checkedIn) {
    throw new Error(`Check-in failed: ${JSON.stringify(ciRes.body)}`);
  }

  // Regenerate PIN
  const regenRes = await request('POST', `/api/host/bookings/${b.id}/regenerate-pin`);
  if (regenRes.status !== 200 || !regenRes.body?.smartLockPin) {
    throw new Error(`PIN regeneration failed: ${JSON.stringify(regenRes.body)}`);
  }

  // Check-out
  const coRes = await request('POST', `/api/host/bookings/${b.id}/checkout`);
  if (coRes.status !== 200 || !coRes.body?.data?.checkedOut || coRes.body?.data?.status !== 'completed') {
    throw new Error(`Check-out failed: ${JSON.stringify(coRes.body)}`);
  }
});

test('9. Admin CSV Export Check', async () => {
  const res = await request('GET', '/api/admin/bookings/export-csv');
  if (res.status !== 200) {
    throw new Error(`CSV export failed: status=${res.status}`);
  }
  if (!res.raw.includes('ID,Codigo,Huesped,Email')) {
    throw new Error(`CSV content header missing: ${res.raw.substring(0, 50)}`);
  }
});

async function run() {
  console.log('--- INICIANDO TEST INTENSIVO PALACIO DEL MAR ---');
  for (const t of tests) {
    try {
      await t.fn();
      console.log(`[PASS] ${t.name}`);
      passed++;
    } catch (err) {
      console.error(`[FAIL] ${t.name}:`, err.message);
      failed++;
    }
  }
  console.log(`\nRESUMEN: ${passed} pasados, ${failed} fallidos de ${tests.length} pruebas.`);
  if (failed > 0) process.exit(1);
}

run();
