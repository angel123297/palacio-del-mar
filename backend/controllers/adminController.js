import mongoose from 'mongoose';
import Booking, { BOOKING_STATUS, STATUS_TRANSITIONS } from '../models/Booking.js';
import Promotion from '../models/Promotion.js';
import Branch from '../models/Branch.js';
import { todayCalendarDate } from '../utils/dates.js';
import { getCollectedAmount } from '../utils/pricing.js';
import { markPaymentsRefunded } from './bookingController.js';
import {
  BOOKING_VIEWS,
  bookingViewFilter,
  bookingViewSort,
  escapeRegex,
  promotionState,
  validatePromotionInput
} from '../utils/adminFilters.js';

const isoDay = (date) => new Date(date).toISOString().slice(0, 10);
const serverError = (res, label, error) => {
  console.error(`[${label}]:`, error);
  return res.status(500).json({ success: false, message: 'Error interno del servidor' });
};

// ============================================
// RESERVAS (estilo "Reservas" de Booking.com)
// ============================================

/**
 * @desc    Listado de reservas por vista (llegadas, salidas, en el hotel...) con búsqueda
 * @route   GET /api/admin/bookings
 * @access  Admin
 * @query   view, status, search, branch, page, limit
 */
export const listAdminBookings = async (req, res) => {
  try {
    const view = BOOKING_VIEWS.includes(req.query.view) ? req.query.view : 'all';
    const today = todayCalendarDate();
    const query = { ...bookingViewFilter(view, today) };

    const status = req.query.status;
    if (status && view === 'all' && Object.values(BOOKING_STATUS).includes(status)) query.status = status;

    if (req.query.branch && mongoose.isValidObjectId(req.query.branch)) query.branch = req.query.branch;

    const search = String(req.query.search || '').trim().slice(0, 80);
    if (search) {
      const rx = new RegExp(escapeRegex(search), 'i');
      query.$or = [{ guestName: rx }, { guestEmail: rx }, { guestPhone: rx }];
      if (mongoose.isValidObjectId(search)) query.$or.push({ _id: search });
    }

    const page = Math.max(parseInt(req.query.page, 10) || 1, 1);
    const limit = Math.min(Math.max(parseInt(req.query.limit, 10) || 20, 1), 100);

    const [bookings, total, arrivals, departures, inhouse, pending] = await Promise.all([
      Booking.find(query)
        .populate('suite', 'name')
        .populate('branch', 'name')
        .populate('user', 'name email')
        .sort(bookingViewSort(view))
        .skip((page - 1) * limit)
        .limit(limit),
      Booking.countDocuments(query),
      Booking.countDocuments(bookingViewFilter('arrivals', today)),
      Booking.countDocuments(bookingViewFilter('departures', today)),
      Booking.countDocuments(bookingViewFilter('inhouse', today)),
      Booking.countDocuments(bookingViewFilter('pending', today))
    ]);

    res.json({
      success: true,
      data: {
        bookings,
        today: isoDay(today),
        summary: { arrivals, departures, inhouse, pending },
        pagination: { currentPage: page, totalPages: Math.max(Math.ceil(total / limit), 1), totalItems: total }
      }
    });
  } catch (error) {
    return serverError(res, 'AdminListBookings', error);
  }
};

/**
 * @desc    Cambiar el estado de una reserva (confirmar, check-out, no-show, cancelar)
 * @route   PUT /api/admin/bookings/:id/status
 * @access  Admin
 * @body    status: confirmed | completed | no_show | cancelled, reason (solo al cancelar)
 *
 * Respeta STATUS_TRANSITIONS: una reserva vencida/cancelada/completada no se reabre.
 * Al terminar (cancelled/completed/no_show) el hook de Booking libera las noches.
 */
export const updateBookingStatus = async (req, res) => {
  try {
    const next = req.body.status;
    const booking = await Booking.findById(req.params.id);
    if (!booking) return res.status(404).json({ success: false, message: 'Reserva no encontrada' });

    if (booking.status === next) {
      return res.json({ success: true, message: 'La reserva ya tenía ese estado', data: { booking, unchanged: true } });
    }

    if (!(STATUS_TRANSITIONS[booking.status] || []).includes(next)) {
      return res.status(409).json({
        success: false,
        message: `No se puede pasar una reserva de "${booking.status}" a "${next}"`
      });
    }

    const today = todayCalendarDate();
    const startsInFuture = new Date(booking.checkIn).getTime() > today.getTime();
    if ((next === BOOKING_STATUS.COMPLETED || next === BOOKING_STATUS.NO_SHOW) && startsInFuture) {
      return res.status(409).json({
        success: false,
        message: 'Esa acción solo se puede registrar desde el día de llegada'
      });
    }

    if (next === BOOKING_STATUS.CANCELLED) {
      // Cancelación hecha por el hotel: sin penalización; se devuelve todo lo cobrado.
      const refundAmount = getCollectedAmount(booking);
      booking.cancelledAt = new Date();
      booking.cancellationDetails = {
        reason: String(req.body.reason || '').trim() || 'Cancelada por el hotel',
        cancellationFee: 0,
        refundAmount,
        refundStatus: refundAmount > 0 ? 'completed' : 'none',
        cancelledBy: req.user.id
      };
      if (refundAmount > 0) {
        booking.amountRefunded = (booking.amountRefunded || 0) + refundAmount;
      }
    }

    booking.status = next;
    await booking.save();

    if (next === BOOKING_STATUS.CANCELLED) {
      const refundAmount = booking.cancellationDetails?.refundAmount || 0;
      if (refundAmount > 0) {
        await markPaymentsRefunded(booking._id, refundAmount);
      }
    }

    res.json({ success: true, message: 'Estado de la reserva actualizado', data: { booking } });
  } catch (error) {
    if (error.name === 'ValidationError') {
      return res.status(400).json({ success: false, message: error.message });
    }
    return serverError(res, 'AdminUpdateBookingStatus', error);
  }
};

