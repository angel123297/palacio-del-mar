package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.model.Booking;
import co.palaciodelmar.backend.model.Promotion;
import co.palaciodelmar.backend.model.Suite;
import co.palaciodelmar.backend.model.SuiteNight;
import co.palaciodelmar.backend.repository.BookingRepository;
import co.palaciodelmar.backend.repository.BranchRepository;
import co.palaciodelmar.backend.repository.PromotionRepository;
import co.palaciodelmar.backend.repository.SuiteNightRepository;
import co.palaciodelmar.backend.repository.SuiteRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
    private final PricingService pricingService;

    @Data
    @Builder
    public static class SuiteAvailabilityResult {
        private Suite suite;
        private Boolean isAvailable;
        private Integer availableUnits;
        private Integer totalUnits;
        private BigDecimal pricePerNight;
        private BigDecimal totalPrice;
    }

    @Data
    @Builder
    public static class AvailabilityResponseData {
        private LocalDate checkIn;
        private LocalDate checkOut;
        private Long nights;
        private List<SuiteAvailabilityResult> availableSuites;
        private List<SuiteAvailabilityResult> unavailableSuites;
    }

    @Data
    @Builder
    public static class CalendarDay {
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
    public static class MonthlySummary {
        private Integer soldOutDays;
        private Double averageOccupancy;
    }

    @Data
    @Builder
    public static class MonthlyAvailabilityResponseData {
        private Integer year;
        private Integer month;
        private Integer daysInMonth;
        private Integer totalSuites;
        private List<CalendarDay> calendar;
        private MonthlySummary summary;
    }

    public AvailabilityResponseData checkAvailability(LocalDate checkIn, LocalDate checkOut, Integer guests, String branchId, String suiteType) {
        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
        if (nights <= 0) {
            throw new IllegalArgumentException("La fecha de check-out debe ser posterior al check-in");
        }

        String resolvedBranchId = null;
        if (branchId != null && !branchId.isEmpty()) {
            resolvedBranchId = branchRepository.findBySlug(branchId).map(b -> b.getId()).orElse(branchId);
        }

        List<Suite> allSuites = suiteRepository.findByActiveTrue();
        List<SuiteAvailabilityResult> availableSuites = new ArrayList<>();
        List<SuiteAvailabilityResult> unavailableSuites = new ArrayList<>();

        for (Suite suite : allSuites) {
            if (resolvedBranchId != null && !resolvedBranchId.equals(suite.getBranch())) {
                continue;
            }
            if (suiteType != null && !suiteType.isEmpty() && !"Todas las suites".equalsIgnoreCase(suiteType) && !suiteType.equalsIgnoreCase(suite.getType())) {
                continue;
            }
            if (guests != null && guests > 0 && suite.getMaxGuests() != null && suite.getMaxGuests() < guests) {
                continue;
            }

            List<SuiteNight> occupiedNights = suiteNightRepository.findBySuiteAndDatesBetween(suite.getId(), checkIn, checkOut.minusDays(1));
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

    public MonthlyAvailabilityResponseData getMonthlyAvailability(int year, int month, Integer guests, String branchId, String suiteType) {
        LocalDate startDate = LocalDate.of(year, month, 1);
        int daysInMonth = startDate.lengthOfMonth();

        String resolvedBranchId = null;
        if (branchId != null && !branchId.isEmpty()) {
            resolvedBranchId = branchRepository.findBySlug(branchId).map(b -> b.getId()).orElse(branchId);
        }

        List<Suite> allSuites = suiteRepository.findByActiveTrue();
        List<Suite> filteredSuites = new ArrayList<>();

        for (Suite suite : allSuites) {
            if (resolvedBranchId != null && !resolvedBranchId.equals(suite.getBranch())) {
                continue;
            }
            if (suiteType != null && !suiteType.isEmpty() && !"Todas las suites".equalsIgnoreCase(suiteType) && !suiteType.equalsIgnoreCase(suite.getType())) {
                continue;
            }
            if (guests != null && guests > 0 && suite.getMaxGuests() != null && suite.getMaxGuests() < guests) {
                continue;
            }
            filteredSuites.add(suite);
        }

        int totalSuites = 0;
        for (Suite s : filteredSuites) {
            totalSuites += s.getTotalUnits() != null ? s.getTotalUnits() : 1;
        }

        List<CalendarDay> calendar = new ArrayList<>();
        int soldOutDays = 0;
        double sumOccupancy = 0.0;

        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate currentDay = LocalDate.of(year, month, day);
            String dateStr = currentDay.toString();
            int totalOccupiedOnDay = 0;

            for (Suite s : filteredSuites) {
                List<SuiteNight> occupied = suiteNightRepository.findBySuiteAndDatesBetween(s.getId(), currentDay, currentDay);
                totalOccupiedOnDay += occupied.size();
            }

            int available = Math.max(0, totalSuites - totalOccupiedOnDay);
            boolean soldOut = (available == 0);
            if (soldOut) {
                soldOutDays++;
            }

            double occupancyRate = totalSuites > 0 ? ((double) totalOccupiedOnDay / totalSuites) * 100.0 : 0.0;
            sumOccupancy += occupancyRate;

            java.math.BigDecimal minPrice = null;
            if (!soldOut) {
                for (Suite s : filteredSuites) {
                    List<SuiteNight> occupied = suiteNightRepository.findBySuiteAndDatesBetween(s.getId(), currentDay, currentDay);
                    int totU = s.getTotalUnits() != null ? s.getTotalUnits() : 1;
                    if (occupied.size() < totU) {
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
        String resolvedBranchId = null;
        if (branchId != null && !branchId.isEmpty()) {
            resolvedBranchId = branchRepository.findBySlug(branchId).map(b -> b.getId()).orElse(branchId);
            return promotionRepository.findByBranchAndActiveTrue(resolvedBranchId);
        }
        return promotionRepository.findByActiveTrueAndStartDateLessThanEqualAndEndDateGreaterThanEqual(endDate, startDate);
    }
}
