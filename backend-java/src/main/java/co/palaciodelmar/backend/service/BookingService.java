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

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.data.mongodb.core.MongoTemplate mongoTemplate;

    private static final java.util.concurrent.ConcurrentHashMap<String, Object> BOOKING_MONITORS =
            new java.util.concurrent.ConcurrentHashMap<>();

    public void setPaymentRepository(co.palaciodelmar.backend.repository.PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    public void setMongoTemplate(org.springframework.data.mongodb.core.MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
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


    private BookingDTO createStandaloneExperienceBooking(String userId, BookingRequest request) {
        List<Booking.SelectedExperience> selectedExpList = parseExperiences(request.getExperiences());
        if (selectedExpList.isEmpty()) {
            throw new IllegalArgumentException("Debes seleccionar al menos una actividad o experiencia válida");
        }

        LocalDate actDate = request.getCheckIn() != null ? request.getCheckIn() : LocalDate.now(BOGOTA_ZONE);
        if (actDate.isBefore(LocalDate.now(BOGOTA_ZONE))) {
            throw new IllegalArgumentException("La fecha de la experiencia no puede ser anterior a la fecha actual");
        }

        BigDecimal experiencesTotal = BigDecimal.ZERO;
        for (Booking.SelectedExperience exp : selectedExpList) {
            if (exp.getPrice() != null && exp.getQuantity() != null) {
                experiencesTotal = experiencesTotal.add(exp.getPrice().multiply(BigDecimal.valueOf(exp.getQuantity())));
            }
        }

        String bookingCode = "PDM-EXP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        Booking.GuestDetails guestDetails = parseGuestDetails(request);

        Booking booking = Booking.builder()
                .user(userId)
                .suite("standalone-experience")
                .branch("cartagena-indias")
                .platformFee(experiencesTotal.multiply(new BigDecimal("0.08")).setScale(2, RoundingMode.HALF_UP))
                .hostPayout(experiencesTotal.multiply(new BigDecimal("0.92")).setScale(2, RoundingMode.HALF_UP))
                .checkIn(actDate)
                .checkOut(actDate)
                .nights(1)
                .guests(request.getGuests() != null ? request.getGuests() : 1)
                .children(request.getChildren() != null ? request.getChildren() : 0)
                .pricePerNight(BigDecimal.ZERO)
                .subtotal(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .taxes(BigDecimal.ZERO)
                .totalPrice(experiencesTotal)
                .paidAmount(BigDecimal.ZERO)
                .balanceDue(experiencesTotal)
                .status("pending")
                .guaranteeDeposit(BigDecimal.ZERO)
                .guaranteeRefunded(false)
                .paymentStatus("pending")
                .bookingCode(bookingCode)
                .guestDetails(guestDetails)
                .experiences(selectedExpList)
                .expiresAt(Instant.now().plusSeconds(900))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Booking persistedBooking = bookingRepository.save(booking);

        BookingDTO dto = toDTO(persistedBooking);
        if (eventPublisher != null) {
            eventPublisher.publishEvent(new co.palaciodelmar.backend.event.BookingDomainEvent(
                    "booking.created",
                    persistedBooking.getId(),
                    null,
                    dto,
                    Instant.now()
            ));
        }
        return dto;
    }

    public BookingDTO createBooking(String userId, BookingRequest request) {
        if (userId == null || userId.isBlank()) {
            throw new ForbiddenAccessException("Debes iniciar sesión para realizar una reserva");
        }

        // Soporte de Reserva Exclusiva de Experiencia / Actividad independiente
        if ((request.getSuiteId() == null || request.getSuiteId().isBlank()) && request.getExperiences() != null && !request.getExperiences().isEmpty()) {
            return createStandaloneExperienceBooking(userId, request);
        }

        if (request.getSuiteId() == null || request.getSuiteId().isBlank()) {
            throw new IllegalArgumentException("Por favor selecciona una habitación o experiencia válida");
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

        Booking modifiedBooking = executeWithBookingLock(resolvedBookingId, () -> executeWithSuiteLock(suite.getId(), () -> {
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
            reconcilePendingRefundBeforeChange(freshBooking, resolvedBookingId);

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
            BigDecimal currentRefunded = freshBooking.getRefundedAmount() != null ? freshBooking.getRefundedAmount() : BigDecimal.ZERO;
            BigDecimal netPaid = paidAmt.subtract(currentRefunded).max(BigDecimal.ZERO);
            BigDecimal refundDiff = netPaid.subtract(newTotalPrice).max(BigDecimal.ZERO);

            if (netPaid.compareTo(newTotalPrice) >= 0) {
                freshBooking.setBalanceDue(BigDecimal.ZERO);
                freshBooking.setPaymentStatus("paid");
            } else {
                freshBooking.setBalanceDue(newTotalPrice.subtract(netPaid));
                freshBooking.setPaymentStatus(netPaid.compareTo(BigDecimal.ZERO) > 0 ? "partial" : "pending");
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

            if (refundDiff.compareTo(BigDecimal.ZERO) > 0 && paymentGateway != null) {
                String txId = resolveRefundTransactionId(resolvedBookingId);
                String refundIdemKey = saved.getPendingRefundIdempotencyKey();
                BigDecimal pendingAmt = saved.getPendingRefundAmount();
                if (refundIdemKey == null || pendingAmt == null || pendingAmt.compareTo(refundDiff) != 0) {
                    refundIdemKey = "ref_mod_" + resolvedBookingId + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
                    saved.setPendingRefundIdempotencyKey(refundIdemKey);
                    saved.setPendingRefundAmount(refundDiff);
                    saved = bookingRepository.save(saved);
                }
                try {
                    PaymentGateway.PaymentRefundResponse refundResp = paymentGateway.refund(txId, refundDiff, refundIdemKey);
                    if (refundResp != null && refundResp.success()) {
                        applyConfirmedRefundToBooking(saved, refundDiff, refundResp.refundId());
                        recordConfirmedRefundOnPayment(resolvedBookingId, refundDiff, refundResp.refundId());
                    } else {
                        log.warn("Pasarela no pudo procesar reembolso inmediato para modificación de reserva {}: {}", resolvedBookingId, (refundResp != null ? refundResp.errorMessage() : "respuesta nula"));
                        saved.setPendingRefundAmount(refundDiff);
                        saved.setPendingRefundIdempotencyKey(refundIdemKey);
                    }
                } catch (Exception ex) {
                    log.error("Excepción al ejecutar reembolso en pasarela para modificación de reserva {}: {}", resolvedBookingId, ex.getMessage(), ex);
                    saved.setPendingRefundAmount(refundDiff);
                    saved.setPendingRefundIdempotencyKey(refundIdemKey);
                }
                saved = bookingRepository.save(saved);
            }

            return saved;
        }));

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
            if ( booking.getTotalPrice() == null || booking.getTotalPrice().signum() < 0) {
                throw new IllegalStateException("La reserva no tiene un precio total válido para calcular la retención");
            }
            reconcilePendingRefundBeforeChange(booking, resolvedBookingId);

            long daysUntilCheckIn = ChronoUnit.DAYS.between(LocalDate.now(BOGOTA_ZONE), booking.getCheckIn());
            BigDecimal paidAmount = booking.getPaidAmount() != null
                    ? booking.getPaidAmount().max(BigDecimal.ZERO)
                    : BigDecimal.ZERO;
            BigDecimal alreadyRefunded = booking.getRefundedAmount() != null
                    ? booking.getRefundedAmount().max(BigDecimal.ZERO)
                    : BigDecimal.ZERO;
            BigDecimal netPaid = paidAmount.subtract(alreadyRefunded).max(BigDecimal.ZERO);
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
            BigDecimal refundToProcess = netPaid.subtract(retention).max(BigDecimal.ZERO).min(netPaid);
            BigDecimal totalRefundedAfterCancel = alreadyRefunded.add(refundToProcess).min(paidAmount);

            changeBookingStatus(booking, co.palaciodelmar.backend.model.BookingStatus.CANCELLED, userId, reason);
            if (reason != null && !reason.isEmpty()) {
                booking.setCancellationReason(reason);
            }
            booking.setRefundedAmount(alreadyRefunded);
            booking.setBalanceDue(BigDecimal.ZERO);

            String cancelIdemKey = null;
            if (netPaid.compareTo(BigDecimal.ZERO) > 0 && refundToProcess.compareTo(BigDecimal.ZERO) > 0) {
                cancelIdemKey = booking.getPendingRefundIdempotencyKey();
                if (cancelIdemKey == null || cancelIdemKey.isBlank()) {
                    cancelIdemKey = "ref_canc_" + resolvedBookingId + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
                }
                booking.setPendingRefundIdempotencyKey(cancelIdemKey);
                booking.setPendingRefundAmount(refundToProcess);
                booking.setPaymentStatus("requires_refund");
            } else if (paidAmount.compareTo(BigDecimal.ZERO) > 0) {
                booking.setPaymentStatus(alreadyRefunded.compareTo(paidAmount) >= 0 ? "refunded" : "partial");
            }

            // A-2: Persistir CANCELLED con @Version ANTES de liberar inventario y emitir el reembolso en pasarela
            Booking savedBooking = bookingRepository.save(booking);
            suiteNightRepository.deleteByBooking(resolvedBookingId);
            if (redisLockService != null) {
                redisLockService.releaseSuiteHold(booking.getSuite(), booking.getUnitSlot(), booking.getCheckIn(), booking.getCheckOut(), resolvedBookingId);
            }
            if (availabilityCacheEvictor != null) {
                availabilityCacheEvictor.evictAvailability();
            }

            if (cancelIdemKey != null && refundToProcess.compareTo(BigDecimal.ZERO) > 0 && paymentGateway != null) {
                String txId = resolveRefundTransactionId(resolvedBookingId);
                try {
                    PaymentGateway.PaymentRefundResponse refundResp = paymentGateway.refund(txId, refundToProcess, cancelIdemKey);
                    if (refundResp != null && refundResp.success()) {
                        applyConfirmedRefundToBooking(savedBooking, refundToProcess, refundResp.refundId());
                        BigDecimal finalBookingRefunded = savedBooking.getRefundedAmount() != null
                                ? savedBooking.getRefundedAmount()
                                : totalRefundedAfterCancel;
                        savedBooking.setPaymentStatus(finalBookingRefunded.compareTo(paidAmount) >= 0 ? "refunded" : "partial");
                        recordConfirmedRefundOnPayment(
                                resolvedBookingId,
                                refundToProcess,
                                refundResp.refundId()
                        );
                        savedBooking = bookingRepository.save(savedBooking);
                    } else {
                        String failureMsg = refundResp != null ? refundResp.errorMessage() : "respuesta nula de pasarela";
                        log.warn("Fallo o respuesta nula en pasarela al reembolsar reserva {}: {}. Marcando paymentStatus=requires_refund y preservando reembolso previo {}",
                                resolvedBookingId, failureMsg, alreadyRefunded);
                        savedBooking.setPaymentStatus("requires_refund");
                        savedBooking.setRefundedAmount(alreadyRefunded);
                        savedBooking.setPendingRefundAmount(refundToProcess);
                        savedBooking.setPendingRefundIdempotencyKey(cancelIdemKey);
                        savedBooking = bookingRepository.save(savedBooking);
                    }
                } catch (Exception refundEx) {
                    log.error("Excepción al reembolsar reserva {} en pasarela (tx={}): {}", resolvedBookingId, txId, refundEx.getMessage());
                    savedBooking.setPaymentStatus("requires_refund");
                    savedBooking.setRefundedAmount(alreadyRefunded);
                    savedBooking.setPendingRefundAmount(refundToProcess);
                    savedBooking.setPendingRefundIdempotencyKey(cancelIdemKey);
                    savedBooking = bookingRepository.save(savedBooking);
                }
            }

            return toDTO(savedBooking);
        });

        return executeWithBookingLock(resolvedBookingId, cancelCriticalSection);
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

    public static Object getBookingMonitor(String bookingId) {
        String key = (bookingId != null && !bookingId.isBlank()) ? bookingId.trim() : "__default__";
        return BOOKING_MONITORS.computeIfAbsent(key, k -> new Object());
    }

    public <T> T executeWithBookingLock(String bookingId, java.util.function.Supplier<T> action) {
        if (bookingId == null || bookingId.isBlank()) {
            return action.get();
        }
        String normalizedId = bookingId.trim();
        Object monitor = getBookingMonitor(normalizedId);
        java.util.function.Supplier<T> synchronizedAction = () -> {
            synchronized (monitor) {
                return action.get();
            }
        };
        if (redisLockService == null) {
            return synchronizedAction.get();
        }
        return redisLockService.executeWithLock(
                "booking:" + normalizedId,
                java.time.Duration.ofSeconds(15),
                java.time.Duration.ofSeconds(5),
                synchronizedAction
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

    private boolean applyConfirmedRefundToBooking(Booking booking, BigDecimal refundAmount, String refundId) {
        if (booking == null || refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        if (booking.getProcessedRefundIds() == null) {
            booking.setProcessedRefundIds(new java.util.ArrayList<>());
        }
        if (refundId != null && booking.getProcessedRefundIds().contains(refundId)) {
            booking.setPendingRefundAmount(BigDecimal.ZERO);
            booking.setPendingRefundIdempotencyKey(null);
            return false;
        }
        BigDecimal currentRef = booking.getRefundedAmount() != null ? booking.getRefundedAmount().max(BigDecimal.ZERO) : BigDecimal.ZERO;
        BigDecimal paidCap = booking.getPaidAmount() != null ? booking.getPaidAmount().max(BigDecimal.ZERO) : BigDecimal.ZERO;
        BigDecimal nextRef = currentRef.add(refundAmount);
        if (paidCap.compareTo(BigDecimal.ZERO) > 0 && nextRef.compareTo(paidCap) > 0) {
            nextRef = paidCap;
        }
        if (mongoTemplate != null && booking.getId() != null && refundId != null) {
            org.springframework.data.mongodb.core.query.Query q = new org.springframework.data.mongodb.core.query.Query(
                    org.springframework.data.mongodb.core.query.Criteria.where("id").is(booking.getId())
                            .and("processedRefundIds").ne(refundId)
            );
            org.springframework.data.mongodb.core.query.Update u = new org.springframework.data.mongodb.core.query.Update()
                    .set("refundedAmount", nextRef)
                    .addToSet("processedRefundIds", refundId)
                    .set("pendingRefundAmount", BigDecimal.ZERO)
                    .set("pendingRefundIdempotencyKey", null)
                    .set("updatedAt", Instant.now());
            Booking updated = mongoTemplate.findAndModify(
                    q, u,
                    org.springframework.data.mongodb.core.FindAndModifyOptions.options().returnNew(true),
                    Booking.class
            );
            if (updated == null) {
                Booking fresh = bookingRepository.findById(booking.getId()).orElse(booking);
                booking.setRefundedAmount(fresh.getRefundedAmount());
                booking.setProcessedRefundIds(fresh.getProcessedRefundIds());
                booking.setPendingRefundAmount(BigDecimal.ZERO);
                booking.setPendingRefundIdempotencyKey(null);
                return false;
            }
            booking.setVersion(updated.getVersion());
        }
        booking.setRefundedAmount(nextRef);
        if (refundId != null) {
            booking.getProcessedRefundIds().add(refundId);
        }
        booking.setPendingRefundAmount(BigDecimal.ZERO);
        booking.setPendingRefundIdempotencyKey(null);
        return true;
    }

    private void recordConfirmedRefundOnPayment(String bookingId, BigDecimal refundAmount, String refundId) {
        if (paymentRepository == null || bookingId == null || bookingId.isBlank()
                || refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        paymentRepository.findByBooking(bookingId).stream()
                .filter(p -> "completed".equalsIgnoreCase(p.getStatus()) || "partially_refunded".equalsIgnoreCase(p.getStatus()))
                .findFirst()
                .ifPresent(p -> {
                    if (p.getProcessedRefundIds() == null) {
                        p.setProcessedRefundIds(new java.util.ArrayList<>());
                    }
                    if (refundId != null && p.getProcessedRefundIds().contains(refundId)) {
                        return;
                    }
                    BigDecimal currentPRefunded = p.getRefundedAmount() != null ? p.getRefundedAmount().max(BigDecimal.ZERO) : BigDecimal.ZERO;
                    BigDecimal pTotal = p.getAmount() != null ? p.getAmount().max(BigDecimal.ZERO) : BigDecimal.ZERO;
                    BigDecimal pRefunded = currentPRefunded.add(refundAmount);
                    if (pTotal.compareTo(BigDecimal.ZERO) > 0 && pRefunded.compareTo(pTotal) > 0) {
                        pRefunded = pTotal;
                    }
                    String nextStatus = (pTotal.compareTo(BigDecimal.ZERO) > 0 && pRefunded.compareTo(pTotal) >= 0)
                            ? "refunded"
                            : "partially_refunded";
                    if (mongoTemplate != null && p.getId() != null && refundId != null) {
                        org.springframework.data.mongodb.core.query.Query q = new org.springframework.data.mongodb.core.query.Query(
                                org.springframework.data.mongodb.core.query.Criteria.where("id").is(p.getId())
                                        .and("processedRefundIds").ne(refundId)
                        );
                        org.springframework.data.mongodb.core.query.Update u = new org.springframework.data.mongodb.core.query.Update()
                                .set("refundedAmount", pRefunded)
                                .set("status", nextStatus)
                                .addToSet("processedRefundIds", refundId)
                                .set("updatedAt", Instant.now());
                        co.palaciodelmar.backend.model.Payment updated = mongoTemplate.findAndModify(
                                q, u,
                                org.springframework.data.mongodb.core.FindAndModifyOptions.options().returnNew(true),
                                co.palaciodelmar.backend.model.Payment.class
                        );
                        if (updated == null) {
                            return;
                        }
                        p.setRefundedAmount(updated.getRefundedAmount());
                        p.setStatus(updated.getStatus());
                        p.setProcessedRefundIds(updated.getProcessedRefundIds());
                        p.setVersion(updated.getVersion());
                        p.setUpdatedAt(updated.getUpdatedAt());
                        return;
                    }
                    p.setRefundedAmount(pRefunded);
                    p.setStatus(nextStatus);
                    if (refundId != null) {
                        p.getProcessedRefundIds().add(refundId);
                    }
                    p.setUpdatedAt(Instant.now());
                    paymentRepository.save(p);
                });
    }

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    @org.springframework.scheduling.annotation.Scheduled(
            fixedDelayString = "${palacio.booking.refund-reconciliation.interval.ms:30000}",
            initialDelayString = "${palacio.booking.refund-reconciliation.initial-delay.ms:20000}"
    )
    public void reconcilePendingBookingRefunds() {
        try {
            if (bookingRepository == null || paymentGateway == null) {
                return;
            }
            List<Booking> pendingBookings = bookingRepository.findByPendingRefundIdempotencyKeyIsNotNull();
            if (pendingBookings == null || pendingBookings.isEmpty()) {
                return;
            }
            for (Booking candidate : pendingBookings) {
                if (candidate == null || candidate.getId() == null || candidate.getId().isBlank()) {
                    continue;
                }
                String bId = candidate.getId();
                try {
                    executeWithBookingLock(bId, () -> {
                        Booking fresh = bookingRepository.findById(bId).orElse(candidate);
                        if (fresh.getPendingRefundIdempotencyKey() != null && !fresh.getPendingRefundIdempotencyKey().isBlank()) {
                            reconcilePendingRefundBeforeChange(fresh, bId);
                        }
                        return null;
                    });
                } catch (Exception ex) {
                    log.warn("Reintento periódico de reembolso pendiente para reserva {} pospuesto: {}", bId, ex.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Error durante la conciliación periódica de reembolsos pendientes en reservas: {}", e.getMessage(), e);
        }
    }

    private void reconcilePendingRefundBeforeChange(Booking booking, String resolvedBookingId) {
        if (booking == null) {
            return;
        }
        String pendingKey = booking.getPendingRefundIdempotencyKey();
        BigDecimal pendingAmt = booking.getPendingRefundAmount();
        if (pendingKey == null || pendingKey.isBlank() || pendingAmt == null || pendingAmt.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        if (paymentGateway == null) {
            throw new IllegalStateException("Existe un reembolso previo pendiente de confirmación (" + pendingKey + ") y la pasarela no está disponible.");
        }
        String bId = (resolvedBookingId != null && !resolvedBookingId.isBlank()) ? resolvedBookingId : booking.getId();
        String txId = resolveRefundTransactionId(bId);
        try {
            PaymentGateway.PaymentRefundResponse refundResp = paymentGateway.refund(txId, pendingAmt, pendingKey);
            if (refundResp != null && refundResp.success()) {
                String rId = refundResp.refundId();
                applyConfirmedRefundToBooking(booking, pendingAmt, rId);
                recordConfirmedRefundOnPayment(bId, pendingAmt, rId);

                BigDecimal totalBookingRefunded = booking.getRefundedAmount() != null ? booking.getRefundedAmount() : BigDecimal.ZERO;
                BigDecimal paidAmt = booking.getPaidAmount() != null ? booking.getPaidAmount().max(BigDecimal.ZERO) : BigDecimal.ZERO;
                boolean isCancelled = "cancelled".equalsIgnoreCase(booking.getStatus());

                if (isCancelled) {
                    booking.setPaymentStatus(totalBookingRefunded.compareTo(paidAmt) >= 0 ? "refunded" : "partial");
                } else if ("requires_refund".equalsIgnoreCase(booking.getPaymentStatus())) {
                    BigDecimal netPaidAfter = paidAmt.subtract(totalBookingRefunded).max(BigDecimal.ZERO);
                    BigDecimal totalPrice = booking.getTotalPrice() != null ? booking.getTotalPrice() : BigDecimal.ZERO;
                    booking.setPaymentStatus(netPaidAfter.compareTo(totalPrice) >= 0 ? "paid" : (netPaidAfter.compareTo(BigDecimal.ZERO) > 0 ? "partial" : "pending"));
                }
                booking.setUpdatedAt(Instant.now());
                bookingRepository.save(booking);
            } else {
                String errMsg = refundResp != null && refundResp.errorMessage() != null ? refundResp.errorMessage() : "estado indeterminado";
                throw new IllegalStateException("Existe un reembolso previo pendiente de confirmación con la pasarela (" + pendingKey + "): " + errMsg);
            }
        } catch (IllegalStateException ise) {
            throw ise;
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo conciliar el reembolso previo pendiente (" + pendingKey + ") con la pasarela: " + ex.getMessage(), ex);
        }
    }

    public BookingDTO addExperienceToBooking(String bookingId, String userId, String experienceId) {
        Booking outerBooking = getBookingEntityById(bookingId);
        String resolvedBookingId = outerBooking.getId() != null ? outerBooking.getId() : bookingId;
        return executeWithBookingLock(resolvedBookingId, () -> {
            Booking booking = bookingRepository.findById(resolvedBookingId).orElse(outerBooking);
            if (!booking.getUser().equals(userId)) {
                throw new ForbiddenAccessException("No tienes permiso para modificar esta reserva");
            }
            assertBookingCanBeModified(booking);
            reconcilePendingRefundBeforeChange(booking, resolvedBookingId);
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
            boolean isStandalone = "standalone-experience".equalsIgnoreCase(booking.getSuite()) || booking.getSuite() == null;
            BigDecimal taxes = booking.getTaxes() != null ? booking.getTaxes() : BigDecimal.ZERO;
            BigDecimal discount = booking.getDiscount() != null ? booking.getDiscount() : BigDecimal.ZERO;
            BigDecimal subtotal = booking.getSubtotal() != null ? booking.getSubtotal() : BigDecimal.ZERO;
            BigDecimal roomTotal = isStandalone ? BigDecimal.ZERO : subtotal.add(taxes).subtract(discount);
            BigDecimal vipExtrasTotal = isStandalone ? BigDecimal.ZERO : calculateVipExtrasTotal(booking);
            BigDecimal fixed = isStandalone ? BigDecimal.ZERO : fixedCharges(booking);
            BigDecimal newTotal = isStandalone ? expTotal : roomTotal.add(expTotal).add(vipExtrasTotal).add(fixed);
            booking.setTotalPrice(newTotal);

            if (isStandalone) {
                booking.setPlatformFee(expTotal.multiply(new BigDecimal("0.08")).setScale(2, RoundingMode.HALF_UP));
                booking.setHostPayout(expTotal.multiply(new BigDecimal("0.92")).setScale(2, RoundingMode.HALF_UP));
            }

            BigDecimal paidAmt = booking.getPaidAmount() != null ? booking.getPaidAmount() : BigDecimal.ZERO;
            BigDecimal currentRefunded = booking.getRefundedAmount() != null ? booking.getRefundedAmount() : BigDecimal.ZERO;
            BigDecimal netPaid = paidAmt.subtract(currentRefunded).max(BigDecimal.ZERO);
            if (netPaid.compareTo(newTotal) >= 0) {
                booking.setBalanceDue(BigDecimal.ZERO);
                booking.setPaymentStatus("paid");
            } else {
                booking.setBalanceDue(newTotal.subtract(netPaid));
                booking.setPaymentStatus(netPaid.compareTo(BigDecimal.ZERO) > 0 ? "partial" : "pending");
            }
            booking.setUpdatedAt(Instant.now());
            return toDTO(bookingRepository.save(booking));
        });
    }

    public BookingDTO removeExperienceFromBooking(String bookingId, String userId, String experienceId) {
        Booking outerBooking = getBookingEntityById(bookingId);
        String resolvedBookingId = outerBooking.getId() != null ? outerBooking.getId() : bookingId;
        return executeWithBookingLock(resolvedBookingId, () -> {
            Booking booking = bookingRepository.findById(resolvedBookingId).orElse(outerBooking);
            if (!booking.getUser().equals(userId)) {
                throw new ForbiddenAccessException("No tienes permiso para modificar esta reserva");
            }
            assertBookingCanBeModified(booking);
            reconcilePendingRefundBeforeChange(booking, resolvedBookingId);
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
            boolean isStandalone = "standalone-experience".equalsIgnoreCase(booking.getSuite()) || booking.getSuite() == null;
            BigDecimal taxes = booking.getTaxes() != null ? booking.getTaxes() : BigDecimal.ZERO;
            BigDecimal discount = booking.getDiscount() != null ? booking.getDiscount() : BigDecimal.ZERO;
            BigDecimal subtotal = booking.getSubtotal() != null ? booking.getSubtotal() : BigDecimal.ZERO;
            BigDecimal roomTotal = isStandalone ? BigDecimal.ZERO : subtotal.add(taxes).subtract(discount);
            BigDecimal vipExtrasTotal = isStandalone ? BigDecimal.ZERO : calculateVipExtrasTotal(booking);
            BigDecimal fixed = isStandalone ? BigDecimal.ZERO : fixedCharges(booking);
            BigDecimal newTotal = isStandalone ? expTotal : roomTotal.add(expTotal).add(vipExtrasTotal).add(fixed);
            booking.setTotalPrice(newTotal);

            if (isStandalone) {
                booking.setPlatformFee(expTotal.multiply(new BigDecimal("0.08")).setScale(2, RoundingMode.HALF_UP));
                booking.setHostPayout(expTotal.multiply(new BigDecimal("0.92")).setScale(2, RoundingMode.HALF_UP));
            }

            BigDecimal paidAmt = booking.getPaidAmount() != null ? booking.getPaidAmount() : BigDecimal.ZERO;
            BigDecimal currentRefunded = booking.getRefundedAmount() != null ? booking.getRefundedAmount() : BigDecimal.ZERO;
            BigDecimal netPaid = paidAmt.subtract(currentRefunded).max(BigDecimal.ZERO);
            BigDecimal refundDiff = netPaid.subtract(newTotal).max(BigDecimal.ZERO);

            if (netPaid.compareTo(newTotal) >= 0) {
                booking.setBalanceDue(BigDecimal.ZERO);
                booking.setPaymentStatus("paid");
            } else {
                booking.setBalanceDue(newTotal.subtract(netPaid));
                booking.setPaymentStatus(netPaid.compareTo(BigDecimal.ZERO) > 0 ? "partial" : "pending");
            }

            booking.setUpdatedAt(Instant.now());
            Booking saved = bookingRepository.save(booking);

            if (refundDiff.compareTo(BigDecimal.ZERO) > 0 && paymentGateway != null) {
                String txId = resolveRefundTransactionId(resolvedBookingId);
                String refundIdemKey = saved.getPendingRefundIdempotencyKey();
                BigDecimal pendingAmt = saved.getPendingRefundAmount();
                if (refundIdemKey == null || pendingAmt == null || pendingAmt.compareTo(refundDiff) != 0) {
                    refundIdemKey = "ref_rmexp_" + resolvedBookingId + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
                    saved.setPendingRefundIdempotencyKey(refundIdemKey);
                    saved.setPendingRefundAmount(refundDiff);
                    saved = bookingRepository.save(saved);
                }
                try {
                    PaymentGateway.PaymentRefundResponse refundResp = paymentGateway.refund(txId, refundDiff, refundIdemKey);
                    if (refundResp != null && refundResp.success()) {
                        applyConfirmedRefundToBooking(saved, refundDiff, refundResp.refundId());
                        recordConfirmedRefundOnPayment(resolvedBookingId, refundDiff, refundResp.refundId());
                    } else {
                        log.warn("Pasarela no pudo procesar reembolso por remoción de experiencia en reserva {}: {}", resolvedBookingId, (refundResp != null ? refundResp.errorMessage() : "respuesta nula"));
                        saved.setPendingRefundAmount(refundDiff);
                        saved.setPendingRefundIdempotencyKey(refundIdemKey);
                    }
                } catch (Exception ex) {
                    log.error("Excepción al ejecutar reembolso por remoción de experiencia en reserva {}: {}", resolvedBookingId, ex.getMessage(), ex);
                    saved.setPendingRefundAmount(refundDiff);
                    saved.setPendingRefundIdempotencyKey(refundIdemKey);
                }
                saved = bookingRepository.save(saved);
            }

            return toDTO(saved);
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
