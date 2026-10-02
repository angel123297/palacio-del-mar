// ============================================
// CANCELACIÓN Y REEMBOLSO (BUG-003)
// ============================================
// Sin dependencias, para poder probarlo aislado. El reembolso se calcula
// sobre lo EFECTIVAMENTE COBRADO, nunca sobre el precio de la reserva.

const round2 = (n) => Math.round((Number(n) || 0) * 100) / 100;

/**
 * Importe neto que el hotel tiene cobrado (cobrado - ya reembolsado).
 * Compatibilidad: reservas marcadas 'paid' antes de existir `amountPaid`
 * (amountPaid = 0) se consideran cobradas por su total.
 */
export const getCollectedAmount = (booking) => {
  const { paymentStatus, totalPrice = 0 } = booking;
  const amountPaid = Number(booking.amountPaid) || 0;
  const amountRefunded = Number(booking.amountRefunded) || 0;

  let gross = 0;
  if (paymentStatus === 'paid') gross = amountPaid > 0 ? amountPaid : totalPrice;
  else if (paymentStatus === 'partial') gross = amountPaid;
  else return 0; // pending, failed y refunded: no hay dinero por devolver

  return Math.max(0, round2(gross - amountRefunded));
};

/**
 * Penalización (10% del precio si faltan menos de 7 días) y reembolso.
 * refund = max(0, cobrado_neto - penalización_retenida); la penalización
 * retenida nunca supera lo cobrado.
 */
export const calculateCancellation = (booking, daysUntilCheckIn) => {
  const collected = getCollectedAmount(booking);
  const policyFee =
    daysUntilCheckIn >= 0 && daysUntilCheckIn < 7 ? round2((booking.totalPrice || 0) * 0.1) : 0;
  const cancellationFee = Math.min(policyFee, collected);
  const refundAmount = Math.max(0, round2(collected - cancellationFee));

  return {
    collected,
    cancellationFee,
    refundAmount,
    // 'none' = no hay nada que devolver; 'pending' = falta ejecutar la devolución
    refundStatus: refundAmount > 0 ? 'pending' : 'none'
  };
};
