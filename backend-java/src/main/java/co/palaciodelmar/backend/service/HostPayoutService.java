package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.dto.HostPayoutDTO;
import co.palaciodelmar.backend.model.Booking;
import co.palaciodelmar.backend.model.HostPayout;
import co.palaciodelmar.backend.model.Property;
import co.palaciodelmar.backend.repository.BookingRepository;
import co.palaciodelmar.backend.repository.HostPayoutRepository;
import co.palaciodelmar.backend.repository.PropertyRepository;
import co.palaciodelmar.backend.repository.UserRepository;
import co.palaciodelmar.backend.webhook.WebhookDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class HostPayoutService {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private final BookingRepository bookingRepository;
    private final HostPayoutRepository hostPayoutRepository;
    private final PropertyRepository propertyRepository;

    @Autowired(required = false)
    private WebhookDispatcher webhookDispatcher;

    @lombok.Setter
    @Autowired(required = false)
    private RedisLockService redisLockService;

    @lombok.Setter
    @Autowired(required = false)
    private UserRepository userRepository;

    /**
     * Proceso batch automático de liquidación de ingresos a anfitriones.
     * Agrupa reservas elegibles (solo estancias finalizadas: checked_out o completed, no canceladas, no liquidadas) por propiedad.
     */
    public List<HostPayoutDTO> processAutomatedPayouts() {
        if (redisLockService != null) {
            if (redisLockService.isDistributedConfigured()) {
                return redisLockService.executeWithStrictDistributedLock(
                        "payouts:global",
                        java.time.Duration.ofSeconds(60),
                        java.time.Duration.ofSeconds(5),
                        this::doProcessAutomatedPayouts
                );
            }
            return redisLockService.executeWithLock(
                    "payouts:global",
                    java.time.Duration.ofSeconds(60),
                    java.time.Duration.ofSeconds(5),
                    this::doProcessAutomatedPayouts
            );
        }
        return doProcessAutomatedPayouts();
    }

    private synchronized List<HostPayoutDTO> doProcessAutomatedPayouts() {
        LocalDate today = LocalDate.now(BOGOTA);
        List<String> eligibleStatuses = List.of("checked_out", "completed");

        List<Booking> candidates = bookingRepository.findByStatusIn(eligibleStatuses).stream()
                .filter(b -> b.getPayoutId() == null)
                .filter(b -> b.getCheckIn() != null && !b.getCheckIn().isAfter(today))
                .filter(b -> b.getPaidAmount() != null && b.getPaidAmount().compareTo(BigDecimal.ZERO) > 0)
                .toList();

        if (candidates.isEmpty()) {
            log.info("No hay reservas pendientes de liquidación a anfitriones al {}", today);
            return List.of();
        }

        // Agrupar por propiedad
        Map<String, List<Booking>> byProperty = new HashMap<>();
        for (Booking b : candidates) {
            String propId = b.getPropertyId() != null ? b.getPropertyId() : b.getBranch();
            if (propId != null) {
                byProperty.computeIfAbsent(propId, k -> new ArrayList<>()).add(b);
            }
        }

        List<HostPayoutDTO> generated = new ArrayList<>();
        String datePrefix = today.format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        for (Map.Entry<String, List<Booking>> entry : byProperty.entrySet()) {
            String propId = entry.getKey();
            List<Booking> propBookings = entry.getValue();

            // Verificación atómica anti-doble liquidación: re-verificar estado y liquidaciones previas en BD
            List<Booking> validBookings = new ArrayList<>();
            for (Booking b : propBookings) {
                Booking fresh = bookingRepository.findById(b.getId()).orElse(b);
                if (fresh.getPayoutId() == null && fresh.getStatus() != null && eligibleStatuses.contains(fresh.getStatus().toLowerCase())) {
                    Optional<HostPayout> existingPayout = hostPayoutRepository.findFirstByBookingIdsContaining(fresh.getId());
                    if (existingPayout != null && existingPayout.isPresent()) {
                        fresh.setPayoutId(existingPayout.get().getId());
                        fresh.setUpdatedAt(Instant.now());
                        bookingRepository.save(fresh);
                        log.warn("Auto-reparado payoutId={} en reserva {} ya registrada en liquidación previa",
                                existingPayout.get().getId(), fresh.getId());
                        continue;
                    }
                    validBookings.add(fresh);
                }
            }

            if (validBookings.isEmpty()) {
                continue;
            }

            Property property = propertyRepository.findById(propId).orElse(null);
            String ownerId = property != null && property.getOwnerId() != null ? property.getOwnerId() : "system";
            String propName = property != null ? property.getName() : "Alojamiento (" + propId + ")";

            String payoutCode = "PO-" + datePrefix + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            HostPayout payout = HostPayout.builder()
                    .payoutCode(payoutCode)
                    .propertyId(propId)
                    .propertyName(propName)
                    .ownerId(ownerId)
                    .status("PROCESSED")
                    .processedAt(Instant.now())
                    .createdAt(Instant.now())
                    .build();

            if (userRepository != null && ownerId != null && !"system".equals(ownerId)) {
                userRepository.findById(ownerId).ifPresent(u -> {
                    if (u.getBankAccount() != null) {
                        payout.setDestinationBank(u.getBankAccount().getBankName());
                        payout.setDestinationAccountType(u.getBankAccount().getAccountType());
                        payout.setDestinationAccountMasked(u.getBankAccount().getAccountNumberMasked());
                        payout.setDestinationHolder(u.getBankAccount().getHolderName());
                    }
                });
            }

            recomputePayoutTotals(payout, validBookings, today);

            HostPayout savedPayout = hostPayoutRepository.save(payout);

            // Marcar cada reserva con el ID de la liquidación tolerando fallos parciales sin doble liquidación
            List<Booking> settledBookings = new ArrayList<>();
            for (Booking b : validBookings) {
                try {
                    b.setPayoutId(savedPayout.getId());
                    b.setUpdatedAt(Instant.now());
                    bookingRepository.save(b);
                    settledBookings.add(b);
                } catch (Exception ex) {
                    b.setPayoutId(null);
                    log.error("Fallo al marcar reserva {} en liquidación {}: {}. Se reconciliará el payout para evitar doble cobro.",
                            b.getId(), payoutCode, ex.getMessage());
                }
            }

            if (settledBookings.isEmpty()) {
                hostPayoutRepository.delete(savedPayout);
                log.warn("Liquidación {} revertida porque ninguna reserva pudo sellarse con @Version.", payoutCode);
                continue;
            }

            if (settledBookings.size() < validBookings.size()) {
                recomputePayoutTotals(savedPayout, settledBookings, today);
                savedPayout = hostPayoutRepository.save(savedPayout);
            }

            HostPayoutDTO dto = toDTO(savedPayout);
            generated.add(dto);

            log.info("Liquidación generada con éxito: {} para propiedad '{}' ({} reservas, neto: ${})",
                    payoutCode, propName, savedPayout.getTotalBookings(), savedPayout.getNetAmount());

            // Disparar webhook saliente al anfitrión
            if (webhookDispatcher != null && propId != null) {
                webhookDispatcher.dispatchToProperty(propId, "payout.processed", dto);
            }
        }

        return generated;
    }

    private void recomputePayoutTotals(HostPayout target, List<Booking> bookings, LocalDate fallbackDate) {
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal fee = BigDecimal.ZERO;
        BigDecimal net = BigDecimal.ZERO;
        LocalDate minDate = null;
        LocalDate maxDate = null;
        List<String> bIds = new ArrayList<>();

        for (Booking b : bookings) {
            BigDecimal fallbackRoom = b.getSubtotal() != null ? b.getSubtotal() : (b.getTotalPrice() != null ? b.getTotalPrice() : BigDecimal.ZERO);
            BigDecimal bookingFee = b.getPlatformFee() != null ? b.getPlatformFee() : fallbackRoom.multiply(new BigDecimal("0.08")).setScale(2, RoundingMode.HALF_UP);
            BigDecimal bookingNet = b.getHostPayout() != null ? b.getHostPayout() : fallbackRoom.subtract(bookingFee);
            BigDecimal roomPrice = (b.getPlatformFee() != null && b.getHostPayout() != null)
                    ? bookingFee.add(bookingNet)
                    : fallbackRoom;

            gross = gross.add(roomPrice);
            fee = fee.add(bookingFee);
            net = net.add(bookingNet);
            bIds.add(b.getId());

            if (minDate == null || (b.getCheckIn() != null && b.getCheckIn().isBefore(minDate))) {
                minDate = b.getCheckIn();
            }
            if (maxDate == null || (b.getCheckOut() != null && b.getCheckOut().isAfter(maxDate))) {
                maxDate = b.getCheckOut();
            }
        }

        target.setBookingIds(bIds);
        target.setTotalBookings(bIds.size());
        target.setGrossTotal(gross.setScale(2, RoundingMode.HALF_UP));
        target.setPlatformFee(fee.setScale(2, RoundingMode.HALF_UP));
        target.setNetAmount(net.setScale(2, RoundingMode.HALF_UP));
        target.setPeriodStart(minDate != null ? minDate : fallbackDate);
        target.setPeriodEnd(maxDate != null ? maxDate : fallbackDate);
    }

    public List<HostPayoutDTO> getPayoutsForHost(String ownerId) {
        if (ownerId == null) return List.of();
        return hostPayoutRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                .map(this::toDTO)
                .toList();
    }

    public List<HostPayoutDTO> getPayoutsForAdmin() {
        return hostPayoutRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toDTO)
                .toList();
    }

    public HostPayoutDTO toDTO(HostPayout p) {
        if (p == null) return null;
        return HostPayoutDTO.builder()
                .id(p.getId())
                .payoutCode(p.getPayoutCode())
                .propertyId(p.getPropertyId())
                .propertyName(p.getPropertyName())
                .ownerId(p.getOwnerId())
                .bookingIds(p.getBookingIds())
                .totalBookings(p.getTotalBookings())
                .grossTotal(p.getGrossTotal())
                .platformFee(p.getPlatformFee())
                .netAmount(p.getNetAmount())
                .status(p.getStatus())
                .periodStart(p.getPeriodStart())
                .periodEnd(p.getPeriodEnd())
                .destinationBank(p.getDestinationBank())
                .destinationAccountType(p.getDestinationAccountType())
                .destinationAccountMasked(p.getDestinationAccountMasked())
                .destinationHolder(p.getDestinationHolder())
                .processedAt(p.getProcessedAt())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
