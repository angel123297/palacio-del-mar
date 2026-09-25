import Booking, { BOOKING_STATUS, PAYMENT_STATUS, STATUS_TRANSITIONS, PAYMENT_STATUS_TRANSITIONS } from '../models/Booking.js';
import SuiteNight, { NightsConflictError } from '../models/SuiteNight.js';
import { toCalendarDate, todayCalendarDate, calculateNights, eachNight, daysBetween } from '../utils/dates.js';
import { calculateCancellation, getCollectedAmount } from '../utils/pricing.js';

const BLOCKING_STATUSES = [BOOKING_STATUS.PENDING, BOOKING_STATUS.CONFIRMED];
import Suite from '../models/Suite.js';
import Experience from '../models/Experience.js';
import { getSeason, calculateSeasonalPrice } from './suiteController.js';
import { sendBookingConfirmationEmail } from '../utils/email.js';

// ============================================
// HELPER FUNCTIONS
// ============================================

/**
 * Valida que las fechas sean correctas. Las fechas son FECHAS DE CALENDARIO
 * (utils/dates.js): sin zonas horarias del servidor ni horas (CONS-014).
 */
const validateDates = (checkIn, checkOut) => {
  const errors = [];
  const checkInDate = toCalendarDate(checkIn);
  const checkOutDate = toCalendarDate(checkOut);

  if (!checkInDate || !checkOutDate) {
    return { isValid: false, errors: ['Fechas con formato inválido (use YYYY-MM-DD)'], nights: 0, checkInDate, checkOutDate };
  }

  if (checkInDate < todayCalendarDate()) {
    errors.push('La fecha de check-in no puede ser anterior a hoy');
  }

  if (checkOutDate <= checkInDate) {
    errors.push('La fecha de check-out debe ser posterior al check-in');
  }

  const maxNights = 90;
  const nights = calculateNights(checkInDate, checkOutDate);
  if (nights > maxNights) {
    errors.push(`La estadía no puede exceder ${maxNights} noches`);
  }

  return { isValid: errors.length === 0, errors, nights, checkInDate, checkOutDate };
};

/**
 * Comprobación amistosa de disponibilidad (responde 409 rápido). NO es la
 * garantía contra la doble reserva: esa la da SuiteNight (clave única).
 * Usa el mismo conjunto de estados que el resto de consultas (BUG-005).
 */
const isSuiteAvailable = (suiteId, checkInDate, checkOutDate, excludeBookingId = null) =>
  Booking.checkSuiteAvailability(suiteId, checkInDate, checkOutDate, excludeBookingId);

const conflictResponse = (res, message) =>
  res.status(409).json({ success: false, message });

/**
 * Calcula el precio total de la reserva, aplicando el mismo recargo por
 * temporada que usa /api/suites/calculate-price (para que el precio que
 * se le mostró al huésped durante la búsqueda coincida con el que se le
 * cobra), más el 10%/5% de descuento por estadías largas.
 * Devuelve un desglose completo, no solo el total.
 */
const calculateTotalPrice = async (suiteId, checkInDate, nights, experienceIds) => {
  const suite = await Suite.findById(suiteId);
  if (!suite) throw new Error('Suite no encontrada');

  const season = getSeason(checkInDate);
  const nightlyPrice = calculateSeasonalPrice(suite.basePrice, season);
  const subtotal = nightlyPrice * nights;

  let experiencesTotal = 0;
  if (experienceIds && experienceIds.length > 0) {
    const experiences = await Experience.find({ _id: { $in: experienceIds } });
    experiencesTotal = experiences.reduce((sum, exp) => sum + exp.price, 0);
  }

  const preDiscountTotal = subtotal + experiencesTotal;
  let discount = 0;
  if (nights >= 7) discount = preDiscountTotal * 0.1;
  else if (nights >= 5) discount = preDiscountTotal * 0.05;

  return {
    season,
    nightlyPrice,
    subtotal,
    experiencesTotal,
    discount,
    totalPrice: preDiscountTotal - discount
  };
};

/**
 * Instrucciones de pago manual. El proyecto no tiene credenciales de
 * ninguna pasarela de pago (Wompi, PayU, Mercado Pago...) configuradas;
 * en vez de devolver un enlace inventado a una ruta que no existe
 * (`/api/payments/initiate/:id`, que antes daba 404 siempre), se informa
 * al huésped cómo se confirma el pago hoy: transferencia + WhatsApp, y un
 * administrador marca la reserva como pagada desde el panel.
 */
