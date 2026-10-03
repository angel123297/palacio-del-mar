import mongoose from 'mongoose';

// Promoción de una sucursal en una ventana de fechas (medianoche UTC).
// Por ahora es INFORMATIVA: se muestra al huésped pero no modifica el precio
// final. Más adelante el anfitrión las gestionará desde su propio panel.
const promotionSchema = new mongoose.Schema(
  {
    branch: { type: mongoose.Schema.Types.ObjectId, ref: 'Branch', required: true },
    title: { type: String, required: true, trim: true, maxlength: 80 },
    discountPercent: { type: Number, required: true, min: 1, max: 60 },
    startDate: { type: Date, required: true }, // primera noche cubierta
    endDate: { type: Date, required: true }, // última noche cubierta (inclusive)
    active: { type: Boolean, default: true },
    isSample: { type: Boolean, default: false } // dato de ejemplo del seed
  },
  { timestamps: true }
);

promotionSchema.path('endDate').validate(function (value) {
  return !this.startDate || value >= this.startDate;
}, 'La fecha final no puede ser anterior a la inicial');

promotionSchema.index({ branch: 1, active: 1, startDate: 1, endDate: 1 });

export default mongoose.models.Promotion || mongoose.model('Promotion', promotionSchema);
