package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.dto.BookingDTO;
import co.palaciodelmar.backend.dto.BookingRequest;
import co.palaciodelmar.backend.model.Booking;
import co.palaciodelmar.backend.model.Branch;
import co.palaciodelmar.backend.model.Experience;
import co.palaciodelmar.backend.model.Suite;
import co.palaciodelmar.backend.model.SuiteNight;
import co.palaciodelmar.backend.model.Promotion;
import co.palaciodelmar.backend.model.User;
import co.palaciodelmar.backend.repository.BookingRepository;
import co.palaciodelmar.backend.repository.BranchRepository;
import co.palaciodelmar.backend.repository.ExperienceRepository;
import co.palaciodelmar.backend.repository.PromotionRepository;
import co.palaciodelmar.backend.repository.SuiteNightRepository;
import co.palaciodelmar.backend.repository.SuiteRepository;
import co.palaciodelmar.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingService {

    private static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");
    private static final java.security.SecureRandom SECURE_RANDOM = new java.security.SecureRandom();

    private final BookingRepository bookingRepository;
    private final SuiteRepository suiteRepository;
    private final BranchRepository branchRepository;
    private final ExperienceRepository experienceRepository;
    private final SuiteNightRepository suiteNightRepository;
    private final UserRepository userRepository;
    private final PromotionRepository promotionRepository;
    private final PricingService pricingService;
    private final PaymentGateway paymentGateway;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private DynamicPricingService dynamicPricingService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private co.palaciodelmar.backend.repository.PropertyRepository propertyRepository;

    public BookingDTO createBooking(String userId, BookingRequest request) {

        if (userId == null || userId.isBlank()) {
            String rawEmail = request.getGuestEmail() == null ? "" : request.getGuestEmail().trim();
            String rawName = request.getGuestName() == null ? "" : request.getGuestName().trim();
            if (rawName.isEmpty() || rawEmail.isEmpty()) {
                throw new IllegalArgumentException("Por favor completa tu nombre y correo electrónico");
            }
            if (!rawEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                throw new IllegalArgumentException("Ingresa un correo electrónico válido");
            }
            String email = rawEmail.toLowerCase();
            User guest = userRepository.findByEmail(email).orElseGet(() -> {
                User u = User.builder()
                        .name(request.getGuestName() != null ? request.getGuestName() : "Huésped")
                        .email(email)
                        .role("user")
                        .status("active")
                        .emailVerified(false)
                        .profile(User.Profile.builder().phone(request.getGuestPhone()).build())
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build();
                return userRepository.save(u);
            });
            userId = guest.getId();
        }

        Suite suite = suiteRepository.findById(request.getSuiteId())
                .or(() -> suiteRepository.findBySlug(request.getSuiteId().toLowerCase()))
                .orElseThrow(() -> new IllegalArgumentException("Suite no encontrada: esta habitación no existe o ya no está disponible"));

        if ((suite.getActive() != null && !suite.getActive()) || (suite.getAvailable() != null && !suite.getAvailable())) {
            throw new IllegalArgumentException("Esta habitación no está disponible para reservas actualmente");
        }

        if (suite.getPropertyId() != null && propertyRepository != null) {
            co.palaciodelmar.backend.model.Property property = propertyRepository.findById(suite.getPropertyId()).orElse(null);
            if (property == null || Boolean.FALSE.equals(property.getActive()) || !"APPROVED".equalsIgnoreCase(property.getStatus())) {
                throw new IllegalArgumentException("El alojamiento no está disponible para reservas porque la propiedad no está aprobada o está inactiva");
            }
        }

        int maxGuests = suite.getMaxGuests() != null ? suite.getMaxGuests() : 2;
        if (request.getGuests() != null && (request.getGuests() < 1 || request.getGuests() > maxGuests)) {
            throw new IllegalArgumentException("Esta habitación admite máximo " + maxGuests + " huéspedes");
        }

        if (request.getCheckIn().isBefore(LocalDate.now(BOGOTA_ZONE))) {
            throw new IllegalArgumentException("La fecha de check-in no puede ser anterior a la fecha actual");
        }

        long nights = ChronoUnit.DAYS.between(request.getCheckIn(), request.getCheckOut());
        if (nights <= 0) {
            throw new IllegalArgumentException("La fecha de check-out debe ser posterior al check-in");
        }

        List<SuiteNight> occupiedNights = suiteNightRepository.findBySuiteAndDatesBetween(suite.getId(), request.getCheckIn(), request.getCheckOut().minusDays(1));
        assertCribAvailable(request, null);
        int totalCapacity = suite.getTotalUnits() != null ? suite.getTotalUnits() : 1;

        List<Integer> availableSlots = new ArrayList<>();
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
                availableSlots.add(slot);
            }
        }
        if (availableSlots.isEmpty()) {
            throw new IllegalArgumentException("La suite no está disponible para las fechas seleccionadas");
        }

        Promotion activePromo = findActiveBranchPromotionEntity(suite.getBranch(), request.getCheckIn(), request.getCheckOut());
        BigDecimal branchPromoPercent = activePromo != null && activePromo.getDiscountPercent() != null
                ? BigDecimal.valueOf(activePromo.getDiscountPercent())
                : BigDecimal.ZERO;
        Map<LocalDate, DynamicPricingService.Adjustment> dynamicAdjustments = dynamicPricingService != null
                ? dynamicPricingService.adjustmentsFor(suite, request.getCheckIn(), request.getCheckOut(), null)
                : Map.of();
        PricingService.PricingResult pricingResult = (dynamicAdjustments != null && !dynamicAdjustments.isEmpty())
                ? pricingService.calculatePrice(suite.getBasePrice(), request.getCheckIn(), request.getCheckOut(), branchPromoPercent, DynamicPricingService.asFactorFunction(dynamicAdjustments))
                : pricingService.calculatePrice(suite.getBasePrice(), request.getCheckIn(), request.getCheckOut(), branchPromoPercent);
        BigDecimal totalRoomPrice = pricingResult.total();

        List<Booking.SelectedExperience> selectedExpList = parseExperiences(request.getExperiences());
        BigDecimal experiencesTotal = BigDecimal.ZERO;
        for (Booking.SelectedExperience exp : selectedExpList) {
            if (exp.getPrice() != null && exp.getQuantity() != null) {
                experiencesTotal = experiencesTotal.add(exp.getPrice().multiply(BigDecimal.valueOf(exp.getQuantity())));
            }
        }

        BigDecimal vipExtrasTotal = BigDecimal.ZERO;
        if (Boolean.TRUE.equals(request.getPetFriendly())) {
            vipExtrasTotal = vipExtrasTotal.add(PET_FEE);
        }
        if (Boolean.TRUE.equals(request.getAirportPickup())) {
            vipExtrasTotal = vipExtrasTotal.add(new BigDecimal("150000"));
        }
        List<Booking.FoodOrder> sanitizedFoodOrders = new ArrayList<>();
        if (request.getFoodOrders() != null) {
            for (Booking.FoodOrder fo : request.getFoodOrders()) {
                BigDecimal foodPrice = new BigDecimal("80000");
                vipExtrasTotal = vipExtrasTotal.add(foodPrice);
                if (Boolean.TRUE.equals(fo.getIncludesBreakfastCombo())) {
                    vipExtrasTotal = vipExtrasTotal.add(new BigDecimal("35000"));
                }
                sanitizedFoodOrders.add(Booking.FoodOrder.builder()
                        .dishName(fo.getDishName())
                        .type(fo.getType())
                        .includesBreakfastCombo(fo.getIncludesBreakfastCombo())
                        .price(foodPrice)
                        .build());
            }
        }

        BigDecimal extraGuestTotal = extraGuestFee(request.getGuests(), nights);

        BigDecimal totalPrice = totalRoomPrice.add(experiencesTotal).add(vipExtrasTotal).add(extraGuestTotal);
        // RN-F01: Deposito de Garantia Simulado
        BigDecimal guarantee = GUARANTEE_DEPOSIT;
        totalPrice = totalPrice.add(guarantee);
        String bookingCode = "PDM-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        Booking.GuestDetails guestDetails = parseGuestDetails(request);

        AppliedDiscount appliedDiscount = resolveAppliedDiscount(activePromo, nights);
        String promoId = appliedDiscount.id();
        String promoName = appliedDiscount.name();
        BigDecimal promoDiscountPercent = appliedDiscount.percent();

        BigDecimal platformFee = totalRoomPrice.multiply(new BigDecimal("0.08")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal hostPayout = totalRoomPrice.subtract(platformFee);

        Booking booking = Booking.builder()
                .user(userId)
                .suite(suite.getId())
                .branch(suite.getBranch())
                .propertyId(suite.getPropertyId())
                .platformFee(platformFee)
                .hostPayout(hostPayout)
                .checkIn(request.getCheckIn())
                .checkOut(request.getCheckOut())
                .nights((int) nights)
                .guests(request.getGuests() != null ? request.getGuests() : 1)
                .children(request.getChildren() != null ? request.getChildren() : 0)
                .needsCrib(request.getNeedsCrib())
                .pricePerNight(suite.getBasePrice())
                .subtotal(pricingResult.subtotal())
                .discount(pricingResult.discount())
                .taxes(pricingResult.taxes())
                .nightlyPrices(pricingResult.nightlyPrices().stream().map(np -> new Booking.NightlyPrice(np.date(), np.price())).toList())
                .dynamicPricingNotes(new ArrayList<>(DynamicPricingService.describe(dynamicAdjustments)))
                .appliedPromotionId(promoId)
                .appliedPromotionName(promoName)
                .appliedPromotionDiscountPercent(promoDiscountPercent)
                .totalPrice(totalPrice)
                .paidAmount(BigDecimal.ZERO)
                .balanceDue(totalPrice)
                .status("pending")
                .guaranteeDeposit(guarantee)
                .guaranteeRefunded(false)
                .paymentStatus("pending")
                .bookingCode(bookingCode)
                .guestDetails(guestDetails)
                .experiences(selectedExpList)
                .petFriendly(request.getPetFriendly())
                .airportPickup(request.getAirportPickup())
                .foodOrders(sanitizedFoodOrders)
                .smartLockPin(String.format("%06d", SECURE_RANDOM.nextInt(100000, 1000000)))
                .expiresAt(Instant.now().plusSeconds(900)) // 15 mins hold
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Booking savedBooking = bookingRepository.save(booking);

        // Block nights con reintento automático en el siguiente slot disponible si hay colisión concurrente
        boolean nightsLocked = false;
        for (int candidateSlot : availableSlots) {
            List<SuiteNight> insertedNights = new java.util.ArrayList<>();
            try {
                for (int i = 0; i < nights; i++) {
                    LocalDate date = request.getCheckIn().plusDays(i);
                    SuiteNight suiteNight = SuiteNight.builder()
                            .suite(suite.getId())
                            .branch(suite.getBranch())
                            .unitSlot(candidateSlot)
                            .dates(java.util.Collections.singletonList(date))
                            .booking(savedBooking.getId())
                            .status("held")
                            .expiresAt(savedBooking.getExpiresAt())
                            .createdAt(Instant.now())
                            .build();
                    insertedNights.add(suiteNightRepository.save(suiteNight));
                }
                savedBooking.setUnitSlot(candidateSlot);
                bookingRepository.save(savedBooking);
                nightsLocked = true;
                break;
            } catch (org.springframework.dao.DuplicateKeyException e) {
                suiteNightRepository.deleteAll(insertedNights);
            }
        }
        if (!nightsLocked) {
            bookingRepository.delete(savedBooking);
            throw new IllegalStateException("Hubo una colisión concurrente. Las fechas ya no están disponibles.");
        }

        BookingDTO dto = toDTO(savedBooking);
        if (eventPublisher != null) {
            eventPublisher.publishEvent(new co.palaciodelmar.backend.event.BookingDomainEvent(
                    "booking.created",
                    savedBooking.getId(),
                    savedBooking.getPropertyId(),
                    dto,
                    Instant.now()
            ));
        }
        return dto;
    }

    public BookingDTO modifyBooking(String bookingId, String userId, BookingRequest request) {
        Booking booking = getBookingEntityById(bookingId);
        if (!booking.getUser().equals(userId)) {
            throw new IllegalArgumentException("No tienes permiso para modificar esta reserva");
        }

        if (!"pending".equalsIgnoreCase(booking.getStatus()) && !"confirmed".equalsIgnoreCase(booking.getStatus())) {
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
        assertCribAvailable(request, bookingId);
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

        Promotion activePromo = findActiveBranchPromotionEntity(suite.getBranch(), request.getCheckIn(), request.getCheckOut());
        BigDecimal branchPromoPercent = activePromo != null && activePromo.getDiscountPercent() != null
                ? BigDecimal.valueOf(activePromo.getDiscountPercent())
                : BigDecimal.ZERO;
        Map<LocalDate, DynamicPricingService.Adjustment> dynamicAdjustments = dynamicPricingService != null
                ? dynamicPricingService.adjustmentsFor(suite, request.getCheckIn(), request.getCheckOut(), booking.getId())
                : Map.of();
        PricingService.PricingResult pricingResult = (dynamicAdjustments != null && !dynamicAdjustments.isEmpty())
                ? pricingService.calculatePrice(suite.getBasePrice(), request.getCheckIn(), request.getCheckOut(), branchPromoPercent, DynamicPricingService.asFactorFunction(dynamicAdjustments))
                : pricingService.calculatePrice(suite.getBasePrice(), request.getCheckIn(), request.getCheckOut(), branchPromoPercent);
        BigDecimal totalRoomPrice = pricingResult.total();
        List<Booking.SelectedExperience> selectedExpList = request.getExperiences() != null ? parseExperiences(request.getExperiences()) : booking.getExperiences();
        BigDecimal experiencesTotal = BigDecimal.ZERO;
        for (Booking.SelectedExperience exp : selectedExpList) {
            if (exp.getPrice() != null && exp.getQuantity() != null) {
                experiencesTotal = experiencesTotal.add(exp.getPrice().multiply(BigDecimal.valueOf(exp.getQuantity())));
            }
        }

        BigDecimal vipExtrasTotal = calculateVipExtrasTotal(booking);
        int effectiveGuests = request.getGuests() != null ? request.getGuests() : (booking.getGuests() != null ? booking.getGuests() : 1);
        BigDecimal currentDeposit = booking.getGuaranteeDeposit() != null ? booking.getGuaranteeDeposit() : BigDecimal.ZERO;
        BigDecimal newTotalPrice = totalRoomPrice.add(experiencesTotal).add(vipExtrasTotal)
                .add(extraGuestFee(effectiveGuests, nights)).add(currentDeposit);
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
        booking.setDynamicPricingNotes(new ArrayList<>(DynamicPricingService.describe(dynamicAdjustments)));
        AppliedDiscount appliedDiscount = resolveAppliedDiscount(activePromo, nights);
        booking.setAppliedPromotionId(appliedDiscount.id());
        booking.setAppliedPromotionName(appliedDiscount.name());
        booking.setAppliedPromotionDiscountPercent(appliedDiscount.percent());
        booking.setTotalPrice(newTotalPrice);
        BigDecimal modPlatformFee = pricingResult.total().multiply(new BigDecimal("0.08")).setScale(2, RoundingMode.HALF_UP);
        booking.setPlatformFee(modPlatformFee);
        booking.setHostPayout(pricingResult.total().subtract(modPlatformFee));

        if (booking.getPaidAmount().compareTo(newTotalPrice) >= 0) {
            booking.setBalanceDue(BigDecimal.ZERO);
            booking.setRefundedAmount(booking.getPaidAmount().subtract(newTotalPrice));
            booking.setPaymentStatus("paid");
        } else {
            booking.setBalanceDue(newTotalPrice.subtract(booking.getPaidAmount()));
            booking.setPaymentStatus(booking.getPaidAmount().compareTo(BigDecimal.ZERO) > 0 ? "partial" : "pending");
        }

        booking.setUpdatedAt(Instant.now());

        // Re-block nights
        List<SuiteNight> originalNights = suiteNightRepository.findByBooking(bookingId);
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
                        .expiresAt("pending".equalsIgnoreCase(booking.getStatus()) ? Instant.now().plusSeconds(900) : null)
                        .createdAt(Instant.now())
                        .build();
                insertedNights.add(suiteNightRepository.save(suiteNight));
            }
        } catch (org.springframework.dao.DuplicateKeyException e) {
            suiteNightRepository.deleteAll(insertedNights);
            suiteNightRepository.saveAll(originalNights);
            throw new IllegalStateException("Hubo una colisión concurrente al modificar. Las fechas ya no están disponibles.");
        }

        return toDTO(bookingRepository.save(booking));
    }

    public List<BookingDTO> getUserBookings(String userId) {
        return bookingRepository.findByUserOrderByCreatedAtDesc(userId).stream()
                .map(this::toDTO)
                .toList();
    }

    public List<BookingDTO> getUpcomingBookings(String userId) {
        LocalDate today = LocalDate.now(BOGOTA_ZONE);
        return bookingRepository.findByUserOrderByCreatedAtDesc(userId).stream()
                .filter(b -> b.getCheckIn() != null && !b.getCheckIn().isBefore(today))
                .filter(b -> !"cancelled".equalsIgnoreCase(b.getStatus()))
                .map(this::toDTO)
                .toList();
    }

    public BookingDTO getBookingByCode(String bookingCode) {
        Booking booking = bookingRepository.findByBookingCode(bookingCode)
                .orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada con código: " + bookingCode));
        return toDTO(booking);
    }

    public BookingDTO getBookingById(String id) {
        return toDTO(getBookingEntityById(id));
    }

    public BookingDTO getBookingByIdSecure(String id, User user) {
        Booking booking = getBookingEntityById(id);
        if (user != null) {
            boolean isAdmin = "ADMIN".equalsIgnoreCase(user.getRole());
            boolean isHost = "HOST".equalsIgnoreCase(user.getRole());
            if (isHost) {
                validateHostAccessToBooking(user, booking);
            } else if (!isAdmin && !booking.getUser().equals(user.getId())) {
                throw new IllegalArgumentException("No tienes permiso para acceder a esta reserva");
            }
        } else {
            boolean isGuestBooking = userRepository.findById(booking.getUser())
                    .map(u -> u.getPassword() == null || u.getPassword().isBlank())
                    .orElse(true);
            if (!isGuestBooking) {
                throw new IllegalArgumentException("Esta reserva pertenece a una cuenta registrada. Inicia sesión para ver los detalles.");
            }
        }
        BookingDTO dto = toDTO(booking);
        if (user == null) {
            dto.setSmartLockPin(null);
        }
        return dto;
    }

    public BookingDTO getBookingByCodeSecure(String bookingCode, User user) {
        Booking booking = bookingRepository.findByBookingCode(bookingCode)
                .orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada con código: " + bookingCode));
        if (user != null) {
            boolean isAdmin = "ADMIN".equalsIgnoreCase(user.getRole());
            boolean isHost = "HOST".equalsIgnoreCase(user.getRole());
            if (isHost) {
                validateHostAccessToBooking(user, booking);
            } else if (!isAdmin && !booking.getUser().equals(user.getId())) {
                throw new IllegalArgumentException("No tienes permiso para acceder a esta reserva");
            }
        } else {
            boolean isGuestBooking = userRepository.findById(booking.getUser())
                    .map(u -> u.getPassword() == null || u.getPassword().isBlank())
                    .orElse(true);
            if (!isGuestBooking) {
                throw new IllegalArgumentException("Esta reserva pertenece a una cuenta registrada. Inicia sesión para ver los detalles.");
            }
        }
        BookingDTO dto = toDTO(booking);
        if (user == null) {
            dto.setSmartLockPin(null);
        }
        return dto;
    }

    private void validateHostAccessToBooking(User user, Booking booking) {
        if (booking.getPropertyId() != null && propertyRepository != null) {
            co.palaciodelmar.backend.model.Property property = propertyRepository.findById(booking.getPropertyId()).orElse(null);
            if (property != null && property.getOwnerId() != null && property.getOwnerId().equals(user.getId())) {
                return;
            }
        }
        if (user.getBranches() != null && !user.getBranches().isEmpty() && booking.getBranch() != null && user.getBranches().contains(booking.getBranch())) {
            return;
        }
        throw new IllegalArgumentException("No tienes permiso para acceder a una reserva de otra propiedad");
    }

    public Booking getBookingEntityById(String id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada con id: " + id));
    }

    public BookingDTO cancelBooking(String bookingId, String userId, String reason) {
        Booking booking = getBookingEntityById(bookingId);
        if (!booking.getUser().equals(userId)) {
            throw new IllegalArgumentException("No tienes permiso para cancelar esta reserva");
        }

        if (booking.getCheckIn() == null) {
            throw new IllegalStateException("La reserva no tiene una fecha de check-in válida");
        }

        long daysUntilCheckIn = ChronoUnit.DAYS.between(LocalDate.now(BOGOTA_ZONE), booking.getCheckIn());
        BigDecimal paidAmount = booking.getPaidAmount() != null
                ? booking.getPaidAmount().max(BigDecimal.ZERO)
                : BigDecimal.ZERO;
        BigDecimal retention = BigDecimal.ZERO;
        if (booking.getTotalPrice() == null || booking.getTotalPrice().signum() < 0) {
            throw new IllegalStateException("La reserva no tiene un precio total válido para calcular la retención");
        }
        // El depósito de garantía no es ingreso: la penalidad se calcula sobre el total sin él.
        BigDecimal cancelDeposit = booking.getGuaranteeDeposit() != null ? booking.getGuaranteeDeposit() : BigDecimal.ZERO;
        BigDecimal retentionBase = booking.getTotalPrice().subtract(cancelDeposit).max(BigDecimal.ZERO);
        if (daysUntilCheckIn < 2) {
            // RN-F03 (No-Show estricto < 48 horas): Cancelación tardía con penalidad del 100%.
            retention = retentionBase;
        } else if (daysUntilCheckIn < 7) {
            // Penalidad del 10% si cancela dentro de los 7 días
            retention = retentionBase.multiply(new BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal refundAmount = paidAmount.subtract(retention).max(BigDecimal.ZERO).min(paidAmount);

        changeBookingStatus(booking, co.palaciodelmar.backend.model.BookingStatus.CANCELLED, userId, reason);
        if (reason != null && !reason.isEmpty()) {
            booking.setCancellationReason(reason);
        }
        booking.setRefundedAmount(refundAmount);
        if (paidAmount.compareTo(BigDecimal.ZERO) > 0) {
            booking.setPaymentStatus(refundAmount.compareTo(paidAmount) >= 0 ? "refunded" : "partial");
            if (refundAmount.compareTo(BigDecimal.ZERO) > 0) {
                paymentGateway.refund("tx_" + booking.getId(), refundAmount);
            }
        }
        booking.setBalanceDue(BigDecimal.ZERO);

        Booking savedBooking = bookingRepository.save(booking);
        suiteNightRepository.deleteByBooking(bookingId);
        return toDTO(savedBooking);
    }

    public BookingDTO addExperienceToBooking(String bookingId, String userId, String experienceId) {
        Booking booking = getBookingEntityById(bookingId);
        if (!booking.getUser().equals(userId)) {
            throw new IllegalArgumentException("No tienes permiso para modificar esta reserva");
        }
        Experience experience = experienceRepository.findById(experienceId)
                .orElseThrow(() -> new IllegalArgumentException("Experiencia no encontrada"));

        List<Booking.SelectedExperience> current = booking.getExperiences();
        if (current == null) current = new ArrayList<>();

        boolean exists = false;
        for (Booking.SelectedExperience exp : current) {
            if (exp.getExperienceId() != null && exp.getExperienceId().equals(experienceId)) {
                exp.setQuantity((exp.getQuantity() != null ? exp.getQuantity() : 1) + 1);
                exists = true;
                break;
            }
        }
        if (!exists) {
            current.add(Booking.SelectedExperience.builder()
                    .experienceId(experience.getId())
                    .name(experience.getName())
                    .price(experience.getPrice())
                    .quantity(1)
                    .build());
        }
        booking.setExperiences(current);

        BigDecimal expTotal = BigDecimal.ZERO;
        if (current != null) {
            for (Booking.SelectedExperience exp : current) {
                if (exp.getPrice() != null && exp.getQuantity() != null) {
                    expTotal = expTotal.add(exp.getPrice().multiply(BigDecimal.valueOf(exp.getQuantity())));
                }
            }
        }
        BigDecimal roomTotal = booking.getSubtotal() != null ? booking.getSubtotal().add(booking.getTaxes() != null ? booking.getTaxes() : BigDecimal.ZERO).subtract(booking.getDiscount() != null ? booking.getDiscount() : BigDecimal.ZERO) : BigDecimal.ZERO;
        BigDecimal vipExtrasTotal = calculateVipExtrasTotal(booking);
        BigDecimal newTotal = roomTotal.add(expTotal).add(vipExtrasTotal).add(fixedCharges(booking));
        booking.setTotalPrice(newTotal);
        if (booking.getPaidAmount().compareTo(newTotal) >= 0) {
            booking.setBalanceDue(BigDecimal.ZERO);
            booking.setPaymentStatus("paid");
        } else {
            booking.setBalanceDue(newTotal.subtract(booking.getPaidAmount()));
            booking.setPaymentStatus(booking.getPaidAmount().compareTo(BigDecimal.ZERO) > 0 ? "partial" : "pending");
        }
        booking.setUpdatedAt(Instant.now());
        return toDTO(bookingRepository.save(booking));
    }

    public BookingDTO removeExperienceFromBooking(String bookingId, String userId, String experienceId) {
        Booking booking = getBookingEntityById(bookingId);
        if (!booking.getUser().equals(userId)) {
            throw new IllegalArgumentException("No tienes permiso para modificar esta reserva");
        }
        List<Booking.SelectedExperience> current = booking.getExperiences();
        if (current != null) {
            current.removeIf(exp -> exp.getExperienceId() != null && exp.getExperienceId().equals(experienceId));
            booking.setExperiences(current);
        }

        BigDecimal expTotal = BigDecimal.ZERO;
        if (current != null) {
            for (Booking.SelectedExperience exp : current) {
                if (exp.getPrice() != null && exp.getQuantity() != null) {
                    expTotal = expTotal.add(exp.getPrice().multiply(BigDecimal.valueOf(exp.getQuantity())));
                }
            }
        }
        BigDecimal roomTotal = booking.getSubtotal() != null ? booking.getSubtotal().add(booking.getTaxes() != null ? booking.getTaxes() : BigDecimal.ZERO).subtract(booking.getDiscount() != null ? booking.getDiscount() : BigDecimal.ZERO) : BigDecimal.ZERO;
        BigDecimal vipExtrasTotal = calculateVipExtrasTotal(booking);
        BigDecimal newTotal = roomTotal.add(expTotal).add(vipExtrasTotal).add(fixedCharges(booking));
        booking.setTotalPrice(newTotal);
        if (booking.getPaidAmount().compareTo(newTotal) >= 0) {
            booking.setBalanceDue(BigDecimal.ZERO);
            booking.setPaymentStatus("paid");
        } else {
            booking.setBalanceDue(newTotal.subtract(booking.getPaidAmount()));
            booking.setPaymentStatus(booking.getPaidAmount().compareTo(BigDecimal.ZERO) > 0 ? "partial" : "pending");
        }
        booking.setUpdatedAt(Instant.now());
        return toDTO(bookingRepository.save(booking));
    }

    private static final int MAX_CRIBS = 5;
    private static final BigDecimal EXTRA_GUEST_FEE = new BigDecimal("120000");
    private static final BigDecimal GUARANTEE_DEPOSIT = new BigDecimal("100000");
    private static final BigDecimal PET_FEE = new BigDecimal("80000");

    private record AppliedDiscount(String id, String name, BigDecimal percent) {}

    /** Misma regla que PricingService: gana el mayor entre la promoción de la sede y la estadía larga. */
    private AppliedDiscount resolveAppliedDiscount(Promotion promo, long nights) {
        BigDecimal promoPercent = promo != null && promo.getDiscountPercent() != null
                ? BigDecimal.valueOf(promo.getDiscountPercent())
                : BigDecimal.ZERO;
        BigDecimal longStay = PricingService.longStayPercent(nights);
        if (promo != null && promoPercent.compareTo(longStay) > 0) {
            return new AppliedDiscount(promo.getId(), promo.getTitle(), promoPercent);
        }
        if (longStay.signum() > 0) {
            return new AppliedDiscount(null, "Estadía larga (" + longStay.stripTrailingZeros().toPlainString() + "%)", longStay);
        }
        return new AppliedDiscount(null, null, BigDecimal.ZERO);
    }

    private BigDecimal extraGuestFee(Integer guests, long nights) {
        if (guests == null || guests <= 2 || nights <= 0) {
            return BigDecimal.ZERO;
        }
        return EXTRA_GUEST_FEE.multiply(BigDecimal.valueOf(guests - 2)).multiply(BigDecimal.valueOf(nights));
    }

    /** Cargos fijos de una reserva ya creada: huéspedes extra + depósito de garantía. */
    private BigDecimal fixedCharges(Booking booking) {
        BigDecimal deposit = booking.getGuaranteeDeposit() != null ? booking.getGuaranteeDeposit() : BigDecimal.ZERO;
        long nights = booking.getNights() != null ? booking.getNights() : 0;
        return extraGuestFee(booking.getGuests(), nights).add(deposit);
    }

    /** Cunas: tope global de 5. Solapamiento estricto (check-out = check-in no cuenta) y excluye la propia reserva. */
    private void assertCribAvailable(BookingRequest request, String excludeBookingId) {
        if (!Boolean.TRUE.equals(request.getNeedsCrib())) {
            return;
        }
        long cribsInUse = bookingRepository.findByStatusIn(List.of("confirmed", "checked_in", "pending")).stream()
                .filter(b -> Boolean.TRUE.equals(b.getNeedsCrib())
                        && (excludeBookingId == null || !excludeBookingId.equals(b.getId()))
                        && b.getCheckIn() != null && b.getCheckOut() != null
                        && b.getCheckIn().isBefore(request.getCheckOut())
                        && b.getCheckOut().isAfter(request.getCheckIn()))
                .count();
        if (cribsInUse >= MAX_CRIBS) {
            throw new IllegalArgumentException("Lo sentimos, no hay stock físico de cunas disponible para estas fechas.");
        }
    }

    private BigDecimal calculateVipExtrasTotal(Booking booking) {
        BigDecimal vipExtrasTotal = BigDecimal.ZERO;
        if (Boolean.TRUE.equals(booking.getPetFriendly())) {
            vipExtrasTotal = vipExtrasTotal.add(PET_FEE);
        }
        if (Boolean.TRUE.equals(booking.getAirportPickup())) {
            vipExtrasTotal = vipExtrasTotal.add(new BigDecimal("150000"));
        }
        if (booking.getFoodOrders() != null) {
            for (Booking.FoodOrder fo : booking.getFoodOrders()) {
                vipExtrasTotal = vipExtrasTotal.add(new BigDecimal("80000"));
                if (Boolean.TRUE.equals(fo.getIncludesBreakfastCombo())) {
                    vipExtrasTotal = vipExtrasTotal.add(new BigDecimal("35000"));
                }
            }
        }
        return vipExtrasTotal;
    }

    public BookingDTO toDTO(Booking booking) {
        if (booking == null) return null;

        Suite suite = null;
        if (booking.getSuite() != null) {
            suite = suiteRepository.findById(booking.getSuite()).orElse(null);
        }

        Branch branch = null;
        if (booking.getBranch() != null) {
            branch = branchRepository.findById(booking.getBranch()).orElse(null);
        } else if (suite != null && suite.getBranch() != null) {
            branch = branchRepository.findById(suite.getBranch()).orElse(null);
        }

        Booking.GuestDetails gd = booking.getGuestDetails();
        String guestName = gd != null ? gd.getFullName() : "";
        String guestEmail = gd != null ? gd.getEmail() : "";
        String guestPhone = gd != null ? gd.getPhone() : "";
        String specialRequests = gd != null ? gd.getSpecialRequests() : "";

        int nights = booking.getNights() != null ? booking.getNights() : 1;
        BigDecimal pricePerNight = booking.getPricePerNight() != null ? booking.getPricePerNight() : (suite != null ? suite.getBasePrice() : BigDecimal.ZERO);
        BigDecimal subtotal = booking.getSubtotal() != null ? booking.getSubtotal() : pricePerNight.multiply(BigDecimal.valueOf(nights));

        String status = booking.getStatus() != null ? booking.getStatus() : "pending";
        boolean isCancelable = "pending".equalsIgnoreCase(status) || "confirmed".equalsIgnoreCase(status);
        boolean isModifiable = isCancelable && (booking.getCheckIn() == null || !booking.getCheckIn().isBefore(LocalDate.now(BOGOTA_ZONE)));

        String code = booking.getBookingCode();
        if (code == null || code.trim().isEmpty()) {
            code = "PDM-" + (booking.getId() != null && booking.getId().length() >= 6
                    ? booking.getId().substring(booking.getId().length() - 6).toUpperCase()
                    : "000000");
        }

        return BookingDTO.builder()
                .id(booking.getId())
                ._id(booking.getId())
                .user(booking.getUser())
                .suite(suite)
                .branch(branch)
                .propertyId(booking.getPropertyId() != null ? booking.getPropertyId() : (suite != null ? suite.getPropertyId() : null))
                .platformFee(booking.getPlatformFee())
                .hostPayout(booking.getHostPayout())
                .unitSlot(booking.getUnitSlot())
                .checkIn(booking.getCheckIn())
                .checkOut(booking.getCheckOut())
                .nights(nights)
                .guests(booking.getGuests())
                .children(booking.getChildren())
                .needsCrib(booking.getNeedsCrib())
                .guestName(guestName)
                .guestEmail(guestEmail)
                .guestPhone(guestPhone)
                .specialRequests(specialRequests)
                .pricePerNight(pricePerNight)
                .subtotal(subtotal)
                .discount(booking.getDiscount())
                .taxes(booking.getTaxes())
                .nightlyPrices(booking.getNightlyPrices())
                .totalPrice(booking.getTotalPrice())
                .paidAmount(booking.getPaidAmount())
                .refundedAmount(booking.getRefundedAmount())
                .balanceDue(booking.getBalanceDue())
                .status(status)
                .guaranteeDeposit(booking.getGuaranteeDeposit())
                .guaranteeRefunded(booking.getGuaranteeRefunded())
                .paymentStatus(booking.getPaymentStatus())
                .bookingCode(code)
                .isModifiable(isModifiable)
                .isCancelable(isCancelable)
                .isCancellable(isCancelable)
                .guestDetails(gd)
                .experiences(booking.getExperiences())
                .petFriendly(booking.getPetFriendly())
                .airportPickup(booking.getAirportPickup())
                .foodOrders(booking.getFoodOrders())
                .smartLockPin(("confirmed".equalsIgnoreCase(status) || "checked_in".equalsIgnoreCase(status) || "completed".equalsIgnoreCase(status)) ? booking.getSmartLockPin() : null)
                .preCheckInCompleted(booking.getPreCheckInCompleted())
                .dynamicPricingNotes(booking.getDynamicPricingNotes() != null ? booking.getDynamicPricingNotes() : java.util.List.of())
                .payoutId(booking.getPayoutId())
                .reviewRequestSentAt(booking.getReviewRequestSentAt())
                .notes(booking.getNotes())
                .cancellationReason(booking.getCancellationReason())
                .appliedPromotionId(booking.getAppliedPromotionId())
                .appliedPromotionName(booking.getAppliedPromotionName())
                .appliedPromotionDiscountPercent(booking.getAppliedPromotionDiscountPercent())
                .expiresAt(booking.getExpiresAt())
                .createdAt(booking.getCreatedAt())
                .updatedAt(booking.getUpdatedAt())
                .build();
    }

    private List<Booking.SelectedExperience> parseExperiences(List<Object> rawExperiences) {
        List<Booking.SelectedExperience> result = new ArrayList<>();
        if (rawExperiences == null || rawExperiences.isEmpty()) {
            return result;
        }
        for (Object item : rawExperiences) {
            String expId = null;
            if (item instanceof String id) {
                expId = id;
            } else if (item instanceof Booking.SelectedExperience selExp) {
                expId = selExp.getExperienceId();
            } else if (item instanceof Map<?, ?> map) {
                expId = (String) map.get("experienceId");
                if (expId == null) expId = (String) map.get("id");
                if (expId == null) expId = (String) map.get("_id");
            }
            if (expId != null) {
                experienceRepository.findById(expId).ifPresent(exp -> {
                    result.add(Booking.SelectedExperience.builder()
                            .experienceId(exp.getId())
                            .name(exp.getName())
                            .price(exp.getPrice())
                            .quantity(1)
                            .build());
                });
            }
        }
        return result;
    }

    private Booking.GuestDetails parseGuestDetails(BookingRequest request) {
        String fullName = request.getGuestName();
        String email = request.getGuestEmail();
        String phone = request.getGuestPhone();
        String specialRequests = request.getSpecialRequests();

        if (request.getGuestDetails() != null) {
            if (fullName == null || fullName.isBlank()) fullName = request.getGuestDetails().getFullName();
            if (email == null || email.isBlank()) email = request.getGuestDetails().getEmail();
            if (phone == null || phone.isBlank()) phone = request.getGuestDetails().getPhone();
            if (specialRequests == null || specialRequests.isBlank()) specialRequests = request.getGuestDetails().getSpecialRequests();
        }

        return Booking.GuestDetails.builder()
                .fullName(fullName != null ? fullName : "")
                .email(email != null ? email : "")
                .phone(phone != null ? phone : "")
                .specialRequests(specialRequests != null ? specialRequests : "")
                .build();
    }

    public void changeBookingStatus(Booking booking, co.palaciodelmar.backend.model.BookingStatus newStatus, String changedBy, String reason) {
        if (booking.getStatus() == null) {
            throw new IllegalStateException("El estado actual de la reserva es nulo");
        }
        co.palaciodelmar.backend.model.BookingStatus currentStatus;
        try {
            currentStatus = co.palaciodelmar.backend.model.BookingStatus.valueOf(booking.getStatus().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("El estado actual de la reserva es inválido: " + booking.getStatus());
        }

        if (!currentStatus.canTransitionTo(newStatus)) {
            throw new IllegalStateException("No se puede transicionar del estado " + currentStatus + " a " + newStatus);
        }

        booking.setStatus(newStatus.name().toLowerCase());
        booking.getStatusHistory().add(Booking.StatusHistoryEntry.builder()
                .status(newStatus.name().toLowerCase())
                .timestamp(Instant.now())
                .changedBy(changedBy)
                .reason(reason)
                .build());
        booking.setUpdatedAt(Instant.now());

        if (newStatus == co.palaciodelmar.backend.model.BookingStatus.CONFIRMED) {
            java.util.List<co.palaciodelmar.backend.model.SuiteNight> nights = suiteNightRepository.findByBooking(booking.getId());
            for (co.palaciodelmar.backend.model.SuiteNight sn : nights) {
                sn.setExpiresAt(null);
                sn.setStatus("confirmed");
                suiteNightRepository.save(sn);
            }
            if (eventPublisher != null) {
                eventPublisher.publishEvent(new co.palaciodelmar.backend.event.BookingDomainEvent(
                        "booking.confirmed",
                        booking.getId(),
                        booking.getPropertyId(),
                        toDTO(booking),
                        Instant.now()
                ));
            }
        } else if (newStatus == co.palaciodelmar.backend.model.BookingStatus.CANCELLED) {
            if (eventPublisher != null) {
                eventPublisher.publishEvent(new co.palaciodelmar.backend.event.BookingDomainEvent(
                        "booking.cancelled",
                        booking.getId(),
                        booking.getPropertyId(),
                        toDTO(booking),
                        Instant.now()
                ));
            }
        } else if (newStatus == co.palaciodelmar.backend.model.BookingStatus.CHECKED_IN) {
            if (eventPublisher != null) {
                eventPublisher.publishEvent(new co.palaciodelmar.backend.event.BookingDomainEvent(
                        "booking.checked_in",
                        booking.getId(),
                        booking.getPropertyId(),
                        toDTO(booking),
                        Instant.now()
                ));
            }
        } else if (newStatus == co.palaciodelmar.backend.model.BookingStatus.CHECKED_OUT) {
            if (eventPublisher != null) {
                eventPublisher.publishEvent(new co.palaciodelmar.backend.event.BookingDomainEvent(
                        "booking.checked_out",
                        booking.getId(),
                        booking.getPropertyId(),
                        toDTO(booking),
                        Instant.now()
                ));
            }
        } else if (newStatus == co.palaciodelmar.backend.model.BookingStatus.NO_SHOW) {
            if (eventPublisher != null) {
                eventPublisher.publishEvent(new co.palaciodelmar.backend.event.BookingDomainEvent(
                        "booking.no_show",
                        booking.getId(),
                        booking.getPropertyId(),
                        toDTO(booking),
                        Instant.now()
                ));
            }
        }
    }

    private Promotion findActiveBranchPromotionEntity(String branchId, LocalDate checkIn, LocalDate checkOut) {
        if (branchId == null || checkIn == null || checkOut == null) return null;
        List<Promotion> promotions = promotionRepository.findByBranchAndActiveTrue(branchId);
        return promotions.stream()
                .filter(p -> p.getDiscountPercent() != null && p.getDiscountPercent() > 0)
                .filter(p -> (p.getStartDate() == null || !p.getStartDate().isAfter(checkOut)) &&
                             (p.getEndDate() == null || !p.getEndDate().isBefore(checkIn)))
                .max(java.util.Comparator.comparing(Promotion::getDiscountPercent))
                .orElse(null);
    }

    private BigDecimal findActiveBranchPromotion(String branchId, LocalDate checkIn, LocalDate checkOut) {
        Promotion promo = findActiveBranchPromotionEntity(branchId, checkIn, checkOut);
        return promo != null && promo.getDiscountPercent() != null ? BigDecimal.valueOf(promo.getDiscountPercent()) : BigDecimal.ZERO;
    }

    public BookingDTO performPreCheckIn(String bookingId, String userId, String documentNumber, String companionGuests) {
        Booking booking = getBookingEntityById(bookingId);
        if (userId != null && !userId.equals(booking.getUser())) {
            throw new IllegalArgumentException("No tienes permiso para gestionar esta reserva");
        }
        if (documentNumber == null || documentNumber.isBlank()) {
            throw new IllegalArgumentException("El número de documento es obligatorio para el registro turístico");
        }

        booking.setDocumentNumber(documentNumber.trim());
        booking.setCompanionGuests(companionGuests != null ? companionGuests.trim() : "");
        booking.setPreCheckInCompleted(true);
        if (booking.getSmartLockPin() == null) {
            booking.setSmartLockPin(String.format("%06d", SECURE_RANDOM.nextInt(100000, 1000000)));
        }
        booking.setUpdatedAt(Instant.now());
        return toDTO(bookingRepository.save(booking));
    }
}