const getPaymentInstructions = (booking) => ({
  method: 'manual',
  message: 'Tu reserva quedó registrada como pendiente de pago. Nuestro equipo te contactará por WhatsApp con los datos para la transferencia o el enlace de pago.',
  whatsapp: process.env.HOTEL_WHATSAPP || '+57 300 000 0000',
  reference: String(booking._id).slice(-8).toUpperCase()
});

// ============================================
// MAIN CONTROLLERS
// ============================================

/**
 * @desc    Crear una nueva reserva
 * @route   POST /api/bookings
 * @access  Private
 */
export const createBooking = async (req, res) => {
  try {
    const {
      suiteId,
      checkIn,
      checkOut,
      guests,
      experiences,
      guestName,
      guestEmail,
      guestPhone,
      specialRequests
    } = req.body;
    
    // 1. Validar campos requeridos
    if (!suiteId || !checkIn || !checkOut || !guests) {
      return res.status(400).json({
        success: false,
        message: 'Faltan campos requeridos: suiteId, checkIn, checkOut, guests'
      });
    }
    
    // 2. Validar fechas
    const dateValidation = validateDates(checkIn, checkOut);
    if (!dateValidation.isValid) {
      return res.status(400).json({
        success: false,
        message: 'Fechas inválidas',
        errors: dateValidation.errors
      });
    }
    
    const { nights, checkInDate, checkOutDate } = dateValidation;
    
    // 3. Verificar que la suite existe
    const suite = await Suite.findById(suiteId);
    if (!suite) {
      return res.status(404).json({
        success: false,
        message: 'Suite no encontrada'
      });
    }
    
    // 4. Verificar capacidad de huéspedes
    if (guests > suite.maxGuests) {
      return res.status(400).json({
        success: false,
        message: `La suite solo permite hasta ${suite.maxGuests} huéspedes`
      });
    }
    
    // 5. Verificar disponibilidad
    const isAvailable = await isSuiteAvailable(suiteId, checkInDate, checkOutDate);
    if (!isAvailable) {
      return res.status(409).json({
        success: false,
        message: 'La suite no está disponible para las fechas seleccionadas'
      });
    }
    
    // 6. Validar experiencias si existen
    if (experiences && experiences.length > 0) {
      const maxExperiences = 5;
      if (experiences.length > maxExperiences) {
        return res.status(400).json({
          success: false,
          message: `No se pueden agregar más de ${maxExperiences} experiencias`
        });
      }
      
      const experienceObjects = await Experience.find({ _id: { $in: experiences } });
      if (experienceObjects.length !== experiences.length) {
        return res.status(400).json({
          success: false,
          message: 'Una o más experiencias no existen'
        });
      }
    }
    
    // 7. Calcular precio total (con temporada y descuentos por estadía larga)
    const pricing = await calculateTotalPrice(suiteId, checkInDate, nights, experiences);
    
    // 8. Validar datos de contacto.
    // req.user solo trae { id, role }; los datos de contacto del usuario
    // logueado (nombre/email) viven en req.userData, que adjunta
    // authMiddleware. Usar req.user.name/req.user.email (como hacía la
    // versión anterior) siempre daba "undefined" cuando el huésped no
    // escribía su nombre/email a mano en el formulario.
    const finalGuestName = guestName || req.userData?.name;
    const finalGuestEmail = guestEmail || req.userData?.email;
    
    if (!finalGuestName || !finalGuestEmail) {
      return res.status(400).json({
        success: false,
        message: 'Nombre y email del huésped son requeridos'
      });
    }
    
    // 9. Crear la reserva
    const booking = new Booking({
      user: req.user.id,
      suite: suiteId,
      checkIn: checkInDate,
      checkOut: checkOutDate,
      guests: parseInt(guests),
      experiences: experiences || [],
      subtotal: pricing.subtotal,
      experiencesTotal: pricing.experiencesTotal,
      discount: pricing.discount,
      discountReason: pricing.discount > 0
        ? (nights >= 7 ? 'Descuento por estadía de 7+ noches (10%)' : 'Descuento por estadía de 5+ noches (5%)')
        : null,
      totalPrice: pricing.totalPrice,
      guestName: finalGuestName,
      guestEmail: finalGuestEmail,
      guestPhone: guestPhone || null,
      specialRequests: specialRequests || null,
      status: 'pending',
      bookingDate: new Date(),
      paymentStatus: 'pending'
    });
    
    // 9b. Adquirir las noches de forma atómica (BUG-001). Si otra solicitud
    // concurrente ya las tiene, MongoDB rechaza el insert por clave única.
    try {
      await SuiteNight.acquire(suiteId, booking._id, eachNight(checkInDate, checkOutDate));
    } catch (err) {
      if (err instanceof NightsConflictError) {
        return conflictResponse(res, 'La suite no está disponible para las fechas seleccionadas');
      }
      throw err;
    }
    
    try {
      await booking.save();
    } catch (err) {
      await SuiteNight.release(booking._id).catch(() => {});
      throw err;
    }
    await booking.populate('suite experiences');
    
    // 10. Enviar email de confirmación (opcional, no bloqueante: si no hay
    // SMTP configurado simplemente no se envía nada, no rompe la reserva)
    sendBookingConfirmationEmail(booking, finalGuestEmail, finalGuestName).catch(err => {
      console.error('[CreateBooking] Error enviando email de confirmación:', err.message);
    });
    
    // 11. Responder
    res.status(201).json({
      success: true,
      message: 'Reserva creada exitosamente',
      data: {
        booking,
        payment: getPaymentInstructions(booking)
      }
    });
    
  } catch (error) {
    console.error('[CreateBooking Error]:', error);
    res.status(500).json({
      success: false,
      message: 'Error interno del servidor al crear la reserva'
    });
  }
};

