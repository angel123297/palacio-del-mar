package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.dto.HostPayoutDTO;
import co.palaciodelmar.backend.model.Booking;
import co.palaciodelmar.backend.model.HostPayout;
import co.palaciodelmar.backend.model.Property;
import co.palaciodelmar.backend.repository.BookingRepository;
import co.palaciodelmar.backend.repository.HostPayoutRepository;
import co.palaciodelmar.backend.repository.PropertyRepository;
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

    /**
     * Proceso batch automático de liquidación de ingresos a anfitriones.
     * Agrupa reservas elegibles (check-in iniciado o finalizado, no canceladas, no liquidadas) por propiedad.
     */
    public List<HostPayoutDTO> processAutomatedPayouts() {
        LocalDate today = LocalDate.now(BOGOTA);
        List<String> eligibleStatuses = List.of("confirmed", "checked_in", "checked_out", "completed");

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

            Property property = propertyRepository.findById(propId).orElse(null);
            String ownerId = property != null && property.getOwnerId() != null ? property.getOwnerId() : "system";
            String propName = property != null ? property.getName() : "Alojamiento (" + propId + ")";

            BigDecimal gross = BigDecimal.ZERO;
            BigDecimal fee = BigDecimal.ZERO;
            BigDecimal net = BigDecimal.ZERO;
            LocalDate minDate = null;
            LocalDate maxDate = null;
            List<String> bIds = new ArrayList<>();

            for (Booking b : propBookings) {
                BigDecimal roomPrice = b.getSubtotal() != null ? b.getSubtotal() : b.getTotalPrice();
                BigDecimal bookingFee = b.getPlatformFee() != null ? b.getPlatformFee() : roomPrice.multiply(new BigDecimal("0.08")).setScale(2, RoundingMode.HALF_UP);
                BigDecimal bookingNet = b.getHostPayout() != null ? b.getHostPayout() : roomPrice.subtract(bookingFee);

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

            String payoutCode = "PO-" + datePrefix + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

            HostPayout payout = HostPayout.builder()
                    .payoutCode(payoutCode)
                    .propertyId(propId)
                    .propertyName(propName)
                    .ownerId(ownerId)
                    .bookingIds(bIds)
                    .totalBookings(bIds.size())
                    .grossTotal(gross.setScale(2, RoundingMode.HALF_UP))
                    .platformFee(fee.setScale(2, RoundingMode.HALF_UP))
                    .netAmount(net.setScale(2, RoundingMode.HALF_UP))
                    .status("PROCESSED")
                    .periodStart(minDate != null ? minDate : today)
                    .periodEnd(maxDate != null ? maxDate : today)
                    .processedAt(Instant.now())
                    .createdAt(Instant.now())
                    .build();

            HostPayout savedPayout = hostPayoutRepository.save(payout);

            // Marcar las reservas con el ID de la liquidación
            for (Booking b : propBookings) {
                b.setPayoutId(savedPayout.getId());
                b.setUpdatedAt(Instant.now());
                bookingRepository.save(b);
            }

            HostPayoutDTO dto = toDTO(savedPayout);
            generated.add(dto);

            log.info("Liquidación generada con éxito: {} para propiedad '{}' ({} reservas, neto: ${})",
                    payoutCode, propName, bIds.size(), net);

            // Disparar webhook saliente al anfitrión
            if (webhookDispatcher != null && propId != null) {
                webhookDispatcher.dispatchToProperty(propId, "payout.processed", dto);
            }
        }

        return generated;
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
                .processedAt(p.getProcessedAt())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
