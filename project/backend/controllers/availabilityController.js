import Booking, { BLOCKING_BOOKING_STATUSES } from '../models/Booking.js';
import Suite from '../models/Suite.js';
import { toCalendarDate } from '../utils/dates.js';

// ============================================
// HELPER FUNCTIONS
// ============================================

/**
 * Normaliza a fecha de calendario (medianoche UTC, día en la zona horaria
 * del hotel). Mismo criterio que las reservas: ver utils/dates.js (CONS-014).
 */
const normalizeDate = (date) => toCalendarDate(date);

/**
 * Calcula el número de noches entre dos fechas
 */
const calculateNights = (checkIn, checkOut) => {
  const diffTime = Math.abs(checkOut - checkIn);
  return Math.ceil(diffTime / (1000 * 60 * 60 * 24));
};

/**
 * Valida que las fechas sean correctas
 */
const validateDates = (checkIn, checkOut) => {
  const errors = [];
  const today = normalizeDate(new Date());
  const checkInDate = normalizeDate(checkIn);
  const checkOutDate = normalizeDate(checkOut);
  
  if (checkInDate < today) {
    errors.push('La fecha de check-in no puede ser anterior a hoy');
  }
  
  if (checkOutDate <= checkInDate) {
    errors.push('La fecha de check-out debe ser posterior al check-in');
  }
  
  const maxNights = 90; // Máximo 90 noches
  const nights = calculateNights(checkInDate, checkOutDate);
  if (nights > maxNights) {
    errors.push(`La estadía no puede exceder ${maxNights} noches`);
  }
  
  return { isValid: errors.length === 0, errors, nights };
};

/**
 * Verifica si una suite está disponible para el rango de fechas
 */
const isSuiteAvailable = async (suiteId, checkInDate, checkOutDate, excludeBookingId = null) => {
  const query = {
    suite: suiteId,
    status: { $in: BLOCKING_BOOKING_STATUSES },
    $or: [
      // Nueva reserva comienza dentro de una reserva existente
      { checkIn: { $lt: checkOutDate, $gte: checkInDate } },
      // Nueva reserva termina dentro de una reserva existente
      { checkOut: { $gt: checkInDate, $lte: checkOutDate } },
      // Nueva reserva engloba una reserva existente
      { checkIn: { $lte: checkInDate }, checkOut: { $gte: checkOutDate } }
    ]
  };
  
  // Si estamos editando una reserva, excluirla de la verificación
  if (excludeBookingId) {
    query._id = { $ne: excludeBookingId };
  }
  
  const conflictingBooking = await Booking.findOne(query);
  return !conflictingBooking;
};

/**
 * Obtiene el número de reservas para una suite en un rango de fechas
 */
const getBookingCountForSuite = async (suiteId, checkInDate, checkOutDate) => {
  return await Booking.countDocuments({
    suite: suiteId,
    status: { $in: BLOCKING_BOOKING_STATUSES },
    $or: [
      { checkIn: { $lt: checkOutDate, $gte: checkInDate } },
      { checkOut: { $gt: checkInDate, $lte: checkOutDate } },
      { checkIn: { $lte: checkInDate }, checkOut: { $gte: checkOutDate } }
    ]
  });
};

// ============================================
// MAIN CONTROLLER
// ============================================

/**
 * @desc    Verificar disponibilidad de suites para un rango de fechas
 * @route   GET /api/availability
 * @access  Public
 * @param   {string} checkIn - Fecha de check-in (YYYY-MM-DD)
 * @param   {string} checkOut - Fecha de check-out (YYYY-MM-DD)
 * @param   {number} guests - Número de huéspedes (opcional)
 * @param   {string} suiteType - Tipo de suite (opcional)
 */