/**
 * @desc    Obtener todas las reservas del usuario
 * @route   GET /api/bookings
 * @access  Private
 */
export const getUserBookings = async (req, res) => {
  try {
    const { status, page = 1, limit = 10 } = req.query;
    
    const query = { user: req.user.id };
    if (status) query.status = status;
    
    const skip = (parseInt(page) - 1) * parseInt(limit);
    
    const [bookings, total] = await Promise.all([
      Booking.find(query)
        .populate('suite')
        .populate('experiences')
        .sort('-createdAt')
        .skip(skip)
        .limit(parseInt(limit)),
      Booking.countDocuments(query)
    ]);
    
    res.json({
      success: true,
      data: {
        bookings,
        pagination: {
          currentPage: parseInt(page),
          totalPages: Math.ceil(total / parseInt(limit)),
          totalItems: total,
          itemsPerPage: parseInt(limit)
        }
      }
    });
    
  } catch (error) {
    console.error('[GetUserBookings Error]:', error);
    res.status(500).json({
      success: false,
      message: 'Error interno del servidor'
    });
  }
};

/**
 * @desc    Obtener una reserva por ID
 * @route   GET /api/bookings/:id
 * @access  Private
 */
export const getBookingById = async (req, res) => {
  try {
    const { id } = req.params;
    
    const booking = await Booking.findById(id)
      .populate('suite')
      .populate('experiences');
    
    if (!booking) {
      return res.status(404).json({
        success: false,
        message: 'Reserva no encontrada'
      });
    }
    
    // Verificar autorización
    if (booking.user.toString() !== req.user.id && req.user.role !== 'admin') {
      return res.status(403).json({
        success: false,
        message: 'No autorizado para ver esta reserva'
      });
    }
    
    res.json({
      success: true,
      data: booking
    });
    
  } catch (error) {
    console.error('[GetBookingById Error]:', error);
    res.status(500).json({
      success: false,
      message: 'Error interno del servidor'
    });
  }
};

/**
 * @desc    Cancelar una reserva
 * @route   PUT /api/bookings/:id/cancel
 * @access  Private
 */
