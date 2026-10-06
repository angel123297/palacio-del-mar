import mongoose from 'mongoose';

// Intento de pago de una reserva. Hoy lo produce el proveedor SIMULADO.
// Nunca se guardan datos de tarjeta.
const paymentSchema = new mongoose.Schema(
  {
    booking: { type: mongoose.Schema.Types.ObjectId, ref: 'Booking', required: true, index: true },
    user: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true },
    amount: { type: Number, required: true, min: 0 },
    currency: { type: String, default: 'COP' },
    method: { type: String, enum: ['card', 'pse', 'nequi'], required: true },
    provider: { type: String, required: true },
    // processing -> approved | declined | void (la reserva venció mientras se pagaba) | refunded
    status: { type: String, enum: ['processing', 'approved', 'declined', 'void', 'refunded'], default: 'processing' },
    providerRef: { type: String },
    // Misma clave = mismo intento: un doble clic nunca cobra dos veces
    idempotencyKey: { type: String, required: true },
    receiptNumber: { type: String },
    failureReason: { type: String },
    approvedAt: { type: Date },
    refundedAmount: { type: Number, min: 0 },
    refundedAt: { type: Date },
    // true mientras el pago está en proceso o aprobado. El índice único parcial de
    // abajo permite UN solo pago activo por reserva aunque lleguen dos peticiones
    // con claves de idempotencia distintas (con una pasarela real serían dos cobros).
    active: { type: Boolean }
  },
  { timestamps: true }
);

paymentSchema.index({ booking: 1, idempotencyKey: 1 }, { unique: true });
paymentSchema.index(
  { booking: 1 },
  { unique: true, partialFilterExpression: { active: true }, name: 'one_active_payment_per_booking' }
);
paymentSchema.index({ receiptNumber: 1 }, { unique: true, sparse: true });

export default mongoose.models.Payment || mongoose.model('Payment', paymentSchema);