export const checkAvailability = async (req, res) => {
  try {
    // 1. Extraer y validar parámetros
    let { checkIn, checkOut, guests, suiteType } = req.query;
    
    // Validar que las fechas existen
    if (!checkIn || !checkOut) {
      return res.status(400).json({
        success: false,
        message: 'Se requieren las fechas de check-in y check-out'
      });
    }
    
    // 2. Normalizar fechas
    const checkInDate = normalizeDate(new Date(checkIn));
    const checkOutDate = normalizeDate(new Date(checkOut));
    
    // 3. Validar fechas
    const dateValidation = validateDates(checkInDate, checkOutDate);
    if (!dateValidation.isValid) {
      return res.status(400).json({
        success: false,
        message: 'Fechas inválidas',
        errors: dateValidation.errors
      });
    }
    
    const nights = dateValidation.nights;
    
    // 4. Validar número de huéspedes (opcional)
    let guestCount = null;
    if (guests) {
      guestCount = parseInt(guests);
      if (isNaN(guestCount) || guestCount < 1 || guestCount > 20) {
        return res.status(400).json({
          success: false,
          message: 'El número de huéspedes debe ser entre 1 y 20'
        });
      }
    }
    
    // 5. Construir query base para suites
    let suiteQuery = { available: true };
    
    // Filtrar por tipo de suite si se especificó
    if (suiteType && suiteType !== 'Todas las suites') {
      suiteQuery.type = suiteType;
    }
    
    // Filtrar por capacidad de huéspedes si se especificó
    if (guestCount) {
      suiteQuery.maxGuests = { $gte: guestCount };
    }
    
    // 6. Obtener todas las suites que cumplen los filtros básicos
    const allSuites = await Suite.find(suiteQuery).sort('order');
    
    // 7. Verificar disponibilidad de cada suite
    const availabilityPromises = allSuites.map(async (suite) => {
      const bookingCount = await getBookingCountForSuite(suite._id, checkInDate, checkOutDate);
      const isAvailable = bookingCount === 0;
      
      // Asumiendo que cada suite tiene 1 unidad (ajustar según tu modelo)
      const availableUnits = isAvailable ? 1 : 0;
      const totalUnits = suite.totalUnits || 1;
      
      return {
        ...suite.toObject(),
        isAvailable,
        availableUnits,
        totalUnits,
        bookingCount,
        pricePerNight: suite.basePrice,
        totalPrice: suite.basePrice * nights
      };
    });
    
    const suitesWithAvailability = await Promise.all(availabilityPromises);
    
    // 8. Separar suites disponibles y no disponibles
    const availableSuites = suitesWithAvailability.filter(s => s.isAvailable);
    const unavailableSuites = suitesWithAvailability.filter(s => !s.isAvailable);
    
    // 9. Estadísticas de disponibilidad
    const stats = {
      totalSuites: allSuites.length,
      availableSuites: availableSuites.length,
      unavailableSuites: unavailableSuites.length,
      occupancyRate: allSuites.length > 0 
        ? ((unavailableSuites.length / allSuites.length) * 100).toFixed(1)
        : 0
    };
    
    // 10. Sugerencias de fechas alternativas (si no hay disponibilidad)
    let alternativeDates = null;
    if (availableSuites.length === 0 && allSuites.length > 0) {
      alternativeDates = await findAlternativeDates(checkInDate, checkOutDate, suiteQuery, guestCount);
    }
    
    // 11. Responder con datos estructurados
    res.json({
      success: true,
      data: {
        checkIn: checkInDate,
        checkOut: checkOutDate,
        nights,
        stats,
        availableSuites,
        unavailableSuites: process.env.NODE_ENV === 'development' ? unavailableSuites : undefined,
        alternativeDates,
        filters: {
          guests: guestCount,
          suiteType: suiteType || null
        }
      }
    });
    
  } catch (error) {
    console.error('[Availability Error]:', error);
    res.status(500).json({
      success: false,
      message: 'Error interno del servidor al verificar disponibilidad',
      error: process.env.NODE_ENV === 'development' ? error.message : undefined
    });
  }
};

/**
 * @desc    Verificar disponibilidad para una suite específica
 * @route   GET /api/availability/suite/:suiteId
 * @access  Public
 */