export const cancelBooking = async (req, res) => {
  try {
    const { id } = req.params;
    
    const booking = await Booking.findById(id);
    
    if (!booking) {
      return res.status(404).json({
        success: false,
        message: 'Reserva no encontrada'
      });
    }
    
    // Verificar autorización
    if (booking.user.toString() !== req.user.id && req.user.role !== 'admin') {
      return res.status(403).json({
        success: false,
        message: 'No autorizado para cancelar esta reserva'
      });
    }
    
    // Idempotencia: cancelar de nuevo una reserva ya cancelada devuelve el
    // resultado original, sin recalcular ni tocar importes (BUG-004).
    if (booking.status === BOOKING_STATUS.CANCELLED) {
      return res.json({
        success: true,
        message: 'La reserva ya estaba cancelada',
        data: {
          booking,
          alreadyCancelled: true,
          refundAmount: booking.cancellationDetails?.refundAmount || 0,
          cancellationFee: booking.cancellationDetails?.cancellationFee || 0
        }
      });
    }
    
    // Validar la transición ANTES de calcular importes (BUG-004)
    if (!(STATUS_TRANSITIONS[booking.status] || []).includes(BOOKING_STATUS.CANCELLED)) {
      return conflictResponse(res, `No se puede cancelar una reserva en estado "${booking.status}"`);
    }
    
    // Verificar si ya pasó la fecha de check-in
    const today = todayCalendarDate();
    const daysUntilCheckIn = daysBetween(today, booking.checkIn);
    
    if (daysUntilCheckIn <= 0 && booking.status === BOOKING_STATUS.CONFIRMED) {
      return res.status(400).json({
        success: false,
        message: 'No se puede cancelar una reserva después de la fecha de check-in'
      });
    }
    
    // Penalización (10% si faltan menos de 7 días) y reembolso sobre lo
    // EFECTIVAMENTE COBRADO, no sobre el precio de la reserva (BUG-003).
    const { cancellationFee, refundAmount, refundStatus } = calculateCancellation(booking, daysUntilCheckIn);
    
    booking.status = BOOKING_STATUS.CANCELLED;
    booking.cancelledAt = new Date();
    booking.cancellationDetails = {
      reason: req.body.reason || 'Cancelado por el usuario',
      cancellationFee,
      refundAmount,
      refundStatus,
      cancelledBy: req.user.id
    };
    
    await booking.save(); // el hook post-save libera las noches (SuiteNight)
    
    res.json({
      success: true,
      message: 'Reserva cancelada exitosamente',
      data: {
        booking,
        refundAmount,
        cancellationFee,
        // No hay pasarela de pagos: el reembolso queda PENDIENTE hasta que
        // un administrador lo ejecute y lo registre (payment-status: refunded).
        refundStatus,
        refundNote: refundAmount > 0
          ? 'El reembolso es una estimación y se procesará manualmente; recibirás confirmación.'
          : 'No hay importes cobrados por reembolsar.'
      }
    });
    
  } catch (error) {
    console.error('[CancelBooking Error]:', error);
    res.status(500).json({
      success: false,
      message: 'Error interno del servidor'
    });
  }
};

/**
 * @desc    Modificar fechas de una reserva
 * @route   PUT /api/bookings/:id/modify-dates
 * @access  Private
 */
export const modifyBookingDates = async (req, res) => {
  try {
    const { id } = req.params;
    const { newCheckIn, newCheckOut } = req.body;
    
    const booking = await Booking.findById(id);
    
    if (!booking) {
      return res.status(404).json({
        success: false,
        message: 'Reserva no encontrada'
      });
    }
    
    // Verificar autorización
    if (booking.user.toString() !== req.user.id && req.user.role !== 'admin') {
      return res.status(403).json({
        success: false,
        message: 'No autorizado para modificar esta reserva'
      });
    }
    
    // Verificar que la reserva no esté cancelada o completada
    if (!(BLOCKING_STATUSES.includes(booking.status))) {
      return res.status(400).json({
        success: false,
        message: 'No se pueden modificar reservas canceladas o completadas'
      });
    }
    
    // Validar nuevas fechas
    const dateValidation = validateDates(newCheckIn, newCheckOut);
    if (!dateValidation.isValid) {
      return res.status(400).json({
        success: false,
        message: 'Fechas inválidas',
        errors: dateValidation.errors
      });
    }
    
    // Verificar disponibilidad para las nuevas fechas
    const isAvailable = await isSuiteAvailable(
      booking.suite, 
      dateValidation.checkInDate, 
      dateValidation.checkOutDate,
      booking._id // Excluir la reserva actual
    );
    
    if (!isAvailable) {
      return res.status(409).json({
        success: false,
        message: 'La suite no está disponible para las nuevas fechas'
      });
    }
    
    // Recalcular precio (misma lógica de temporada y descuentos que al crear)
    const newNights = dateValidation.nights;
    const pricing = await calculateTotalPrice(
      booking.suite,
      dateValidation.checkInDate,
      newNights,
      booking.experiences
    );

    // Guardar los valores viejos ANTES de sobreescribirlos: el código
    // anterior armaba el historial de modificaciones después de mutar
    // booking.checkIn/checkOut/totalPrice, así que "oldCheckIn" y
    // "oldTotalPrice" terminaban guardando los valores NUEVOS.
    const oldCheckIn = booking.checkIn;
    const oldCheckOut = booking.checkOut;
    const oldTotalPrice = booking.totalPrice;
    const priceDifference = pricing.totalPrice - oldTotalPrice;

    // Adquirir atómicamente las noches nuevas que esta reserva aún no tiene
    // (BUG-001). Se consultan los bloqueos reales de la reserva, así también
    // funciona con reservas anteriores a SuiteNight (aún sin bloqueos).
    const heldTimes = new Set(
      (await SuiteNight.find({ booking: booking._id }).select('date').lean()).map((n) => n.date.getTime())
    );
    const newNightList = eachNight(dateValidation.checkInDate, dateValidation.checkOutDate);
    const nightsToAcquire = newNightList.filter((d) => !heldTimes.has(d.getTime()));
    const newTimes = new Set(newNightList.map((d) => d.getTime()));
    const nightsToRelease = [...heldTimes].filter((t) => !newTimes.has(t)).map((t) => new Date(t));
    
    let acquiredNights;
    try {
      acquiredNights = await SuiteNight.acquire(booking.suite, booking._id, nightsToAcquire);
    } catch (err) {
      if (err instanceof NightsConflictError) {
        return conflictResponse(res, 'La suite no está disponible para las nuevas fechas');
      }
      throw err;
    }

    booking.checkIn = dateValidation.checkInDate;
    booking.checkOut = dateValidation.checkOutDate;
    booking.subtotal = pricing.subtotal;
    booking.experiencesTotal = pricing.experiencesTotal;
    booking.discount = pricing.discount;
    booking.totalPrice = pricing.totalPrice;
    booking.modifiedAt = new Date();
    booking.modificationHistory = booking.modificationHistory || [];
    booking.modificationHistory.push({
      date: new Date(),
      oldCheckIn,
      oldCheckOut,
      oldTotalPrice,
      newCheckIn: dateValidation.checkInDate,
      newCheckOut: dateValidation.checkOutDate,
      newTotalPrice: pricing.totalPrice,
      priceDifference,
      modifiedBy: req.user.id
    });
    
    try {
      await booking.save();
    } catch (err) {
      // Revertir solo lo adquirido en esta operación
      await SuiteNight.release(booking._id, acquiredNights).catch(() => {});
      throw err;
    }
    // Guardado OK: liberar las noches que ya no se usan
    if (nightsToRelease.length) {
      await SuiteNight.release(booking._id, nightsToRelease).catch((e) =>
        console.error('[ModifyBookingDates] No se liberaron noches antiguas:', e.message));
    }
    
    res.json({
      success: true,
      message: 'Fechas de reserva modificadas exitosamente',
      data: {
        booking,
        priceDifference,
        needsPayment: priceDifference > 0,
        refundAmount: priceDifference < 0 ? Math.abs(priceDifference) : 0
      }
    });
    
  } catch (error) {
    console.error('[ModifyBookingDates Error]:', error);
    res.status(500).json({
      success: false,
      message: 'Error interno del servidor'
    });
  }
};

