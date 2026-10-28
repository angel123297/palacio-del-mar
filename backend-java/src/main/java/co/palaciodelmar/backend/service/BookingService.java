package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.dto.BookingDTO;
import co.palaciodelmar.backend.dto.BookingRequest;
import co.palaciodelmar.backend.exception.ForbiddenAccessException;
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
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingService {

    private static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");
    private static final java.security.SecureRandom SECURE_RANDOM = new java.security.SecureRandom();
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BookingService.class);

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

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private EmailService emailService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private RedisLockService redisLockService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private SocialProofService socialProofService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private AvailabilityCacheEvictor availabilityCacheEvictor;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private co.palaciodelmar.backend.repository.PaymentRepository paymentRepository;

    public void setPaymentRepository(co.palaciodelmar.backend.repository.PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    public void setRedisLockService(RedisLockService redisLockService) {
        this.redisLockService = redisLockService;
    }

    public void setSocialProofService(SocialProofService socialProofService) {
        this.socialProofService = socialProofService;
    }

    public void setAvailabilityCacheEvictor(AvailabilityCacheEvictor availabilityCacheEvictor) {
        this.availabilityCacheEvictor = availabilityCacheEvictor;
    }

    private <T> T executeWithSuiteLock(String suiteId, java.util.function.Supplier<T> action) {
        if (redisLockService == null || suiteId == null || suiteId.isBlank()) {
            return action.get();
        }
        return redisLockService.executeWithLock(
                "suite:" + suiteId,
                java.time.Duration.ofSeconds(10),
                java.time.Duration.ofSeconds(4),
                action
        );
    }

    public BookingDTO createBooking(String userId, BookingRequest request) {
        if (userId == null || userId.isBlank()) {
            throw new ForbiddenAccessException("Debes iniciar sesión para realizar una reserva");
        }

        String rawSuiteId = request.getSuiteId().trim();
        String lowerSuiteId = rawSuiteId.toLowerCase();
        Suite suite = suiteRepository.findById(rawSuiteId)
                .or(() -> suiteRepository.findBySlug(lowerSuiteId))
                .or(() -> {
                    String alias = AvailabilityService.SLUG_ALIASES.get(lowerSuiteId);
                    return alias != null ? suiteRepository.findById(alias).or(() -> suiteRepository.findBySlug(alias)) : Optional.empty();
                })
                .orElseThrow(() -> new IllegalArgumentException("Suite no encontrada: esta habitación no existe o ya no está disponible"));

        if ((suite.getActive() != null && !suite.getActive()) || (suite.getAvailable() != null && !suite.getAvailable())) {
            throw new IllegalArgumentException("Esta habitación no está disponible para reservas actualmente");
        }

        if (suite.getPropertyId() != null && propertyRepository != null) {
            co.palaciodelmar.backend.model.Property property = propertyRepository.findById(suite.getPropertyId()).orElse(null);
            if (property == null || Boolean.FALSE.equals(property.getActive()) || !"APPROVED".equalsIgnoreCase(property.getStatus())) {
                throw new IllegalArgumentException("El alojamiento no está disponible para reservas porque la propiedad no está aprobada o está inactiva");
            }
            if (userId != null && property.getOwnerId() != null && userId.equals(property.getOwnerId())) {
                throw new IllegalArgumentException("Un anfitrión no puede reservar su propia propiedad");
            }
        }

        if (userId != null && !userId.isBlank()) {
            User bookingUser = userRepository.findById(userId).orElse(null);
            if (bookingUser != null) {
                String role = bookingUser.getRole() != null ? bookingUser.getRole().toLowerCase() : "";
                if ("host".equals(role) || "anfitrion".equals(role) || "admin".equals(role)) {
                    throw new IllegalArgumentException("Las cuentas de Anfitrión y Administrador tienen un perfil corporativo y no pueden realizar reservas de hospedaje. Utiliza una cuenta de huésped.");
                }
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

        final String resolvedUserId = userId;
        Booking savedBooking = executeWithSuiteLock(suite.getId(), () -> {
            java.time.Instant now = java.time.Instant.now();
            List<SuiteNight> occupiedNights = suiteNightRepository.findBySuiteAndDatesBetween(suite.getId(), request.getCheckIn(), request.getCheckOut().minusDays(1)).stream()
                    .filter(sn -> !"cancelled".equalsIgnoreCase(sn.getStatus()) && (sn.getExpiresAt() == null || sn.getExpiresAt().isAfter(now)))
                    .toList();
            assertCribAvailable(request, null);
            int totalCapacity = suite.getTotalUnits() != null ? suite.getTotalUnits() : 1;

            List<Integer> availableSlots = new ArrayList<>();
            for (int slot = 1; slot <= totalCapacity; slot++) {
                int finalSlot = slot;
                boolean isAvailable = true;
                for (int i = 0; i < nights; i++) {
                    LocalDate d = request.getCheckIn().plusDays(i);
                    boolean slotOccupiedOnDate = occupiedNights.stream()
                            .anyMatch(sn -> sn.getUnitSlot() != null && sn.getUnitSlot() == finalSlot && sn.getDates() != null && sn.getDates().contains(d));
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

            Promotion activePromo = findActiveBranchPromotionEntity(suite.getBranch(), request.getCheckIn(), request.getCheckIn().plusDays(nights));
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
                    .user(resolvedUserId)
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

            Booking persistedBooking = bookingRepository.save(booking);

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
                                .booking(persistedBooking.getId())
                                .status("held")
                                .expiresAt(persistedBooking.getExpiresAt())
                                .createdAt(Instant.now())
                                .build();
                        insertedNights.add(suiteNightRepository.save(suiteNight));
                    }
                    persistedBooking.setUnitSlot(candidateSlot);
                    bookingRepository.save(persistedBooking);
                    nightsLocked = true;
                    break;
                } catch (org.springframework.dao.DuplicateKeyException e) {
                    suiteNightRepository.deleteAll(insertedNights);
                }
            }
            if (!nightsLocked) {
                bookingRepository.delete(persistedBooking);
                throw new IllegalStateException("Hubo una colisión concurrente. Las fechas ya no están disponibles.");
            }

            if (redisLockService != null && persistedBooking.getId() != null) {
                redisLockService.holdSuiteDates(
                        suite.getId(),
                        persistedBooking.getUnitSlot() != null ? persistedBooking.getUnitSlot() : 1,
                        request.getCheckIn(),
                        request.getCheckOut(),
                        persistedBooking.getId(),
                        java.time.Duration.ofMinutes(15)
                );
            }
            return persistedBooking;
        });

        if (socialProofService != null) {
            socialProofService.recordBookingActivity(suite.getId(), suite.getSlug(), savedBooking.getId());
        }
        if (availabilityCacheEvictor != null) {
            availabilityCacheEvictor.evictAvailability();
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
            throw new ForbiddenAccessException("No tienes permiso para modificar esta reserva");
        }

        if (!"pending".equalsIgnoreCase(booking.getStatus()) && !"confirmed".equalsIgnoreCase(booking.getStatus())) {
            throw new IllegalArgumentException("No se puede modificar una reserva en estado " + booking.getStatus());
        }

        String rawSuiteId = booking.getSuite() != null ? booking.getSuite().trim() : "";
        String lowerSuiteId = rawSuiteId.toLowerCase();
        Suite suite = suiteRepository.findById(rawSuiteId)
                .or(() -> suiteRepository.findBySlug(lowerSuiteId))
                .or(() -> {
                    String alias = AvailabilityService.SLUG_ALIASES.get(lowerSuiteId);
                    return alias != null ? suiteRepository.findById(alias).or(() -> suiteRepository.findBySlug(alias)) : Optional.empty();
                })
                .orElseThrow(() -> new IllegalArgumentException("Suite no encontrada"));

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

        String resolvedBookingId = booking.getId() != null ? booking.getId() : bookingId;

        Booking modifiedBooking = executeWithSuiteLock(suite.getId(), () -> {
            java.time.Instant now = java.time.Instant.now();
            Booking freshBooking = bookingRepository.findById(resolvedBookingId).orElse(booking);
            if (!freshBooking.getUser().equals(userId)) {
                throw new ForbiddenAccessException("No tienes permiso para modificar esta reserva");
            }
            if (!"pending".equalsIgnoreCase(freshBooking.getStatus()) && !"confirmed".equalsIgnoreCase(freshBooking.getStatus())) {
                throw new IllegalArgumentException("No se puede modificar una reserva en estado " + freshBooking.getStatus());
            }
            if ("pending".equalsIgnoreCase(freshBooking.getStatus())
                    && freshBooking.getExpiresAt() != null
                    && freshBooking.getExpiresAt().isBefore(now)) {
                throw new IllegalArgumentException("No se puede modificar una reserva cuyo plazo de pago ha expirado");
            }

            LocalDate innerOldCheckIn = freshBooking.getCheckIn();
            LocalDate innerOldCheckOut = freshBooking.getCheckOut();
            int normalizedOldSlot = (freshBooking.getUnitSlot() != null && freshBooking.getUnitSlot() > 0)
                    ? freshBooking.getUnitSlot()
                    : 1;

            List<SuiteNight> occupiedNights = new ArrayList<>(suiteNightRepository.findBySuiteAndDatesBetween(suite.getId(), request.getCheckIn(), request.getCheckOut().minusDays(1)).stream()
                    .filter(sn -> !"cancelled".equalsIgnoreCase(sn.getStatus()) && (sn.getExpiresAt() == null || sn.getExpiresAt().isAfter(now)))
                    .toList());
            assertCribAvailable(request, resolvedBookingId);
            occupiedNights.removeIf(sn -> resolvedBookingId.equals(sn.getBooking()));
            int totalCapacity = suite.getTotalUnits() != null ? suite.getTotalUnits() : 1;

            List<Integer> availableSlots = new ArrayList<>();
            for (int slot = 1; slot <= totalCapacity; slot++) {
                int finalSlot = slot;
                boolean isAvailable = true;
                for (int i = 0; i < nights; i++) {
                    LocalDate d = request.getCheckIn().plusDays(i);
                    boolean slotOccupiedOnDate = occupiedNights.stream()
                            .anyMatch(sn -> sn.getUnitSlot() != null && sn.getUnitSlot() == finalSlot && sn.getDates() != null && sn.getDates().contains(d));
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
                    ? dynamicPricingService.adjustmentsFor(suite, request.getCheckIn(), request.getCheckOut(), resolvedBookingId)
                    : Map.of();
            PricingService.PricingResult pricingResult = (dynamicAdjustments != null && !dynamicAdjustments.isEmpty())
                    ? pricingService.calculatePrice(suite.getBasePrice(), request.getCheckIn(), request.getCheckOut(), branchPromoPercent, DynamicPricingService.asFactorFunction(dynamicAdjustments))
                    : pricingService.calculatePrice(suite.getBasePrice(), request.getCheckIn(), request.getCheckOut(), branchPromoPercent);
            BigDecimal totalRoomPrice = pricingResult.total();
            List<Booking.SelectedExperience> selectedExpList = request.getExperiences() != null ? parseExperiences(request.getExperiences()) : freshBooking.getExperiences();
            BigDecimal experiencesTotal = BigDecimal.ZERO;
            if (selectedExpList != null) {
                for (Booking.SelectedExperience exp : selectedExpList) {
                    if (exp.getPrice() != null && exp.getQuantity() != null) {
                        experiencesTotal = experiencesTotal.add(exp.getPrice().multiply(BigDecimal.valueOf(exp.getQuantity())));
                    }
                }
            }

            BigDecimal vipExtrasTotal = calculateVipExtrasTotal(freshBooking);
            int effectiveGuests = request.getGuests() != null ? request.getGuests() : (freshBooking.getGuests() != null ? freshBooking.getGuests() : 1);
            BigDecimal currentDeposit = freshBooking.getGuaranteeDeposit() != null ? freshBooking.getGuaranteeDeposit() : BigDecimal.ZERO;
            BigDecimal newTotalPrice = totalRoomPrice.add(experiencesTotal).add(vipExtrasTotal)
                    .add(extraGuestFee(effectiveGuests, nights)).add(currentDeposit);
            freshBooking.setCheckIn(request.getCheckIn());
            freshBooking.setCheckOut(request.getCheckOut());
            freshBooking.setNights((int) nights);
            if (request.getGuests() != null) freshBooking.setGuests(request.getGuests());
            if (request.getChildren() != null) freshBooking.setChildren(request.getChildren());
            Booking.GuestDetails parsedGd = parseGuestDetails(request);
            if (parsedGd != null && ((parsedGd.getFullName() != null && !parsedGd.getFullName().isBlank()) || (parsedGd.getEmail() != null && !parsedGd.getEmail().isBlank()))) {
                freshBooking.setGuestDetails(parsedGd);
            }
            freshBooking.setExperiences(selectedExpList);
            freshBooking.setSubtotal(pricingResult.subtotal());
            freshBooking.setDiscount(pricingResult.discount());
            freshBooking.setTaxes(pricingResult.taxes());
            freshBooking.setNightlyPrices(pricingResult.nightlyPrices().stream().map(np -> new Booking.NightlyPrice(np.date(), np.price())).toList());
            freshBooking.setDynamicPricingNotes(new ArrayList<>(DynamicPricingService.describe(dynamicAdjustments)));
            AppliedDiscount appliedDiscount = resolveAppliedDiscount(activePromo, nights);
            freshBooking.setAppliedPromotionId(appliedDiscount.id());
            freshBooking.setAppliedPromotionName(appliedDiscount.name());
            freshBooking.setAppliedPromotionDiscountPercent(appliedDiscount.percent());
            freshBooking.setTotalPrice(newTotalPrice);
            BigDecimal modPlatformFee = pricingResult.total().multiply(new BigDecimal("0.08")).setScale(2, RoundingMode.HALF_UP);
            freshBooking.setPlatformFee(modPlatformFee);
            freshBooking.setHostPayout(pricingResult.total().subtract(modPlatformFee));

            BigDecimal paidAmt = freshBooking.getPaidAmount() != null ? freshBooking.getPaidAmount() : BigDecimal.ZERO;
            if (paidAmt.compareTo(newTotalPrice) >= 0) {
                freshBooking.setBalanceDue(BigDecimal.ZERO);
                freshBooking.setRefundedAmount(paidAmt.subtract(newTotalPrice));
                freshBooking.setPaymentStatus("paid");
            } else {
                freshBooking.setBalanceDue(newTotalPrice.subtract(paidAmt));
                freshBooking.setPaymentStatus(paidAmt.compareTo(BigDecimal.ZERO) > 0 ? "partial" : "pending");
            }

            freshBooking.setUpdatedAt(Instant.now());

            // Re-block nights con reintento automático en slots alternativos ante colisión concurrente
            List<SuiteNight> originalNights = suiteNightRepository.findByBooking(resolvedBookingId);
            suiteNightRepository.deleteByBooking(resolvedBookingId);
            boolean nightsLocked = false;
            List<SuiteNight> committedNewNights = new java.util.ArrayList<>();
            boolean isPending = "pending".equalsIgnoreCase(freshBooking.getStatus());
            Instant nightExpiry = isPending
                    ? (freshBooking.getExpiresAt() != null ? freshBooking.getExpiresAt() : now.plusSeconds(900))
                    : null;

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
                                .booking(resolvedBookingId)
                                .status(isPending ? "held" : "confirmed")
                                .expiresAt(nightExpiry)
                                .createdAt(Instant.now())
                                .build();
                        insertedNights.add(suiteNightRepository.save(suiteNight));
                    }
                    freshBooking.setUnitSlot(candidateSlot);
                    committedNewNights = insertedNights;
                    nightsLocked = true;
                    break;
                } catch (org.springframework.dao.DuplicateKeyException e) {
                    suiteNightRepository.deleteAll(insertedNights);
                }
            }
            if (!nightsLocked) {
                restoreSuiteNights(originalNights);
                throw new IllegalStateException("Hubo una colisión concurrente al modificar. Las fechas ya no están disponibles.");
            }

            Booking saved;
            try {
                saved = bookingRepository.save(freshBooking);
            } catch (RuntimeException saveEx) {
                if (!committedNewNights.isEmpty()) {
                    suiteNightRepository.deleteAll(committedNewNights);
                } else {
                    suiteNightRepository.deleteByBooking(resolvedBookingId);
                }
                restoreSuiteNights(originalNights);
                throw saveEx;
            }

            if (redisLockService != null) {
                redisLockService.releaseSuiteHold(suite.getId(), normalizedOldSlot, innerOldCheckIn, innerOldCheckOut, resolvedBookingId);
                if (isPending) {
                    int newSlot = (freshBooking.getUnitSlot() != null && freshBooking.getUnitSlot() > 0) ? freshBooking.getUnitSlot() : 1;
                    java.time.Duration remainingTtl = java.time.Duration.between(now, nightExpiry);
                    if (!remainingTtl.isNegative() && !remainingTtl.isZero()) {
                        redisLockService.holdSuiteDates(suite.getId(), newSlot, request.getCheckIn(), request.getCheckOut(), resolvedBookingId, remainingTtl);
                    }
                }
            }
            return saved;
        });

        if (availabilityCacheEvictor != null) {
            availabilityCacheEvictor.evictAvailability();
        }
        return toDTO(modifiedBooking);
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
                .filter(b -> !"cancelled".equalsIgnoreCase(b.getStatus()) && !"expired".equalsIgnoreCase(b.getStatus()))
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
                throw new ForbiddenAccessException("No tienes permiso para acceder a esta reserva");
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
            maskAnonymousBookingPii(dto);
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
                throw new ForbiddenAccessException("No tienes permiso para acceder a esta reserva");
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
            maskAnonymousBookingPii(dto);
        }
        return dto;
    }

    private void restoreSuiteNights(List<SuiteNight> originalNights) {
        if (originalNights == null || originalNights.isEmpty()) {
            return;
        }
        originalNights.forEach(sn -> {
            sn.setId(null);
            sn.setVersion(null);
        });
        suiteNightRepository.saveAll(originalNights);
    }

    private void maskAnonymousBookingPii(BookingDTO dto) {
        if (dto == null) return;
        dto.setSmartLockPin(null);
        dto.setSpecialRequests(null);
        dto.setDocumentNumber(null);
        dto.setCompanionGuests(null);
        dto.setGuestEmail(maskEmail(dto.getGuestEmail()));
        dto.setGuestPhone(maskPhone(dto.getGuestPhone()));
        if (dto.getGuestDetails() != null) {
            Booking.GuestDetails orig = dto.getGuestDetails();
            dto.setGuestDetails(Booking.GuestDetails.builder()
                    .fullName(orig.getFullName())
                    .email(maskEmail(orig.getEmail()))
                    .phone(maskPhone(orig.getPhone()))
                    .specialRequests(null)
                    .build());
        }
    }

    private static String maskEmail(String email) {
        if (email == null || email.isBlank()) return email;
        int at = email.indexOf('@');
        if (at <= 0) return "***";
        String prefix = email.substring(0, 1);
        return prefix + "***" + email.substring(at);
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) return phone;
        String trimmed = phone.trim();
        if (trimmed.length() <= 4) return "***";
        return "***" + trimmed.substring(trimmed.length() - 4);
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
        throw new ForbiddenAccessException("No tienes permiso para acceder a una reserva de otra propiedad");
    }

    public Booking getBookingEntityById(String id) {
        return bookingRepository.findById(id)
                .or(() -> bookingRepository.findByBookingCode(id))
                .orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada con id: " + id));
    }

    public BookingDTO cancelBooking(String bookingId, String userId, String reason) {
        Booking outerBooking = getBookingEntityById(bookingId);
        if (!outerBooking.getUser().equals(userId)) {
            throw new ForbiddenAccessException("No tienes permiso para cancelar esta reserva");
        }
        String resolvedBookingId = outerBooking.getId() != null ? outerBooking.getId() : bookingId;

        java.util.function.Supplier<BookingDTO> cancelCriticalSection = () -> executeWithSuiteLock(outerBooking.getSuite(), () -> {
            Booking booking = bookingRepository.findById(resolvedBookingId).orElse(outerBooking);
            if (!booking.getUser().equals(userId)) {
                throw new ForbiddenAccessException("No tienes permiso para cancelar esta reserva");
            }
            if (booking.getCheckIn() == null) {
                throw new IllegalStateException("La reserva no tiene una fecha de check-in válida");
            }
            if (booking.getTotalPrice() == null || booking.getTotalPrice().signum() < 0) {
                throw new IllegalStateException("La reserva no tiene un precio total válido para calcular la retención");
            }

            long daysUntilCheckIn = ChronoUnit.DAYS.between(LocalDate.now(BOGOTA_ZONE), booking.getCheckIn());
            BigDecimal paidAmount = booking.getPaidAmount() != null
                    ? booking.getPaidAmount().max(BigDecimal.ZERO)
                    : BigDecimal.ZERO;
            BigDecimal retention = BigDecimal.ZERO;
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
            }
            booking.setBalanceDue(BigDecimal.ZERO);

            // A-2: Persistir CANCELLED con @Version ANTES de liberar inventario y emitir el reembolso en pasarela
            Booking savedBooking = bookingRepository.save(booking);
            suiteNightRepository.deleteByBooking(resolvedBookingId);
            if (redisLockService != null) {
                redisLockService.releaseSuiteHold(booking.getSuite(), booking.getUnitSlot(), booking.getCheckIn(), booking.getCheckOut(), resolvedBookingId);
            }
            if (availabilityCacheEvictor != null) {
                availabilityCacheEvictor.evictAvailability();
            }

            if (paidAmount.compareTo(BigDecimal.ZERO) > 0 && refundAmount.compareTo(BigDecimal.ZERO) > 0 && paymentGateway != null) {
                String txId = resolveRefundTransactionId(resolvedBookingId);
                try {
                    PaymentGateway.PaymentRefundResponse refundResp = paymentGateway.refund(txId, refundAmount);
                    if (refundResp != null) {
                        if (refundResp.success() && paymentRepository != null && refundAmount.compareTo(paidAmount) >= 0) {
                            paymentRepository.findByBooking(resolvedBookingId).stream()
                                    .filter(p -> "completed".equalsIgnoreCase(p.getStatus()))
                                    .findFirst()
                                    .ifPresent(p -> {
                                        p.setStatus("refunded");
                                        p.setUpdatedAt(Instant.now());
                                        paymentRepository.save(p);
                                    });
                        } else if (!refundResp.success()) {
                            log.warn("Fallo en pasarela al reembolsar reserva {}. Marcando paymentStatus=requires_refund", resolvedBookingId);
                            savedBooking.setPaymentStatus("requires_refund");
                            savedBooking.setRefundedAmount(BigDecimal.ZERO);
                            savedBooking = bookingRepository.save(savedBooking);
                        }
                    }
                } catch (Exception refundEx) {
                    log.error("Excepción al reembolsar reserva {} en pasarela (tx={}): {}", resolvedBookingId, txId, refundEx.getMessage());
                    savedBooking.setPaymentStatus("requires_refund");
                    savedBooking.setRefundedAmount(BigDecimal.ZERO);
                    savedBooking = bookingRepository.save(savedBooking);
                }
            }

            return toDTO(savedBooking);
        });

        if (redisLockService != null && resolvedBookingId != null && !resolvedBookingId.isBlank()) {
            return redisLockService.executeWithLock(
                    "booking:" + resolvedBookingId.trim(),
                    java.time.Duration.ofSeconds(15),
                    java.time.Duration.ofSeconds(5),
                    cancelCriticalSection
            );
        }
        return cancelCriticalSection.get();
    }

    private String resolveRefundTransactionId(String bookingId) {
        if (paymentRepository != null && bookingId != null) {
            List<co.palaciodelmar.backend.model.Payment> payments = paymentRepository.findByBooking(bookingId);
            if (payments != null) {
                for (co.palaciodelmar.backend.model.Payment p : payments) {
                    if (p != null && "completed".equalsIgnoreCase(p.getStatus())
                            && p.getTransactionId() != null && !p.getTransactionId().isBlank()) {
                        return p.getTransactionId().trim();
                    }
                }
                for (co.palaciodelmar.backend.model.Payment p : payments) {
                    if (p != null && p.getTransactionId() != null && !p.getTransactionId().isBlank()) {
                        return p.getTransactionId().trim();
                    }
                }
            }
        }
        return "tx_" + bookingId;
    }

    private <T> T executeWithBookingLock(String bookingId, java.util.function.Supplier<T> action) {
        if (redisLockService == null || bookingId == null || bookingId.isBlank()) {
            return action.get();
        }
        return redisLockService.executeWithLock(
                "booking:" + bookingId.trim(),
                java.time.Duration.ofSeconds(10),
                java.time.Duration.ofSeconds(4),
                action
        );
    }

    private void assertBookingCanBeModified(Booking booking) {
        String status = booking.getStatus() != null ? booking.getStatus().toLowerCase() : "";
        if ("cancelled".equals(status) || "expired".equals(status) || "checked_out".equals(status) || "no_show".equals(status)) {
            throw new IllegalStateException("No se puede modificar una reserva inactiva o cancelada (estado: " + status + ")");
        }
        if ("pending".equals(status) && booking.getExpiresAt() != null && booking.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalStateException("El tiempo de retención de esta reserva ha expirado.");
        }
    }

    public BookingDTO addExperienceToBooking(String bookingId, String userId, String experienceId) {
        return executeWithBookingLock(bookingId, () -> {
            Booking booking = getBookingEntityById(bookingId);
            if (!booking.getUser().equals(userId)) {
                throw new ForbiddenAccessException("No tienes permiso para modificar esta reserva");
            }
            assertBookingCanBeModified(booking);
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
            BigDecimal paidAmt = booking.getPaidAmount() != null ? booking.getPaidAmount() : BigDecimal.ZERO;
            if (paidAmt.compareTo(newTotal) >= 0) {
                booking.setBalanceDue(BigDecimal.ZERO);
                booking.setPaymentStatus("paid");
            } else {
                booking.setBalanceDue(newTotal.subtract(paidAmt));
                booking.setPaymentStatus(paidAmt.compareTo(BigDecimal.ZERO) > 0 ? "partial" : "pending");
            }
            booking.setUpdatedAt(Instant.now());
            return toDTO(bookingRepository.save(booking));
        });
    }

    public BookingDTO removeExperienceFromBooking(String bookingId, String userId, String experienceId) {
        return executeWithBookingLock(bookingId, () -> {
            Booking booking = getBookingEntityById(bookingId);
            if (!booking.getUser().equals(userId)) {
                throw new ForbiddenAccessException("No tienes permiso para modificar esta reserva");
            }
            assertBookingCanBeModified(booking);
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
            BigDecimal paidAmt = booking.getPaidAmount() != null ? booking.getPaidAmount() : BigDecimal.ZERO;
            if (paidAmt.compareTo(newTotal) >= 0) {
                booking.setBalanceDue(BigDecimal.ZERO);
                booking.setPaymentStatus("paid");
            } else {
                booking.setBalanceDue(newTotal.subtract(paidAmt));
                booking.setPaymentStatus(paidAmt.compareTo(BigDecimal.ZERO) > 0 ? "partial" : "pending");
            }
            booking.setUpdatedAt(Instant.now());
            return toDTO(bookingRepository.save(booking));
        });
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
        List<Booking> overlapping = bookingRepository.findByStatusInAndNeedsCribTrueAndCheckInBeforeAndCheckOutAfter(
                List.of("confirmed", "checked_in", "pending"),
                request.getCheckOut(),
                request.getCheckIn()
        );
        long cribsInUse = overlapping.stream()
                .filter(b -> excludeBookingId == null || !excludeBookingId.equals(b.getId()))
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
            String sId = booking.getSuite().trim();
            suite = suiteRepository.findById(sId)
                    .or(() -> suiteRepository.findBySlug(sId.toLowerCase()))
                    .orElse(null);
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
                .smartLockPin((Boolean.TRUE.equals(booking.getPreCheckInCompleted()) || "confirmed".equalsIgnoreCase(status) || "checked_in".equalsIgnoreCase(status) || "completed".equalsIgnoreCase(status)) && !"cancelled".equalsIgnoreCase(status) && !"expired".equalsIgnoreCase(status) && !"no_show".equalsIgnoreCase(status) ? booking.getSmartLockPin() : null)
                .preCheckInCompleted(booking.getPreCheckInCompleted())
                .documentNumber(booking.getDocumentNumber())
                .companionGuests(booking.getCompanionGuests())
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
            int quantity = 1;
            if (item instanceof String id) {
                expId = id;
            } else if (item instanceof Booking.SelectedExperience selExp) {
                expId = selExp.getExperienceId();
                if (selExp.getQuantity() != null && selExp.getQuantity() > 0) {
                    quantity = selExp.getQuantity();
                }
            } else if (item instanceof Map<?, ?> map) {
                expId = (String) map.get("experienceId");
                if (expId == null) expId = (String) map.get("id");
                if (expId == null) expId = (String) map.get("_id");
                Object q = map.get("quantity");
                if (q == null) q = map.get("qty");
                if (q instanceof Number n) {
                    int val = n.intValue();
                    // Limitar a un rango razonable por seguridad (1 a 10) para neutralizar intentos de manipulación
                    quantity = (val >= 1 && val <= 10) ? val : 1;
                }
            }
            if (expId != null) {
                final int finalQty = quantity;
                experienceRepository.findById(expId).ifPresent(exp -> {
                    result.add(Booking.SelectedExperience.builder()
                            .experienceId(exp.getId())
                            .name(exp.getName())
                            .price(exp.getPrice())
                            .quantity(finalQty)
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
        if (booking.getStatusHistory() == null) {
            booking.setStatusHistory(new ArrayList<>());
        }
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
            if (redisLockService != null) {
                redisLockService.releaseSuiteHold(booking.getSuite(), booking.getUnitSlot(), booking.getCheckIn(), booking.getCheckOut(), booking.getId());
            }
            if (socialProofService != null) {
                socialProofService.recordBookingActivity(booking.getSuite(), null, booking.getId());
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
        if (availabilityCacheEvictor != null) {
            availabilityCacheEvictor.evictAvailability();
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


    public BookingDTO performPreCheckIn(String bookingId, String userId, String documentNumber, String companionGuests) {
        if (userId == null || userId.isBlank()) {
            throw new ForbiddenAccessException("Debes iniciar sesión para realizar el pre-check-in");
        }
        Booking booking = getBookingEntityById(bookingId);
        if (!userId.equals(booking.getUser())) {
            throw new ForbiddenAccessException("No tienes permiso para gestionar esta reserva");
        }
        String st = booking.getStatus() != null ? booking.getStatus() : "";
        if ("cancelled".equalsIgnoreCase(st) || "expired".equalsIgnoreCase(st) || "no_show".equalsIgnoreCase(st)) {
            throw new IllegalStateException("No es posible realizar el pre-check-in para una reserva en estado " + st);
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
