import re

with open('backend-java/src/main/java/co/palaciodelmar/backend/service/BookingService.java', 'r') as f:
    content = f.read()

search = """        if ("cancelled".equalsIgnoreCase(booking.getStatus()) || "completed".equalsIgnoreCase(booking.getStatus())) {
            throw new IllegalArgumentException("No se puede modificar una reserva cancelada o completada");
        }

        Suite suite = suiteRepository.findById(booking.getSuite())
                .or(() -> suiteRepository.findBySlug(booking.getSuite().toLowerCase()))
                .orElseThrow(() -> new IllegalArgumentException("Suite no encontrada"));

        long nights = ChronoUnit.DAYS.between(request.getCheckIn(), request.getCheckOut());
        if (nights <= 0) {
            throw new IllegalArgumentException("La fecha de check-out debe ser posterior al check-in");
        }

        BigDecimal totalRoomPrice = suite.getBasePrice().multiply(BigDecimal.valueOf(nights));"""

replace = """        if (!"pending".equalsIgnoreCase(booking.getStatus()) && !"confirmed".equalsIgnoreCase(booking.getStatus())) {
            throw new IllegalArgumentException("No se puede modificar una reserva en estado " + booking.getStatus());
        }

        Suite suite = suiteRepository.findById(booking.getSuite())
                .or(() -> suiteRepository.findBySlug(booking.getSuite().toLowerCase()))
                .orElseThrow(() -> new IllegalArgumentException("Suite no encontrada"));

        int maxGuests = suite.getMaxGuests() != null ? suite.getMaxGuests() : 2;
        if (request.getGuests() != null && (request.getGuests() < 1 || request.getGuests() > maxGuests)) {
            throw new IllegalArgumentException("Esta habitación admite máximo " + maxGuests + " huéspedes");
        }

        long nights = ChronoUnit.DAYS.between(request.getCheckIn(), request.getCheckOut());
        if (nights <= 0) {
            throw new IllegalArgumentException("La fecha de check-out debe ser posterior al check-in");
        }

        List<SuiteNight> occupiedNights = suiteNightRepository.findBySuiteAndDatesBetween(suite.getId(), request.getCheckIn(), request.getCheckOut().minusDays(1));
        occupiedNights.removeIf(sn -> bookingId.equals(sn.getBooking()));
        int totalCapacity = suite.getTotalUnits() != null ? suite.getTotalUnits() : 1;

        int assignedSlot = -1;
        for (int slot = 1; slot <= totalCapacity; slot++) {
            int finalSlot = slot;
            boolean isAvailable = true;
            for (int i = 0; i < nights; i++) {
                LocalDate d = request.getCheckIn().plusDays(i);
                boolean slotOccupiedOnDate = occupiedNights.stream()
                        .anyMatch(sn -> sn.getUnitSlot() != null && sn.getUnitSlot() == finalSlot && sn.getDates().contains(d));
                if (slotOccupiedOnDate) {
                    isAvailable = false;
                    break;
                }
            }
            if (isAvailable) {
                assignedSlot = slot;
                break;
            }
        }
        if (assignedSlot == -1) {
            throw new IllegalArgumentException("La suite no está disponible para las fechas seleccionadas");
        }

        PricingService.PricingResult pricingResult = pricingService.calculatePrice(suite.getBasePrice(), request.getCheckIn(), request.getCheckOut(), BigDecimal.ZERO);
        BigDecimal totalRoomPrice = pricingResult.total();"""

if search in content:
    content = content.replace(search, replace)
    with open('backend-java/src/main/java/co/palaciodelmar/backend/service/BookingService.java', 'w') as f:
        f.write(content)
        print("Success")
else:
    print("Not found")

