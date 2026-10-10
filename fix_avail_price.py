import re

with open('backend-java/src/main/java/co/palaciodelmar/backend/service/AvailabilityService.java', 'r') as f:
    content = f.read()

content = content.replace("private Double occupancyRate;", "private Double occupancyRate;\n        private java.math.BigDecimal startingPrice;")

search = """            calendar.add(CalendarDay.builder()
                    .date(dateStr)
                    .day(day)
                    .totalSuites(totalSuites)
                    .availableSuites(available)
                    .soldOut(soldOut)
                    .occupancyRate(occupancyRate)
                    .build());"""

replace = """            java.math.BigDecimal minPrice = null;
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
                    .build());"""

if search in content:
    content = content.replace(search, replace)
    with open('backend-java/src/main/java/co/palaciodelmar/backend/service/AvailabilityService.java', 'w') as f:
        f.write(content)
        print("Success")
else:
    print("Not found")
