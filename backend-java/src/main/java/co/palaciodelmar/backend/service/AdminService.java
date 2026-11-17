package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.model.Booking;
import co.palaciodelmar.backend.model.Promotion;
import co.palaciodelmar.backend.repository.*;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class AdminService {

    private final BookingRepository bookingRepository;
    private final SuiteRepository suiteRepository;
    private final UserRepository userRepository;
    private final PromotionRepository promotionRepository;
    private final SuiteNightRepository suiteNightRepository;
    private final BookingService bookingService;
    private final PropertyRepository propertyRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private RedisLockService redisLockService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private AvailabilityCacheEvictor availabilityCacheEvictor;

    @org.springframework.beans.factory.annotation.Autowired
    public AdminService(
            BookingRepository bookingRepository,
            SuiteRepository suiteRepository,
            UserRepository userRepository,
            PromotionRepository promotionRepository,
            SuiteNightRepository suiteNightRepository,
            BookingService bookingService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) PropertyRepository propertyRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) PaymentRepository paymentRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) PaymentGateway paymentGateway
    ) {
        this.bookingRepository = bookingRepository;
        this.suiteRepository = suiteRepository;
        this.userRepository = userRepository;
        this.promotionRepository = promotionRepository;
        this.suiteNightRepository = suiteNightRepository;
        this.bookingService = bookingService;
        this.propertyRepository = propertyRepository;
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
    }

    @Data
    @Builder
    public static class AdminDashboardStats {
        private long totalSuites;
        private long totalBookings;
        private long totalUsers;
        private BigDecimal totalRevenue;
        private long activeBookings;
        private double averageOccupancy;
    }

    public org.springframework.data.domain.Page<Booking> getAllBookings(org.springframework.data.domain.Pageable pageable) {
        return getAllBookings(null, null, pageable);
    }

    public org.springframework.data.domain.Page<Booking> getAllBookings(String status, String search, org.springframework.data.domain.Pageable pageable) {
        if (status != null && !status.isBlank() && !status.equalsIgnoreCase("all") && (search == null || search.isBlank())) {
            return bookingRepository.findByStatusIgnoreCase(status, pageable);
        }

        if ((status != null && !status.isBlank() && !status.equalsIgnoreCase("all")) || (search != null && !search.isBlank())) {
            List<Booking> all = bookingRepository.findAll();
            List<Booking> filtered = all.stream()
                    .filter(b -> {
                        if (status != null && !status.isBlank() && !status.equalsIgnoreCase("all")) {
                            if (b.getStatus() == null || !b.getStatus().equalsIgnoreCase(status)) return false;
                        }
                        if (search != null && !search.isBlank()) {
                            String q = search.toLowerCase();
                            boolean codeMatch = b.getBookingCode() != null && b.getBookingCode().toLowerCase().contains(q);
                            boolean guestMatch = b.getGuestDetails() != null && (
                                    (b.getGuestDetails().getFullName() != null && b.getGuestDetails().getFullName().toLowerCase().contains(q)) ||
                                    (b.getGuestDetails().getEmail() != null && b.getGuestDetails().getEmail().toLowerCase().contains(q))
                            );
                            if (!codeMatch && !guestMatch) return false;
                        }
                        return true;
                    })
                    .toList();
            int start = (int) pageable.getOffset();
            int end = Math.min(start + pageable.getPageSize(), filtered.size());
            List<Booking> subList = start <= end && start < filtered.size() ? filtered.subList(start, end) : java.util.Collections.emptyList();
            return new org.springframework.data.domain.PageImpl<>(subList, pageable, filtered.size());
        }
        return bookingRepository.findAll(pageable);
    }

    public Booking updateBookingStatus(String bookingId, String newStatus) {
        Booking booking = findBookingByIdOrCode(bookingId);

        if (newStatus == null || newStatus.isBlank()) {
            throw new IllegalArgumentException("El nuevo estado no puede ser nulo o vacío");
        }

        co.palaciodelmar.backend.model.BookingStatus targetStatus;
        try {
            targetStatus = co.palaciodelmar.backend.model.BookingStatus.valueOf(newStatus.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Estado no válido: " + newStatus);
        }

        if (targetStatus == co.palaciodelmar.backend.model.BookingStatus.CHECKED_OUT) {
            String effectiveBookingId = booking.getId() != null ? booking.getId() : bookingId;
            bookingService.processCheckOut(effectiveBookingId, "ADMIN", "Check-out administrativo", false);
            if (availabilityCacheEvictor != null) {
                availabilityCacheEvictor.evictAvailability();
            }
            return findBookingByIdOrCode(bookingId);
        }

        return executeWithSuiteLock(booking.getSuite(), () -> {
            Booking fresh = findBookingByIdOrCode(bookingId);
            BigDecimal paid = fresh.getPaidAmount() != null ? fresh.getPaidAmount() : BigDecimal.ZERO;
            BigDecimal refunded = fresh.getRefundedAmount() != null ? fresh.getRefundedAmount() : BigDecimal.ZERO;
            BigDecimal netPaid = paid.subtract(refunded).max(BigDecimal.ZERO);
            String idemKey = null;

            if (targetStatus == co.palaciodelmar.backend.model.BookingStatus.CANCELLED
                    || targetStatus == co.palaciodelmar.backend.model.BookingStatus.EXPIRED) {
                fresh.setBalanceDue(BigDecimal.ZERO);
                if (netPaid.compareTo(BigDecimal.ZERO) > 0) {
                    idemKey = fresh.getPendingRefundIdempotencyKey();
                    if (idemKey == null || idemKey.isBlank()) {
                        String prefix = targetStatus == co.palaciodelmar.backend.model.BookingStatus.EXPIRED
                                ? "ref_exp_"
                                : "ref_admin_canc_";
                        idemKey = prefix + fresh.getId() + "_" + java.util.UUID.randomUUID().toString().substring(0, 8);
                    }
                    fresh.setPendingRefundIdempotencyKey(idemKey);
                    fresh.setPendingRefundAmount(netPaid);
                    fresh.setPaymentStatus("requires_refund");
                } else if (paid.compareTo(BigDecimal.ZERO) > 0) {
                    fresh.setPaymentStatus(refunded.compareTo(paid) >= 0 ? "refunded" : "partial");
                }
            }

            bookingService.changeBookingStatus(fresh, targetStatus, "ADMIN", "Cambio administrativo de estado", false);
            Booking saved = bookingRepository.save(fresh);
            bookingService.publishBookingStatusEvent(saved, targetStatus);

            if (targetStatus == co.palaciodelmar.backend.model.BookingStatus.CANCELLED
                    || targetStatus == co.palaciodelmar.backend.model.BookingStatus.EXPIRED
                    || targetStatus == co.palaciodelmar.backend.model.BookingStatus.NO_SHOW) {
                String effectiveBookingId = saved.getId() != null ? saved.getId() : bookingId;
                suiteNightRepository.deleteByBooking(effectiveBookingId);
                if (redisLockService != null) {
                    redisLockService.releaseSuiteHold(
                            saved.getSuite(),
                            saved.getUnitSlot(),
                            saved.getCheckIn(),
                            saved.getCheckOut(),
                            effectiveBookingId
                    );
                }
            }

            if ((targetStatus == co.palaciodelmar.backend.model.BookingStatus.CANCELLED
                    || targetStatus == co.palaciodelmar.backend.model.BookingStatus.EXPIRED)
                    && idemKey != null && netPaid.compareTo(BigDecimal.ZERO) > 0) {
                try {
                    boolean refundedOk = bookingService.executeSequentialRefund(saved, netPaid, idemKey);
                    if (!refundedOk && (saved.getPendingRefundAmount() == null || saved.getPendingRefundAmount().compareTo(BigDecimal.ZERO) <= 0)) {
                        saved.setPaymentStatus("requires_refund");
                        saved.setPendingRefundAmount(netPaid);
                        saved.setPendingRefundIdempotencyKey(idemKey);
                        saved = bookingRepository.save(saved);
                    }
                } catch (RuntimeException ex) {
                    log.warn("Fallo ejecutando reembolso automático para reserva {}: {}", saved.getId(), ex.getMessage());
                    saved.setPaymentStatus("requires_refund");
                    if (saved.getPendingRefundAmount() == null || saved.getPendingRefundAmount().compareTo(BigDecimal.ZERO) <= 0) {
                        saved.setPendingRefundAmount(netPaid);
                        saved.setPendingRefundIdempotencyKey(idemKey);
                    }
                    try {
                        saved = bookingRepository.save(saved);
                    } catch (Exception saveEx) {
                        log.error("Fallo persistiendo requires_refund para reserva {}: {}", saved.getId(), saveEx.getMessage());
                    }
                }
            }

            if (availabilityCacheEvictor != null) {
                availabilityCacheEvictor.evictAvailability();
            }
            return saved;
        });
    }

    private Booking findBookingByIdOrCode(String bookingId) {
        return bookingRepository.findById(bookingId)
                .or(() -> bookingRepository.findByBookingCode(bookingId))
                .orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada: " + bookingId));
    }

    private <T> T executeWithSuiteLock(String suiteId, java.util.function.Supplier<T> action) {
        if (redisLockService == null || suiteId == null || suiteId.isBlank()) {
            return action.get();
        }
        return redisLockService.executeWithStrictDistributedLock(
                "suite:" + suiteId,
                java.time.Duration.ofSeconds(10),
                java.time.Duration.ofSeconds(4),
                action
        );
    }

    public List<Promotion> getAllPromotions() {
        return promotionRepository.findAll();
    }

    public Promotion createPromotion(Promotion promotion) {
        promotion.setCreatedAt(Instant.now());
        promotion.setUpdatedAt(Instant.now());
        Promotion saved = promotionRepository.save(promotion);
        if (availabilityCacheEvictor != null) {
            availabilityCacheEvictor.evictAvailability();
        }
        return saved;
    }

    public void deletePromotion(String promotionId) {
        promotionRepository.deleteById(promotionId);
        if (availabilityCacheEvictor != null) {
            availabilityCacheEvictor.evictAvailability();
        }
    }

    /** Estados "vivos": el flujo real es pending → confirmed → checked_in → checked_out (no existe "completed"). */
    public static boolean isActiveStatus(String status) {
        return "pending".equalsIgnoreCase(status) || "confirmed".equalsIgnoreCase(status) || "checked_in".equalsIgnoreCase(status);
    }

    /** Ingreso real de una reserva: lo cobrado menos lo devuelto (incluye retenciones por cancelación). */
    public static BigDecimal netCollected(Booking b) {
        BigDecimal paid = b.getPaidAmount() != null ? b.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal refunded = b.getRefundedAmount() != null ? b.getRefundedAmount() : BigDecimal.ZERO;
        return paid.subtract(refunded).max(BigDecimal.ZERO);
    }

    public AdminDashboardStats getDashboardStats() {
        long totalSuites = suiteRepository.count();
        long totalBookings = bookingRepository.count();
        long totalUsers = userRepository.count();

        LocalDate today = LocalDate.now(ZoneId.of("America/Bogota"));
        List<Booking> allBookings = bookingRepository.findAll();
        List<co.palaciodelmar.backend.model.Suite> allSuites = suiteRepository.findAll();

        long activeBookings = allBookings.stream()
                .filter(b -> isActiveStatus(b.getStatus()))
                .count();

        // Filtrar solo suites de propiedades aprobadas (o del hotel principal)
        java.util.Set<String> approvedPropIds = (propertyRepository != null) ? propertyRepository.findAll().stream()
                .filter(p -> "APPROVED".equalsIgnoreCase(p.getStatus()) && !Boolean.FALSE.equals(p.getActive()))
                .map(co.palaciodelmar.backend.model.Property::getId)
                .collect(java.util.stream.Collectors.toSet()) : java.util.Set.of();

        List<co.palaciodelmar.backend.model.Suite> approvedActiveSuites = allSuites.stream()
                .filter(s -> !Boolean.FALSE.equals(s.getActive()))
                .filter(s -> s.getPropertyId() == null || approvedPropIds.contains(s.getPropertyId()))
                .toList();

        // Inventario total de unidades vendibles (considerando totalUnits de cada suite activa aprobada)
        long totalRentableUnits = approvedActiveSuites.stream()
                .mapToLong(s -> (s.getTotalUnits() != null && s.getTotalUnits() > 0) ? s.getTotalUnits() : 1)
                .sum();

        java.util.Set<String> approvedActiveSuiteIds = approvedActiveSuites.stream()
                .map(co.palaciodelmar.backend.model.Suite::getId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        // Unidades ocupadas hoy: conteo de reservas activas alojadas hoy para suites aprobadas
        long occupiedUnitsToday = allBookings.stream()
                .filter(b -> isActiveStatus(b.getStatus()))
                .filter(b -> b.getSuite() != null && approvedActiveSuiteIds.contains(b.getSuite()))
                .filter(b -> b.getCheckIn() != null && b.getCheckOut() != null)
                .filter(b -> !today.isBefore(b.getCheckIn()) && today.isBefore(b.getCheckOut()))
                .count();

        double averageOccupancy = totalRentableUnits > 0
                ? Math.min(100.0, Math.round(((double) occupiedUnitsToday / totalRentableUnits) * 100.0 * 10.0) / 10.0)
                : 0.0;

        BigDecimal totalRevenue = allBookings.stream()
                .map(AdminService::netCollected)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return AdminDashboardStats.builder()
                .totalSuites(totalSuites)
                .totalBookings(totalBookings)
                .totalUsers(totalUsers)
                .activeBookings(activeBookings)
                .averageOccupancy(averageOccupancy)
                .totalRevenue(totalRevenue)
                .build();
    }

    public List<co.palaciodelmar.backend.model.Payment> getPendingRefunds() {
        List<co.palaciodelmar.backend.model.Payment> result = new java.util.ArrayList<>();
        java.util.Set<String> coveredBookingIds = new java.util.HashSet<>();

        if (paymentRepository != null) {
            List<co.palaciodelmar.backend.model.Payment> paymentList = paymentRepository.findByStatus("requires_refund");
            if (paymentList != null) {
                for (co.palaciodelmar.backend.model.Payment p : paymentList) {
                    if (p.getBooking() != null && !p.getBooking().isBlank()) {
                        coveredBookingIds.add(p.getBooking());
                    }
                    result.add(p);
                }
            }
        }

        if (bookingRepository != null) {
            List<Booking> bookingsWithPending = bookingRepository.findByPendingRefundIdempotencyKeyIsNotNull();
            if (bookingsWithPending != null) {
                for (Booking b : bookingsWithPending) {
                    if (b == null || b.getId() == null || coveredBookingIds.contains(b.getId())) {
                        continue;
                    }
                    BigDecimal pendingAmt = b.getPendingRefundAmount();
                    if (pendingAmt != null && pendingAmt.compareTo(BigDecimal.ZERO) > 0) {
                        result.add(co.palaciodelmar.backend.model.Payment.builder()
                                .id("booking:" + b.getId())
                                .booking(b.getId())
                                .user(b.getUser())
                                .amount(pendingAmt)
                                .currency("COP")
                                .refundedAmount(b.getRefundedAmount() != null ? b.getRefundedAmount() : BigDecimal.ZERO)
                                .status("requires_refund")
                                .provider("booking_pending_refund")
                                .notes("Reembolso pendiente en reserva "
                                        + (b.getBookingCode() != null ? b.getBookingCode() : b.getId())
                                        + " (clave: " + b.getPendingRefundIdempotencyKey() + ")")
                                .createdAt(b.getUpdatedAt() != null ? b.getUpdatedAt() : Instant.now())
                                .updatedAt(b.getUpdatedAt() != null ? b.getUpdatedAt() : Instant.now())
                                .build());
                    }
                }
            }
        }

        return result;
    }

    public co.palaciodelmar.backend.model.Payment resolveRefund(String paymentId, String action, String notes) {
        if (paymentId != null && paymentId.startsWith("booking:")) {
            if (bookingRepository == null) {
                throw new IllegalStateException("BookingRepository no configurado");
            }
            String targetBookingId = paymentId.substring("booking:".length());
            Booking b = findBookingByIdOrCode(targetBookingId);
            BigDecimal pendingAmt = b.getPendingRefundAmount() != null ? b.getPendingRefundAmount() : BigDecimal.ZERO;
            String pendingKey = b.getPendingRefundIdempotencyKey();
            if (pendingAmt.compareTo(BigDecimal.ZERO) <= 0 && (pendingKey == null || pendingKey.isBlank()) && !"requires_refund".equalsIgnoreCase(b.getPaymentStatus())) {
                throw new IllegalStateException("La reserva no está pendiente de reembolso");
            }

            if ("mark_refunded".equalsIgnoreCase(action) || "refund".equalsIgnoreCase(action) || "complete".equalsIgnoreCase(action)) {
                if ("refund".equalsIgnoreCase(action)) {
                    if (bookingService == null || paymentGateway == null) {
                        throw new IllegalStateException("Pasarela o servicio de reservas no configurado para ejecutar reembolso automático. Usa 'mark_refunded' para conciliar manualmente.");
                    }
                    if (pendingAmt.compareTo(BigDecimal.ZERO) > 0) {
                        String idemKey = (pendingKey != null && !pendingKey.isBlank()) ? pendingKey : ("ref_adm_" + b.getId());
                        boolean ok = bookingService.executeSequentialRefund(b, pendingAmt, idemKey);
                        if (!ok || (b.getPendingRefundAmount() != null && b.getPendingRefundAmount().compareTo(BigDecimal.ZERO) > 0)) {
                            throw new IllegalStateException("Fallo en pasarela al procesar reembolso pendiente de la reserva");
                        }
                        bookingRepository.save(b);
                    }
                } else {
                    BigDecimal paid = b.getPaidAmount() != null ? b.getPaidAmount() : BigDecimal.ZERO;
                    BigDecimal prevRefunded = b.getRefundedAmount() != null ? b.getRefundedAmount() : BigDecimal.ZERO;
                    BigDecimal newRefunded = prevRefunded.add(pendingAmt);
                    if (paid.compareTo(BigDecimal.ZERO) > 0 && newRefunded.compareTo(paid) > 0) {
                        newRefunded = paid;
                    }
                    b.setRefundedAmount(newRefunded);
                    if (pendingKey != null && pendingKey.startsWith("ref_dep_") && pendingAmt.compareTo(BigDecimal.ZERO) > 0) {
                        b.setGuaranteeRefunded(true);
                    }
                    b.setPaymentStatus(resolveBookingPaymentStatusAfterRefund(b, paid, newRefunded));
                    b.setPendingRefundAmount(BigDecimal.ZERO);
                    b.setPendingRefundIdempotencyKey(null);
                    b.setNotes((b.getNotes() != null ? b.getNotes() + " | " : "") +
                            "Reembolso conciliado por ADMIN (" + action + "): " + (notes != null && !notes.isBlank() ? notes : "Aprobado manualmente"));
                    b.setUpdatedAt(Instant.now());
                    bookingRepository.save(b);
                }
                return co.palaciodelmar.backend.model.Payment.builder()
                        .id(paymentId)
                        .booking(b.getId())
                        .user(b.getUser())
                        .amount(pendingAmt)
                        .currency("COP")
                        .refundedAmount(b.getRefundedAmount() != null ? b.getRefundedAmount() : pendingAmt)
                        .status("refunded")
                        .provider("booking_pending_refund")
                        .notes(b.getNotes())
                        .updatedAt(Instant.now())
                        .build();
            } else if ("dismiss".equalsIgnoreCase(action) || "reject".equalsIgnoreCase(action)) {
                BigDecimal paid = b.getPaidAmount() != null ? b.getPaidAmount() : BigDecimal.ZERO;
                BigDecimal refunded = b.getRefundedAmount() != null ? b.getRefundedAmount() : BigDecimal.ZERO;
                b.setPaymentStatus(resolveBookingPaymentStatusAfterRefund(b, paid, refunded));
                b.setPendingRefundAmount(BigDecimal.ZERO);
                b.setPendingRefundIdempotencyKey(null);
                b.setNotes((b.getNotes() != null ? b.getNotes() + " | " : "") +
                        "Reembolso descartado por ADMIN: " + (notes != null && !notes.isBlank() ? notes : "No procede reembolso"));
                b.setUpdatedAt(Instant.now());
                bookingRepository.save(b);
                return co.palaciodelmar.backend.model.Payment.builder()
                        .id(paymentId)
                        .booking(b.getId())
                        .user(b.getUser())
                        .amount(pendingAmt)
                        .currency("COP")
                        .refundedAmount(refunded)
                        .status("failed")
                        .provider("booking_pending_refund")
                        .notes(b.getNotes())
                        .updatedAt(Instant.now())
                        .build();
            } else {
                throw new IllegalArgumentException("Acción no válida. Opciones permitidas: 'mark_refunded', 'complete', 'refund', 'dismiss'");
            }
        }

        if (paymentRepository == null) {
            throw new IllegalStateException("PaymentRepository no configurado");
        }
        co.palaciodelmar.backend.model.Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Pago no encontrado: " + paymentId));

        if (!"requires_refund".equalsIgnoreCase(payment.getStatus())) {
            throw new IllegalStateException("El pago no está pendiente de reembolso (estado: " + payment.getStatus() + ")");
        }

        if ("mark_refunded".equalsIgnoreCase(action) || "refund".equalsIgnoreCase(action) || "complete".equalsIgnoreCase(action)) {
            String refundDetails = "";
            BigDecimal totalAmt = payment.getAmount() != null ? payment.getAmount() : BigDecimal.ZERO;
            BigDecimal currentRefunded = payment.getRefundedAmount() != null ? payment.getRefundedAmount().max(BigDecimal.ZERO) : BigDecimal.ZERO;
            BigDecimal refundable = totalAmt.subtract(currentRefunded).max(BigDecimal.ZERO);

            Booking linkedBooking = (payment.getBooking() != null && bookingRepository != null)
                    ? bookingRepository.findById(payment.getBooking()).orElse(null)
                    : null;
            BigDecimal targetDelta = refundable;
            if (linkedBooking != null && linkedBooking.getPendingRefundAmount() != null && linkedBooking.getPendingRefundAmount().compareTo(BigDecimal.ZERO) > 0) {
                targetDelta = refundable.min(linkedBooking.getPendingRefundAmount());
            }

            if ("refund".equalsIgnoreCase(action)) {
                if (paymentGateway == null) {
                    throw new IllegalStateException("Pasarela de pago no configurada para ejecutar reembolso automático. Usa 'mark_refunded' para conciliar manualmente.");
                }
                if (payment.getTransactionId() == null || payment.getTransactionId().isBlank()) {
                    throw new IllegalStateException("No se puede procesar reembolso en pasarela sin ID de transacción. Usa 'mark_refunded' para conciliar manualmente.");
                }
            }
            String gatewayRefundId = null;
            if (paymentGateway != null && payment.getTransactionId() != null && !payment.getTransactionId().isBlank() && targetDelta.compareTo(BigDecimal.ZERO) > 0) {
                co.palaciodelmar.backend.service.PaymentGateway.PaymentRefundResponse res =
                        paymentGateway.refund(payment.getTransactionId(), targetDelta);
                if (res == null || !res.success()) {
                    String errorMsg = (res != null && res.errorMessage() != null) ? res.errorMessage() : "Transacción rechazada por la pasarela";
                    throw new IllegalStateException("Fallo en pasarela al procesar reembolso: " + errorMsg);
                }
                if (payment.getProcessedRefundIds() == null) {
                    payment.setProcessedRefundIds(new java.util.ArrayList<>());
                }
                if (res.refundId() != null && !payment.getProcessedRefundIds().contains(res.refundId())) {
                    payment.getProcessedRefundIds().add(res.refundId());
                }
                gatewayRefundId = res.refundId();
                refundDetails = " (id=" + res.refundId() + ")";
            }
            BigDecimal newPaymentRefunded = currentRefunded.add(targetDelta);
            payment.setRefundedAmount(newPaymentRefunded);
            payment.setStatus(newPaymentRefunded.compareTo(totalAmt) >= 0 ? "refunded" : "partially_refunded");
            payment.setNotes((payment.getNotes() != null ? payment.getNotes() + " | " : "") +
                    "Reembolso procesado por ADMIN" + refundDetails + ": " + (notes != null && !notes.isBlank() ? notes : "Aprobado manualmente"));

            if (linkedBooking != null) {
                if (gatewayRefundId != null && !gatewayRefundId.isBlank()) {
                    if (linkedBooking.getProcessedRefundIds() == null) {
                        linkedBooking.setProcessedRefundIds(new java.util.ArrayList<>());
                    }
                    if (!linkedBooking.getProcessedRefundIds().contains(gatewayRefundId)) {
                        linkedBooking.getProcessedRefundIds().add(gatewayRefundId);
                    }
                }
                BigDecimal paid = linkedBooking.getPaidAmount() != null ? linkedBooking.getPaidAmount() : BigDecimal.ZERO;
                BigDecimal prevBookRefunded = linkedBooking.getRefundedAmount() != null ? linkedBooking.getRefundedAmount() : BigDecimal.ZERO;
                BigDecimal newBookRefunded = prevBookRefunded.add(targetDelta);
                if (paid.compareTo(BigDecimal.ZERO) > 0 && newBookRefunded.compareTo(paid) > 0) {
                    newBookRefunded = paid;
                }
                linkedBooking.setRefundedAmount(newBookRefunded);
                reconcileLinkedBookingPendingRefund(linkedBooking, payment.getId(), targetDelta, paid, newBookRefunded);
                linkedBooking.setUpdatedAt(Instant.now());
                bookingRepository.save(linkedBooking);
            }
        } else if ("dismiss".equalsIgnoreCase(action) || "reject".equalsIgnoreCase(action)) {
            payment.setStatus("failed");
            payment.setNotes((payment.getNotes() != null ? payment.getNotes() + " | " : "") +
                    "Reembolso descartado por ADMIN: " + (notes != null && !notes.isBlank() ? notes : "No procede reembolso"));
            if (payment.getBooking() != null && bookingRepository != null) {
                bookingRepository.findById(payment.getBooking()).ifPresent(b -> {
                    BigDecimal paid = b.getPaidAmount() != null ? b.getPaidAmount() : BigDecimal.ZERO;
                    BigDecimal refunded = b.getRefundedAmount() != null ? b.getRefundedAmount() : BigDecimal.ZERO;
                    reconcileLinkedBookingPendingRefund(b, payment.getId(), BigDecimal.ZERO, paid, refunded);
                    b.setUpdatedAt(Instant.now());
                    bookingRepository.save(b);
                });
            }
        } else {
            throw new IllegalArgumentException("Acción no válida. Opciones permitidas: 'mark_refunded', 'complete', 'refund', 'dismiss'");
        }

        payment.setUpdatedAt(Instant.now());
        return paymentRepository.save(payment);
    }

    private void reconcileLinkedBookingPendingRefund(
            Booking booking, String resolvedPaymentId, BigDecimal refundedDelta, BigDecimal paid, BigDecimal totalRefunded) {
        BigDecimal prevPending = booking.getPendingRefundAmount() != null ? booking.getPendingRefundAmount() : BigDecimal.ZERO;
        BigDecimal delta = refundedDelta != null ? refundedDelta.max(BigDecimal.ZERO) : BigDecimal.ZERO;
        BigDecimal remainingPending = prevPending.subtract(delta).max(BigDecimal.ZERO);
        booking.setPendingRefundAmount(remainingPending);
        if (remainingPending.compareTo(BigDecimal.ZERO) == 0) {
            if (booking.getPendingRefundIdempotencyKey() != null
                    && booking.getPendingRefundIdempotencyKey().startsWith("ref_dep_")
                    && delta.compareTo(BigDecimal.ZERO) > 0) {
                booking.setGuaranteeRefunded(true);
            }
            booking.setPendingRefundIdempotencyKey(null);
        }
        boolean otherPendingPayments = booking.getId() != null && paymentRepository.findByBooking(booking.getId()).stream()
                .anyMatch(p -> !Objects.equals(p.getId(), resolvedPaymentId) && "requires_refund".equalsIgnoreCase(p.getStatus()));
        if (remainingPending.compareTo(BigDecimal.ZERO) > 0 || otherPendingPayments) {
            booking.setPaymentStatus("requires_refund");
        } else {
            booking.setPaymentStatus(resolveBookingPaymentStatusAfterRefund(booking, paid, totalRefunded));
        }
    }

    private String resolveBookingPaymentStatusAfterRefund(Booking b, BigDecimal paid, BigDecimal refunded) {
        BigDecimal safePaid = paid != null ? paid : BigDecimal.ZERO;
        BigDecimal safeRefunded = refunded != null ? refunded : BigDecimal.ZERO;
        if ("cancelled".equalsIgnoreCase(b.getStatus())) {
            if (safePaid.compareTo(BigDecimal.ZERO) <= 0) {
                return "pending";
            }
            return safeRefunded.compareTo(safePaid) >= 0 ? "refunded" : (safeRefunded.compareTo(BigDecimal.ZERO) > 0 ? "partial" : "paid");
        }
        BigDecimal netPaid = safePaid.subtract(safeRefunded).max(BigDecimal.ZERO);
        BigDecimal total = b.getTotalPrice() != null ? b.resolveEffectiveTotalPrice() : BigDecimal.ZERO;
        b.setBalanceDue(total.subtract(netPaid).max(BigDecimal.ZERO));
        if (total.compareTo(BigDecimal.ZERO) > 0 && netPaid.compareTo(total) >= 0) {
            return "paid";
        }
        return netPaid.compareTo(BigDecimal.ZERO) > 0 ? "partial" : "pending";
    }

    public byte[] exportBookingsCsv() {
        List<Booking> bookings = bookingRepository.findAll(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
        StringBuilder csv = new StringBuilder();
        csv.append("\uFEFF"); // UTF-8 BOM
        csv.append("ID,Código Reserva,Huésped,Email,Teléfono,Check-In,Check-Out,Noches,Huéspedes,Total COP,Monto Pagado COP,Saldo Pendiente COP,Estado Reserva,Estado Pago,Fecha Creación\n");
        for (Booking b : bookings) {
            String id = escapeCsv(b.getId());
            String code = escapeCsv(b.getBookingCode());
            String name = escapeCsv(b.getGuestDetails() != null ? b.getGuestDetails().getFullName() : "");
            String email = escapeCsv(b.getGuestDetails() != null ? b.getGuestDetails().getEmail() : "");
            String phone = escapeCsv(b.getGuestDetails() != null ? b.getGuestDetails().getPhone() : "");
            String checkIn = b.getCheckIn() != null ? b.getCheckIn().toString() : "";
            String checkOut = b.getCheckOut() != null ? b.getCheckOut().toString() : "";
            int nights = b.getNights() != null ? b.getNights() : 0;
            int guests = b.getGuests() != null ? b.getGuests() : 0;
            String total = b.getTotalPrice() != null ? b.getTotalPrice().toPlainString() : "0";
            String paid = b.getPaidAmount() != null ? b.getPaidAmount().toPlainString() : "0";
            String balance = b.getBalanceDue() != null ? b.getBalanceDue().toPlainString() : "0";
            String status = escapeCsv(b.getStatus() != null ? b.getStatus() : "");
            String paymentStatus = escapeCsv(b.getPaymentStatus() != null ? b.getPaymentStatus() : "");
            String createdAt = b.getCreatedAt() != null ? b.getCreatedAt().toString() : "";

            csv.append(String.join(",",
                    id, code, name, email, phone,
                    checkIn, checkOut, String.valueOf(nights), String.valueOf(guests),
                    total, paid, balance,
                    status, paymentStatus, createdAt
            )).append("\n");
        }
        return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        String sanitized = value;
        String trimmed = sanitized.stripLeading();
        if (!sanitized.isEmpty()) {
            char first = sanitized.charAt(0);
            char firstNonWs = trimmed.isEmpty() ? first : trimmed.charAt(0);
            if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r'
                    || firstNonWs == '=' || firstNonWs == '+' || firstNonWs == '-' || firstNonWs == '@') {
                sanitized = "'" + sanitized;
            }
        }
        if (sanitized.contains(",") || sanitized.contains("\"") || sanitized.contains("\n") || sanitized.contains("\r")) {
            return "\"" + sanitized.replace("\"", "\"\"") + "\"";
        }
        return sanitized;
    }
}
