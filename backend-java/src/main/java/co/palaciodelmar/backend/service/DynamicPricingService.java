package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.model.Suite;
import co.palaciodelmar.backend.model.SuiteNight;
import co.palaciodelmar.backend.repository.SuiteNightRepository;
import co.palaciodelmar.backend.repository.SuiteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;

/**
 * Motor de tarifa dinámica (revenue management) automático.
 *
 * Reglas, evaluadas noche a noche sobre la ocupación del alojamiento completo (todas sus habitaciones):
 *  - Alta demanda: ocupación >= umbral (75% por defecto) → recargo (+15%).
 *  - Última hora: noches de hoy o mañana (&lt; 48 h) con ocupación baja (&lt; 50%) → descuento (-10%).
 * La temporada y el recargo de fin de semana ya los aplica {@link PricingService}.
 */
@Service
public class DynamicPricingService {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private final SuiteRepository suiteRepository;
    private final SuiteNightRepository suiteNightRepository;
    private final Clock clock;

    @Value("${palacio.pricing.dynamic.enabled:true}")
    private boolean enabled = true;
    @Value("${palacio.pricing.dynamic.surge-threshold:0.75}")
    private double surgeThreshold = 0.75;
    @Value("${palacio.pricing.dynamic.surge-factor:1.15}")
    private BigDecimal surgeFactor = new BigDecimal("1.15");
    @Value("${palacio.pricing.dynamic.last-minute-max-occupancy:0.50}")
    private double lastMinuteMaxOccupancy = 0.50;
    @Value("${palacio.pricing.dynamic.last-minute-factor:0.90}")
    private BigDecimal lastMinuteFactor = new BigDecimal("0.90");

    @Autowired
    public DynamicPricingService(SuiteRepository suiteRepository, SuiteNightRepository suiteNightRepository) {
        this(suiteRepository, suiteNightRepository, Clock.system(BOGOTA));
    }

    public DynamicPricingService(SuiteRepository suiteRepository, SuiteNightRepository suiteNightRepository, Clock clock) {
        this.suiteRepository = suiteRepository;
        this.suiteNightRepository = suiteNightRepository;
        this.clock = clock;
    }

    public record Adjustment(LocalDate date, BigDecimal factor, String reason, int occupancyPercent) implements Serializable {}

    /**
     * Calcula los ajustes de la estancia [checkIn, checkOut). Solo devuelve noches con factor distinto de 1.
     *
     * @param excludeBookingId reserva a ignorar al medir la ocupación (al modificar una reserva existente).
     */
    @Cacheable(
            value = "dynamic_pricing",
            key = "(#suite != null ? #suite.id : 'null') + ':' + #checkIn + ':' + #checkOut + ':' + (#excludeBookingId != null ? #excludeBookingId : 'none')",
            condition = "#suite != null && #suite.id != null && #checkIn != null && #checkOut != null"
    )
    public Map<LocalDate, Adjustment> adjustmentsFor(Suite suite, LocalDate checkIn, LocalDate checkOut, String excludeBookingId) {
        Map<LocalDate, Adjustment> result = new TreeMap<>();
        if (!enabled || suite == null || checkIn == null || checkOut == null || !checkOut.isAfter(checkIn)) {
            return result;
        }

        List<Suite> propertySuites = siblingSuites(suite);
        int totalUnits = propertySuites.stream()
                .mapToInt(s -> s.getTotalUnits() != null && s.getTotalUnits() > 0 ? s.getTotalUnits() : 1)
                .sum();
        if (totalUnits <= 0) return result;

        Instant now = Instant.now(clock);
        LocalDate lastNight = checkOut.minusDays(1);
        Map<LocalDate, Integer> occupied = new HashMap<>();
        for (Suite s : propertySuites) {
            List<SuiteNight> suiteNights = suiteNightRepository.findBySuiteAndDatesBetween(s.getId(), checkIn, lastNight);
            if (suiteNights == null) continue;
            for (SuiteNight sn : suiteNights) {
                if (sn == null) continue;
                if ("cancelled".equalsIgnoreCase(sn.getStatus())) continue;
                if (sn.getExpiresAt() != null && !sn.getExpiresAt().isAfter(now)) continue;
                if (excludeBookingId != null && excludeBookingId.equals(sn.getBooking())) continue;
                if (sn.getDates() == null) continue;
                for (LocalDate d : sn.getDates()) {
                    if (d != null && !d.isBefore(checkIn) && !d.isAfter(lastNight)) {
                        occupied.merge(d, 1, Integer::sum);
                    }
                }
            }
        }

        LocalDate today = LocalDate.now(clock);
        LocalDate lastMinuteLimit = today.plusDays(1); // noches de hoy y mañana (< 48 h)
        for (LocalDate d = checkIn; d.isBefore(checkOut); d = d.plusDays(1)) {
            double occupancy = Math.min(1.0, occupied.getOrDefault(d, 0) / (double) totalUnits);
            int pct = (int) Math.round(occupancy * 100);
            if (occupancy >= surgeThreshold) {
                result.put(d, new Adjustment(d, surgeFactor, "Alta demanda (ocupación " + pct + "%)", pct));
            } else if (!d.isAfter(lastMinuteLimit) && !d.isBefore(today) && occupancy < lastMinuteMaxOccupancy) {
                result.put(d, new Adjustment(d, lastMinuteFactor, "Oferta de última hora", pct));
            }
        }
        return result;
    }

    /** Convierte los ajustes en la función que consume {@link PricingService}. */
    public static Function<LocalDate, BigDecimal> asFactorFunction(Map<LocalDate, Adjustment> adjustments) {
        if (adjustments == null || adjustments.isEmpty()) return null;
        return date -> {
            Adjustment a = adjustments.get(date);
            return a != null ? a.factor() : null;
        };
    }

    /** Texto legible de cada ajuste, para mostrar al huésped y guardar en la reserva. */
    public static List<String> describe(Map<LocalDate, Adjustment> adjustments) {
        if (adjustments == null || adjustments.isEmpty()) return List.of();
        List<String> notes = new ArrayList<>();
        for (Adjustment a : adjustments.values()) {
            int pct = a.factor().subtract(BigDecimal.ONE).movePointRight(2).intValue();
            notes.add(a.date() + ": " + a.reason() + " (" + (pct > 0 ? "+" : "") + pct + "%)");
        }
        return notes;
    }

    private List<Suite> siblingSuites(Suite suite) {
        List<Suite> siblings = List.of();
        if (suite.getPropertyId() != null) {
            siblings = suiteRepository.findByPropertyIdAndActiveTrue(suite.getPropertyId());
        } else if (suite.getBranch() != null) {
            siblings = suiteRepository.findByBranchAndActiveTrue(suite.getBranch());
        }
        if (siblings == null || siblings.isEmpty()) {
            return List.of(suite);
        }
        boolean containsSelf = siblings.stream().anyMatch(s -> Objects.equals(s.getId(), suite.getId()));
        if (!containsSelf) {
            List<Suite> withSelf = new ArrayList<>(siblings);
            withSelf.add(suite);
            return withSelf;
        }
        return siblings;
    }
}
