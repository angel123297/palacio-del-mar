package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.dto.BookingDTO;
import co.palaciodelmar.backend.dto.BookingRequest;
import co.palaciodelmar.backend.model.Booking;
import co.palaciodelmar.backend.model.Branch;
import co.palaciodelmar.backend.model.Experience;
import co.palaciodelmar.backend.model.Suite;
import co.palaciodelmar.backend.model.SuiteNight;
import co.palaciodelmar.backend.model.User;
import co.palaciodelmar.backend.repository.BookingRepository;
import co.palaciodelmar.backend.repository.BranchRepository;
import co.palaciodelmar.backend.repository.ExperienceRepository;
import co.palaciodelmar.backend.repository.SuiteNightRepository;
import co.palaciodelmar.backend.repository.SuiteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final SuiteRepository suiteRepository;
    private final BranchRepository branchRepository;
    private final ExperienceRepository experienceRepository;
    private final SuiteNightRepository suiteNightRepository;

    public BookingDTO createBooking(String userId, BookingRequest request) {
        Suite suite = suiteRepository.findById(request.getSuiteId())
                .orElseThrow(() -> new IllegalArgumentException("Suite no encontrada: " + request.getSuiteId()));

        long nights = ChronoUnit.DAYS.between(request.getCheckIn(), request.getCheckOut());
        if (nights <= 0) {
            throw new IllegalArgumentException("La fecha de check-out debe ser posterior al check-in");
        }

        BigDecimal pricePerNight = suite.getBasePrice();
        BigDecimal totalRoomPrice = pricePerNight.multiply(BigDecimal.valueOf(nights));

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

        Booking booking = Booking.builder()
                .user(userId)
                .suite(suite.getId())
                .branch(suite.getBranch())
                .checkIn(request.getCheckIn())
                .checkOut(request.getCheckOut())
                .nights((int) nights)
                .guests(request.getGuests() != null ? request.getGuests() : 1)
                .children(request.getChildren() != null ? request.getChildren() : 0)
                .pricePerNight(pricePerNight)
                .totalPrice(totalPrice)
                .paidAmount(BigDecimal.ZERO)
                .balanceDue(totalPrice)
                .status("pending")
                .paymentStatus("pending")
                .bookingCode(bookingCode)
                .guestDetails(guestDetails)
                .experiences(selectedExpList)
                .expiresAt(Instant.now().plusSeconds(1800)) // 30 mins hold
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Booking savedBooking = bookingRepository.save(booking);

        // Block nights
        for (int i = 0; i < nights; i++) {
            LocalDate date = request.getCheckIn().plusDays(i);
            SuiteNight suiteNight = SuiteNight.builder()
                    .suite(suite.getId())
                    .branch(suite.getBranch())
                    .unitSlot(1)
                    .date(date)
                    .booking(savedBooking.getId())
                    .status("held")
                    .expiresAt(savedBooking.getExpiresAt())
                    .createdAt(Instant.now())
                    .build();
            try {
                suiteNightRepository.save(suiteNight);
            } catch (Exception ignored) {
            }
        }

        return toDTO(savedBooking);
    }

    public BookingDTO modifyBooking(String bookingId, String userId, BookingRequest request) {
        Booking booking = getBookingEntityById(bookingId);
        if (!booking.getUser().equals(userId)) {
            throw new IllegalArgumentException("No tienes permiso para modificar esta reserva");
        }

        if ("cancelled".equalsIgnoreCase(booking.getStatus()) || "completed".equalsIgnoreCase(booking.getStatus())) {
            throw new IllegalArgumentException("No se puede modificar una reserva cancelada o completada");
        }

        Suite suite = suiteRepository.findById(booking.getSuite())
                .orElseThrow(() -> new IllegalArgumentException("Suite no encontrada"));

        long nights = ChronoUnit.DAYS.between(request.getCheckIn(), request.getCheckOut());
        if (nights <= 0) {
            throw new IllegalArgumentException("La fecha de check-out debe ser posterior al check-in");
        }

        BigDecimal totalRoomPrice = suite.getBasePrice().multiply(BigDecimal.valueOf(nights));
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
        for (int i = 0; i < nights; i++) {
            LocalDate date = request.getCheckIn().plusDays(i);
            SuiteNight suiteNight = SuiteNight.builder()
                    .suite(suite.getId())
                    .branch(suite.getBranch())
                    .unitSlot(1)
                    .date(date)
                    .booking(booking.getId())
                    .status("blocked")
                    .createdAt(Instant.now())
                    .build();
            try {
                suiteNightRepository.save(suiteNight);
            } catch (Exception ignored) {
            }
        }

        return toDTO(bookingRepository.save(booking));
    }

    public List<BookingDTO> getUserBookings(String userId) {
        return bookingRepository.findByUserOrderByCreatedAtDesc(userId).stream()
                .map(this::toDTO)
                .toList();
    }

    public List<BookingDTO> getUpcomingBookings(String userId) {
        LocalDate today = LocalDate.now();
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

        booking.setStatus("cancelled");
        if (reason != null && !reason.isEmpty()) {
            booking.setCancellationReason(reason);
        }
        if (booking.getPaidAmount() != null && booking.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
            booking.setRefundedAmount(booking.getPaidAmount());
            booking.setPaymentStatus("refunded");
            booking.setBalanceDue(BigDecimal.ZERO);
        }
        booking.setUpdatedAt(Instant.now());
        suiteNightRepository.deleteByBooking(bookingId);

        return toDTO(bookingRepository.save(booking));
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

        Suite suite = suiteRepository.findById(booking.getSuite()).orElse(null);
        BigDecimal pricePerNight = booking.getPricePerNight() != null ? booking.getPricePerNight() : (suite != null ? suite.getBasePrice() : BigDecimal.ZERO);
        int nights = booking.getNights() != null ? booking.getNights() : 1;
        BigDecimal roomTotal = pricePerNight.multiply(BigDecimal.valueOf(nights));

        BigDecimal expTotal = BigDecimal.ZERO;
        for (Booking.SelectedExperience exp : current) {
            if (exp.getPrice() != null && exp.getQuantity() != null) {
                expTotal = expTotal.add(exp.getPrice().multiply(BigDecimal.valueOf(exp.getQuantity())));
            }
        }
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

        Suite suite = suiteRepository.findById(booking.getSuite()).orElse(null);
        BigDecimal pricePerNight = booking.getPricePerNight() != null ? booking.getPricePerNight() : (suite != null ? suite.getBasePrice() : BigDecimal.ZERO);
        int nights = booking.getNights() != null ? booking.getNights() : 1;
        BigDecimal roomTotal = pricePerNight.multiply(BigDecimal.valueOf(nights));

        BigDecimal expTotal = BigDecimal.ZERO;
        if (current != null) {
            for (Booking.SelectedExperience exp : current) {
                if (exp.getPrice() != null && exp.getQuantity() != null) {
                    expTotal = expTotal.add(exp.getPrice().multiply(BigDecimal.valueOf(exp.getQuantity())));
                }
            }
        }
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
        BigDecimal subtotal = pricePerNight.multiply(BigDecimal.valueOf(nights));

        String status = booking.getStatus() != null ? booking.getStatus() : "pending";
        boolean isCancelable = !"cancelled".equalsIgnoreCase(status) && !"completed".equalsIgnoreCase(status) && !"expired".equalsIgnoreCase(status);
        boolean isModifiable = isCancelable && (booking.getCheckIn() == null || !booking.getCheckIn().isBefore(LocalDate.now()));

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
                .cancellationReason(booking.getCancellationReason())
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
}