/**
 * @desc    Actualizar estado de pago de una reserva
 * @route   PUT /api/bookings/:id/payment-status
 * @access  Private (Admin)
 */
export const updatePaymentStatus = async (req, res) => {
  try {
    const { id } = req.params;
    const { paymentStatus, transactionId } = req.body;
    const amount = req.body.amount === undefined || req.body.amount === '' ? null : Number(req.body.amount);
    
    if (!Object.values(PAYMENT_STATUS).includes(paymentStatus)) {
      return res.status(400).json({
        success: false,
        message: 'Estado de pago inválido'
      });
    }
    if (amount !== null && (!Number.isFinite(amount) || amount <= 0)) {
      return res.status(400).json({ success: false, message: 'El importe debe ser un número mayor que 0' });
    }
    
    const booking = await Booking.findById(id);
    
    if (!booking) {
      return res.status(404).json({
        success: false,
        message: 'Reserva no encontrada'
      });
    }
    
    const previous = booking.paymentStatus;
    
    // Idempotente: repetir el mismo estado (salvo 'partial', que actualiza el
    // importe cobrado) no cambia nada ni falla (BUG-006).
    if (previous === paymentStatus && paymentStatus !== PAYMENT_STATUS.PARTIAL) {
      return res.json({ success: true, message: 'Sin cambios: la reserva ya tenía ese estado de pago', data: booking });
    }
    
    if (previous !== paymentStatus && !(PAYMENT_STATUS_TRANSITIONS[previous] || []).includes(paymentStatus)) {
      return conflictResponse(
        res,
        `Transición de pago no permitida: ${previous} -> ${paymentStatus}. Permitidas desde "${previous}": ${(PAYMENT_STATUS_TRANSITIONS[previous] || []).join(', ') || 'ninguna'}`
      );
    }
    
    if (booking.status === BOOKING_STATUS.CANCELLED &&
        [PAYMENT_STATUS.PAID, PAYMENT_STATUS.PARTIAL].includes(paymentStatus)) {
      return conflictResponse(res, 'No se puede registrar un cobro en una reserva cancelada');
    }
    
    switch (paymentStatus) {
      case PAYMENT_STATUS.PAID:
        booking.amountPaid = booking.totalPrice;
        booking.paidAt = new Date();
        break;
      case PAYMENT_STATUS.PARTIAL:
        // `amount` = total cobrado hasta ahora; debe ser menor que el precio
        if (amount === null || amount >= booking.totalPrice) {
          return res.status(400).json({
            success: false,
            message: 'Un pago parcial requiere "amount" mayor que 0 y menor que el precio total de la reserva'
          });
        }
        booking.amountPaid = amount;
        break;
      case PAYMENT_STATUS.REFUNDED: {
        const refundable = getCollectedAmount(booking);
        const refunded = amount ?? refundable;
        if (refunded > refundable) {
          return conflictResponse(res, `No se puede reembolsar más de lo cobrado (máximo ${refundable})`);
        }
        // Registrar el importe cobrado si es una reserva antigua sin `amountPaid`
        if (!booking.amountPaid) booking.amountPaid = refundable;
        booking.amountRefunded = (booking.amountRefunded || 0) + refunded;
        if (booking.status === BOOKING_STATUS.CANCELLED) booking.cancellationDetails.refundStatus = 'completed';
        break;
      }
      default:
        break;
    }
    
    booking.paymentStatus = paymentStatus;
    
    if (transactionId) {
      booking.transactionId = transactionId;
    }
    
    // Solo una reserva pendiente pasa a confirmada al recibir el pago total
    if (paymentStatus === PAYMENT_STATUS.PAID && booking.status === BOOKING_STATUS.PENDING) {
      booking.status = BOOKING_STATUS.CONFIRMED;
    }
    
    await booking.save();
    
    res.json({
      success: true,
      message: 'Estado de pago actualizado',
      data: booking
    });
    
  } catch (error) {
    if (error?.code === 11000) {
      return conflictResponse(res, 'Ese transactionId ya está asociado a otra reserva');
    }
    console.error('[UpdatePaymentStatus Error]:', error);
    res.status(500).json({
      success: false,
      message: 'Error interno del servidor'
    });
  }
};

