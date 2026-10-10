import re

with open('backend-java/src/main/java/co/palaciodelmar/backend/service/AvailabilityService.java', 'r') as f:
    content = f.read()

search = """            List<SuiteNight> occupiedNights = suiteNightRepository.findBySuiteAndDatesBetween(suite.getId(), checkIn, checkOut.minusDays(1));
            int occupiedUnits = occupiedNights.size();
            int totalCapacity = suite.getTotalUnits() != null ? suite.getTotalUnits() : 1;
            int free = Math.max(0, totalCapacity - occupiedUnits);

            BigDecimal totalPrice = suite.getBasePrice().multiply(BigDecimal.valueOf(nights));
            BigDecimal pricePerNight = suite.getBasePrice();"""

replace = """            List<SuiteNight> occupiedNights = suiteNightRepository.findBySuiteAndDatesBetween(suite.getId(), checkIn, checkOut.minusDays(1));
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
            java.math.BigDecimal pricePerNight = suite.getBasePrice();"""

if search in content:
    content = content.replace(search, replace)
    with open('backend-java/src/main/java/co/palaciodelmar/backend/service/AvailabilityService.java', 'w') as f:
        f.write(content)
        print("Success")
else:
    print("Not found")
