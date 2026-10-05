// Cuenta del huésped: favoritos y derechos sobre sus datos personales (Ley 1581 de 2012:
// conocer, rectificar y suprimir). Rectificar = PUT /api/auth/profile.
import mongoose from 'mongoose';
import User from '../models/User.js';
import Suite from '../models/Suite.js';
import Booking from '../models/Booking.js';
import Payment from '../models/Payment.js';
import Review from '../models/Review.js';
import { toCalendarDate } from '../utils/dates.js';

const isId = (v) => mongoose.Types.ObjectId.isValid(String(v)) && String(new mongoose.Types.ObjectId(String(v))) === String(v);
const fail = (res, status, message, code) => res.status(status).json({ success: false, message, ...(code && { code }) });

// ---------- Favoritos ----------

/** GET /api/account/favorites: habitaciones guardadas (solo las que siguen publicadas) */
export const listFavorites = async (req, res) => {
  try {
    const user = await User.findById(req.user.id).select('favorites');
    const suites = await Suite.find({ _id: { $in: user?.favorites || [] }, available: true })
      .populate('branch', 'name slug zone')
      .select('name type basePrice size mainImage maxGuests branch averageRating reviewCount');
    res.json({ success: true, data: suites });
  } catch (error) {
    console.error('[ListFavorites Error]:', error);
    fail(res, 500, 'Error al obtener tus favoritos');
  }
};

/** GET /api/account/favorites/ids: solo los ids (para pintar los corazones) */
export const favoriteIds = async (req, res) => {
  try {
    const user = await User.findById(req.user.id).select('favorites');
    res.json({ success: true, data: (user?.favorites || []).map(String) });
  } catch (error) {
    console.error('[FavoriteIds Error]:', error);
    fail(res, 500, 'Error al obtener tus favoritos');
  }
};

/** PUT /api/account/favorites/:suiteId (idempotente) */
export const addFavorite = async (req, res) => {
  try {
    if (!isId(req.params.suiteId)) return fail(res, 400, 'ID de habitación inválido');
    if (!(await Suite.exists({ _id: req.params.suiteId }))) return fail(res, 404, 'Habitación no encontrada');
    await User.updateOne({ _id: req.user.id }, { $addToSet: { favorites: req.params.suiteId } });
    res.json({ success: true });
  } catch (error) {
    console.error('[AddFavorite Error]:', error);
    fail(res, 500, 'No se pudo guardar el favorito');
  }
};

/** DELETE /api/account/favorites/:suiteId (idempotente) */
export const removeFavorite = async (req, res) => {
  try {
    if (!isId(req.params.suiteId)) return fail(res, 400, 'ID de habitación inválido');
    await User.updateOne({ _id: req.user.id }, { $pull: { favorites: req.params.suiteId } });
    res.json({ success: true });
  } catch (error) {
    console.error('[RemoveFavorite Error]:', error);
    fail(res, 500, 'No se pudo quitar el favorito');
  }
};

// ---------- Derechos sobre los datos ----------

/** GET /api/account/export: copia de los datos personales del huésped (JSON descargable) */
export const exportMyData = async (req, res) => {
  try {
    const user = await User.findById(req.user.id).lean();
    if (!user) return fail(res, 404, 'Usuario no encontrado');
    const [bookings, reviews] = await Promise.all([
      Booking.find({ user: user._id }).select('-__v').lean(),
      Review.find({ user: user._id }).select('suite booking rating comment createdAt').lean()
    ]);
    const payments = await Payment.find({ user: user._id })
      .select('booking amount currency method status receiptNumber approvedAt refundedAmount refundedAt createdAt').lean();

    res.setHeader('Content-Disposition', 'attachment; filename="mis-datos-palacio-del-mar.json"');
    res.json({
      generatedAt: new Date().toISOString(),
      account: {
        name: user.name, email: user.email, phone: user.profile?.phone || null,
        createdAt: user.createdAt, emailVerified: !!user.emailVerified, consent: user.consent || null
      },
      favorites: (user.favorites || []).map(String),
      bookings, payments, reviews
    });
  } catch (error) {
    console.error('[ExportMyData Error]:', error);
    fail(res, 500, 'No se pudo generar la copia de tus datos');
  }
};

/**
 * DELETE /api/account { password }
 * Suprime la cuenta. No se permite con estadías futuras confirmadas (primero hay que
 * cancelarlas). Las reservas pendientes se cancelan y liberan sus noches. Las reservas
 * y pagos ya hechos se conservan por obligación contable, desvinculados de una cuenta activa.
 */
export const deleteMyAccount = async (req, res) => {
  try {
    const user = await User.findById(req.user.id).select('+password');
    if (!user) return fail(res, 404, 'Usuario no encontrado');
    if (user.role === 'admin') return fail(res, 403, 'Una cuenta de administrador no se elimina desde aquí');

    if (!req.body?.password || !(await user.comparePassword(String(req.body.password)))) {
      // 400 y no 401: el frontend cierra la sesión ante cualquier 401
      return fail(res, 400, 'La contraseña es incorrecta');
    }

    const active = await Booking.countDocuments({
      user: user._id, status: 'confirmed', checkOut: { $gte: toCalendarDate(new Date()) }
    });
    if (active > 0) {
      return fail(res, 409, 'Tienes estadías confirmadas. Cancélalas antes de eliminar tu cuenta.', 'ACTIVE_BOOKINGS');
    }

    // Una a una: el guardado libera las noches ocupadas (hook de Booking)
    const pending = await Booking.find({ user: user._id, status: 'pending' });
    for (const b of pending) {
      b.status = 'cancelled';
      b.cancelledAt = new Date();
      b.cancellationDetails = { reason: 'Cuenta eliminada por el usuario', cancellationFee: 0, refundAmount: 0, refundStatus: 'none', cancelledBy: user._id };
      await b.save();
    }

    user.name = 'Usuario eliminado';
    user.favorites = [];
    if (user.profile) user.profile.phone = undefined;
    await user.softDelete();

    res.json({ success: true, message: 'Tu cuenta fue eliminada.' });
  } catch (error) {
    console.error('[DeleteMyAccount Error]:', error);
    fail(res, 500, 'No se pudo eliminar la cuenta');
  }
};
