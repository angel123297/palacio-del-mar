import mongoose from 'mongoose';
import Review from '../models/Review.js';
import Booking from '../models/Booking.js';
import Suite from '../models/Suite.js';
import User from '../models/User.js';
import { reviewEligibility, publicName, MAX_COMMENT } from '../utils/reviewRules.js';

const isId = (v) => mongoose.Types.ObjectId.isValid(String(v)) && String(new mongoose.Types.ObjectId(String(v))) === String(v);

// Recalcula promedio y cantidad de la habitación a partir de las reseñas visibles
const refreshSuiteRating = async (suiteId) => {
  const [agg] = await Review.aggregate([
    { $match: { suite: new mongoose.Types.ObjectId(String(suiteId)), hidden: false } },
    { $group: { _id: null, avg: { $avg: '$rating' }, count: { $sum: 1 } } }
  ]);
  await Suite.updateOne(
    { _id: suiteId },
    { $set: { averageRating: agg ? Math.round(agg.avg * 10) / 10 : 0, reviewCount: agg?.count || 0 } }
  );
};

/** GET /api/reviews/suite/:suiteId (público) */
export const listSuiteReviews = async (req, res) => {
  try {
    if (!isId(req.params.suiteId)) return res.status(400).json({ success: false, message: 'ID de habitación inválido' });
    const reviews = await Review.find({ suite: req.params.suiteId, hidden: false })
      .sort({ createdAt: -1 }).limit(50).select('authorName rating comment createdAt').lean();
    res.json({ success: true, data: reviews });
  } catch (error) {
    console.error('[ListReviews Error]:', error);
    res.status(500).json({ success: false, message: 'Error al obtener las reseñas' });
  }
};

/** GET /api/reviews/booking/:bookingId: ¿puedo reseñar? ¿ya reseñé? (dueño) */
export const getBookingReviewState = async (req, res) => {
  try {
    if (!isId(req.params.bookingId)) return res.status(404).json({ success: false, message: 'Reserva no encontrada' });
    const booking = await Booking.findOne({ _id: req.params.bookingId, user: req.user.id });
    if (!booking) return res.status(404).json({ success: false, message: 'Reserva no encontrada' });
    const review = await Review.findOne({ booking: booking._id }).select('rating comment createdAt').lean();
    const eligibility = reviewEligibility(booking);
    res.json({ success: true, data: { review, canReview: eligibility.ok && !review, reason: review ? 'Ya reseñaste esta estadía' : eligibility.reason || null } });
  } catch (error) {
    console.error('[ReviewState Error]:', error);
    res.status(500).json({ success: false, message: 'Error al consultar la reseña' });
  }
};

/** POST /api/reviews { bookingId, rating, comment } */
export const createReview = async (req, res) => {
  try {
    const { bookingId, rating, comment } = req.body;
    const stars = Number(rating);
    if (!Number.isInteger(stars) || stars < 1 || stars > 5) {
      return res.status(400).json({ success: false, message: 'La calificación debe ser de 1 a 5 estrellas' });
    }
    const text = typeof comment === 'string' ? comment.trim() : '';
    if (text.length > MAX_COMMENT) {
      return res.status(400).json({ success: false, message: `El comentario no puede pasar de ${MAX_COMMENT} caracteres` });
    }
    if (!isId(bookingId)) return res.status(404).json({ success: false, message: 'Reserva no encontrada' });

    const booking = await Booking.findOne({ _id: bookingId, user: req.user.id });
    if (!booking) return res.status(404).json({ success: false, message: 'Reserva no encontrada' });

    const eligibility = reviewEligibility(booking);
    if (!eligibility.ok) return res.status(409).json({ success: false, message: eligibility.reason });

    const user = await User.findById(req.user.id).select('name');
    let review;
    try {
      review = await Review.create({
        suite: booking.suite,
        branch: booking.branch,
        user: req.user.id,
        booking: booking._id,
        authorName: publicName(user?.name),
        rating: stars,
        comment: text || undefined
      });
    } catch (err) {
      if (err?.code === 11000) return res.status(409).json({ success: false, message: 'Ya reseñaste esta estadía' });
      throw err;
    }
    await refreshSuiteRating(booking.suite);
    res.status(201).json({ success: true, message: '¡Gracias por tu reseña!', data: { rating: review.rating, comment: review.comment } });
  } catch (error) {
    console.error('[CreateReview Error]:', error);
    res.status(500).json({ success: false, message: 'Error al guardar la reseña' });
  }
};
