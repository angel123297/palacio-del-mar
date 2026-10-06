import Booking from '../models/Booking.js';
import Suite from '../models/Suite.js';
import SuiteNight from '../models/SuiteNight.js';
import { toCalendarDate, eachNight } from '../utils/dates.js';
import { resolveBranchId } from '../utils/branches.js';
import { getSeasonCalendar } from '../utils/seasons.js';
import { loadPromotions, quoteSuiteStay } from '../services/pricingService.js';
import Promotion from '../models/Promotion.js';
import { summarizeBranches, attachPromotions } from '../utils/branchSummary.js';

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
 * Habitaciones físicas libres de una suite en [checkIn, checkOut).
 * Fuente de verdad: SuiteNight (la misma que impide la doble reserva).
 */
const getFreeUnits = async (suite, checkInDate, checkOutDate) => {
  const capacity = suite.availableUnitsCount;
  const free = await SuiteNight.freeSlots(suite._id, eachNight(checkInDate, checkOutDate), capacity);
  return { free: free.length, capacity };
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
    let { checkIn, checkOut, guests, suiteType, branch } = req.query;
    
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

    // Filtrar por sucursal (slug o id)
    const branchId = await resolveBranchId(branch);
    if (branchId === null) {
      return res.status(404).json({ success: false, message: 'Sucursal no encontrada' });
    }
    if (branchId) suiteQuery.branch = branchId;
    
    // 6. Obtener todas las suites que cumplen los filtros básicos
    const allSuites = await Suite.find(suiteQuery).sort('order').populate('branch', 'name slug zone');
    
    // 7. Verificar disponibilidad y cotizar cada suite (mismo cálculo que la reserva)
    const branchIds = [...new Set(allSuites.map((s) => String(s.branch?._id || s.branch)))];
    const promotions = await loadPromotions(branchIds, checkInDate, checkOutDate);
    const availabilityPromises = allSuites.map(async (suite) => {
      const { free, capacity } = await getFreeUnits(suite, checkInDate, checkOutDate);
      const quote = await quoteSuiteStay({
        suite, checkIn: checkInDate, checkOut: checkOutDate, promotions
      });
      
      return {
        ...suite.toObject(),
        isAvailable: free > 0,
        availableUnits: free,
        totalUnits: capacity,
        pricePerNight: Math.round(quote.total / nights),
        totalPrice: quote.total,
        priceBreakdown: {
          lodging: quote.lodging,
          discount: quote.discount,
          discountType: quote.discountType,
          discountReason: quote.discountReason
        }
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
    
    // 10b. Resumen por sucursal (libres, "desde $X" y promoción vigente)
    const branchSummary = summarizeBranches(suitesWithAvailability);
    if (branchSummary.length) {
      // las mismas promociones con las que se cotizó cada suite
      attachPromotions(branchSummary, promotions);
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
        branchSummary,
        filters: {
          guests: guestCount,
          suiteType: suiteType || null,
          branch: branch || null
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
 * @desc    Encontrar fechas alternativas cercanas
 * @access  Private
 */
const findAlternativeDates = async (checkInDate, checkOutDate, suiteQuery, guestCount) => {
  const alternatives = [];
  const nights = calculateNights(checkInDate, checkOutDate);
  const today = normalizeDate(new Date());
  const suites = await Suite.find(suiteQuery);

  // ±7 días con las MISMAS noches, probando primero las fechas más cercanas
  // (+1, -1, +2, -2...) en vez de recorrer de -7 a +7.
  const offsets = [];
  for (let i = 1; i <= 7; i++) offsets.push(i, -i);

  for (const offset of offsets) {
    const altCheckIn = new Date(checkInDate);
    altCheckIn.setUTCDate(checkInDate.getUTCDate() + offset);
    if (altCheckIn < today) continue; // no sugerir fechas pasadas

    const altCheckOut = new Date(altCheckIn);
    altCheckOut.setUTCDate(altCheckIn.getUTCDate() + nights);

    const frees = await Promise.all(suites.map((suite) => getFreeUnits(suite, altCheckIn, altCheckOut)));
    const availableCount = frees.filter((f) => f.free > 0).length;

    if (availableCount > 0) {
      alternatives.push({ checkIn: altCheckIn, checkOut: altCheckOut, nights, availableSuites: availableCount, offset });
    }
    if (alternatives.length >= 3) break;
  }

  return alternatives.sort((a, b) => a.checkIn - b.checkIn);
};

const DAY_MS = 24 * 60 * 60 * 1000;
const isoDay = (d) => d.toISOString().slice(0, 10);

/**
 * @desc    Disponibilidad día por día de un mes (todas las suites)
 * @route   GET /api/availability/monthly
 * @access  Public
 * @query   year, month, guests (opcional), suiteType (opcional)
 *
 * La fuente de verdad es SuiteNight (una noche ocupada = un documento), la
 * misma que usa bookingController para impedir la doble reserva. No se
 * devuelven IDs de reservas: es un endpoint público.
 */
export const getMonthlyAvailability = async (req, res) => {
  try {
    const year = Number(req.query.year);
    const month = Number(req.query.month);
    const { suiteType } = req.query;
    const guests = Number.parseInt(req.query.guests, 10);

    const startDate = new Date(Date.UTC(year, month - 1, 1));
    const nextMonth = new Date(Date.UTC(year, month, 1));
    const daysInMonth = Math.round((nextMonth - startDate) / DAY_MS);

    const suiteQuery = { available: true };
    if (suiteType) suiteQuery.type = String(suiteType);
    if (guests > 0) suiteQuery.maxGuests = { $gte: guests };

    const branchId = await resolveBranchId(req.query.branch);
    if (branchId === null) {
      return res.status(404).json({ success: false, message: 'Sucursal no encontrada' });
    }
    if (branchId) suiteQuery.branch = branchId;

    // Se cuentan habitaciones físicas, no tipos de suite.
    const suites = await Suite.find(suiteQuery).select('_id totalUnits units available');
    const totalSuites = suites.reduce((sum, s) => sum + s.availableUnitsCount, 0);

    const occupiedByDate = new Map();
    if (totalSuites > 0) {
      const rows = await SuiteNight.aggregate([
        {
          $match: {
            suite: { $in: suites.map((s) => s._id) },
            date: { $gte: startDate, $lt: nextMonth }
          }
        },
        { $group: { _id: '$date', count: { $sum: 1 } } }
      ]);
      rows.forEach((r) => occupiedByDate.set(isoDay(r._id), r.count));
    }

    const calendar = [];
    for (let day = 1; day <= daysInMonth; day++) {
      const date = isoDay(new Date(Date.UTC(year, month - 1, day)));
      const occupied = occupiedByDate.get(date) || 0;
      const availableSuites = Math.max(0, totalSuites - occupied);
      calendar.push({
        date,
        day,
        totalSuites,
        availableSuites,
        soldOut: availableSuites === 0,
        occupancyRate: totalSuites ? (occupied / totalSuites) * 100 : 0
      });
    }

    res.json({
      success: true,
      data: {
        year,
        month,
        daysInMonth,
        totalSuites,
        calendar,
        summary: {
          soldOutDays: calendar.filter((d) => d.soldOut).length,
          averageOccupancy: calendar.reduce((sum, d) => sum + d.occupancyRate, 0) / daysInMonth
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
 * @desc    Obtener fechas de temporada alta
 * @route   GET /api/availability/peak-dates
 * @access  Public
 */
export const getPeakDates = async (req, res) => {
  try {
    const year = Number.parseInt(req.query.year, 10) || new Date().getFullYear();
    
    // Misma tabla de temporadas que usa el cobro (utils/seasons.js)
    const peakDates = getSeasonCalendar(year);
    
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


/**
 * @desc    Descuentos (promociones activas) que cubren algún día de un mes
 * @route   GET /api/availability/promotions
 * @access  Public
 * @query   year, month, branch (opcional: slug o id de sucursal)
 *
 * Son informativas: se muestran en el calendario pero no cambian el precio.
 */
export const getMonthlyPromotions = async (req, res) => {
  try {
    const year = Number(req.query.year);
    const month = Number(req.query.month);
    const startDate = new Date(Date.UTC(year, month - 1, 1));
    const nextMonth = new Date(Date.UTC(year, month, 1));

    const query = { active: true, startDate: { $lt: nextMonth }, endDate: { $gte: startDate } };

    const branchId = await resolveBranchId(req.query.branch);
    if (branchId === null) {
      return res.status(404).json({ success: false, message: 'Sucursal no encontrada' });
    }
    if (branchId) query.branch = branchId;

    const promotions = await Promotion.find(query)
      .populate('branch', 'name slug zone')
      .sort({ startDate: 1 })
      .lean();

    res.json({
      success: true,
      data: {
        year,
        month,
        promotions: promotions.map((p) => ({
          id: String(p._id),
          title: p.title,
          discountPercent: p.discountPercent,
          startDate: isoDay(p.startDate),
          endDate: isoDay(p.endDate),
          branch: p.branch ? { name: p.branch.name, slug: p.branch.slug, zone: p.branch.zone } : null
        }))
      }
    });
  } catch (error) {
    console.error('[MonthlyPromotions Error]:', error);
    res.status(500).json({
      success: false,
      message: 'Error al obtener los descuentos del mes'
    });
  }
};
