package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.model.Booking;
import co.palaciodelmar.backend.model.Promotion;
import co.palaciodelmar.backend.model.Suite;
import co.palaciodelmar.backend.model.SuiteNight;
import co.palaciodelmar.backend.repository.BookingRepository;
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

        List<Suite> allSuites = suiteRepository.findByActiveTrue();
        List<SuiteAvailabilityResult> availableSuites = new ArrayList<>();
        List<SuiteAvailabilityResult> unavailableSuites = new ArrayList<>();

        for (Suite suite : allSuites) {
            if (branchId != null && !branchId.isEmpty() && !branchId.equals(suite.getBranch())) {
                continue;
            }
            if (suiteType != null && !suiteType.isEmpty() && !"Todas las suites".equalsIgnoreCase(suiteType) && !suiteType.equalsIgnoreCase(suite.getType())) {
                continue;
            }
            if (guests != null && guests > 0 && suite.getMaxGuests() != null && suite.getMaxGuests() < guests) {
                continue;
            }

            List<SuiteNight> occupiedNights = suiteNightRepository.findBySuiteAndDateBetween(suite.getId(), checkIn, checkOut.minusDays(1));
            int occupiedUnits = occupiedNights.size();
            int totalCapacity = suite.getTotalUnits() != null ? suite.getTotalUnits() : 1;
            int free = Math.max(0, totalCapacity - occupiedUnits);

            BigDecimal totalPrice = suite.getBasePrice().multiply(BigDecimal.valueOf(nights));
            BigDecimal pricePerNight = suite.getBasePrice();

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

        List<Suite> allSuites = suiteRepository.findByActiveTrue();
        List<Suite> filteredSuites = new ArrayList<>();

        for (Suite suite : allSuites) {
            if (branchId != null && !branchId.isEmpty() && !branchId.equals(suite.getBranch())) {
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
                List<SuiteNight> occupied = suiteNightRepository.findBySuiteAndDateBetween(s.getId(), currentDay, currentDay);
                totalOccupiedOnDay += occupied.size();
            }

            int available = Math.max(0, totalSuites - totalOccupiedOnDay);
            boolean soldOut = (available == 0);
            if (soldOut) {
                soldOutDays++;
            }

            double occupancyRate = totalSuites > 0 ? ((double) totalOccupiedOnDay / totalSuites) * 100.0 : 0.0;
            sumOccupancy += occupancyRate;

            calendar.add(CalendarDay.builder()
                    .date(dateStr)
                    .day(day)
                    .totalSuites(totalSuites)
                    .availableSuites(available)
                    .soldOut(soldOut)
                    .occupancyRate(occupancyRate)
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
            return promotionRepository.findByBranchAndActiveTrue(branchId);
        }
        return promotionRepository.findByActiveTrueAndStartDateLessThanEqualAndEndDateGreaterThanEqual(endDate, startDate);
    }
}