// ============================================
// PROMOCIONES (como "Ofertas y promociones" de Booking.com)
// ============================================

const promoJSON = (promo, today) => {
  const plain = promo.toObject ? promo.toObject() : promo;
  return { ...plain, state: promotionState(plain, today) };
};

/** Otras promociones activas de la misma sucursal que se cruzan con las fechas dadas. */
const findOverlaps = (promo) =>
  Promotion.find({
    _id: { $ne: promo._id },
    branch: promo.branch,
    active: true,
    startDate: { $lte: promo.endDate },
    endDate: { $gte: promo.startDate }
  }).select('title discountPercent startDate endDate').lean();

const overlapWarnings = async (promo) => {
  if (!promo.active) return [];
  const overlaps = await findOverlaps(promo);
  return overlaps.map(
    (o) => `Se cruza con "${o.title}" (${o.discountPercent}%, ${isoDay(o.startDate)} a ${isoDay(o.endDate)}): en las noches comunes se aplica la de mayor descuento`
  );
};

/**
 * @route   GET /api/admin/promotions
 * @access  Admin
 * @query   branch, state (current | upcoming | past | paused | all)
 */
export const listPromotions = async (req, res) => {
  try {
    const today = todayCalendarDate();
    const query = {};
    if (req.query.branch && mongoose.isValidObjectId(req.query.branch)) query.branch = req.query.branch;

    switch (req.query.state) {
      case 'current': Object.assign(query, { active: true, startDate: { $lte: today }, endDate: { $gte: today } }); break;
      case 'upcoming': Object.assign(query, { active: true, startDate: { $gt: today } }); break;
      case 'past': Object.assign(query, { active: true, endDate: { $lt: today } }); break;
      case 'paused': query.active = false; break;
      default: break;
    }

    const promotions = await Promotion.find(query).populate('branch', 'name slug').sort({ startDate: -1 });
    res.json({ success: true, data: { today: isoDay(today), promotions: promotions.map((p) => promoJSON(p, today)) } });
  } catch (error) {
    return serverError(res, 'AdminListPromotions', error);
  }
};

/**
 * @route   POST /api/admin/promotions
 * @access  Admin
 * @body    branch, title, discountPercent (1-60), startDate, endDate (YYYY-MM-DD, inclusive), active
 *
 * Una promoción activa REBAJA el precio de las reservas nuevas que toquen sus noches
 * (pricing.js). Las reservas ya hechas conservan el precio que se les guardó.
 */
export const createPromotion = async (req, res) => {
  try {
    const { errors, data } = validatePromotionInput(req.body);
    const branchId = req.body.branch;
    if (!mongoose.isValidObjectId(branchId) || !(await Branch.exists({ _id: branchId }))) {
      errors.push('La sucursal no existe');
    }
    if (errors.length) return res.status(400).json({ success: false, message: errors[0], errors });

    const promo = await Promotion.create({ ...data, branch: branchId, active: data.active ?? true, isSample: false });
    const warnings = await overlapWarnings(promo);
    await promo.populate('branch', 'name slug');
    res.status(201).json({ success: true, message: 'Promoción creada', data: { promotion: promoJSON(promo, todayCalendarDate()), warnings } });
  } catch (error) {
    return serverError(res, 'AdminCreatePromotion', error);
  }
};

/**
 * @route   PUT /api/admin/promotions/:id
 * @access  Admin
 * @body    cualquiera de: title, discountPercent, startDate, endDate, active
 */
export const updatePromotion = async (req, res) => {
  try {
    const promo = await Promotion.findById(req.params.id);
    if (!promo) return res.status(404).json({ success: false, message: 'Promoción no encontrada' });

    const { errors, data } = validatePromotionInput(req.body, promo);
    if (errors.length) return res.status(400).json({ success: false, message: errors[0], errors });

    promo.set({ ...data, isSample: false });
    await promo.save();
    const warnings = await overlapWarnings(promo);
    await promo.populate('branch', 'name slug');
    res.json({ success: true, message: 'Promoción actualizada', data: { promotion: promoJSON(promo, todayCalendarDate()), warnings } });
  } catch (error) {
    return serverError(res, 'AdminUpdatePromotion', error);
  }
};

/**
 * @route   DELETE /api/admin/promotions/:id
 * @access  Admin
 * Las reservas ya hechas guardan su propio desglose de precio, así que no cambian.
 */
export const deletePromotion = async (req, res) => {
  try {
    const promo = await Promotion.findByIdAndDelete(req.params.id);
    if (!promo) return res.status(404).json({ success: false, message: 'Promoción no encontrada' });
    res.json({ success: true, message: 'Promoción eliminada' });
  } catch (error) {
    return serverError(res, 'AdminDeletePromotion', error);
  }
};
