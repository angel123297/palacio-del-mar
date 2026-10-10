import re

with open('backend-java/src/main/java/co/palaciodelmar/backend/service/BookingService.java', 'r') as f:
    content = f.read()

create_booking_search = """        List<SuiteNight> occupiedNights = suiteNightRepository.findBySuiteAndDatesBetween(suite.getId(), request.getCheckIn(), request.getCheckOut().minusDays(1));
        int totalCapacity = suite.getTotalUnits() != null ? suite.getTotalUnits() : 1;
        if (occupiedNights.size() >= totalCapacity) {
            throw new IllegalArgumentException("La suite no está disponible para las fechas seleccionadas");
        }"""

create_booking_replace = """        List<SuiteNight> occupiedNights = suiteNightRepository.findBySuiteAndDatesBetween(suite.getId(), request.getCheckIn(), request.getCheckOut().minusDays(1));
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
        }"""

content = content.replace(create_booking_search, create_booking_replace)

block_nights_search = """        // Block nights
        for (int i = 0; i < nights; i++) {
            LocalDate date = request.getCheckIn().plusDays(i);
            SuiteNight suiteNight = SuiteNight.builder()
                    .suite(suite.getId())
                    .branch(suite.getBranch())
                    .unitSlot(1)
                    .dates(java.util.Collections.singletonList(date))
                    .booking(savedBooking.getId())
                    .status("held")
                    .expiresAt(savedBooking.getExpiresAt())
                    .createdAt(Instant.now())
                    .build();
            try {
                suiteNightRepository.save(suiteNight);
            } catch (Exception ignored) {
            }
        }"""

block_nights_replace = """        // Block nights
        savedBooking.setUnitSlot(assignedSlot);
        bookingRepository.save(savedBooking);
        List<SuiteNight> insertedNights = new java.util.ArrayList<>();
        try {
            for (int i = 0; i < nights; i++) {
                LocalDate date = request.getCheckIn().plusDays(i);
                SuiteNight suiteNight = SuiteNight.builder()
                        .suite(suite.getId())
                        .branch(suite.getBranch())
                        .unitSlot(assignedSlot)
                        .dates(java.util.Collections.singletonList(date))
                        .booking(savedBooking.getId())
                        .status("held")
                        .expiresAt(savedBooking.getExpiresAt())
                        .createdAt(Instant.now())
                        .build();
                insertedNights.add(suiteNightRepository.save(suiteNight));
            }
        } catch (org.springframework.dao.DuplicateKeyException e) {
            suiteNightRepository.deleteAll(insertedNights);
            bookingRepository.delete(savedBooking);
            throw new IllegalStateException("Hubo una colisión concurrente. Las fechas ya no están disponibles.");
        }"""

content = content.replace(block_nights_search, block_nights_replace)

modify_booking_search = """        if ("cancelled".equalsIgnoreCase(booking.getStatus()) || "checked_out".equalsIgnoreCase(booking.getStatus()) || "expired".equalsIgnoreCase(booking.getStatus()) || "no_show".equalsIgnoreCase(booking.getStatus()) || "checked_in".equalsIgnoreCase(booking.getStatus())) {
            throw new IllegalArgumentException("No se puede modificar una reserva en estado " + booking.getStatus());
        }

        Suite suite = suiteRepository.findById(booking.getSuite())
                .or(() -> suiteRepository.findBySlug(booking.getSuite().toLowerCase()))
                .orElseThrow(() -> new IllegalArgumentException("Suite no encontrada"));

        long nights = ChronoUnit.DAYS.between(request.getCheckIn(), request.getCheckOut());
        if (nights <= 0) {
            throw new IllegalArgumentException("La fecha de check-out debe ser posterior al check-in");
        }

        BigDecimal totalRoomPrice = suite.getBasePrice().multiply(BigDecimal.valueOf(nights));"""

modify_booking_replace = """        if (!"pending".equalsIgnoreCase(booking.getStatus()) && !"confirmed".equalsIgnoreCase(booking.getStatus())) {
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

content = content.replace(modify_booking_search, modify_booking_replace)

modify_booking_search_2 = """        BigDecimal newTotalPrice = totalRoomPrice.add(experiencesTotal);
        booking.setCheckIn(request.getCheckIn());
        booking.setCheckOut(request.getCheckOut());
        booking.setNights((int) nights);
        if (request.getGuests() != null) booking.setGuests(request.getGuests());
        if (request.getChildren() != null) booking.setChildren(request.getChildren());
        booking.setGuestDetails(parseGuestDetails(request));
        booking.setExperiences(selectedExpList);
        booking.setTotalPrice(newTotalPrice);"""

modify_booking_replace_2 = """        BigDecimal newTotalPrice = totalRoomPrice.add(experiencesTotal);
        booking.setCheckIn(request.getCheckIn());
        booking.setCheckOut(request.getCheckOut());
        booking.setNights((int) nights);
        if (request.getGuests() != null) booking.setGuests(request.getGuests());
        if (request.getChildren() != null) booking.setChildren(request.getChildren());
        booking.setGuestDetails(parseGuestDetails(request));
        booking.setExperiences(selectedExpList);
        booking.setUnitSlot(assignedSlot);
        booking.setSubtotal(pricingResult.subtotal());
        booking.setDiscount(pricingResult.discount());
        booking.setTaxes(pricingResult.taxes());
        booking.setNightlyPrices(pricingResult.nightlyPrices().stream().map(np -> new Booking.NightlyPrice(np.date(), np.price())).toList());
        booking.setTotalPrice(newTotalPrice);"""

content = content.replace(modify_booking_search_2, modify_booking_replace_2)

modify_block_nights_search = """        // Re-block nights
        suiteNightRepository.deleteByBooking(bookingId);
        for (int i = 0; i < nights; i++) {
            LocalDate date = request.getCheckIn().plusDays(i);
            SuiteNight suiteNight = SuiteNight.builder()
                    .suite(suite.getId())
                    .branch(suite.getBranch())
                    .unitSlot(1)
                    .dates(java.util.Collections.singletonList(date))
                    .booking(booking.getId())
                    .status("blocked")
                    .createdAt(Instant.now())
                    .build();
            try {
                suiteNightRepository.save(suiteNight);
            } catch (Exception ignored) {
            }
        }"""

modify_block_nights_replace = """        // Re-block nights
        suiteNightRepository.deleteByBooking(bookingId);
        List<SuiteNight> insertedNights = new java.util.ArrayList<>();
        try {
            for (int i = 0; i < nights; i++) {
                LocalDate date = request.getCheckIn().plusDays(i);
                SuiteNight suiteNight = SuiteNight.builder()
                        .suite(suite.getId())
                        .branch(suite.getBranch())
                        .unitSlot(assignedSlot)
                        .dates(java.util.Collections.singletonList(date))
                        .booking(booking.getId())
                        .status("pending".equalsIgnoreCase(booking.getStatus()) ? "held" : "blocked")
                        .expiresAt("pending".equalsIgnoreCase(booking.getStatus()) ? Instant.now().plusSeconds(1800) : null)
                        .createdAt(Instant.now())
                        .build();
                insertedNights.add(suiteNightRepository.save(suiteNight));
            }
        } catch (org.springframework.dao.DuplicateKeyException e) {
            suiteNightRepository.deleteAll(insertedNights);
            throw new IllegalStateException("Hubo una colisión concurrente al modificar. Las fechas ya no están disponibles.");
        }"""

content = content.replace(modify_block_nights_search, modify_block_nights_replace)

with open('backend-java/src/main/java/co/palaciodelmar/backend/service/BookingService.java', 'w') as f:
    f.write(content)