export const checkSuiteAvailability = async (req, res) => {
  try {
    const { suiteId } = req.params;
    const { checkIn, checkOut } = req.query;
    
    if (!checkIn || !checkOut) {
      return res.status(400).json({
        success: false,
        message: 'Se requieren las fechas de check-in y check-out'
      });
    }
    
    const checkInDate = normalizeDate(new Date(checkIn));
    const checkOutDate = normalizeDate(new Date(checkOut));
    
    const dateValidation = validateDates(checkInDate, checkOutDate);
    if (!dateValidation.isValid) {
      return res.status(400).json({
        success: false,
        message: 'Fechas inválidas',
        errors: dateValidation.errors
      });
    }
    
    const suite = await Suite.findById(suiteId);
    if (!suite) {
      return res.status(404).json({
        success: false,
        message: 'Suite no encontrada'
      });
    }
    
    const isAvailable = await isSuiteAvailable(suiteId, checkInDate, checkOutDate);
    const bookingCount = await getBookingCountForSuite(suiteId, checkInDate, checkOutDate);
    
    res.json({
      success: true,
      data: {
        suite,
        isAvailable,
        bookingCount,
        checkIn: checkInDate,
        checkOut: checkOutDate,
        nights: dateValidation.nights,
        totalPrice: suite.basePrice * dateValidation.nights
      }
    });
    
  } catch (error) {
    console.error('[Suite Availability Error]:', error);
    res.status(500).json({
      success: false,
      message: 'Error interno del servidor'
    });
  }
};

/**
 * @desc    Encontrar fechas alternativas cercanas
 * @access  Private
 */
const findAlternativeDates = async (checkInDate, checkOutDate, suiteQuery, guestCount) => {
  const alternatives = [];
  const nights = calculateNights(checkInDate, checkOutDate);
  
  // Buscar ±7 días alrededor de las fechas solicitadas
  for (let offset = -7; offset <= 7; offset++) {
    if (offset === 0) continue;
    
    const altCheckIn = new Date(checkInDate);
    altCheckIn.setUTCDate(checkInDate.getUTCDate() + offset);
    
    const altCheckOut = new Date(altCheckIn);
    altCheckOut.setUTCDate(altCheckIn.getUTCDate() + nights);
    
    // No sugerir fechas pasadas
    if (altCheckIn < normalizeDate(new Date())) continue;
    
    const suites = await Suite.find(suiteQuery);
    let availableCount = 0;
    
    for (const suite of suites) {
      const isAvail = await isSuiteAvailable(suite._id, altCheckIn, altCheckOut);
      if (isAvail) availableCount++;
    }
    
    if (availableCount > 0) {
      alternatives.push({
        checkIn: altCheckIn,
        checkOut: altCheckOut,
        nights,
        availableSuites: availableCount,
        offset: offset
      });
    }
    
    if (alternatives.length >= 3) break;
  }
  
  return alternatives;
};

/**
 * @desc    Obtener precios dinámicos según temporada
 * @route   GET /api/availability/prices
 * @access  Public
 */
