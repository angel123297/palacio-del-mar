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
import java.time.LocalTime;
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
    private static final LocalTime DEFAULT_CHECK_IN_TIME = LocalTime.of(15, 0);
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

    @lombok.Setter
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

    @lombok.Setter
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private java.time.Clock clock = java.time.Clock.system(BOGOTA_ZONE);

    private LocalDate today() {
        return clock.instant().atZone(BOGOTA_ZONE).toLocalDate();
    }

    private LocalTime nowTime() {
        return clock.instant().atZone(BOGOTA_ZONE).toLocalTime();
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private co.palaciodelmar.backend.repository.PaymentRepository paymentRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.data.mongodb.core.MongoTemplate mongoTemplate;

    private static final Object[] BOOKING_STRIPED_MONITORS = new Object[64];
    static {
        for (int i = 0; i < BOOKING_STRIPED_MONITORS.length; i++) {
            BOOKING_STRIPED_MONITORS[i] = new Object();
        }
    }
    private static final Object CRIB_MONITOR = new Object();

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

    private LocalTime resolveCheckInTime(Booking booking) {
        if (branchRepository != null && booking != null && booking.getBranch() != null) {
            Branch branch = branchRepository.findById(booking.getBranch()).orElse(null);
            if (branch != null && branch.getCheckInTime() != null && !branch.getCheckInTime().isBlank()) {
                try {
                    return LocalTime.parse(branch.getCheckInTime().trim());
                } catch (Exception ignored) {
                }
            }
        }
        return DEFAULT_CHECK_IN_TIME;
    }

    boolean isWithinHoursOfCheckIn(Booking booking, long hours, Instant now) {
        if (booking == null || booking.getCheckIn() == null) {
            return false;
        }
        Instant effectiveNow = now != null ? now : clock.instant();
        Instant checkInInstant = booking.getCheckIn()
                .atTime(resolveCheckInTime(booking))
                .atZone(BOGOTA_ZONE)
                .toInstant();
        return java.time.Duration.between(effectiveNow, checkInInstant)
                .compareTo(java.time.Duration.ofHours(hours)) < 0;
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

    private <T> T executeWithCribLock(boolean needsCribLock, java.util.function.Supplier<T> action) {
        if (!needsCribLock) {
            return action.get();
        }
        synchronized (CRIB_MONITOR) {
            if (redisLockService == null) {
                return action.get();
            }
            return redisLockService.executeWithLock(
                    "cribs:global",
                    java.time.Duration.ofSeconds(10),
                    java.time.Duration.ofSeconds(4),
                    action
            );
        }
    }


    private static final Map<String, BigDecimal> KNOWN_DISH_PRICES = Map.ofEntries(
            Map.entry("cazuela de mariscos", new BigDecimal("80000")),
            Map.entry("posta negra cartagenera", new BigDecimal("94000")),
            Map.entry("posta cartagenera", new BigDecimal("94000")),
            Map.entry("pescado frito", new BigDecimal("80000")),
            Map.entry("pargo rojo frito con patacones", new BigDecimal("88000")),
            Map.entry("pargo rojo", new BigDecimal("88000")),
            Map.entry("pargo", new BigDecimal("88000")),
            Map.entry("ceviche de camaron al coco", new BigDecimal("68000")),
            Map.entry("ceviche de camaron", new BigDecimal("68000")),
            Map.entry("ceviche mixto", new BigDecimal("55000")),
            Map.entry("ceviche", new BigDecimal("55000")),
            Map.entry("arroz de mariscos del caribe", new BigDecimal("125000")),
            Map.entry("arroz con mariscos", new BigDecimal("125000")),
            Map.entry("arroz de mariscos", new BigDecimal("125000")),
            Map.entry("cocada horneada al horno de leña", new BigDecimal("38000")),
            Map.entry("cocada", new BigDecimal("38000")),
            Map.entry("mousse de mango biche & corozo", new BigDecimal("34000")),
            Map.entry("mousse de mango", new BigDecimal("34000")),
            Map.entry("postre tipico", new BigDecimal("34000")),
            Map.entry("torta de coco", new BigDecimal("34000")),
            Map.entry("coctel de autor palacio del mar", new BigDecimal("48000")),
            Map.entry("coctel de autor", new BigDecimal("48000")),
            Map.entry("langosta al termidor", new BigDecimal("160000")),
            Map.entry("langosta", new BigDecimal("160000")),
            Map.entry("patacones con hogao", new BigDecimal("28000")),
            Map.entry("arepa de huevo", new BigDecimal("25000"))
    );
    private static final BigDecimal DEFAULT_FOOD_PRICE = new BigDecimal("80000");

    public static BigDecimal resolveServerFoodPrice(String dishName, BigDecimal clientPrice) {
        if (dishName != null) {
            String norm = dishName.toLowerCase().trim()
                    .replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u");
            for (Map.Entry<String, BigDecimal> entry : KNOWN_DISH_PRICES.entrySet()) {
                if (norm.contains(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }
        if (clientPrice != null && clientPrice.compareTo(DEFAULT_FOOD_PRICE) >= 0) {
            return clientPrice;
        }
        return DEFAULT_FOOD_PRICE;
    }

    private BookingDTO createStandaloneExperienceBooking(String userId, BookingRequest request) {
        int expGuests = request.getGuests() != null ? request.getGuests() : 1;
        if (expGuests < 1 || expGuests > 50) {
            throw new IllegalArgumentException("El número de participantes debe estar entre 1 y 50 personas");
        }
        List<Booking.SelectedExperience> selectedExpList = parseExperiences(request.getExperiences());
        if (selectedExpList.isEmpty()) {
            throw new IllegalArgumentException("Debes seleccionar al menos una actividad o experiencia válida");
        }

        LocalDate actDate = request.getCheckIn() != null ? request.getCheckIn() : today();
        if (actDate.isBefore(today())) {
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

        User bookingUser = userRepository != null ? userRepository.findById(userId).orElse(null) : null;
        if (bookingUser != null) {
            String role = bookingUser.getRole() != null ? bookingUser.getRole().toLowerCase() : "";
            if ("host".equals(role) || "anfitrion".equals(role) || "admin".equals(role)) {
                throw new IllegalArgumentException("Las cuentas de Anfitrión y Administrador tienen un perfil corporativo y no pueden realizar reservas de hospedaje. Utiliza una cuenta de huésped.");
            }
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

        // Validar capacidad de huespedes para creacion de reserva
        int maxGuests = suite.getMaxGuests() != null ? suite.getMaxGuests() : 2;
        int adults = request.getGuests() != null ? request.getGuests() : 1;
        int children = request.getChildren() != null ? request.getChildren() : 0;
        if (adults < 1) {
            throw new IllegalArgumentException("Debe haber al menos 1 adulto en la reserva");
        }
        if (children < 0) {
            throw new IllegalArgumentException("El número de niños no puede ser negativo");
        }
        if (adults + children > maxGuests) {
            throw new IllegalArgumentException("Esta habitación admite máximo " + maxGuests + " huéspedes (ocupación solicitada: " + (adults + children) + ")");
        }

        if (request.getCheckIn() == null || request.getCheckOut() == null) {
            throw new IllegalArgumentException("Las fechas de check-in y check-out son obligatorias");
        }

        if (request.getCheckIn().isBefore(today())) {
            throw new IllegalArgumentException("La fecha de check-in no puede ser anterior a la fecha actual");
        }
        if (request.getCheckIn().isAfter(today().plusDays(730))) {
            throw new IllegalArgumentException("La fecha de check-in no puede superar los 2 años en el futuro");
        }

        long nights = ChronoUnit.DAYS.between(request.getCheckIn(), request.getCheckOut());
        if (nights <= 0) {
            throw new IllegalArgumentException("La fecha de check-out debe ser posterior al check-in");
        }
        if (nights > 365) {
            throw new IllegalArgumentException("La reserva no puede superar los 365 días");
        }

        final String resolvedUserId = userId;
        Booking savedBooking = executeWithSuiteLock(suite.getId(), () -> executeWithCribLock(Boolean.TRUE.equals(request.getNeedsCrib()), () -> {
            java.time.Instant now = java.time.Instant.now();
            List<SuiteNight> occupiedNights = suiteNightRepository.findBySuiteAndDatesBetween(suite.getId(), request.getCheckIn(), request.getCheckOut().minusDays(1)).stream()
                    .filter(sn -> !"cancelled".equalsIgnoreCase(sn.getStatus()) && (sn.getExpiresAt() == null || sn.getExpiresAt().isAfter(now)))
                    .toList();
            assertCribAvailable(Boolean.TRUE.equals(request.getNeedsCrib()), request.getCheckIn(), request.getCheckOut(), null, now);
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

            Promotion activePromo = findActiveBranchPromotionEntity(suite, request.getCheckIn(), request.getCheckIn().plusDays(nights));
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
                    BigDecimal foodPrice = resolveServerFoodPrice(fo.getDishName(), fo.getPrice());
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

            BigDecimal roomTaxableBase = pricingResult.subtotal().subtract(pricingResult.discount()).max(BigDecimal.ZERO);
            BigDecimal commissionRate = resolveCommissionRate(suite.getPropertyId());
            BigDecimal taxableTotal = roomTaxableBase.add(experiencesTotal);
            BigDecimal platformFee = taxableTotal.multiply(commissionRate).setScale(2, RoundingMode.HALF_UP);
            BigDecimal hostPayout = taxableTotal.subtract(platformFee);

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

            // Clean up expired held nights for this suite in the date range before allocating slots
            cleanExpiredHeldNights(suite.getId(), request.getCheckIn(), request.getCheckOut());

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
                } catch (Exception e) {
                    suiteNightRepository.deleteAll(insertedNights);
                    suiteNightRepository.deleteByBooking(persistedBooking.getId());
                    bookingRepository.delete(persistedBooking);
                    throw e;
                }
            }
            if (!nightsLocked) {
                suiteNightRepository.deleteByBooking(persistedBooking.getId());
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
        }));

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
        if (!java.util.Objects.equals(booking.getUser(), userId)) {
            throw new ForbiddenAccessException("No tienes permiso para modificar esta reserva");
        }

        if (!"pending".equalsIgnoreCase(booking.getStatus()) && !"confirmed".equalsIgnoreCase(booking.getStatus())) {
            throw new IllegalArgumentException("No se puede modificar una reserva en estado " + booking.getStatus());
        }

        if ("standalone-experience".equalsIgnoreCase(booking.getSuite())) {
            throw new IllegalArgumentException("Las reservas independientes de experiencias no permiten cambio de fechas de alojamiento");
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

        // Validar capacidad de huespedes para modificacion de reserva
        int maxGuests = suite.getMaxGuests() != null ? suite.getMaxGuests() : 2;
        int adults = request.getGuests() != null ? request.getGuests() : (booking.getGuests() != null ? booking.getGuests() : 1);
        int children = request.getChildren() != null ? request.getChildren() : (booking.getChildren() != null ? booking.getChildren() : 0);
        if (adults < 1) {
            throw new IllegalArgumentException("Debe haber al menos 1 adulto en la reserva");
        }
        if (children < 0) {
            throw new IllegalArgumentException("El número de niños no puede ser negativo");
        }
        if (adults + children > maxGuests) {
            throw new IllegalArgumentException("Esta habitación admite máximo " + maxGuests + " huéspedes (ocupación solicitada: " + (adults + children) + ")");
        }

        if (request.getCheckIn() == null || request.getCheckOut() == null) {
            throw new IllegalArgumentException("Las fechas de check-in y check-out son obligatorias");
        }

        if (request.getCheckIn().isBefore(today())) {
            throw new IllegalArgumentException("La fecha de check-in no puede ser anterior a la fecha actual");
        }
        if (request.getCheckIn().isAfter(today().plusDays(730))) {
            throw new IllegalArgumentException("La fecha de check-in no puede superar los 2 años en el futuro");
        }

        long nights = ChronoUnit.DAYS.between(request.getCheckIn(), request.getCheckOut());
        if (nights <= 0) {
            throw new IllegalArgumentException("La fecha de check-out debe ser posterior al check-in");
        }
        if (nights > 365) {
            throw new IllegalArgumentException("La reserva no puede superar los 365 días");
        }

        String resolvedBookingId = booking.getId() != null ? booking.getId() : bookingId;
        boolean needsCribLockForModify = Boolean.TRUE.equals(request.getNeedsCrib()) || Boolean.TRUE.equals(booking.getNeedsCrib());

        Booking modifiedBooking = executeWithBookingLock(resolvedBookingId, () -> executeWithSuiteLock(suite.getId(), () -> executeWithCribLock(needsCribLockForModify, () -> {
            java.time.Instant now = java.time.Instant.now();
            Booking freshBooking = bookingRepository.findById(resolvedBookingId).orElse(booking);
            if (!java.util.Objects.equals(freshBooking.getUser(), userId)) {
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

            BigDecimal existingPaidAmt = freshBooking.getPaidAmount() != null ? freshBooking.getPaidAmount() : BigDecimal.ZERO;
            BigDecimal existingRefunded = freshBooking.getRefundedAmount() != null ? freshBooking.getRefundedAmount() : BigDecimal.ZERO;
            BigDecimal existingNetPaid = existingPaidAmt.subtract(existingRefunded).max(BigDecimal.ZERO);
            boolean withinPenaltyWindow = isWithinHoursOfCheckIn(freshBooking, 48, now)
                    && (existingNetPaid.compareTo(BigDecimal.ZERO) > 0 || "confirmed".equalsIgnoreCase(freshBooking.getStatus()));
            if (withinPenaltyWindow && !request.getCheckIn().equals(innerOldCheckIn)) {
                throw new IllegalArgumentException("No se puede cambiar la fecha de check-in con menos de 48 horas de anticipación en una reserva confirmada o pagada");
            }

            List<SuiteNight> occupiedNights = new ArrayList<>(suiteNightRepository.findBySuiteAndDatesBetween(suite.getId(), request.getCheckIn(), request.getCheckOut().minusDays(1)).stream()
                    .filter(sn -> !"cancelled".equalsIgnoreCase(sn.getStatus()) && (sn.getExpiresAt() == null || sn.getExpiresAt().isAfter(now)))
                    .toList());
            boolean effectiveNeedsCrib = request.getNeedsCrib() != null
                    ? request.getNeedsCrib()
                    : Boolean.TRUE.equals(freshBooking.getNeedsCrib());
            assertCribAvailable(effectiveNeedsCrib, request.getCheckIn(), request.getCheckOut(), resolvedBookingId, now);
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

            Promotion activePromo = findActiveBranchPromotionEntity(suite, request.getCheckIn(), request.getCheckOut());
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
            int effectiveAdults = request.getGuests() != null ? request.getGuests() : (freshBooking.getGuests() != null ? freshBooking.getGuests() : 1);
            int effectiveChildren = request.getChildren() != null ? request.getChildren() : (freshBooking.getChildren() != null ? freshBooking.getChildren() : 0);
            if (effectiveAdults + effectiveChildren > maxGuests) {
                throw new IllegalArgumentException("Esta habitación admite máximo " + maxGuests + " huéspedes (ocupación solicitada: " + (effectiveAdults + effectiveChildren) + ")");
            }
            int effectiveGuests = effectiveAdults;
            BigDecimal currentDeposit = freshBooking.getGuaranteeDeposit() != null ? freshBooking.getGuaranteeDeposit() : BigDecimal.ZERO;
            BigDecimal newTotalPrice = totalRoomPrice.add(experiencesTotal).add(vipExtrasTotal)
                    .add(extraGuestFee(effectiveGuests, nights)).add(currentDeposit);
            BigDecimal oldTotalPrice = freshBooking.getTotalPrice() != null ? freshBooking.getTotalPrice() : BigDecimal.ZERO;
            if (withinPenaltyWindow && newTotalPrice.compareTo(oldTotalPrice) < 0) {
                throw new IllegalArgumentException("No se permite reducir el valor total ni acortar noches con menos de 48 horas de anticipación");
            }
            freshBooking.setCheckIn(request.getCheckIn());
            freshBooking.setCheckOut(request.getCheckOut());
            freshBooking.setNights((int) nights);
            freshBooking.setGuests(effectiveAdults);
            freshBooking.setChildren(effectiveChildren);
            if (request.getNeedsCrib() != null) freshBooking.setNeedsCrib(request.getNeedsCrib());
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
            BigDecimal modRoomTaxableBase = pricingResult.subtotal().subtract(pricingResult.discount()).max(BigDecimal.ZERO);
            BigDecimal modCommissionRate = resolveCommissionRate(suite.getPropertyId());
            BigDecimal modTaxableTotal = modRoomTaxableBase.add(experiencesTotal);
            BigDecimal modPlatformFee = modTaxableTotal.multiply(modCommissionRate).setScale(2, RoundingMode.HALF_UP);
            freshBooking.setPlatformFee(modPlatformFee);
            freshBooking.setHostPayout(modTaxableTotal.subtract(modPlatformFee));

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

            cleanExpiredHeldNights(suite.getId(), request.getCheckIn(), request.getCheckOut());

            try {
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
                    } catch (Exception e) {
                        suiteNightRepository.deleteAll(insertedNights);
                        throw e;
                    }
                }
                if (!nightsLocked) {
                    restoreSuiteNights(originalNights);
                    throw new IllegalStateException("Hubo una colisión concurrente al modificar. Las fechas ya no están disponibles.");
                }
            } catch (Exception e) {
                if (!nightsLocked) {
                    restoreSuiteNights(originalNights);
                }
                throw (e instanceof RuntimeException re) ? re : new RuntimeException(e);
            }

            String preparedModRefundIdemKey = null;
            if (refundDiff.compareTo(BigDecimal.ZERO) > 0 && paymentGateway != null) {
                preparedModRefundIdemKey = freshBooking.getPendingRefundIdempotencyKey();
                BigDecimal pendingAmt = freshBooking.getPendingRefundAmount();
                if (preparedModRefundIdemKey == null || pendingAmt == null || pendingAmt.compareTo(refundDiff) != 0) {
                    preparedModRefundIdemKey = "ref_mod_" + resolvedBookingId + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
                    freshBooking.setPendingRefundIdempotencyKey(preparedModRefundIdemKey);
                    freshBooking.setPendingRefundAmount(refundDiff);
                }
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
                String refundIdemKey = preparedModRefundIdemKey != null
                        ? preparedModRefundIdemKey
                        : "ref_mod_" + resolvedBookingId + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
                boolean refundedOk = executeSequentialRefund(saved, refundDiff, refundIdemKey);
                if (!refundedOk && (saved.getPendingRefundAmount() == null || saved.getPendingRefundAmount().compareTo(BigDecimal.ZERO) <= 0)) {
                    saved.setPendingRefundAmount(refundDiff);
                    saved.setPendingRefundIdempotencyKey(refundIdemKey);
                }
                saved = bookingRepository.save(saved);
            }

            return saved;
        })));

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
        LocalDate today = today();
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
            } else if (!isAdmin && !java.util.Objects.equals(booking.getUser(), user.getId())) {
                throw new ForbiddenAccessException("No tienes permiso para acceder a esta reserva");
            }
        } else {
            boolean isGuestBooking = booking.getUser() == null || userRepository.findById(booking.getUser())
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
            } else if (!isAdmin && !java.util.Objects.equals(booking.getUser(), user.getId())) {
                throw new ForbiddenAccessException("No tienes permiso para acceder a esta reserva");
            }
        } else {
            boolean isGuestBooking = booking.getUser() == null || userRepository.findById(booking.getUser())
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

    private void cleanExpiredHeldNights(String suiteId, LocalDate checkIn, LocalDate checkOut) {
        if (suiteId == null || checkIn == null || checkOut == null) return;
        try {
            List<SuiteNight> candidates = suiteNightRepository.findBySuiteAndDatesBetween(suiteId, checkIn, checkOut.minusDays(1));
            Instant now = Instant.now();
            List<SuiteNight> expired = candidates.stream()
                    .filter(sn -> "held".equalsIgnoreCase(sn.getStatus()) && sn.getExpiresAt() != null && sn.getExpiresAt().isBefore(now))
                    .toList();
            if (!expired.isEmpty()) {
                suiteNightRepository.deleteAll(expired);
                log.info("Eliminadas {} retenciones (held) expiradas para suite {} entre {} y {}", expired.size(), suiteId, checkIn, checkOut);
            }
        } catch (Exception e) {
            log.warn("No se pudieron limpiar retenciones expiradas para suite {}: {}", suiteId, e.getMessage());
        }
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
        if (!java.util.Objects.equals(outerBooking.getUser(), userId)) {
            throw new ForbiddenAccessException("No tienes permiso para cancelar esta reserva");
        }
        String resolvedBookingId = outerBooking.getId() != null ? outerBooking.getId() : bookingId;

        if ("cancelled".equalsIgnoreCase(outerBooking.getStatus())) {
            suiteNightRepository.deleteByBooking(resolvedBookingId);
            if (redisLockService != null) {
                redisLockService.releaseSuiteHold(outerBooking.getSuite(), outerBooking.getUnitSlot(), outerBooking.getCheckIn(), outerBooking.getCheckOut(), resolvedBookingId);
            }
            if (availabilityCacheEvictor != null) {
                availabilityCacheEvictor.evictAvailability();
            }
            return toDTO(outerBooking);
        }

        java.util.function.Supplier<BookingDTO> cancelCriticalSection = () -> executeWithSuiteLock(outerBooking.getSuite(), () -> {
            Booking booking = bookingRepository.findById(resolvedBookingId).orElse(outerBooking);
            if (!java.util.Objects.equals(booking.getUser(), userId)) {
                throw new ForbiddenAccessException("No tienes permiso para cancelar esta reserva");
            }
            if ("cancelled".equalsIgnoreCase(booking.getStatus())) {
                suiteNightRepository.deleteByBooking(resolvedBookingId);
                if (redisLockService != null) {
                    redisLockService.releaseSuiteHold(booking.getSuite(), booking.getUnitSlot(), booking.getCheckIn(), booking.getCheckOut(), resolvedBookingId);
                }
                if (availabilityCacheEvictor != null) {
                    availabilityCacheEvictor.evictAvailability();
                }
                return toDTO(booking);
            }
            if (booking.getCheckIn() == null) {
                throw new IllegalStateException("La reserva no tiene una fecha de check-in válida");
            }
            if ( booking.getTotalPrice() == null || booking.getTotalPrice().signum() < 0) {
                throw new IllegalStateException("La reserva no tiene un precio total válido para calcular la retención");
            }
            reconcilePendingRefundBeforeChange(booking, resolvedBookingId);

            long daysUntilCheckIn = ChronoUnit.DAYS.between(today(), booking.getCheckIn());
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
            if (isWithinHoursOfCheckIn(booking, 48, clock.instant())) {
                // RN-F03 (No-Show estricto < 48 horas): Cancelación tardía con penalidad del 100%.
                retention = retentionBase;
            } else if (daysUntilCheckIn < 7) {
                // Penalidad del 10% si cancela dentro de los 7 días
                retention = retentionBase.multiply(new BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP);
            }
            BigDecimal refundToProcess = netPaid.subtract(retention).max(BigDecimal.ZERO).min(netPaid);
            BigDecimal totalRefundedAfterCancel = alreadyRefunded.add(refundToProcess).min(paidAmount);

            changeBookingStatus(booking, co.palaciodelmar.backend.model.BookingStatus.CANCELLED, userId, reason, false);
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
            publishBookingStatusEvent(savedBooking, co.palaciodelmar.backend.model.BookingStatus.CANCELLED);
            suiteNightRepository.deleteByBooking(resolvedBookingId);
            if (redisLockService != null) {
                redisLockService.releaseSuiteHold(booking.getSuite(), booking.getUnitSlot(), booking.getCheckIn(), booking.getCheckOut(), resolvedBookingId);
            }
            if (availabilityCacheEvictor != null) {
                availabilityCacheEvictor.evictAvailability();
            }

            if (cancelIdemKey != null && refundToProcess.compareTo(BigDecimal.ZERO) > 0 && paymentGateway != null) {
                executeSequentialRefund(savedBooking, refundToProcess, cancelIdemKey);
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
        int index = Math.abs(key.hashCode() % BOOKING_STRIPED_MONITORS.length);
        return BOOKING_STRIPED_MONITORS[index];
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

    public BigDecimal resolveCommissionRate(String propertyId) {
        if (propertyRepository != null && propertyId != null && !propertyId.isBlank()) {
            return propertyRepository.findById(propertyId)
                    .map(co.palaciodelmar.backend.model.Property::getCommissionRate)
                    .filter(r -> r != null && r.compareTo(BigDecimal.ZERO) >= 0)
                    .orElse(new BigDecimal("0.08"));
        }
        return new BigDecimal("0.08");
    }

    public void recalculateBookingFeesAndPayout(Booking booking, BigDecimal extraTaxableIncome) {
        if (booking == null) return;
        boolean isStandalone = "standalone-experience".equalsIgnoreCase(booking.getSuite()) || booking.getSuite() == null;
        BigDecimal commissionRate = resolveCommissionRate(booking.getPropertyId());

        BigDecimal expTotal = BigDecimal.ZERO;
        if (booking.getExperiences() != null) {
            for (Booking.SelectedExperience exp : booking.getExperiences()) {
                if (exp.getPrice() != null && exp.getQuantity() != null) {
                    expTotal = expTotal.add(exp.getPrice().multiply(BigDecimal.valueOf(exp.getQuantity())));
                }
            }
        }

        BigDecimal taxableBase;
        if (isStandalone) {
            taxableBase = expTotal;
        } else {
            BigDecimal currentTotal = booking.getTotalPrice() != null ? booking.getTotalPrice() : BigDecimal.ZERO;
            BigDecimal deposit = booking.getGuaranteeDeposit() != null ? booking.getGuaranteeDeposit() : BigDecimal.ZERO;
            BigDecimal subtotal = booking.getSubtotal() != null ? booking.getSubtotal() : BigDecimal.ZERO;
            BigDecimal discount = booking.getDiscount() != null ? booking.getDiscount() : BigDecimal.ZERO;
            BigDecimal roomTaxable = subtotal.compareTo(BigDecimal.ZERO) > 0
                    ? subtotal.subtract(discount).max(BigDecimal.ZERO)
                    : currentTotal.subtract(deposit).max(BigDecimal.ZERO);
            taxableBase = roomTaxable.add(expTotal);
        }

        if (extraTaxableIncome != null && extraTaxableIncome.signum() > 0) {
            taxableBase = taxableBase.add(extraTaxableIncome);
        }

        BigDecimal fee = taxableBase.multiply(commissionRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal payout = taxableBase.subtract(fee);

        booking.setPlatformFee(fee);
        booking.setHostPayout(payout);
    }

    public void recordConfirmedRefundOnSpecificPayment(co.palaciodelmar.backend.model.Payment payment, BigDecimal refundAmount, String refundId) {
        if (payment == null || refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        if (payment.getProcessedRefundIds() == null) {
            payment.setProcessedRefundIds(new java.util.ArrayList<>());
        }
        if (refundId != null && payment.getProcessedRefundIds().contains(refundId)) {
            return;
        }
        BigDecimal currentPRefunded = payment.getRefundedAmount() != null ? payment.getRefundedAmount().max(BigDecimal.ZERO) : BigDecimal.ZERO;
        BigDecimal pTotal = payment.getAmount() != null ? payment.getAmount().max(BigDecimal.ZERO) : BigDecimal.ZERO;
        BigDecimal pRefunded = currentPRefunded.add(refundAmount);
        if (pTotal.compareTo(BigDecimal.ZERO) > 0 && pRefunded.compareTo(pTotal) > 0) {
            pRefunded = pTotal;
        }
        String nextStatus = (pTotal.compareTo(BigDecimal.ZERO) > 0 && pRefunded.compareTo(pTotal) >= 0)
                ? "refunded"
                : "partially_refunded";

        payment.setRefundedAmount(pRefunded);
        payment.setStatus(nextStatus);
        if (refundId != null && !payment.getProcessedRefundIds().contains(refundId)) {
            payment.getProcessedRefundIds().add(refundId);
        }
        payment.setUpdatedAt(Instant.now());

        if (mongoTemplate != null && payment.getId() != null && refundId != null) {
            org.springframework.data.mongodb.core.query.Query q = new org.springframework.data.mongodb.core.query.Query(
                    org.springframework.data.mongodb.core.query.Criteria.where("id").is(payment.getId())
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
            if (updated != null) {
                payment.setRefundedAmount(updated.getRefundedAmount());
                payment.setStatus(updated.getStatus());
                payment.setProcessedRefundIds(updated.getProcessedRefundIds());
            } else if (paymentRepository != null) {
                paymentRepository.save(payment);
            }
        } else if (paymentRepository != null) {
            paymentRepository.save(payment);
        }
    }

    public boolean executeSequentialRefund(Booking booking, BigDecimal totalRefundToProcess, String baseIdempotencyKey) {
        if (totalRefundToProcess == null || totalRefundToProcess.compareTo(BigDecimal.ZERO) <= 0) {
            return true;
        }
        if (paymentGateway == null) {
            booking.setPendingRefundAmount(totalRefundToProcess);
            booking.setPendingRefundIdempotencyKey(baseIdempotencyKey);
            booking.setPaymentStatus("requires_refund");
            if (bookingRepository != null) {
                try {
                    bookingRepository.save(booking);
                } catch (RuntimeException ignored) {
                }
            }
            return false;
        }

        List<co.palaciodelmar.backend.model.Payment> allPaymentsWithTx = (paymentRepository != null && booking.getId() != null)
                ? paymentRepository.findByBooking(booking.getId()).stream()
                        .filter(p -> p.getTransactionId() != null && !p.getTransactionId().isBlank())
                        .toList()
                : java.util.Collections.emptyList();

        java.util.List<String> processedPayIds = booking.getProcessedPaymentIds();
        boolean filterByProcessedIds = processedPayIds != null && !processedPayIds.isEmpty();
        List<co.palaciodelmar.backend.model.Payment> bookingPaymentsWithTx = allPaymentsWithTx.stream()
                .filter(p -> !filterByProcessedIds || (p.getId() != null && processedPayIds.contains(p.getId())))
                .toList();
        if (bookingPaymentsWithTx.isEmpty() && !filterByProcessedIds) {
            bookingPaymentsWithTx = allPaymentsWithTx;
        }

        List<co.palaciodelmar.backend.model.Payment> eligiblePayments = bookingPaymentsWithTx.stream()
                .filter(p -> "completed".equalsIgnoreCase(p.getStatus()) || "partially_refunded".equalsIgnoreCase(p.getStatus()))
                .sorted(java.util.Comparator.comparing(co.palaciodelmar.backend.model.Payment::getCreatedAt, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .toList();

        boolean multiPaymentBooking = bookingPaymentsWithTx.size() > 1;
        BigDecimal remainingToRefund = totalRefundToProcess;

        // Reconciliar solo si un pago perteneciente a la reserva tiene refundIds no registrados aún en Booking
        if (!bookingPaymentsWithTx.isEmpty()) {
            java.util.List<String> bookingRefIds = booking.getProcessedRefundIds() != null
                    ? booking.getProcessedRefundIds()
                    : java.util.Collections.emptyList();
            java.util.List<String> missingRefundIds = bookingPaymentsWithTx.stream()
                    .filter(p -> p.getProcessedRefundIds() != null)
                    .flatMap(p -> p.getProcessedRefundIds().stream())
                    .filter(rid -> rid != null && !rid.isBlank() && !bookingRefIds.contains(rid))
                    .distinct()
                    .toList();
            if (!missingRefundIds.isEmpty()) {
                BigDecimal totalPaymentsRefunded = bookingPaymentsWithTx.stream()
                        .map(p -> p.getRefundedAmount() != null ? p.getRefundedAmount() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal bookingRefunded = booking.getRefundedAmount() != null ? booking.getRefundedAmount() : BigDecimal.ZERO;
                BigDecimal unrecordedOnBooking = totalPaymentsRefunded.subtract(bookingRefunded).max(BigDecimal.ZERO);
                if (unrecordedOnBooking.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal syncedDelta = unrecordedOnBooking.min(remainingToRefund);
                    BigDecimal nextRem = remainingToRefund.subtract(syncedDelta).max(BigDecimal.ZERO);
                    applyConfirmedRefundToBooking(booking, syncedDelta, missingRefundIds, nextRem, baseIdempotencyKey);
                    remainingToRefund = nextRem;
                }
            }
        }

        boolean allSucceeded = true;

        if (!eligiblePayments.isEmpty()) {
            for (int i = 0; i < eligiblePayments.size() && remainingToRefund.compareTo(BigDecimal.ZERO) > 0; i++) {
                co.palaciodelmar.backend.model.Payment payment = eligiblePayments.get(i);
                BigDecimal pAmount = payment.getAmount() != null ? payment.getAmount() : BigDecimal.ZERO;
                BigDecimal pRefunded = payment.getRefundedAmount() != null ? payment.getRefundedAmount() : BigDecimal.ZERO;
                BigDecimal available = pAmount.subtract(pRefunded).max(BigDecimal.ZERO);

                if (available.compareTo(BigDecimal.ZERO) <= 0) continue;

                BigDecimal chunk = remainingToRefund.min(available);
                String paymentDiscriminator = (payment.getId() != null && !payment.getId().isBlank())
                        ? payment.getId()
                        : ("tx_" + payment.getTransactionId());
                String chunkKey = multiPaymentBooking
                        ? (baseIdempotencyKey + "_" + paymentDiscriminator)
                        : baseIdempotencyKey;

                try {
                    PaymentGateway.PaymentRefundResponse resp = paymentGateway.refund(payment.getTransactionId(), chunk, chunkKey);
                    if (resp != null && resp.success()) {
                        BigDecimal nextRemaining = remainingToRefund.subtract(chunk).max(BigDecimal.ZERO);
                        recordConfirmedRefundOnSpecificPayment(payment, chunk, resp.refundId());
                        applyConfirmedRefundToBooking(booking, chunk, resp.refundId(), nextRemaining, baseIdempotencyKey);
                        remainingToRefund = nextRemaining;
                    } else {
                        allSucceeded = false;
                        break;
                    }
                } catch (Exception ex) {
                    log.error("Excepción al reembolsar pago {} de reserva {}: {}", payment.getId(), booking.getId(), ex.getMessage());
                    allSucceeded = false;
                    break;
                }
            }
        } else if (remainingToRefund.compareTo(BigDecimal.ZERO) > 0) {
            // Fallback para pruebas unitarias o reservas legadas sin entidades Payment en BD
            String txId = resolveRefundTransactionId(booking.getId());
            try {
                PaymentGateway.PaymentRefundResponse resp = paymentGateway.refund(txId, remainingToRefund, baseIdempotencyKey);
                if (resp != null && resp.success()) {
                    applyConfirmedRefundToBooking(booking, remainingToRefund, resp.refundId());
                    recordConfirmedRefundOnPayment(booking.getId(), remainingToRefund, resp.refundId());
                    remainingToRefund = BigDecimal.ZERO;
                } else {
                    allSucceeded = false;
                }
            } catch (Exception ex) {
                log.error("Excepción al reembolsar reserva {} (tx={}): {}", booking.getId(), txId, ex.getMessage());
                allSucceeded = false;
            }
        }

        if (remainingToRefund.compareTo(BigDecimal.ZERO) > 0) {
            booking.setPendingRefundAmount(remainingToRefund);
            booking.setPendingRefundIdempotencyKey(baseIdempotencyKey);
            booking.setPaymentStatus("requires_refund");
            if (bookingRepository != null) {
                bookingRepository.save(booking);
            }
            return false;
        } else {
            BigDecimal paid = booking.getPaidAmount() != null ? booking.getPaidAmount() : BigDecimal.ZERO;
            BigDecimal refunded = booking.getRefundedAmount() != null ? booking.getRefundedAmount() : BigDecimal.ZERO;
            String prevPaymentStatus = booking.getPaymentStatus();
            if ("cancelled".equalsIgnoreCase(booking.getStatus())) {
                booking.setPaymentStatus(refunded.compareTo(paid) >= 0 ? "refunded" : "partial");
            } else if (booking.getBalanceDue() != null && booking.getBalanceDue().compareTo(BigDecimal.ZERO) <= 0) {
                booking.setPaymentStatus("paid");
            } else {
                booking.setPaymentStatus("partial");
            }
            booking.setPendingRefundAmount(BigDecimal.ZERO);
            booking.setPendingRefundIdempotencyKey(null);
            if (bookingRepository != null) {
                try {
                    bookingRepository.save(booking);
                } catch (RuntimeException ex) {
                    booking.setPaymentStatus(prevPaymentStatus != null ? prevPaymentStatus : "requires_refund");
                    throw ex;
                }
            }
            return true;
        }
    }

    public BookingDTO guestCheckOut(String bookingId, String userId) {
        Booking booking = getBookingEntityById(bookingId);
        if (!java.util.Objects.equals(booking.getUser(), userId)) {
            throw new ForbiddenAccessException("No tienes permiso para finalizar esta reserva");
        }
        return processCheckOut(bookingId, userId, "Check-out realizado por el huésped", false);
    }

    public BookingDTO processCheckOut(String bookingId, String performedBy, String reason, boolean isAutomatic) {
        return executeWithBookingLock(bookingId, () -> {
            Booking booking = bookingRepository.findById(bookingId)
                    .orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada: " + bookingId));

            if ("checked_out".equalsIgnoreCase(booking.getStatus())) {
                return toDTO(booking);
            }
            if (!"checked_in".equalsIgnoreCase(booking.getStatus())) {
                throw new IllegalStateException("No se puede realizar check-out desde el estado " + booking.getStatus());
            }

            LocalDate today = today();
            LocalTime nowTime = nowTime();

            // Evaluate late check-out únicamente para check-outs manuales; el automático de la 1:00 PM cierra a tiempo sin penalizar depósito
            boolean isLate = !isAutomatic && booking.getCheckOut() != null
                    && (today.isAfter(booking.getCheckOut()) || (today.isEqual(booking.getCheckOut()) && nowTime.isAfter(LocalTime.NOON)));

            boolean pinActive = booking.getSmartLockPin() != null && !booking.getSmartLockPin().isBlank();

            BigDecimal effectivePricePerNight = booking.getPricePerNight();
            if (effectivePricePerNight == null && booking.getSubtotal() != null && booking.getNights() != null && booking.getNights() > 0) {
                effectivePricePerNight = booking.getSubtotal().divide(BigDecimal.valueOf(booking.getNights()), 2, RoundingMode.HALF_UP);
            }

            if (isLate && pinActive && effectivePricePerNight != null) {
                BigDecimal currentTotal = booking.getTotalPrice() != null ? booking.getTotalPrice() : BigDecimal.ZERO;
                BigDecimal currentBalance = booking.getBalanceDue() != null ? booking.getBalanceDue() : BigDecimal.ZERO;
                BigDecimal recargo = effectivePricePerNight.multiply(new BigDecimal("0.50")).setScale(2, RoundingMode.HALF_UP);

                booking.setTotalPrice(currentTotal.add(recargo));
                booking.setBalanceDue(currentBalance.add(recargo));
                booking.setPaymentStatus("partial");

                recalculateBookingFeesAndPayout(booking, recargo);
                booking.setNotes((booking.getNotes() != null ? booking.getNotes() + " | " : "") + (isAutomatic ? "Recargo automático Late Check-out aplicado: $" : "Recargo Late Check-out aplicado: $") + recargo);
            }

            // Desactivar el PIN de la cerradura inteligente al cerrar
            booking.setSmartLockPin(null);

            // Depósito de garantía: retención y compensación frente a balanceDue
            BigDecimal deposit = booking.getGuaranteeDeposit() != null ? booking.getGuaranteeDeposit() : BigDecimal.ZERO;
            if (deposit.signum() > 0 && !Boolean.TRUE.equals(booking.getGuaranteeRefunded())) {
                BigDecimal debt = booking.getBalanceDue() != null ? booking.getBalanceDue() : BigDecimal.ZERO;
                BigDecimal compensated = deposit.min(debt);

                if (compensated.signum() > 0) {
                    BigDecimal prevCompensated = booking.getGuaranteeCompensated() != null ? booking.getGuaranteeCompensated() : BigDecimal.ZERO;
                    booking.setGuaranteeCompensated(prevCompensated.add(compensated));
                    booking.setBalanceDue(debt.subtract(compensated));
                    deposit = deposit.subtract(compensated);
                    booking.setNotes((booking.getNotes() != null ? booking.getNotes() + " | " : "") + "Depósito de garantía retenido/compensado: $" + compensated);
                    if (booking.getBalanceDue().compareTo(BigDecimal.ZERO) <= 0) {
                        booking.setPaymentStatus("paid");
                    }
                }

                // Devolver excedente del depósito con pagos reales
                BigDecimal paid = booking.getPaidAmount() != null ? booking.getPaidAmount() : BigDecimal.ZERO;
                BigDecimal refunded = booking.getRefundedAmount() != null ? booking.getRefundedAmount() : BigDecimal.ZERO;
                BigDecimal refundableExcess = paid.subtract(refunded).min(deposit).max(BigDecimal.ZERO);

                boolean excessRefunded = false;
                if (refundableExcess.signum() > 0 && paymentGateway != null) {
                    String depKey = "ref_dep_" + booking.getId();
                    excessRefunded = executeSequentialRefund(booking, refundableExcess, depKey);
                    if (excessRefunded) {
                        booking.setGuaranteeRefunded(true);
                    }
                } else if (refundableExcess.compareTo(BigDecimal.ZERO) <= 0) {
                    booking.setGuaranteeRefunded(true);
                }
            }

            changeBookingStatus(booking, co.palaciodelmar.backend.model.BookingStatus.CHECKED_OUT, performedBy, reason);
            Booking saved = bookingRepository.save(booking);
            return toDTO(saved);
        });
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
        return applyConfirmedRefundToBooking(booking, refundAmount, refundId, BigDecimal.ZERO, null);
    }

    private boolean applyConfirmedRefundToBooking(
            Booking booking,
            BigDecimal refundAmount,
            String refundId,
            BigDecimal remainingPendingAfter,
            String pendingKey) {
        java.util.List<String> ids = (refundId != null && !refundId.isBlank())
                ? java.util.List.of(refundId)
                : java.util.Collections.emptyList();
        return applyConfirmedRefundToBooking(booking, refundAmount, ids, remainingPendingAfter, pendingKey);
    }

    private boolean applyConfirmedRefundToBooking(
            Booking booking,
            BigDecimal refundAmount,
            java.util.List<String> refundIds,
            BigDecimal remainingPendingAfter,
            String pendingKey) {
        if (booking == null || refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        java.util.List<String> cleanRefundIds = refundIds != null
                ? refundIds.stream().filter(id -> id != null && !id.isBlank()).distinct().toList()
                : java.util.Collections.emptyList();
        BigDecimal safePending = remainingPendingAfter != null ? remainingPendingAfter.max(BigDecimal.ZERO) : BigDecimal.ZERO;
        String nextPendingKey = safePending.compareTo(BigDecimal.ZERO) > 0 ? pendingKey : null;
        if (booking.getProcessedRefundIds() == null) {
            booking.setProcessedRefundIds(new java.util.ArrayList<>());
        }
        java.util.List<String> newRefundIds = cleanRefundIds.stream()
                .filter(id -> !booking.getProcessedRefundIds().contains(id))
                .toList();
        if (!cleanRefundIds.isEmpty() && newRefundIds.isEmpty()) {
            booking.setPendingRefundAmount(safePending);
            booking.setPendingRefundIdempotencyKey(nextPendingKey);
            return false;
        }
        BigDecimal currentRef = booking.getRefundedAmount() != null ? booking.getRefundedAmount().max(BigDecimal.ZERO) : BigDecimal.ZERO;
        BigDecimal paidCap = booking.getPaidAmount() != null ? booking.getPaidAmount().max(BigDecimal.ZERO) : BigDecimal.ZERO;
        BigDecimal nextRef = currentRef.add(refundAmount);
        if (paidCap.compareTo(BigDecimal.ZERO) > 0 && nextRef.compareTo(paidCap) > 0) {
            nextRef = paidCap;
        }
        String activeDepKey = pendingKey != null ? pendingKey : booking.getPendingRefundIdempotencyKey();
        boolean isDepositRefund = safePending.compareTo(BigDecimal.ZERO) == 0
                && activeDepKey != null
                && activeDepKey.startsWith("ref_dep_");
        if (isDepositRefund) {
            booking.setGuaranteeRefunded(true);
        }
        if (mongoTemplate != null && booking.getId() != null && !newRefundIds.isEmpty()) {
            org.springframework.data.mongodb.core.query.Query q = new org.springframework.data.mongodb.core.query.Query(
                    org.springframework.data.mongodb.core.query.Criteria.where("id").is(booking.getId())
                            .and("processedRefundIds").nin(newRefundIds)
            );
            org.springframework.data.mongodb.core.query.Update u = new org.springframework.data.mongodb.core.query.Update()
                    .set("refundedAmount", nextRef)
                    .set("pendingRefundAmount", safePending)
                    .set("pendingRefundIdempotencyKey", nextPendingKey)
                    .set("updatedAt", Instant.now());
            u.addToSet("processedRefundIds").each(newRefundIds.toArray());
            if (safePending.compareTo(BigDecimal.ZERO) > 0) {
                u.set("paymentStatus", "requires_refund");
            }
            if (isDepositRefund) {
                u.set("guaranteeRefunded", true);
            }
            Booking updated = mongoTemplate.findAndModify(
                    q, u,
                    org.springframework.data.mongodb.core.FindAndModifyOptions.options().returnNew(true),
                    Booking.class
            );
            if (updated == null) {
                Booking fresh = bookingRepository.findById(booking.getId()).orElse(booking);
                booking.setRefundedAmount(fresh.getRefundedAmount());
                booking.setProcessedRefundIds(fresh.getProcessedRefundIds());
                booking.setPendingRefundAmount(safePending);
                booking.setPendingRefundIdempotencyKey(nextPendingKey);
                if (isDepositRefund) {
                    booking.setGuaranteeRefunded(true);
                }
                return false;
            }
            booking.setVersion(updated.getVersion());
            if (isDepositRefund) {
                booking.setGuaranteeRefunded(true);
            }
        }
        booking.setRefundedAmount(nextRef);
        for (String rid : newRefundIds) {
            if (!booking.getProcessedRefundIds().contains(rid)) {
                booking.getProcessedRefundIds().add(rid);
            }
        }
        booking.setPendingRefundAmount(safePending);
        booking.setPendingRefundIdempotencyKey(nextPendingKey);
        if (safePending.compareTo(BigDecimal.ZERO) > 0) {
            booking.setPaymentStatus("requires_refund");
        }
        if (isDepositRefund) {
            booking.setGuaranteeRefunded(true);
        }
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
        boolean reconciled = executeSequentialRefund(booking, pendingAmt, pendingKey);
        if (!reconciled) {
            throw new IllegalStateException("Existe un reembolso previo pendiente de confirmación con la pasarela (" + pendingKey + ")");
        }

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
        booking.setPendingRefundAmount(BigDecimal.ZERO);
        booking.setPendingRefundIdempotencyKey(null);
        booking.setUpdatedAt(Instant.now());
        if (bookingRepository != null) {
            bookingRepository.save(booking);
        }
    }

    public BookingDTO addExperienceToBooking(String bookingId, String userId, String experienceId) {
        Booking outerBooking = getBookingEntityById(bookingId);
        String resolvedBookingId = outerBooking.getId() != null ? outerBooking.getId() : bookingId;
        return executeWithBookingLock(resolvedBookingId, () -> {
            Booking booking = bookingRepository.findById(resolvedBookingId).orElse(outerBooking);
            if (!java.util.Objects.equals(booking.getUser(), userId)) {
                throw new ForbiddenAccessException("No tienes permiso para modificar esta reserva");
            }
            assertBookingCanBeModified(booking);
            reconcilePendingRefundBeforeChange(booking, resolvedBookingId);
            Experience experience = experienceRepository.findById(experienceId)
                    .orElseThrow(() -> new IllegalArgumentException("Experiencia no encontrada"));
            if (Boolean.FALSE.equals(experience.getActive()) || Boolean.FALSE.equals(experience.getAvailable())) {
                throw new IllegalArgumentException("La experiencia no está disponible para reserva");
            }

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

            recalculateBookingFeesAndPayout(booking, null);

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
            if (!java.util.Objects.equals(booking.getUser(), userId)) {
                throw new ForbiddenAccessException("No tienes permiso para modificar esta reserva");
            }
            assertBookingCanBeModified(booking);
            reconcilePendingRefundBeforeChange(booking, resolvedBookingId);
            if ("checked_in".equalsIgnoreCase(booking.getStatus())) {
                throw new IllegalStateException("No se pueden eliminar experiencias una vez realizado el check-in");
            }
            boolean isStandalone = "standalone-experience".equalsIgnoreCase(booking.getSuite()) || booking.getSuite() == null;
            List<Booking.SelectedExperience> current = booking.getExperiences() != null
                    ? new ArrayList<>(booking.getExperiences())
                    : new ArrayList<>();
            current.removeIf(exp -> exp.getExperienceId() != null && exp.getExperienceId().equals(experienceId));
            if (isStandalone && current.isEmpty()) {
                throw new IllegalArgumentException("No se puede eliminar la última experiencia de una reserva independiente; cancela la reserva en su lugar");
            }

            BigDecimal expTotal = BigDecimal.ZERO;
            for (Booking.SelectedExperience exp : current) {
                if (exp.getPrice() != null && exp.getQuantity() != null) {
                    expTotal = expTotal.add(exp.getPrice().multiply(BigDecimal.valueOf(exp.getQuantity())));
                }
            }
            BigDecimal taxes = booking.getTaxes() != null ? booking.getTaxes() : BigDecimal.ZERO;
            BigDecimal discount = booking.getDiscount() != null ? booking.getDiscount() : BigDecimal.ZERO;
            BigDecimal subtotal = booking.getSubtotal() != null ? booking.getSubtotal() : BigDecimal.ZERO;
            BigDecimal roomTotal = isStandalone ? BigDecimal.ZERO : subtotal.add(taxes).subtract(discount);
            BigDecimal vipExtrasTotal = isStandalone ? BigDecimal.ZERO : calculateVipExtrasTotal(booking);
            BigDecimal fixed = isStandalone ? BigDecimal.ZERO : fixedCharges(booking);
            BigDecimal newTotal = isStandalone ? expTotal : roomTotal.add(expTotal).add(vipExtrasTotal).add(fixed);

            BigDecimal paidAmt = booking.getPaidAmount() != null ? booking.getPaidAmount() : BigDecimal.ZERO;
            BigDecimal currentRefunded = booking.getRefundedAmount() != null ? booking.getRefundedAmount() : BigDecimal.ZERO;
            BigDecimal netPaid = paidAmt.subtract(currentRefunded).max(BigDecimal.ZERO);
            BigDecimal refundDiff = netPaid.subtract(newTotal).max(BigDecimal.ZERO);

            if (refundDiff.compareTo(BigDecimal.ZERO) > 0
                    && isWithinHoursOfCheckIn(booking, 48, clock.instant())) {
                throw new IllegalArgumentException("No se permiten reembolsos por eliminación de experiencias con menos de 48 horas de anticipación al check-in");
            }

            booking.setExperiences(current);
            booking.setTotalPrice(newTotal);

            recalculateBookingFeesAndPayout(booking, null);

            if (newTotal.compareTo(BigDecimal.ZERO) > 0 && netPaid.compareTo(newTotal) >= 0) {
                booking.setBalanceDue(BigDecimal.ZERO);
                booking.setPaymentStatus("paid");
            } else {
                booking.setBalanceDue(newTotal.subtract(netPaid).max(BigDecimal.ZERO));
                booking.setPaymentStatus(netPaid.compareTo(BigDecimal.ZERO) > 0 ? "partial" : "pending");
            }

            String preparedRmExpRefundIdemKey = null;
            if (refundDiff.compareTo(BigDecimal.ZERO) > 0 && paymentGateway != null) {
                preparedRmExpRefundIdemKey = booking.getPendingRefundIdempotencyKey();
                BigDecimal pendingAmt = booking.getPendingRefundAmount();
                if (preparedRmExpRefundIdemKey == null || pendingAmt == null || pendingAmt.compareTo(refundDiff) != 0) {
                    preparedRmExpRefundIdemKey = "ref_rmexp_" + resolvedBookingId + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
                    booking.setPendingRefundIdempotencyKey(preparedRmExpRefundIdemKey);
                    booking.setPendingRefundAmount(refundDiff);
                }
            }

            booking.setUpdatedAt(Instant.now());
            Booking saved = bookingRepository.save(booking);

            if (refundDiff.compareTo(BigDecimal.ZERO) > 0 && paymentGateway != null) {
                String refundIdemKey = preparedRmExpRefundIdemKey != null
                        ? preparedRmExpRefundIdemKey
                        : "ref_rmexp_" + resolvedBookingId + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
                boolean refundedOk = executeSequentialRefund(saved, refundDiff, refundIdemKey);
                if (!refundedOk && (saved.getPendingRefundAmount() == null || saved.getPendingRefundAmount().compareTo(BigDecimal.ZERO) <= 0)) {
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

    /** Cunas: tope global de 5 por noche. Excluye reservas pendientes expiradas y la propia reserva. */
    private void assertCribAvailable(boolean needsCrib, LocalDate checkIn, LocalDate checkOut, String excludeBookingId, Instant now) {
        if (!needsCrib || checkIn == null || checkOut == null) {
            return;
        }
        Instant effectiveNow = now != null ? now : Instant.now();
        List<Booking> overlapping = bookingRepository.findByStatusInAndNeedsCribTrueAndCheckInBeforeAndCheckOutAfter(
                List.of("confirmed", "checked_in", "pending"),
                checkOut,
                checkIn
        );
        List<Booking> activeOverlapping = overlapping.stream()
                .filter(b -> excludeBookingId == null || !excludeBookingId.equals(b.getId()))
                .filter(b -> !"pending".equalsIgnoreCase(b.getStatus())
                        || b.getExpiresAt() == null
                        || b.getExpiresAt().isAfter(effectiveNow))
                .toList();

        for (LocalDate d = checkIn; d.isBefore(checkOut); d = d.plusDays(1)) {
            final LocalDate night = d;
            long cribsInUseOnNight = activeOverlapping.stream()
                    .filter(b -> b.getCheckIn() == null || b.getCheckOut() == null
                            || (!night.isBefore(b.getCheckIn()) && night.isBefore(b.getCheckOut())))
                    .count();
            if (cribsInUseOnNight >= MAX_CRIBS) {
                throw new IllegalArgumentException("Lo sentimos, no hay stock físico de cunas disponible para estas fechas.");
            }
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
                BigDecimal foodPrice = (fo.getPrice() != null && fo.getPrice().compareTo(BigDecimal.ZERO) > 0)
                        ? fo.getPrice()
                        : resolveServerFoodPrice(fo.getDishName(), null);
                vipExtrasTotal = vipExtrasTotal.add(foodPrice);
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
        boolean isStandalone = "standalone-experience".equalsIgnoreCase(booking.getSuite());
        boolean isModifiable = isCancelable && !isStandalone && (booking.getCheckIn() == null || !booking.getCheckIn().isBefore(today()));

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
                .smartLockPin((("confirmed".equalsIgnoreCase(status) || "checked_in".equalsIgnoreCase(status) || "completed".equalsIgnoreCase(status)) && !"pending".equalsIgnoreCase(booking.getPaymentStatus()) && !"cancelled".equalsIgnoreCase(status) && !"expired".equalsIgnoreCase(status) && !"no_show".equalsIgnoreCase(status)) ? booking.getSmartLockPin() : null)
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
                final String finalExpId = expId;
                final int finalQty = quantity;
                Experience exp = experienceRepository.findById(finalExpId)
                        .orElseThrow(() -> new IllegalArgumentException("Experiencia no encontrada: " + finalExpId));
                if (Boolean.FALSE.equals(exp.getActive()) || Boolean.FALSE.equals(exp.getAvailable())) {
                    throw new IllegalArgumentException("La experiencia '" + exp.getName() + "' no está disponible para reserva");
                }
                result.add(Booking.SelectedExperience.builder()
                        .experienceId(exp.getId())
                        .name(exp.getName())
                        .price(exp.getPrice())
                        .quantity(finalQty)
                        .build());
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
        changeBookingStatus(booking, newStatus, changedBy, reason, true);
    }

    public void changeBookingStatus(Booking booking, co.palaciodelmar.backend.model.BookingStatus newStatus, String changedBy, String reason, boolean persistAndPublish) {
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

        Long previousVersion = booking.getVersion();
        Instant previousExpiresAt = booking.getExpiresAt();
        if (newStatus == co.palaciodelmar.backend.model.BookingStatus.CONFIRMED) {
            booking.setExpiresAt(null);
            confirmBookingSuiteNights(booking);
        }

        if (persistAndPublish) {
            if (booking.getId() != null && bookingRepository != null) {
                try {
                    Booking saved = bookingRepository.save(booking);
                    if (saved != null && saved.getVersion() != null) {
                        booking.setVersion(saved.getVersion());
                    }
                } catch (RuntimeException saveEx) {
                    Booking persistedCheck = null;
                    try {
                        persistedCheck = bookingRepository.findById(booking.getId()).orElse(null);
                    } catch (Exception ignored) {
                    }
                    boolean isOptimistic = saveEx instanceof org.springframework.dao.OptimisticLockingFailureException;
                    boolean paymentsMatch = booking.getProcessedPaymentIds() == null || booking.getProcessedPaymentIds().isEmpty()
                            || (persistedCheck != null && persistedCheck.getProcessedPaymentIds() != null
                                && persistedCheck.getProcessedPaymentIds().containsAll(booking.getProcessedPaymentIds()));
                    boolean paidAmountMatches = booking.getPaidAmount() == null
                            || (persistedCheck != null && persistedCheck.getPaidAmount() != null
                                && persistedCheck.getPaidAmount().compareTo(booking.getPaidAmount()) == 0);
                    boolean historyMatches = persistedCheck == null || persistedCheck.getStatusHistory() == null
                            || persistedCheck.getStatusHistory().isEmpty()
                            || persistedCheck.getStatusHistory().stream().anyMatch(h ->
                                newStatus.name().equalsIgnoreCase(h.getStatus())
                                && java.util.Objects.equals(changedBy, h.getChangedBy())
                                && java.util.Objects.equals(reason, h.getReason()));
                    boolean dbCommittedNewState = !isOptimistic
                            && persistedCheck != null
                            && persistedCheck != booking
                            && newStatus.name().equalsIgnoreCase(persistedCheck.getStatus())
                            && (previousVersion == null || !previousVersion.equals(persistedCheck.getVersion()))
                            && paymentsMatch
                            && paidAmountMatches
                            && historyMatches;
                    if (dbCommittedNewState) {
                        log.warn("bookingRepository.save lanzó excepción para reserva {} pero el estado {} sí se aplicó en BD; tratando guardado como exitoso.",
                                booking.getId(), newStatus.name());
                        if (persistedCheck.getVersion() != null) {
                            booking.setVersion(persistedCheck.getVersion());
                        }
                    } else {
                        if (newStatus == co.palaciodelmar.backend.model.BookingStatus.CONFIRMED) {
                            booking.setExpiresAt(previousExpiresAt);
                            rollbackUnpersistedConfirmedSuiteNights(booking, previousExpiresAt);
                        }
                        throw saveEx;
                    }
                }
            }
        }

        if (newStatus == co.palaciodelmar.backend.model.BookingStatus.CONFIRMED) {
            if (redisLockService != null) {
                try {
                    redisLockService.releaseSuiteHold(booking.getSuite(), booking.getUnitSlot(), booking.getCheckIn(), booking.getCheckOut(), booking.getId());
                } catch (Exception ex) {
                    log.warn("Error liberando hold Redis para reserva {}: {}", booking.getId(), ex.getMessage());
                }
            }
            if (socialProofService != null) {
                try {
                    socialProofService.recordBookingActivity(booking.getSuite(), null, booking.getId());
                } catch (Exception ex) {
                    log.warn("Error registrando actividad social proof para reserva {}: {}", booking.getId(), ex.getMessage());
                }
            }
        }

        if (persistAndPublish) {
            publishBookingStatusEvent(booking, newStatus);
        }

        if (availabilityCacheEvictor != null) {
            try {
                availabilityCacheEvictor.evictAvailability();
            } catch (Exception ex) {
                log.warn("Error limpiando caché de disponibilidad tras cambio de estado de reserva {}: {}", booking.getId(), ex.getMessage());
            }
        }
    }

    public void confirmBookingSuiteNights(Booking booking) {
        if (booking == null || booking.getId() == null || booking.getId().isBlank() || suiteNightRepository == null) {
            return;
        }
        String suiteId = booking.getSuite();
        boolean isStandalone = suiteId == null || suiteId.isBlank() || "standalone-experience".equalsIgnoreCase(suiteId);
        java.util.List<co.palaciodelmar.backend.model.SuiteNight> existingNights = suiteNightRepository.findByBooking(booking.getId());
        if (existingNights == null) {
            existingNights = java.util.List.of();
        }

        java.util.Set<LocalDate> coveredDates = new java.util.HashSet<>();
        for (co.palaciodelmar.backend.model.SuiteNight sn : existingNights) {
            if (sn == null) continue;
            if (sn.getDates() != null) {
                coveredDates.addAll(sn.getDates());
            }
            if (!"confirmed".equalsIgnoreCase(sn.getStatus()) || sn.getExpiresAt() != null) {
                sn.setExpiresAt(null);
                sn.setStatus("confirmed");
                suiteNightRepository.save(sn);
            }
        }

        if (!isStandalone && booking.getCheckIn() != null && booking.getCheckOut() != null
                && booking.getCheckIn().isBefore(booking.getCheckOut())) {
            int slot = (booking.getUnitSlot() != null && booking.getUnitSlot() > 0) ? booking.getUnitSlot() : 1;
            for (LocalDate d = booking.getCheckIn(); d.isBefore(booking.getCheckOut()); d = d.plusDays(1)) {
                if (!coveredDates.contains(d)) {
                    SuiteNight recreated = SuiteNight.builder()
                            .suite(suiteId)
                            .branch(booking.getBranch())
                            .unitSlot(slot)
                            .dates(java.util.Collections.singletonList(d))
                            .booking(booking.getId())
                            .status("confirmed")
                            .expiresAt(null)
                            .createdAt(Instant.now())
                            .build();
                    suiteNightRepository.save(recreated);
                }
            }
        }
    }

    public void releaseUnpersistedConfirmedSuiteNights(Booking booking) {
        rollbackUnpersistedConfirmedSuiteNights(booking, null);
    }

    public void rollbackUnpersistedConfirmedSuiteNights(Booking booking, Instant previousExpiresAt) {
        if (booking == null || booking.getId() == null || booking.getId().isBlank() || suiteNightRepository == null) {
            return;
        }
        Instant now = Instant.now();
        Booking persistedBooking = (bookingRepository != null)
                ? bookingRepository.findById(booking.getId()).orElse(null)
                : null;
        if (persistedBooking == booking) {
            persistedBooking = null;
        }
        if (persistedBooking != null && ("confirmed".equalsIgnoreCase(persistedBooking.getStatus())
                || "checked_in".equalsIgnoreCase(persistedBooking.getStatus())
                || "checked_out".equalsIgnoreCase(persistedBooking.getStatus()))) {
            return;
        }
        Instant effectiveExpiry = previousExpiresAt;
        if (effectiveExpiry == null && persistedBooking != null && "pending".equalsIgnoreCase(persistedBooking.getStatus())) {
            effectiveExpiry = persistedBooking.getExpiresAt();
        }
        boolean stillActivePendingHold = effectiveExpiry != null && effectiveExpiry.isAfter(now)
                && (persistedBooking == null || "pending".equalsIgnoreCase(persistedBooking.getStatus()));

        if (stillActivePendingHold) {
            try {
                java.util.List<co.palaciodelmar.backend.model.SuiteNight> nights = suiteNightRepository.findByBooking(booking.getId());
                if (nights != null) {
                    for (co.palaciodelmar.backend.model.SuiteNight sn : nights) {
                        if (sn == null) continue;
                        if (!"held".equalsIgnoreCase(sn.getStatus()) || !effectiveExpiry.equals(sn.getExpiresAt())) {
                            sn.setStatus("held");
                            sn.setExpiresAt(effectiveExpiry);
                            suiteNightRepository.save(sn);
                        }
                    }
                }
            } catch (Exception ex) {
                log.warn("No se pudieron revertir SuiteNights a held para reserva {}: {}", booking.getId(), ex.getMessage());
            }
            return;
        }

        try {
            suiteNightRepository.deleteByBooking(booking.getId());
            java.util.List<co.palaciodelmar.backend.model.SuiteNight> remaining = suiteNightRepository.findByBooking(booking.getId());
            if (remaining != null && !remaining.isEmpty()) {
                suiteNightRepository.deleteAll(remaining);
            }
        } catch (Exception ex) {
            log.warn("No se pudieron liberar SuiteNights tras fallo al persistir reserva {}: {}", booking.getId(), ex.getMessage());
        }
        if (redisLockService != null) {
            try {
                redisLockService.releaseSuiteHold(
                        booking.getSuite(),
                        booking.getUnitSlot(),
                        booking.getCheckIn(),
                        booking.getCheckOut(),
                        booking.getId()
                );
            } catch (Exception ex) {
                log.warn("Error liberando hold Redis tras fallo al persistir reserva {}: {}", booking.getId(), ex.getMessage());
            }
        }
        if (availabilityCacheEvictor != null) {
            try {
                availabilityCacheEvictor.evictAvailability();
            } catch (Exception ex) {
                log.warn("Error limpiando caché de disponibilidad tras liberar SuiteNights de reserva {}: {}", booking.getId(), ex.getMessage());
            }
        }
    }

    public void publishBookingStatusEvent(Booking booking, co.palaciodelmar.backend.model.BookingStatus newStatus) {
        if (eventPublisher == null || booking == null) return;
        String eventType = switch (newStatus) {
            case CONFIRMED -> "booking.confirmed";
            case CANCELLED -> "booking.cancelled";
            case CHECKED_IN -> "booking.checked_in";
            case CHECKED_OUT -> "booking.checked_out";
            case NO_SHOW -> "booking.no_show";
            default -> null;
        };
        if (eventType != null) {
            try {
                eventPublisher.publishEvent(new co.palaciodelmar.backend.event.BookingDomainEvent(
                        eventType,
                        booking.getId(),
                        booking.getPropertyId(),
                        toDTO(booking),
                        Instant.now()
                ));
            } catch (Exception ex) {
                log.warn("Error publicando evento de dominio {} para reserva {}: {}", eventType, booking.getId(), ex.getMessage());
            }
        }
    }

    private Promotion findActiveBranchPromotionEntity(Suite suite, LocalDate checkIn, LocalDate checkOut) {
        if (suite == null || checkIn == null || checkOut == null) return null;
        java.util.Set<String> targets = new java.util.LinkedHashSet<>();
        if (suite.getBranch() != null && !suite.getBranch().isBlank()) targets.add(suite.getBranch());
        if (suite.getPropertyId() != null && !suite.getPropertyId().isBlank()) targets.add(suite.getPropertyId());
        targets.add("all");

        List<Promotion> promotions = promotionRepository.findByBranchInAndActiveTrue(targets);
        if (promotions == null || promotions.isEmpty()) {
            promotions = new ArrayList<>();
            for (String target : targets) {
                List<Promotion> perTarget = promotionRepository.findByBranchAndActiveTrue(target);
                if (perTarget != null) {
                    promotions.addAll(perTarget);
                }
            }
        }
        return promotions.stream()
                .filter(p -> p.getDiscountPercent() != null && p.getDiscountPercent() > 0)
                .filter(p -> (p.getStartDate() == null || !p.getStartDate().isAfter(checkOut.minusDays(1))) &&
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
        String paySt = booking.getPaymentStatus() != null ? booking.getPaymentStatus() : "";
        boolean isEligibleStatus = "confirmed".equalsIgnoreCase(st) || "checked_in".equalsIgnoreCase(st);
        boolean isUnpaidOrInvalidPayment = "pending".equalsIgnoreCase(paySt)
                || "failed".equalsIgnoreCase(paySt)
                || "cancelled".equalsIgnoreCase(paySt);
        if (!isEligibleStatus || isUnpaidOrInvalidPayment) {
            throw new IllegalStateException("No es posible realizar el pre-check-in para una reserva en estado " + st + " (pago: " + (paySt.isBlank() ? "pendiente" : paySt) + ")");
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
