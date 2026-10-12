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

    private final BookingRepository bookingRepository;
    private final SuiteRepository suiteRepository;
    private final BranchRepository branchRepository;
    private final ExperienceRepository experienceRepository;
    private final SuiteNightRepository suiteNightRepository;
    private final UserRepository userRepository;
    private final PromotionRepository promotionRepository;
    private final PricingService pricingService;

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

        int maxGuests = suite.getMaxGuests() != null ? suite.getMaxGuests() : 2;
        if (request.getGuests() != null && (request.getGuests() < 1 || request.getGuests() > maxGuests)) {
            throw new IllegalArgumentException("Esta habitación admite máximo " + maxGuests + " huéspedes");
        }

        long nights = ChronoUnit.DAYS.between(request.getCheckIn(), request.getCheckOut());
        if (nights <= 0) {
            throw new IllegalArgumentException("La fecha de check-out debe ser posterior al check-in");
        }

        List<SuiteNight> occupiedNights = suiteNightRepository.findBySuiteAndDatesBetween(suite.getId(), request.getCheckIn(), request.getCheckOut().minusDays(1));
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
        PricingService.PricingResult pricingResult = pricingService.calculatePrice(suite.getBasePrice(), request.getCheckIn(), request.getCheckOut(), branchPromoPercent);
        BigDecimal totalRoomPrice = pricingResult.total();

        List<Booking.SelectedExperience> selectedExpList = parseExperiences(request.getExperiences());
        BigDecimal experiencesTotal = BigDecimal.ZERO;
        for (Booking.SelectedExperience exp : selectedExpList) {
            if (exp.getPrice() != null && exp.getQuantity() != null) {
                experiencesTotal = experiencesTotal.add(exp.getPrice().multiply(BigDecimal.valueOf(exp.getQuantity())));
            }
        }

        BigDecimal totalPrice = totalRoomPrice.add(experiencesTotal);
        String bookingCode = "PDM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Booking.GuestDetails guestDetails = parseGuestDetails(request);

        String promoId = activePromo != null ? activePromo.getId() : null;
        String promoName = activePromo != null ? activePromo.getTitle() : (nights >= 30 ? "Estadía mensual (10%)" : (nights >= 7 ? "Estadía semanal (5%)" : null));
        BigDecimal promoDiscountPercent = activePromo != null && activePromo.getDiscountPercent() != null 
                ? BigDecimal.valueOf(activePromo.getDiscountPercent()) 
                : (nights >= 30 ? BigDecimal.TEN : (nights >= 7 ? new BigDecimal("5") : BigDecimal.ZERO));

        Booking booking = Booking.builder()
                .user(userId)
                .suite(suite.getId())
                .branch(suite.getBranch())
                .checkIn(request.getCheckIn())
                .checkOut(request.getCheckOut())
                .nights((int) nights)
                .guests(request.getGuests() != null ? request.getGuests() : 1)
                .children(request.getChildren() != null ? request.getChildren() : 0)
                .pricePerNight(suite.getBasePrice())
                .subtotal(pricingResult.subtotal())
                .discount(pricingResult.discount())
                .taxes(pricingResult.taxes())
                .nightlyPrices(pricingResult.nightlyPrices().stream().map(np -> new Booking.NightlyPrice(np.date(), np.price())).toList())
                .appliedPromotionId(promoId)
                .appliedPromotionName(promoName)
                .appliedPromotionDiscountPercent(promoDiscountPercent)
                .totalPrice(totalPrice)
                .paidAmount(BigDecimal.ZERO)
                .balanceDue(totalPrice)
                .status("pending")
                .paymentStatus("pending")
                .bookingCode(bookingCode)
                .guestDetails(guestDetails)
                .experiences(selectedExpList)
                .smartLockPin(String.format("%06d", java.util.concurrent.ThreadLocalRandom.current().nextInt(100000, 999999)))
                .expiresAt(Instant.now().plusSeconds(1800)) // 30 mins hold
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Booking savedBooking = bookingRepository.save(booking);

        // Block nights
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
        }

        return toDTO(savedBooking);
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
        PricingService.PricingResult pricingResult = pricingService.calculatePrice(suite.getBasePrice(), request.getCheckIn(), request.getCheckOut(), branchPromoPercent);
        BigDecimal totalRoomPrice = pricingResult.total();
        List<Booking.SelectedExperience> selectedExpList = request.getExperiences() != null ? parseExperiences(request.getExperiences()) : booking.getExperiences();
        BigDecimal experiencesTotal = BigDecimal.ZERO;
        for (Booking.SelectedExperience exp : selectedExpList) {
            if (exp.getPrice() != null && exp.getQuantity() != null) {
                experiencesTotal = experiencesTotal.add(exp.getPrice().multiply(BigDecimal.valueOf(exp.getQuantity())));
            }
        }

        BigDecimal newTotalPrice = totalRoomPrice.add(experiencesTotal);
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
        booking.setAppliedPromotionId(activePromo != null ? activePromo.getId() : null);
        booking.setAppliedPromotionName(activePromo != null ? activePromo.getTitle() : (nights >= 30 ? "Estadía mensual (10%)" : (nights >= 7 ? "Estadía semanal (5%)" : null)));
        booking.setAppliedPromotionDiscountPercent(activePromo != null && activePromo.getDiscountPercent() != null 
                ? BigDecimal.valueOf(activePromo.getDiscountPercent()) 
                : (nights >= 30 ? BigDecimal.TEN : (nights >= 7 ? new BigDecimal("5") : BigDecimal.ZERO)));
        booking.setTotalPrice(newTotalPrice);

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
        if (user == null || (!booking.getUser().equals(user.getId()) && !"ADMIN".equalsIgnoreCase(user.getRole()) && !"HOST".equalsIgnoreCase(user.getRole()))) {
            throw new IllegalArgumentException("No tienes permiso para acceder a esta reserva");
        }
        return toDTO(booking);
    }

    public BookingDTO getBookingByCodeSecure(String bookingCode, User user) {
        Booking booking = bookingRepository.findByBookingCode(bookingCode)
                .orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada con código: " + bookingCode));
        if (user == null || (!booking.getUser().equals(user.getId()) && !"ADMIN".equalsIgnoreCase(user.getRole()) && !"HOST".equalsIgnoreCase(user.getRole()))) {
            throw new IllegalArgumentException("No tienes permiso para acceder a esta reserva");
        }
        return toDTO(booking);
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
        if (daysUntilCheckIn < 7) {
            if (booking.getTotalPrice() == null || booking.getTotalPrice().signum() < 0) {
                throw new IllegalStateException("La reserva no tiene un precio total válido para calcular la retención");
            }
            retention = booking.getTotalPrice().multiply(new BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP);
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
        BigDecimal newTotal = roomTotal.add(expTotal);
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
        BigDecimal newTotal = roomTotal.add(expTotal);
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
                .unitSlot(booking.getUnitSlot())
                .checkIn(booking.getCheckIn())
                .checkOut(booking.getCheckOut())
                .nights(nights)
                .guests(booking.getGuests())
                .children(booking.getChildren())
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
                .paymentStatus(booking.getPaymentStatus())
                .bookingCode(code)
                .isModifiable(isModifiable)
                .isCancelable(isCancelable)
                .isCancellable(isCancelable)
                .guestDetails(gd)
                .experiences(booking.getExperiences())
                .smartLockPin(booking.getSmartLockPin())
                .preCheckInCompleted(booking.getPreCheckInCompleted())
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
            if (item instanceof String id) {
                experienceRepository.findById(id).ifPresent(exp -> {
                    result.add(Booking.SelectedExperience.builder()
                            .experienceId(exp.getId())
                            .name(exp.getName())
                            .price(exp.getPrice())
                            .quantity(1)
                            .build());
                });
            } else if (item instanceof Booking.SelectedExperience selExp) {
                result.add(selExp);
            } else if (item instanceof Map<?, ?> map) {
                String expId = (String) map.get("experienceId");
                if (expId == null) expId = (String) map.get("id");
                if (expId == null) expId = (String) map.get("_id");
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
            booking.setSmartLockPin(String.format("%06d", java.util.concurrent.ThreadLocalRandom.current().nextInt(100000, 999999)));
        }
        booking.setUpdatedAt(Instant.now());
        return toDTO(bookingRepository.save(booking));
    }
}
