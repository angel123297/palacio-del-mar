package co.palaciodelmar.backend.controller;

import co.palaciodelmar.backend.dto.ApiResponse;
import co.palaciodelmar.backend.dto.BankAccountDTO;
import co.palaciodelmar.backend.dto.BookingDTO;
import co.palaciodelmar.backend.dto.PromotionDTO;
import co.palaciodelmar.backend.dto.PromotionRequest;
import co.palaciodelmar.backend.model.Booking;
import co.palaciodelmar.backend.model.Promotion;
import co.palaciodelmar.backend.model.Property;
import co.palaciodelmar.backend.model.Suite;
import co.palaciodelmar.backend.model.User;
import co.palaciodelmar.backend.repository.BookingRepository;
import co.palaciodelmar.backend.repository.PromotionRepository;
import co.palaciodelmar.backend.repository.PropertyRepository;
import co.palaciodelmar.backend.repository.SuiteRepository;
import co.palaciodelmar.backend.repository.UserRepository;
import co.palaciodelmar.backend.security.UserPrincipal;
import co.palaciodelmar.backend.service.BookingService;
import jakarta.validation.Valid;
import lombok.Builder;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/host")
public class HostController {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final BookingRepository bookingRepository;
    private final SuiteRepository suiteRepository;
    private final BookingService bookingService;
    private final PromotionRepository promotionRepository;

    @Autowired(required = false)
    private PropertyRepository propertyRepository;

    @Autowired(required = false)
    private UserRepository userRepository;

    @Autowired
    public HostController(
            BookingRepository bookingRepository,
            SuiteRepository suiteRepository,
            BookingService bookingService,
            PromotionRepository promotionRepository,
            @Autowired(required = false) PropertyRepository propertyRepository
    ) {
        this.bookingRepository = bookingRepository;
        this.suiteRepository = suiteRepository;
        this.bookingService = bookingService;
        this.promotionRepository = promotionRepository;
        this.propertyRepository = propertyRepository;
    }

    public HostController(
            BookingRepository bookingRepository,
            SuiteRepository suiteRepository,
            BookingService bookingService,
            PromotionRepository promotionRepository
    ) {
        this(bookingRepository, suiteRepository, bookingService, promotionRepository, null);
    }

    @Data
    @Builder
    public static class HostDashboardStats {
        private long totalSuites;
        private long totalBookings;
        private BigDecimal totalRevenue;
        private long pendingCheckins;
        private long activeBookings;
    }

    private List<String> getOwnedPropertyIds(User user) {
        if (user == null || user.getId() == null || propertyRepository == null) {
            return List.of();
        }
        List<Property> props = propertyRepository.findByOwnerId(user.getId());
        if (props == null || props.isEmpty()) {
            return List.of();
        }
        return props.stream().map(Property::getId).toList();
    }

    private List<String> getEffectiveBranches(User user) {
        if (user == null || user.getBranches() == null) {
            return List.of();
        }
        return user.getBranches();
    }

    private boolean hasAccessToBooking(User user, Booking booking) {
        if (user == null || booking == null) {
            return false;
        }
        if ("ADMIN".equalsIgnoreCase(user.getRole())) {
            return true;
        }
        if (booking.getPropertyId() != null && propertyRepository != null) {
            Property prop = propertyRepository.findById(booking.getPropertyId()).orElse(null);
            if (prop != null && user.getId() != null && user.getId().equals(prop.getOwnerId())) {
                return true;
            }
        }
        List<String> branches = getEffectiveBranches(user);
        return booking.getBranch() != null && !branches.isEmpty() && branches.contains(booking.getBranch());
    }

    private boolean hasAccessToTargetScope(User user, String scopeId) {
        if (user == null || scopeId == null || scopeId.isBlank()) {
            return false;
        }
        if ("ADMIN".equalsIgnoreCase(user.getRole())) {
            return true;
        }
        List<String> branches = getEffectiveBranches(user);
        if (branches.contains(scopeId)) {
            return true;
        }
        List<String> propertyIds = getOwnedPropertyIds(user);
        return propertyIds.contains(scopeId);
    }