/**
 * @desc    Obtener todas las reservas (Admin)
 * @route   GET /api/bookings/admin/all
 * @access  Private (Admin)
 */
export const getAllBookings = async (req, res) => {
  try {
    // Solo administradores pueden acceder
    if (req.user.role !== 'admin') {
      return res.status(403).json({
        success: false,
        message: 'Acceso denegado. Se requieren permisos de administrador.'
      });
    }
    
    const { status, startDate, endDate, page = 1, limit = 20 } = req.query;
    
    const query = {};
    if (status) query.status = status;
    if (startDate && endDate) {
      query.createdAt = {
        $gte: new Date(startDate),
        $lte: new Date(endDate)
      };
    }
    
    const skip = (parseInt(page) - 1) * parseInt(limit);
    
    const [bookings, total] = await Promise.all([
      Booking.find(query)
        .populate('suite')
        .populate('experiences')
        .populate('user', 'name email')
        .sort('-createdAt')
        .skip(skip)
        .limit(parseInt(limit)),
      Booking.countDocuments(query)
    ]);
    
    // Estadísticas
    const stats = {
      totalBookings: total,
      totalRevenue: bookings.reduce((sum, b) => sum + b.totalPrice, 0),
      pendingPayments: bookings.filter(b => b.paymentStatus === 'pending').length,
      confirmedBookings: bookings.filter(b => b.status === 'confirmed').length
    };
    
    res.json({
      success: true,
      data: {
        bookings,
        stats,
        pagination: {
          currentPage: parseInt(page),
          totalPages: Math.ceil(total / parseInt(limit)),
          totalItems: total
        }
      }
    });
    
  } catch (error) {
    console.error('[GetAllBookings Error]:', error);
    res.status(500).json({
      success: false,
      message: 'Error interno del servidor'
    });
  }
};

// ============================================
// NUEVAS FUNCIONES
// ============================================

/**
 * @desc    Obtener reservas próximas del usuario
 * @route   GET /api/bookings/upcoming
 * @access  Private
 */
export const getUpcomingBookings = async (req, res) => {
  try {
    const { days = 30 } = req.query;
    const startDate = new Date();
    const endDate = new Date();
    endDate.setDate(endDate.getDate() + parseInt(days));
    
    const bookings = await Booking.find({
      user: req.user.id,
      status: { $in: BLOCKING_STATUSES },
      checkIn: { $gte: startDate, $lte: endDate }
    })
    .populate('suite', 'name type mainImage')
    .populate('experiences', 'name price durationHours')
    .sort('checkIn');
    
    res.json({
      success: true,
      data: bookings,
      count: bookings.length,
      period: {
        start: startDate,
        end: endDate,
        days: parseInt(days)
      }
    });
    
  } catch (error) {
    console.error('[UpcomingBookings Error]:', error);
    res.status(500).json({
      success: false,
      message: 'Error al obtener reservas próximas'
    });
  }
};