export const getDynamicPrices = async (req, res) => {
  try {
    const { checkIn, checkOut } = req.query;
    
    if (!checkIn || !checkOut) {
      return res.status(400).json({
        success: false,
        message: 'Se requieren las fechas de check-in y check-out'
      });
    }
    
    const checkInDate = normalizeDate(new Date(checkIn));
    const checkOutDate = normalizeDate(new Date(checkOut));
    const nights = calculateNights(checkInDate, checkOutDate);
    
    // Determinar temporada
    const month = checkInDate.getMonth();
    let season = 'low';
    let multiplier = 1;
    
    // Temporada alta: Diciembre, Enero, Julio, Agosto, Semana Santa
    if (month === 11 || month === 0 || month === 6 || month === 7) {
      season = 'high';
      multiplier = 1.3;
    }
    // Temporada media: Febrero, Marzo, Junio
    else if (month === 1 || month === 2 || month === 5) {
      season = 'mid';
      multiplier = 1.15;
    }
    
    const suites = await Suite.find({ available: true });
    
    const prices = suites.map(suite => ({
      suite: {
        id: suite._id,
        name: suite.name,
        type: suite.type
      },
      basePrice: suite.basePrice,
      seasonalPrice: Math.round(suite.basePrice * multiplier),
      totalPrice: Math.round(suite.basePrice * multiplier * nights),
      season,
      nights
    }));
    
    res.json({
      success: true,
      data: {
        checkIn: checkInDate,
        checkOut: checkOutDate,
        nights,
        season,
        seasonMultiplier: multiplier,
        prices
      }
    });
    
  } catch (error) {
    console.error('[Dynamic Prices Error]:', error);
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
 * @desc    Verificar disponibilidad en lote (múltiples suites/fechas)
 * @route   POST /api/availability/batch
 * @access  Public
 */
export const checkBatchAvailability = async (req, res) => {
  try {
    const { requests } = req.body;
    
    const results = await Promise.all(
      requests.map(async (request) => {
        const { suiteId, checkIn, checkOut } = request;
        
        const checkInDate = toCalendarDate(checkIn);
        const checkOutDate = toCalendarDate(checkOut);
        
        
        const suite = await Suite.findById(suiteId);
        if (!suite) {
          return { suiteId, error: 'Suite no encontrada', available: false };
        }
        
        const conflictingBookings = await Booking.countDocuments({
          suite: suiteId,
          status: { $in: BLOCKING_BOOKING_STATUSES },
          checkIn: { $lt: checkOutDate },
          checkOut: { $gt: checkInDate }
        });
        
        const available = conflictingBookings === 0;
        
        return {
          suiteId,
          suiteName: suite.name,
          checkIn: checkInDate,
          checkOut: checkOutDate,
          available,
          nights: Math.ceil((checkOutDate - checkInDate) / (1000 * 60 * 60 * 24)),
          pricePerNight: suite.basePrice,
          totalPrice: suite.basePrice * Math.ceil((checkOutDate - checkInDate) / (1000 * 60 * 60 * 24))
        };
      })
    );
    
    res.json({
      success: true,
      data: results,
      summary: {
        total: results.length,
        available: results.filter(r => r.available).length,
        unavailable: results.filter(r => !r.available && !r.error).length,
        errors: results.filter(r => r.error).length
      }
    });
    
  } catch (error) {
    console.error('[BatchAvailability Error]:', error);
    res.status(500).json({
      success: false,
      message: 'Error al verificar disponibilidad en lote'
    });
  }
};

/**
 * @desc    Verificar disponibilidad de múltiples suites para mismas fechas
 * @route   POST /api/availability/multiple
 * @access  Public
 */
export const checkMultipleSuitesAvailability = async (req, res) => {
  try {
    const { suiteIds, checkIn, checkOut } = req.body;
    
    const checkInDate = toCalendarDate(checkIn);
    const checkOutDate = toCalendarDate(checkOut);
    const nights = Math.ceil((checkOutDate - checkInDate) / (1000 * 60 * 60 * 24));
    
    
    // Obtener todas las suites
    const suites = await Suite.find({ _id: { $in: suiteIds }, available: true });
    
    // Obtener reservas conflictivas
    const conflictingBookings = await Booking.find({
      suite: { $in: suiteIds },
      status: { $in: BLOCKING_BOOKING_STATUSES },
      checkIn: { $lt: checkOutDate },
      checkOut: { $gt: checkInDate }
    }).select('suite');
    
    const bookedSuiteIds = new Set(conflictingBookings.map(b => b.suite.toString()));
    
    const results = suites.map(suite => ({
      suite: {
        id: suite._id,
        name: suite.name,
        type: suite.type,
        price: suite.basePrice,
        size: suite.size,
        maxGuests: suite.maxGuests,
        image: suite.mainImage
      },
      available: !bookedSuiteIds.has(suite._id.toString()),
      nights,
      totalPrice: suite.basePrice * nights,
      pricePerNight: suite.basePrice
    }));
    
    res.json({
      success: true,
      data: {
        checkIn: checkInDate,
        checkOut: checkOutDate,
        nights,
        results,
        summary: {
          total: results.length,
          available: results.filter(r => r.available).length,
          unavailable: results.filter(r => !r.available).length
        }
      }
    });
    
  } catch (error) {
    console.error('[MultipleSuitesAvailability Error]:', error);
    res.status(500).json({
      success: false,
      message: 'Error al verificar disponibilidad de múltiples suites'
    });
  }
};

/**
 * @desc    Obtener disponibilidad por mes (calendario)
 * @route   GET /api/availability/monthly
 * @access  Public
 */
export const getMonthlyAvailability = async (req, res) => {
  try {
    const { year, month, suiteType } = req.query;
    
    const startDate = new Date(Date.UTC(year, month - 1, 1));
    const endDate = new Date(Date.UTC(year, month, 0));
    
    
    // Construir query de suites
    const suiteQuery = { available: true };
    if (suiteType) suiteQuery.type = suiteType;
    
    const suites = await Suite.find(suiteQuery).select('_id name type');
    
    // Obtener todas las reservas del mes
    const bookings = await Booking.find({
      suite: { $in: suites.map(s => s._id) },
      status: { $in: BLOCKING_BOOKING_STATUSES },
      checkIn: { $lte: endDate },
      checkOut: { $gte: startDate }
    });
    
    // Crear mapa de disponibilidad por día
    const calendar = {};
    const daysInMonth = endDate.getUTCDate();
    
    for (let day = 1; day <= daysInMonth; day++) {
      const date = new Date(Date.UTC(year, month - 1, day));
      calendar[day] = {
        date,
        totalSuites: suites.length,
        availableSuites: suites.length,
        bookings: []
      };
    }
    
    // Marcar días con reservas
    for (const booking of bookings) {
      const bookingStart = new Date(booking.checkIn);
      const bookingEnd = new Date(booking.checkOut);
      
      for (let day = 1; day <= daysInMonth; day++) {
        const currentDate = new Date(Date.UTC(year, month - 1, day));
        
        if (currentDate >= bookingStart && currentDate < bookingEnd) {
          calendar[day].availableSuites--;
          calendar[day].bookings.push({
            suiteId: booking.suite,
            bookingId: booking._id
          });
        }
      }
    }
    
    // Calcular porcentaje de ocupación
    for (const day in calendar) {
      calendar[day].occupancyRate = ((calendar[day].totalSuites - calendar[day].availableSuites) / calendar[day].totalSuites) * 100;
    }
    
    res.json({
      success: true,
      data: {
        year,
        month,
        daysInMonth,
        calendar: Object.values(calendar),
        summary: {
          totalSuites: suites.length,
          mostAvailableDay: Object.values(calendar).reduce((max, day) => day.availableSuites > max.availableSuites ? day : max, { availableSuites: -1 }),
          leastAvailableDay: Object.values(calendar).reduce((min, day) => day.availableSuites < min.availableSuites ? day : min, { availableSuites: Infinity }),
          averageOccupancy: Object.values(calendar).reduce((sum, day) => sum + day.occupancyRate, 0) / daysInMonth
        }
      }
    });
    
  } catch (error) {
    console.error('[MonthlyAvailability Error]:', error);
    res.status(500).json({
      success: false,
      message: 'Error al obtener disponibilidad mensual'
    });
  }
};

/**
 * @desc    Obtener calendario de disponibilidad para una suite
 * @route   GET /api/availability/calendar
 * @access  Public
 */
export const getAvailabilityCalendar = async (req, res) => {
  try {
    const { suiteId, year, month } = req.query;
    
    const startDate = new Date(Date.UTC(year, month - 1, 1));
    const endDate = new Date(Date.UTC(year, month, 0));
    
    
    const suite = await Suite.findById(suiteId);
    if (!suite) {
      return res.status(404).json({
        success: false,
        message: 'Suite no encontrada'
      });
    }
    
    // Obtener reservas de la suite
    const bookings = await Booking.find({
      suite: suiteId,
      status: { $in: BLOCKING_BOOKING_STATUSES },
      checkIn: { $lte: endDate },
      checkOut: { $gte: startDate }
    });
    
    // Crear calendario
    const calendar = [];
    const daysInMonth = endDate.getUTCDate();
    
    for (let day = 1; day <= daysInMonth; day++) {
      const currentDate = new Date(Date.UTC(year, month - 1, day));
      const isBooked = bookings.some(booking => 
        currentDate >= booking.checkIn && currentDate < booking.checkOut
      );
      
      calendar.push({
        date: currentDate,
        day,
        available: !isBooked,
        isWeekend: currentDate.getUTCDay() === 0 || currentDate.getUTCDay() === 6,
        bookings: isBooked ? bookings.filter(b => currentDate >= b.checkIn && currentDate < b.checkOut) : []
      });
    }
    
    res.json({
      success: true,
      data: {
        suite: {
          id: suite._id,
          name: suite.name,
          type: suite.type
        },
        year,
        month,
        daysInMonth,
        calendar,
        summary: {
          totalDays: daysInMonth,
          availableDays: calendar.filter(d => d.available).length,
          bookedDays: calendar.filter(d => !d.available).length,
          occupancyRate: (calendar.filter(d => !d.available).length / daysInMonth) * 100
        }
      }
    });
    
  } catch (error) {
    console.error('[Calendar Error]:', error);
    res.status(500).json({
      success: false,
      message: 'Error al obtener calendario de disponibilidad'
    });
  }
};

/**
 * @desc    Obtener fechas de temporada alta
 * @route   GET /api/availability/peak-dates
 * @access  Public
 */
export const getPeakDates = async (req, res) => {
  try {
    const { year = new Date().getFullYear() } = req.query;
    
    // Definir fechas de temporada alta y pico
    const peakDates = {
      highSeason: [
        { name: 'Vacaciones de mitad de año', start: `${year}-06-15`, end: `${year}-07-15` },
        { name: 'Pre-navidad', start: `${year}-12-15`, end: `${year}-12-20` }
      ],
      peakSeason: [
        { name: 'Navidad y Año Nuevo', start: `${year}-12-21`, end: `${year + 1}-01-10` },
        { name: 'Semana Santa', start: `${year}-03-24`, end: `${year}-04-08` }
      ],
      holidays: [
        { name: 'Año Nuevo', date: `${year}-01-01` },
        { name: 'Día de los Reyes Magos', date: `${year}-01-06` },
        { name: 'Día de San José', date: `${year}-03-19` },
        { name: 'Día del Trabajo', date: `${year}-05-01` },
        { name: 'Día de la Independencia', date: `${year}-07-20` },
        { name: 'Día de la Raza', date: `${year}-10-12` },
        { name: 'Día de Todos los Santos', date: `${year}-11-01` },
        { name: 'Día de la Inmaculada Concepción', date: `${year}-12-08` },
        { name: 'Navidad', date: `${year}-12-25` }
      ]
    };
    
    res.json({
      success: true,
      data: peakDates,
      currentDate: new Date().toISOString(),
      recommendations: {
        bestTimeToBook: 'Reserva con al menos 30 días de anticipación para temporada alta',
        bestRates: 'Temporada baja (febrero-mayo, agosto-noviembre excepto fechas especiales)'
      }
    });
    
  } catch (error) {
    console.error('[PeakDates Error]:', error);
    res.status(500).json({
      success: false,
      message: 'Error al obtener fechas de temporada alta'
    });
  }
};

/**
 * @desc    Obtener estadísticas de disponibilidad
 * @route   GET /api/availability/stats
 * @access  Public
 */
export const getAvailabilityStats = async (req, res) => {
  try {
    const { startDate, endDate } = req.query;
    
    const query = {};
    if (startDate && endDate) {
      query.createdAt = { $gte: new Date(startDate), $lte: new Date(endDate) };
    }
    
    
    const [totalSuites, activeBookings, confirmedBookings, pendingBookings] = await Promise.all([
      Suite.countDocuments({ available: true }),
      Booking.countDocuments({ status: 'confirmed' }),
      Booking.countDocuments({ status: 'confirmed' }),
      Booking.countDocuments({ status: 'pending' })
    ]);
    
    const occupancyByMonth = await Booking.aggregate([
      {
        $match: {
          status: 'confirmed',
          ...(startDate && endDate ? { createdAt: { $gte: new Date(startDate), $lte: new Date(endDate) } } : {})
        }
      },
      {
        $group: {
          _id: { year: { $year: '$checkIn' }, month: { $month: '$checkIn' } },
          count: { $sum: 1 },
          revenue: { $sum: '$totalPrice' }
        }
      },
      { $sort: { '_id.year': 1, '_id.month': 1 } }
    ]);
    
    res.json({
      success: true,
      data: {
        general: {
          totalSuites,
          activeBookings,
          confirmedBookings,
          pendingBookings,
          occupancyRate: totalSuites > 0 ? (activeBookings / totalSuites) * 100 : 0
        },
        occupancyByMonth,
        period: startDate && endDate ? { startDate, endDate } : null
      }
    });
    
  } catch (error) {
    console.error('[AvailabilityStats Error]:', error);
    res.status(500).json({
      success: false,
      message: 'Error al obtener estadísticas de disponibilidad'
    });
  }
};