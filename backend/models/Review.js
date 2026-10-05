import mongoose from 'mongoose';

// Reseña de una estadía. Una por reserva (índice único): no se puede reseñar dos veces
// ni reseñar sin haber pagado y terminado la estadía (ver utils/reviewRules.js).
const reviewSchema = new mongoose.Schema(
  {
    suite: { type: mongoose.Schema.Types.ObjectId, ref: 'Suite', required: true, index: true },
    branch: { type: mongoose.Schema.Types.ObjectId, ref: 'Branch' },
    user: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true },
    booking: { type: mongoose.Schema.Types.ObjectId, ref: 'Booking', required: true, unique: true },
    authorName: { type: String, required: true }, // "Ana P." al momento de reseñar
    rating: { type: Number, required: true, min: 1, max: 5 },
    comment: { type: String, trim: true, maxlength: 600 },
    hidden: { type: Boolean, default: false } // para moderación del admin
  },
  { timestamps: true }
);

reviewSchema.index({ suite: 1, hidden: 1, createdAt: -1 });

export default mongoose.models.Review || mongoose.model('Review', reviewSchema);