/**
 * @desc    Agregar experiencia a reserva existente
 * @route   POST /api/bookings/:id/experiences
 * @access  Private
 */
export const addExperienceToBooking = async (req, res) => {
  try {
    const { id } = req.params;
    const { experienceId } = req.body;
    
    const booking = await Booking.findById(id);
    
    if (!booking) {
      return res.status(404).json({
        success: false,
        message: 'Reserva no encontrada'
      });
    }
    
    // Verificar autorización
    if (booking.user.toString() !== req.user.id && req.user.role !== 'admin') {
      return res.status(403).json({
        success: false,
        message: 'No autorizado'
      });
    }
    
    // Verificar que la reserva sea modificable
    if (!booking.isModifiable) {
      return res.status(400).json({
        success: false,
        message: 'Esta reserva no puede ser modificada'
      });
    }
    
    // Verificar que la experiencia existe
    const experience = await Experience.findById(experienceId);
    
    if (!experience || !experience.available) {
      return res.status(404).json({
        success: false,
        message: 'Experiencia no encontrada o no disponible'
      });
    }
    
    // Verificar que no esté ya agregada
    if (booking.experiences.includes(experienceId)) {
      return res.status(400).json({
        success: false,
        message: 'La experiencia ya está agregada a esta reserva'
      });
    }
    
    // Agregar experiencia
    booking.experiences.push(experienceId);
    booking.experiencesTotal = (booking.experiencesTotal || 0) + experience.price;
    booking.totalPrice = (booking.subtotal || 0) + booking.experiencesTotal - (booking.discount || 0);
    
    await booking.save();
    await booking.populate('experiences');
    
    res.json({
      success: true,
      message: 'Experiencia agregada exitosamente',
      data: {
        booking,
        addedExperience: experience
      }
    });
    
  } catch (error) {
    console.error('[AddExperience Error]:', error);
    res.status(500).json({
      success: false,
      message: 'Error al agregar la experiencia'
    });
  }
};

/**
 * @desc    Eliminar experiencia de reserva
 * @route   DELETE /api/bookings/:id/experiences/:experienceId
 * @access  Private
 */
export const removeExperienceFromBooking = async (req, res) => {
  try {
    const { id, experienceId } = req.params;
    
    const booking = await Booking.findById(id);
    
    if (!booking) {
      return res.status(404).json({
        success: false,
        message: 'Reserva no encontrada'
      });
    }
    
    // Verificar autorización
    if (booking.user.toString() !== req.user.id && req.user.role !== 'admin') {
      return res.status(403).json({
        success: false,
        message: 'No autorizado'
      });
    }
    
    // Verificar que la reserva sea modificable
    if (!booking.isModifiable) {
      return res.status(400).json({
        success: false,
        message: 'Esta reserva no puede ser modificada'
      });
    }
    
    // Verificar que la experiencia esté en la reserva
    const experienceIndex = booking.experiences.findIndex(
      exp => exp.toString() === experienceId
    );
    
    if (experienceIndex === -1) {
      return res.status(404).json({
        success: false,
        message: 'La experiencia no está en esta reserva'
      });
    }
    
    // Obtener precio de la experiencia
    const experience = await Experience.findById(experienceId);
    
    // Eliminar experiencia
    booking.experiences.splice(experienceIndex, 1);
    if (experience) {
      booking.experiencesTotal = Math.max(0, (booking.experiencesTotal || 0) - experience.price);
    }
    booking.totalPrice = (booking.subtotal || 0) + (booking.experiencesTotal || 0) - (booking.discount || 0);
    
    await booking.save();
    await booking.populate('experiences');
    
    res.json({
      success: true,
      message: 'Experiencia eliminada exitosamente',
      data: booking
    });
    
  } catch (error) {
    console.error('[RemoveExperience Error]:', error);
    res.status(500).json({
      success: false,
      message: 'Error al eliminar la experiencia'
    });
  }
};

/**
 * @desc    Reenviar confirmación de reserva por email
 * @route   POST /api/bookings/:id/confirm
 * @access  Private
 */