    private List<Booking> loadAllHostBookings(List<String> branches, List<String> propertyIds) {
        Map<String, Booking> merged = new LinkedHashMap<>();
        if (!branches.isEmpty()) {
            List<Booking> byBranches = bookingRepository.findByBranchIn(branches);
            if (byBranches != null) {
                for (Booking b : byBranches) {
                    if (b != null && b.getId() != null) merged.put(b.getId(), b);
                }
            }
        }
        if (!propertyIds.isEmpty()) {
            List<Booking> byProperties = bookingRepository.findByPropertyIdIn(propertyIds);
            if (byProperties != null) {
                for (Booking b : byProperties) {
                    if (b != null && b.getId() != null) merged.put(b.getId(), b);
                }
            }
        }
        return new ArrayList<>(merged.values());
    }

    @GetMapping("/bookings")
    public ResponseEntity<ApiResponse<Page<BookingDTO>>> getHostBookings(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {

        User user = userPrincipal != null ? userPrincipal.getUser() : null;
        List<String> branches = getEffectiveBranches(user);
        List<String> propertyIds = getOwnedPropertyIds(user);

        if (branches.isEmpty() && propertyIds.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.ok(Page.empty()));
        }

        int pageIndex = Math.max(0, page - 1);
        int safeLimit = Math.min(Math.max(1, limit), 100);
        Pageable pageable = PageRequest.of(pageIndex, safeLimit);

        boolean hasStatusFilter = status != null && !status.isBlank() && !status.equalsIgnoreCase("all");
        boolean hasSearchFilter = search != null && !search.isBlank();

        // Ruta optimizada a nivel de MongoDB (PDM-04) cuando no hay búsqueda por texto libre y solo aplica un ámbito
        if (!hasSearchFilter) {
            if (!branches.isEmpty() && propertyIds.isEmpty()) {
                Page<BookingDTO> pageResult = (hasStatusFilter
                        ? bookingRepository.findByBranchInAndStatusIgnoreCase(branches, status, pageable)
                        : bookingRepository.findByBranchIn(branches, pageable))
                        .map(bookingService::toDTO);
                return ResponseEntity.ok(ApiResponse.ok(pageResult));
            }
            if (branches.isEmpty() && !propertyIds.isEmpty()) {
                Page<BookingDTO> pageResult = (hasStatusFilter
                        ? bookingRepository.findByPropertyIdInAndStatusIgnoreCase(propertyIds, status, pageable)
                        : bookingRepository.findByPropertyIdIn(propertyIds, pageable))
                        .map(bookingService::toDTO);
                return ResponseEntity.ok(ApiResponse.ok(pageResult));
            }
        }

        List<Booking> allHostBookings = loadAllHostBookings(branches, propertyIds);
        List<BookingDTO> filtered = allHostBookings.stream()
                .filter(b -> {
                    if (hasStatusFilter) {
                        if (b.getStatus() == null || !b.getStatus().equalsIgnoreCase(status)) return false;
                    }
                    if (hasSearchFilter) {
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
                .map(bookingService::toDTO)
                .toList();

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        List<BookingDTO> subList = start <= end && start < filtered.size() ? filtered.subList(start, end) : Collections.emptyList();
        return ResponseEntity.ok(ApiResponse.ok(new PageImpl<>(subList, pageable, filtered.size())));
    }

    @GetMapping({"/metrics", "/stats"})
    public ResponseEntity<ApiResponse<HostDashboardStats>> getHostMetrics(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        User user = userPrincipal != null ? userPrincipal.getUser() : null;
        List<String> branches = getEffectiveBranches(user);
        List<String> propertyIds = getOwnedPropertyIds(user);

        if (branches.isEmpty() && propertyIds.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.ok(
                    HostDashboardStats.builder()
                            .totalSuites(0)
                            .totalBookings(0)
                            .totalRevenue(BigDecimal.ZERO)
                            .pendingCheckins(0)
                            .activeBookings(0)
                            .build()
            ));
        }

        long totalSuites = 0;
        if (!branches.isEmpty() && propertyIds.isEmpty()) {
            totalSuites = suiteRepository.countByBranchIn(branches);
        } else if (branches.isEmpty() && !propertyIds.isEmpty()) {
            totalSuites = suiteRepository.countByPropertyIdIn(propertyIds);
        } else {
            Map<String, Suite> uniqueSuites = new LinkedHashMap<>();
            for (String branchId : branches) {
                List<Suite> sList = suiteRepository.findByBranchAndActiveTrue(branchId);
                if (sList != null) sList.forEach(s -> uniqueSuites.put(s.getId(), s));
            }
            for (String propId : propertyIds) {
                List<Suite> sList = suiteRepository.findByPropertyId(propId);
                if (sList != null) sList.forEach(s -> uniqueSuites.put(s.getId(), s));
            }
            totalSuites = !uniqueSuites.isEmpty()
                    ? uniqueSuites.size()
                    : Math.max(suiteRepository.countByBranchIn(branches), suiteRepository.countByPropertyIdIn(propertyIds));
        }

        List<Booking> hostBookings = loadAllHostBookings(branches, propertyIds);
        long totalBookings = hostBookings.size();

        BigDecimal totalRevenue = hostBookings.stream()
                .map(co.palaciodelmar.backend.service.AdminService::netCollected)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long pendingCheckins = hostBookings.stream()
                .filter(b -> "confirmed".equalsIgnoreCase(b.getStatus()) && (b.getPreCheckInCompleted() == null || !b.getPreCheckInCompleted()))
                .count();

        long activeBookings = hostBookings.stream()
                .filter(b -> co.palaciodelmar.backend.service.AdminService.isActiveStatus(b.getStatus()))
                .count();

        HostDashboardStats stats = HostDashboardStats.builder()
                .totalSuites(totalSuites)
                .totalBookings(totalBookings)
                .totalRevenue(totalRevenue)
                .pendingCheckins(pendingCheckins)
                .activeBookings(activeBookings)
                .build();

        return ResponseEntity.ok(ApiResponse.ok(stats));
    }

    @GetMapping("/promotions")
    public ResponseEntity<ApiResponse<List<PromotionDTO>>> getPromotions(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        User user = userPrincipal != null ? userPrincipal.getUser() : null;
        if (user == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("No autenticado"));
        }

        Set<String> scopes = new LinkedHashSet<>();
        scopes.addAll(getEffectiveBranches(user));
        scopes.addAll(getOwnedPropertyIds(user));

        if (scopes.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.ok(Collections.emptyList()));
        }

        List<PromotionDTO> promotions = scopes.stream()
                .flatMap(scope -> promotionRepository.findByBranchAndActiveTrue(scope).stream())
                .map(promo -> PromotionDTO.builder()
                        .id(promo.getId())
                        .branch(promo.getBranch())
                        .title(promo.getTitle())
                        .discountPercent(promo.getDiscountPercent())
                        .startDate(promo.getStartDate())
                        .endDate(promo.getEndDate())
                        .active(promo.getActive())
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.ok(promotions));
    }

