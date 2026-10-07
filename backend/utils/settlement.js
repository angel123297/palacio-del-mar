// Cuando el total de una reserva cambia DESPUÉS de haber recibido dinero (días extra,
// experiencias agregadas o quitadas), hay que cobrar la diferencia o devolverla.
// Una sola regla para todos los casos; trabaja sobre el documento sin tocar la base de datos.
//
// Convención (la misma que getCollectedAmount y la cancelación):
//   amountPaid     = dinero recibido en total (bruto)
//   amountRefunded = dinero devuelto
//   en poder del hotel (neto) = amountPaid - amountRefunded
import { getCollectedAmount } from './pricing.js';

/**
 * Asigna `newTotal` a la reserva y ajusta el estado de pago.
 * Devuelve { refundAmount, balanceDue }.
 *  - Sin dinero recibido (pending/failed): solo cambia el total; el estado no se toca.
 *  - Nuevo total mayor que lo cobrado: paymentStatus = 'partial' y balanceDue = lo que falta.
 *  - Nuevo total menor que lo cobrado: se devuelve la diferencia (amountRefunded) y sigue 'paid'.
 */
export const settleTotalChange = (booking, newTotal) => {
  const refundedBefore = Number(booking.amountRefunded) || 0;
  const collected = getCollectedAmount(booking); // con el total ANTERIOR (reservas antiguas pagadas sin amountPaid)

  booking.totalPrice = newTotal;
  if (collected <= 0) return { refundAmount: 0, balanceDue: 0 };

  // Reservas antiguas marcadas como pagadas sin amountPaid: dejar el bruto explícito
  if (!(Number(booking.amountPaid) > 0)) booking.amountPaid = collected + refundedBefore;

  if (newTotal > collected) {
    booking.paymentStatus = 'partial';
    return { refundAmount: 0, balanceDue: newTotal - collected };
  }
  booking.paymentStatus = 'paid';
  const refundAmount = collected - newTotal;
  if (refundAmount > 0) booking.amountRefunded = refundedBefore + refundAmount;
  return { refundAmount, balanceDue: 0 };
};