export const sendBookingConfirmation = async (req, res) => {
  try {
    const { id } = req.params;
    
    const booking = await Booking.findById(id)
      .populate('suite')
      .populate('experiences');
    
    if (!booking) {
      return res.status(404).json({
        success: false,
        message: 'Reserva no encontrada'
      });
    }
    
    // Verificar autorización
    if (booking.user.toString() !== req.user.id && req.user.role !== 'admin') {
      return res.status(403).json({
        success: false,
        message: 'No autorizado'
      });
    }
    
    const result = await sendBookingConfirmationEmail(booking, booking.guestEmail, booking.guestName);
    
    res.json({
      success: true,
      message: result.sent
        ? 'Confirmación enviada exitosamente'
        : 'La reserva está confirmada. El envío de correo no está configurado en este servidor, pero puedes ver todos los detalles en "Mis reservas".'
    });
    
  } catch (error) {
    console.error('[SendConfirmation Error]:', error);
    res.status(500).json({
      success: false,
      message: 'Error al enviar la confirmación'
    });
  }
};

/**
 * @desc    Obtener estadísticas de reservas (admin)
 * @route   GET /api/bookings/admin/stats
 * @access  Private (Admin)
 */
export const getBookingStats = async (req, res) => {
  try {
    const { startDate, endDate } = req.query;
    
    const match = {};
    if (startDate && endDate) {
      match.createdAt = {
        $gte: new Date(startDate),
        $lte: new Date(endDate)
      };
    }
    
    const [stats, dailyStats, monthlyStats, bySuite, byStatus] = await Promise.all([
      // Estadísticas generales
      Booking.aggregate([
        { $match: match },
        {
          $group: {
            _id: null,
            totalBookings: { $sum: 1 },
            totalRevenue: { $sum: '$totalPrice' },
            avgBookingValue: { $avg: '$totalPrice' },
            pendingBookings: { $sum: { $cond: [{ $eq: ['$status', 'pending'] }, 1, 0] } },
            confirmedBookings: { $sum: { $cond: [{ $eq: ['$status', 'confirmed'] }, 1, 0] } },
            cancelledBookings: { $sum: { $cond: [{ $eq: ['$status', 'cancelled'] }, 1, 0] } },
            completedBookings: { $sum: { $cond: [{ $eq: ['$status', 'completed'] }, 1, 0] } }
          }
        }
      ]),
      
      // Estadísticas diarias (últimos 30 días)
      Booking.aggregate([
        { $match: { createdAt: { $gte: new Date(Date.now() - 30 * 24 * 60 * 60 * 1000) } } },
        {
          $group: {
            _id: { $dateToString: { format: '%Y-%m-%d', date: '$createdAt' } },
            count: { $sum: 1 },
            revenue: { $sum: '$totalPrice' }
          }
        },
        { $sort: { _id: 1 } }
      ]),
      
      // Estadísticas mensuales
      Booking.aggregate([
        { $match: match },
        {
          $group: {
            _id: { year: { $year: '$createdAt' }, month: { $month: '$createdAt' } },
            count: { $sum: 1 },
            revenue: { $sum: '$totalPrice' }
          }
        },
        { $sort: { '_id.year': 1, '_id.month': 1 } }
      ]),
      
      // Por suite
      Booking.aggregate([
        { $match: match },
        {
          $group: {
            _id: '$suite',
            count: { $sum: 1 },
            revenue: { $sum: '$totalPrice' }
          }
        },
        { $sort: { count: -1 } },
        { $limit: 10 }
      ]).lookup({ from: 'suites', localField: '_id', foreignField: '_id', as: 'suite' }),
      
      // Por estado
      Booking.aggregate([
        { $match: match },
        {
          $group: {
            _id: '$status',
            count: { $sum: 1 },
            revenue: { $sum: '$totalPrice' }
          }
        }
      ])
    ]);
    
    // Obtener nombres de suites
    const suitesWithNames = await Promise.all(
      (bySuite || []).map(async (item) => {
        if (item._id) {
          const suite = await Suite.findById(item._id).select('name type');
          return {
            ...item,
            suite: suite ? { name: suite.name, type: suite.type } : null
          };
        }
        return item;
      })
    );
    
    res.json({
      success: true,
      data: {
        general: stats[0] || {
          totalBookings: 0,
          totalRevenue: 0,
          avgBookingValue: 0,
          pendingBookings: 0,
          confirmedBookings: 0,
          cancelledBookings: 0,
          completedBookings: 0
        },
        dailyStats,
        monthlyStats,
        bySuite: suitesWithNames,
        byStatus,
        period: startDate && endDate ? { startDate, endDate } : { last30Days: true }
      }
    });
    
  } catch (error) {
    console.error('[BookingStats Error]:', error);
    res.status(500).json({
      success: false,
      message: 'Error al obtener estadísticas de reservas'
    });
  }
};