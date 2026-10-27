package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.model.Booking;
import co.palaciodelmar.backend.model.Promotion;
import co.palaciodelmar.backend.model.Suite;
import co.palaciodelmar.backend.model.SuiteNight;
import co.palaciodelmar.backend.repository.BookingRepository;
import co.palaciodelmar.backend.repository.BranchRepository;
import co.palaciodelmar.backend.repository.PromotionRepository;
import co.palaciodelmar.backend.repository.PropertyRepository;
import co.palaciodelmar.backend.repository.SuiteNightRepository;
import co.palaciodelmar.backend.repository.SuiteRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AvailabilityService {

    private final SuiteRepository suiteRepository;
    private final SuiteNightRepository suiteNightRepository;
    private final BookingRepository bookingRepository;
    private final PromotionRepository promotionRepository;
    private final BranchRepository branchRepository;
    private final PropertyRepository propertyRepository;
    private final PricingService pricingService;

    public static final Map<String, String> SLUG_ALIASES = Map.of(
            "suite-presidencial-mar", "penthouse-bocagrande",
            "casona-colonial-getsemani", "suite-arte-patio-getsemani",
            "loft-getsemani-art", "habitacion-bohemia-getsemani",
            "suite-colonial-patio", "suite-colonial-centro",
            "villa-playa-boquilla", "master-villa-boquilla"
    );

    public Optional<Suite> findSuiteByIdOrSlug(String idOrSlug) {
        if (idOrSlug == null || idOrSlug.isBlank()) return Optional.empty();
        String lower = idOrSlug.trim().toLowerCase();
        Optional<Suite> suite = suiteRepository.findById(idOrSlug)
                .or(() -> suiteRepository.findBySlug(lower));
        if (suite.isPresent()) return suite;
        String alias = SLUG_ALIASES.get(lower);
        if (alias != null) {
            return suiteRepository.findById(alias)
                    .or(() -> suiteRepository.findBySlug(alias));
        }
        return Optional.empty();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SuiteAvailabilityResult implements Serializable {
        private static final long serialVersionUID = 1L;
        private Suite suite;
        private Boolean isAvailable;
        private Integer availableUnits;
        private Integer totalUnits;
        private BigDecimal pricePerNight;
        private BigDecimal totalPrice;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AvailabilityResponseData implements Serializable {
        private static final long serialVersionUID = 1L;
        private LocalDate checkIn;
        private LocalDate checkOut;
        private Long nights;
        private List<SuiteAvailabilityResult> availableSuites;
        private List<SuiteAvailabilityResult> unavailableSuites;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CalendarDay implements Serializable {
        private static final long serialVersionUID = 1L;
        private String date;
        private Integer day;
        private Integer totalSuites;
        private Integer availableSuites;
        private Boolean soldOut;
        private Double occupancyRate;
        private java.math.BigDecimal startingPrice;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlySummary implements Serializable {
        private static final long serialVersionUID = 1L;
        private Integer soldOutDays;
        private Double averageOccupancy;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyAvailabilityResponseData implements Serializable {
        private static final long serialVersionUID = 1L;
        private Integer year;
        private Integer month;
        private Integer daysInMonth;
        private Integer totalSuites;
        private List<CalendarDay> calendar;
        private MonthlySummary summary;
    }

    private List<Suite> getApprovedActiveSuites() {
        Map<String, co.palaciodelmar.backend.model.Property> propMap = propertyRepository.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(
                        co.palaciodelmar.backend.model.Property::getId,
                        java.util.function.Function.identity(),
                        (a, b) -> a
                ));
        return suiteRepository.findByActiveTrue().stream()
                .filter(s -> {
                    if (s.getPropertyId() == null) {
                        return true;
                    }
                    co.palaciodelmar.backend.model.Property p = propMap.get(s.getPropertyId());
                    return p != null && !Boolean.FALSE.equals(p.getActive()) && "APPROVED".equalsIgnoreCase(p.getStatus());
                })
                .toList();
    }

    @Cacheable(
            value = "availability_search",
            key = "#checkIn + ':' + #checkOut + ':' + (#guests != null ? #guests : 0) + ':' + (#branchId != null ? #branchId : 'all') + ':' + (#suiteType != null ? #suiteType : 'all')",
            condition = "#checkIn != null && #checkOut != null && #checkOut.isAfter(#checkIn)"
    )
    public AvailabilityResponseData checkAvailability(LocalDate checkIn, LocalDate checkOut, Integer guests, String branchId, String suiteType) {
        if (checkIn == null || checkOut == null) {
            throw new IllegalArgumentException("Las fechas de check-in y check-out son obligatorias");
        }
        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
        if (nights <= 0) {
            throw new IllegalArgumentException("La fecha de check-out debe ser posterior al check-in");
        }

        String resolvedBranchId = null;
        String resolvedPropertyId = null;
        if (branchId != null && !branchId.isEmpty()) {
            resolvedBranchId = branchRepository.findBySlug(branchId).map(b -> b.getId()).orElse(branchId);
            resolvedPropertyId = propertyRepository.findBySlug(branchId).map(p -> p.getId()).orElse(branchId);
        }

        List<Suite> allSuites = getApprovedActiveSuites();
        List<SuiteAvailabilityResult> availableSuites = new ArrayList<>();
        List<SuiteAvailabilityResult> unavailableSuites = new ArrayList<>();

        for (Suite suite : allSuites) {
            if (branchId != null && !branchId.isEmpty()) {
                boolean matchesBranch = suite.getBranch() != null && (suite.getBranch().equals(resolvedBranchId) || suite.getBranch().equals(branchId));
                boolean matchesProperty = suite.getPropertyId() != null && (suite.getPropertyId().equals(resolvedPropertyId) || suite.getPropertyId().equals(branchId));
                if (!matchesBranch && !matchesProperty) {
                    continue;
                }
            }
            if (suiteType != null && !suiteType.isEmpty() && !"Todas las suites".equalsIgnoreCase(suiteType) && !suiteType.equalsIgnoreCase(suite.getType())) {
                continue;
            }
            if (guests != null && guests > 0 && suite.getMaxGuests() != null && suite.getMaxGuests() < guests) {
                continue;
            }

            java.time.Instant now = java.time.Instant.now();
            List<SuiteNight> occupiedNights = suiteNightRepository.findBySuiteAndDatesBetween(suite.getId(), checkIn, checkOut.minusDays(1)).stream()
                    .filter(sn -> !"cancelled".equalsIgnoreCase(sn.getStatus()) && (sn.getExpiresAt() == null || sn.getExpiresAt().isAfter(now)))
                    .toList();
            int totalCapacity = suite.getTotalUnits() != null ? suite.getTotalUnits() : 1;

            int maxOccupiedOnAnyDate = 0;
            java.util.Map<java.time.LocalDate, Long> occupancyPerDate = occupiedNights.stream()
                .flatMap(sn -> sn.getDates().stream())
                .filter(d -> !d.isBefore(checkIn) && d.isBefore(checkOut))
                .collect(java.util.stream.Collectors.groupingBy(d -> d, java.util.stream.Collectors.counting()));

            for (int i = 0; i < nights; i++) {
                long occ = occupancyPerDate.getOrDefault(checkIn.plusDays(i), 0L);
                if (occ > maxOccupiedOnAnyDate) {
                    maxOccupiedOnAnyDate = (int) occ;
                }
            }
            int free = Math.max(0, totalCapacity - maxOccupiedOnAnyDate);

            co.palaciodelmar.backend.service.PricingService.PricingResult pricingResult = pricingService.calculatePrice(suite.getBasePrice(), checkIn, checkOut, java.math.BigDecimal.ZERO);
            java.math.BigDecimal totalPrice = pricingResult.total();
            java.math.BigDecimal pricePerNight = suite.getBasePrice();

            SuiteAvailabilityResult result = SuiteAvailabilityResult.builder()
                    .suite(suite)
                    .isAvailable(free > 0)
                    .availableUnits(free)
                    .totalUnits(totalCapacity)
                    .pricePerNight(pricePerNight)
                    .totalPrice(totalPrice)
                    .build();

            if (free > 0) {
                availableSuites.add(result);
            } else {
                unavailableSuites.add(result);
            }
        }

        return AvailabilityResponseData.builder()
                .checkIn(checkIn)
                .checkOut(checkOut)
                .nights(nights)
                .availableSuites(availableSuites)
                .unavailableSuites(unavailableSuites)
                .build();
    }

    @Cacheable(
            value = "availability_monthly",
            key = "#year + '-' + #month + ':' + (#guests != null ? #guests : 0) + ':' + (#branchId != null ? #branchId : 'all') + ':' + (#suiteType != null ? #suiteType : 'all') + ':all'",
            condition = "#month >= 1 && #month <= 12 && #year >= 2000 && #year <= 2100"
    )
    public MonthlyAvailabilityResponseData getMonthlyAvailability(int year, int month, Integer guests, String branchId, String suiteType) {
        return getMonthlyAvailability(year, month, guests, branchId, suiteType, null);
    }

    @Cacheable(
            value = "availability_monthly",
            key = "#year + '-' + #month + ':' + (#guests != null ? #guests : 0) + ':' + (#branchId != null ? #branchId : 'all') + ':' + (#suiteType != null ? #suiteType : 'all') + ':' + (#suiteId != null ? #suiteId : 'all')",
            condition = "#month >= 1 && #month <= 12 && #year >= 2000 && #year <= 2100"
    )
    public MonthlyAvailabilityResponseData getMonthlyAvailability(int year, int month, Integer guests, String branchId, String suiteType, String suiteId) {
        if (month < 1 || month > 12 || year < 2000 || year > 2100) {
            throw new IllegalArgumentException("Mes o año fuera de rango válido");
        }
        LocalDate startDate = LocalDate.of(year, month, 1);
        int daysInMonth = startDate.lengthOfMonth();
        LocalDate endDate = startDate.withDayOfMonth(daysInMonth);

        String resolvedBranchId = null;
        String resolvedPropertyId = null;
        if (branchId != null && !branchId.isEmpty()) {
            resolvedBranchId = branchRepository.findBySlug(branchId).map(b -> b.getId()).orElse(branchId);
            resolvedPropertyId = propertyRepository.findBySlug(branchId).map(p -> p.getId()).orElse(branchId);
        }

        List<Suite> allSuites = getApprovedActiveSuites();
        List<Suite> filteredSuites = new ArrayList<>();

        if (suiteId != null && !suiteId.isBlank()) {
            Optional<Suite> specificSuite = findSuiteByIdOrSlug(suiteId);
            if (specificSuite.isPresent() && !Boolean.FALSE.equals(specificSuite.get().getActive())) {
                Suite s = specificSuite.get();
                boolean approved = true;
                if (s.getPropertyId() != null) {
                    co.palaciodelmar.backend.model.Property p = propertyRepository.findById(s.getPropertyId()).orElse(null);
                    approved = p != null && !Boolean.FALSE.equals(p.getActive()) && "APPROVED".equalsIgnoreCase(p.getStatus());
                }
                if (approved) {
                    filteredSuites.add(s);
                }
            }
        }

        if (filteredSuites.isEmpty()) {
            for (Suite suite : allSuites) {
                if (branchId != null && !branchId.isEmpty()) {
                    boolean matchesBranch = suite.getBranch() != null && (suite.getBranch().equals(resolvedBranchId) || suite.getBranch().equals(branchId));
                    boolean matchesProperty = suite.getPropertyId() != null && (suite.getPropertyId().equals(resolvedPropertyId) || suite.getPropertyId().equals(branchId));
                    if (!matchesBranch && !matchesProperty) {
                        continue;
                    }
                }
                if (suiteType != null && !suiteType.isEmpty() && !"Todas las suites".equalsIgnoreCase(suiteType) && !suiteType.equalsIgnoreCase(suite.getType())) {
                    continue;
                }
                if (guests != null && guests > 0 && suite.getMaxGuests() != null && suite.getMaxGuests() < guests) {
                    continue;
                }
                filteredSuites.add(suite);
            }
        }

        int totalSuites = 0;
        for (Suite s : filteredSuites) {
            totalSuites += s.getTotalUnits() != null ? s.getTotalUnits() : 1;
        }

        List<CalendarDay> calendar = new ArrayList<>();
        int soldOutDays = 0;
        double sumOccupancy = 0.0;
        java.time.Instant now = java.time.Instant.now();

        // Pre-cargar ocupación mensual en 1 sola consulta por suite (elimina N+1 de 62*N queries)
        Map<String, Map<LocalDate, Integer>> occupiedBySuiteAndDay = new HashMap<>();
        for (Suite s : filteredSuites) {
            Map<LocalDate, Integer> perDay = new HashMap<>();
            List<SuiteNight> monthNights = suiteNightRepository.findBySuiteAndDatesBetween(s.getId(), startDate, endDate);
            if (monthNights != null) {
                for (SuiteNight sn : monthNights) {
                    if ("cancelled".equalsIgnoreCase(sn.getStatus())) {
                        continue;
                    }
                    if (sn.getExpiresAt() != null && !sn.getExpiresAt().isAfter(now)) {
                        continue;
                    }
                    if (sn.getDates() != null) {
                        for (LocalDate d : sn.getDates()) {
                            if (d != null && !d.isBefore(startDate) && !d.isAfter(endDate)) {
                                perDay.merge(d, 1, Integer::sum);
                            }
                        }
                    }
                }
            }
            occupiedBySuiteAndDay.put(s.getId(), perDay);
        }

        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate currentDay = LocalDate.of(year, month, day);
            String dateStr = currentDay.toString();
            int totalOccupiedOnDay = 0;

            for (Suite s : filteredSuites) {
                Map<LocalDate, Integer> perDay = occupiedBySuiteAndDay.getOrDefault(s.getId(), Collections.emptyMap());
                totalOccupiedOnDay += perDay.getOrDefault(currentDay, 0);
            }

            // Un día solo está agotado (soldOut) si existen habitaciones en catálogo (totalSuites > 0)
            // y todas han sido reservadas. Si no hay suites en el filtro, no es agotado (soldOut = false).
            int available = totalSuites > 0 ? Math.max(0, totalSuites - totalOccupiedOnDay) : 1;
            boolean soldOut = totalSuites > 0 && available == 0;
            if (soldOut) {
                soldOutDays++;
            }

            double occupancyRate = totalSuites > 0 ? ((double) totalOccupiedOnDay / totalSuites) * 100.0 : 0.0;
            sumOccupancy += occupancyRate;

            java.math.BigDecimal minPrice = null;
            if (!soldOut && totalSuites > 0) {
                for (Suite s : filteredSuites) {
                    Map<LocalDate, Integer> perDay = occupiedBySuiteAndDay.getOrDefault(s.getId(), Collections.emptyMap());
                    int occupiedUnits = perDay.getOrDefault(currentDay, 0);
                    int totU = s.getTotalUnits() != null ? s.getTotalUnits() : 1;
                    if (occupiedUnits < totU) {
                        co.palaciodelmar.backend.service.PricingService.PricingResult pr = pricingService.calculatePrice(s.getBasePrice(), currentDay, currentDay.plusDays(1), java.math.BigDecimal.ZERO);
                        java.math.BigDecimal p = pr.nightlyPrices().get(0).price();
                        if (minPrice == null || p.compareTo(minPrice) < 0) {
                            minPrice = p;
                        }
                    }
                }
            }

            calendar.add(CalendarDay.builder()
                    .date(dateStr)
                    .day(day)
                    .totalSuites(totalSuites)
                    .availableSuites(available)
                    .soldOut(soldOut)
                    .occupancyRate(occupancyRate)
                    .startingPrice(minPrice)
                    .build());
        }

        double averageOccupancy = daysInMonth > 0 ? sumOccupancy / daysInMonth : 0.0;

        return MonthlyAvailabilityResponseData.builder()
                .year(year)
                .month(month)
                .daysInMonth(daysInMonth)
                .totalSuites(totalSuites)
                .calendar(calendar)
                .summary(MonthlySummary.builder()
                        .soldOutDays(soldOutDays)
                        .averageOccupancy(averageOccupancy)
                        .build())
                .build();
    }

    public List<Promotion> getMonthlyPromotions(int year, int month, String branchId) {
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.plusMonths(1).minusDays(1);
        if (branchId != null && !branchId.isEmpty()) {
            String resolvedBranchId = branchRepository.findBySlug(branchId).map(b -> b.getId()).orElse(branchId);
            return promotionRepository.findByBranchAndActiveTrue(resolvedBranchId).stream()
                    .filter(p -> (p.getStartDate() == null || !p.getStartDate().isAfter(endDate)) &&
                                 (p.getEndDate() == null || !p.getEndDate().isBefore(startDate)))
                    .toList();
        }
        return promotionRepository.findByActiveTrueAndStartDateLessThanEqualAndEndDateGreaterThanEqual(endDate, startDate);
    }

    public java.util.Map<String, Object> getPeakDates(int year) {
        java.util.Map<String, Object> res = new java.util.HashMap<>();
        List<java.util.Map<String, String>> highSeason = List.of(
                java.util.Map.of("start", year + "-04-01", "end", year + "-04-10", "startDate", year + "-04-01", "endDate", year + "-04-10", "name", "Semana Santa", "label", "Semana Santa"),
                java.util.Map.of("start", year + "-06-01", "end", year + "-08-31", "startDate", year + "-06-01", "endDate", year + "-08-31", "name", "Vacaciones de Mitad de Año", "label", "Vacaciones de Mitad de Año")
        );
        List<java.util.Map<String, String>> peakSeason = List.of(
                java.util.Map.of("start", year + "-12-21", "end", (year + 1) + "-01-10", "startDate", year + "-12-21", "endDate", (year + 1) + "-01-10", "name", "Temporada Pico Fin de Año", "label", "Temporada Pico Fin de Año")
        );
        List<java.util.Map<String, String>> holidaysList = List.of(
                java.util.Map.of("date", year + "-01-01", "name", "Año Nuevo"),
                java.util.Map.of("date", year + "-01-06", "name", "Reyes Magos"),
                java.util.Map.of("date", year + "-03-23", "name", "San José"),
                java.util.Map.of("date", year + "-04-09", "name", "Jueves Santo"),
                java.util.Map.of("date", year + "-04-10", "name", "Viernes Santo"),
                java.util.Map.of("date", year + "-05-01", "name", "Día del Trabajo"),
                java.util.Map.of("date", year + "-05-25", "name", "Ascensión del Señor"),
                java.util.Map.of("date", year + "-06-15", "name", "Corpus Christi"),
                java.util.Map.of("date", year + "-06-22", "name", "Sagrado Corazón"),
                java.util.Map.of("date", year + "-06-29", "name", "San Pedro y San Pablo"),
                java.util.Map.of("date", year + "-07-20", "name", "Independencia de Colombia"),
                java.util.Map.of("date", year + "-08-07", "name", "Batalla de Boyacá"),
                java.util.Map.of("date", year + "-08-17", "name", "Asunción de la Virgen"),
                java.util.Map.of("date", year + "-10-12", "name", "Día de la Raza"),
                java.util.Map.of("date", year + "-11-02", "name", "Todos los Santos"),
                java.util.Map.of("date", year + "-11-16", "name", "Independencia de Cartagena"),
                java.util.Map.of("date", year + "-12-08", "name", "Inmaculada Concepción"),
                java.util.Map.of("date", year + "-12-25", "name", "Navidad")
        );
        res.put("highSeason", highSeason);
        res.put("peakSeason", peakSeason);
        res.put("holidays", holidaysList);
        return res;
    }
}