    @PostMapping("/promotions")
    public ResponseEntity<ApiResponse<PromotionDTO>> createPromotion(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody PromotionRequest request) {

        User user = userPrincipal != null ? userPrincipal.getUser() : null;
        String targetBranch = request.resolveBranch();
        if (targetBranch == null || targetBranch.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Debes especificar una sede o propiedad para la promoción"));
        }
        if (request.getStartDate() != null && request.getEndDate() != null && request.getEndDate().isBefore(request.getStartDate())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("La fecha fin no puede ser anterior a la fecha inicio"));
        }
        if (!hasAccessToTargetScope(user, targetBranch)) {
            return ResponseEntity.status(403).body(ApiResponse.error("No tienes acceso a esta sede o propiedad"));
        }

        Promotion promo = Promotion.builder()
                .title(request.getTitle())
                .branch(targetBranch)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .discountPercent(request.getDiscountPercent())
                .active(true)
                .isSample(false)
                .build();

        Promotion saved = promotionRepository.save(promo);

        PromotionDTO dto = PromotionDTO.builder()
                .id(saved.getId())
                .branch(saved.getBranch())
                .title(saved.getTitle())
                .discountPercent(saved.getDiscountPercent())
                .startDate(saved.getStartDate())
                .endDate(saved.getEndDate())
                .active(saved.getActive())
                .build();

        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    @DeleteMapping("/promotions/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePromotion(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable String id) {

        Optional<Promotion> promoOpt = promotionRepository.findById(id);
        if (promoOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Promotion promo = promoOpt.get();
        User user = userPrincipal != null ? userPrincipal.getUser() : null;
        if (!hasAccessToTargetScope(user, promo.getBranch())) {
            return ResponseEntity.status(403).body(ApiResponse.error("No tienes acceso a esta promoción"));
        }

        promotionRepository.delete(promo);
        return ResponseEntity.ok(ApiResponse.ok("Promoción eliminada", null));
    }

    @PostMapping("/bookings/{id}/checkin")
    public ResponseEntity<ApiResponse<Void>> checkIn(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable String id) {
        Optional<Booking> bookingOpt = bookingRepository.findById(id);
        if (bookingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Booking booking = bookingOpt.get();
        User user = userPrincipal != null ? userPrincipal.getUser() : null;
        if (!hasAccessToBooking(user, booking)) {
            return ResponseEntity.status(403).body(ApiResponse.error("No tienes acceso a esta reserva"));
        }

        bookingService.changeBookingStatus(booking, co.palaciodelmar.backend.model.BookingStatus.CHECKED_IN, user.getId(), "Check-in by host");
        bookingRepository.save(booking);
        return ResponseEntity.ok(ApiResponse.ok("Check-in registrado", null));
    }

    @PostMapping("/bookings/{id}/checkout")
    public ResponseEntity<ApiResponse<Void>> checkOut(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable String id) {
        Optional<Booking> bookingOpt = bookingRepository.findById(id);
        if (bookingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Booking booking = bookingOpt.get();
        User user = userPrincipal != null ? userPrincipal.getUser() : null;
        if (!hasAccessToBooking(user, booking)) {
            return ResponseEntity.status(403).body(ApiResponse.error("No tienes acceso a esta reserva"));
        }

        // Late check-out: el check-out oficial es a las 12:00 (ver FAQ y Branch.checkOutTime).
        java.time.ZoneId bogota = java.time.ZoneId.of("America/Bogota");
        java.time.LocalDate today = java.time.LocalDate.now(bogota);
        java.time.LocalTime nowTime = java.time.LocalTime.now(bogota);
        boolean lateCheckout = booking.getCheckOut() != null
                && (today.isAfter(booking.getCheckOut())
                    || (today.isEqual(booking.getCheckOut()) && nowTime.isAfter(java.time.LocalTime.NOON)));
        BigDecimal currentTotal = booking.getTotalPrice() != null ? booking.getTotalPrice() : BigDecimal.ZERO;
        BigDecimal currentBalance = booking.getBalanceDue() != null ? booking.getBalanceDue() : BigDecimal.ZERO;
        if (lateCheckout && booking.getPricePerNight() != null) {
            BigDecimal recargo = booking.getPricePerNight().multiply(new BigDecimal("0.50")).setScale(2, java.math.RoundingMode.HALF_UP);
            booking.setTotalPrice(currentTotal.add(recargo));
            booking.setBalanceDue(currentBalance.add(recargo));
            booking.setPaymentStatus("partial");
            booking.setNotes((booking.getNotes() != null ? booking.getNotes() + " | " : "") + "Recargo Late Check-out aplicado: $" + recargo);
        }
        // Depósito de garantía: se devuelve al hacer check-out (se refleja en lo devuelto, no como ingreso).
        BigDecimal deposit = booking.getGuaranteeDeposit() != null ? booking.getGuaranteeDeposit() : BigDecimal.ZERO;
        if (deposit.signum() > 0 && !Boolean.TRUE.equals(booking.getGuaranteeRefunded())) {
            BigDecimal paid = booking.getPaidAmount() != null ? booking.getPaidAmount() : BigDecimal.ZERO;
            BigDecimal refunded = booking.getRefundedAmount() != null ? booking.getRefundedAmount() : BigDecimal.ZERO;
            BigDecimal refundable = paid.subtract(refunded).min(deposit).max(BigDecimal.ZERO);
            booking.setRefundedAmount(refunded.add(refundable));
            booking.setGuaranteeRefunded(true);
            booking.setNotes((booking.getNotes() != null ? booking.getNotes() + " | " : "") + "Depósito de garantía devuelto: $" + refundable);
        }
        bookingService.changeBookingStatus(booking, co.palaciodelmar.backend.model.BookingStatus.CHECKED_OUT, user.getId(), "Check-out by host");
        bookingRepository.save(booking);
        return ResponseEntity.ok(ApiResponse.ok("Check-out registrado", null));
    }

    @PostMapping("/bookings/{id}/regenerate-pin")
    public ResponseEntity<ApiResponse<Map<String, String>>> regeneratePin(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable String id) {
        Optional<Booking> bookingOpt = bookingRepository.findById(id);
        if (bookingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Booking booking = bookingOpt.get();
        User user = userPrincipal != null ? userPrincipal.getUser() : null;
        if (!hasAccessToBooking(user, booking)) {
            return ResponseEntity.status(403).body(ApiResponse.error("No tienes acceso a esta reserva"));
        }

        String newPin = String.format("%06d", SECURE_RANDOM.nextInt(100000, 1000000));
        booking.setSmartLockPin(newPin);
        bookingRepository.save(booking);

        return ResponseEntity.ok(ApiResponse.ok(Map.of("smartLockPin", newPin)));
    }

    @GetMapping("/bank-account")
    public ResponseEntity<ApiResponse<BankAccountDTO>> getBankAccount(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        User user = userPrincipal != null ? userPrincipal.getUser() : null;
        if (user == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("No autenticado"));
        }
        User freshUser = userRepository != null && user.getId() != null
                ? userRepository.findById(user.getId()).orElse(user)
                : user;
        User.BankAccount acc = freshUser.getBankAccount();
        if (acc == null) {
            return ResponseEntity.ok(ApiResponse.ok(BankAccountDTO.builder()
                    .bankName("Bancolombia")
                    .accountType("Ahorros")
                    .accountNumber("")
                    .accountNumberMasked("")
                    .holderName("")
                    .build()));
        }
        String masked = acc.getAccountNumberMasked();
        if (masked == null || masked.isBlank()) {
            masked = maskAccountNumber(acc.getAccountNumber());
        }
        return ResponseEntity.ok(ApiResponse.ok(BankAccountDTO.builder()
                .bankName(acc.getBankName())
                .accountType(acc.getAccountType())
                .accountNumber(masked)
                .accountNumberMasked(masked)
                .holderName(acc.getHolderName())
                .documentId(acc.getDocumentId())
                .documentType(acc.getDocumentType())
                .updatedAt(acc.getUpdatedAt())
                .build()));
    }

    @PutMapping("/bank-account")
    public ResponseEntity<ApiResponse<BankAccountDTO>> updateBankAccount(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody BankAccountDTO request) {
        User user = userPrincipal != null ? userPrincipal.getUser() : null;
        if (user == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("No autenticado"));
        }
        User freshUser = userRepository != null && user.getId() != null
                ? userRepository.findById(user.getId()).orElse(user)
                : user;
        User.BankAccount existing = freshUser.getBankAccount();

        String rawAccount = request.getAccountNumber();
        final String effectiveAccountNumber;
        final String effectiveMasked;

        if (rawAccount != null && rawAccount.startsWith("***") && existing != null && existing.getAccountNumber() != null) {
            // Se reenvió el valor ya enmascarado; preservar el número real original
            effectiveAccountNumber = existing.getAccountNumber();
            effectiveMasked = existing.getAccountNumberMasked() != null ? existing.getAccountNumberMasked() : maskAccountNumber(effectiveAccountNumber);
        } else {
            effectiveAccountNumber = rawAccount != null ? rawAccount.trim() : "";
            effectiveMasked = maskAccountNumber(effectiveAccountNumber);
        }

        User.BankAccount updated = User.BankAccount.builder()
                .bankName(request.getBankName() != null ? request.getBankName().trim() : "Bancolombia")
                .accountType(request.getAccountType() != null ? request.getAccountType().trim() : "Ahorros")
                .accountNumber(effectiveAccountNumber)
                .accountNumberMasked(effectiveMasked)
                .holderName(request.getHolderName() != null ? request.getHolderName().trim() : "")
                .documentId(request.getDocumentId())
                .documentType(request.getDocumentType())
                .updatedAt(java.time.Instant.now())
                .build();

        freshUser.setBankAccount(updated);
        freshUser.setUpdatedAt(java.time.Instant.now());
        if (userRepository != null) {
            userRepository.save(freshUser);
        }

        BankAccountDTO responseDTO = BankAccountDTO.builder()
                .bankName(updated.getBankName())
                .accountType(updated.getAccountType())
                .accountNumber(effectiveMasked)
                .accountNumberMasked(effectiveMasked)
                .holderName(updated.getHolderName())
                .documentId(updated.getDocumentId())
                .documentType(updated.getDocumentType())
                .updatedAt(updated.getUpdatedAt())
                .build();

        return ResponseEntity.ok(ApiResponse.ok("Datos bancarios actualizados correctamente", responseDTO));
    }

    private static String maskAccountNumber(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String clean = raw.trim();
        if (clean.length() <= 4) {
            return "***" + clean;
        }
        return "***" + clean.substring(clean.length() - 4);
    }
}
