package co.palaciodelmar.backend.scheduler;

import co.palaciodelmar.backend.model.Booking;
import co.palaciodelmar.backend.model.BookingStatus;
import co.palaciodelmar.backend.repository.BookingRepository;
import co.palaciodelmar.backend.service.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Tarea programada que automatiza el ciclo de vida hotelero (Self Check-in y Auto Check-out):
 * - A las 15:00 COP del día de llegada activa la llave inteligente y pasa reservas confirmadas a CHECKED_IN.
 * - A las 13:00 COP del día de salida (tras período de gracia post 12:00) procesa el CHECKED_OUT,
 *   aplica recargo del 50% si el PIN continúa activo, devuelve el depósito de garantía y libera la habitación.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoStayLifecycleScheduler {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;

    @Value("${palacio.lifecycle.autocheckin.hour:15}")
    private int autoCheckInHour = 15;

    @Value("${palacio.lifecycle.autocheckout.hour:13}")
    private int autoCheckOutHour = 13;

    @Scheduled(fixedRateString = "${palacio.lifecycle.check.interval.ms:60000}")
    public void processStayLifecycleTransitions() {
        LocalDate today = LocalDate.now(BOGOTA);
        LocalTime nowTime = LocalTime.now(BOGOTA);

        processAutoCheckIns(today, nowTime);
        processAutoCheckOuts(today, nowTime);
    }

    void processAutoCheckIns(LocalDate today, LocalTime nowTime) {
        // Reservas confirmadas con fecha de check-in hoy o anterior
        List<Booking> confirmedBookings = bookingRepository.findByStatusIn(List.of("confirmed"));

        for (Booking booking : confirmedBookings) {
            if (booking.getCheckIn() == null) continue;

            boolean isPastCheckInDate = today.isAfter(booking.getCheckIn());
            boolean isCheckInDayReady = today.isEqual(booking.getCheckIn()) && nowTime.getHour() >= autoCheckInHour;

            if (isPastCheckInDate || isCheckInDayReady) {
                try {
                    if (booking.getSmartLockPin() == null || booking.getSmartLockPin().isBlank()) {
                        booking.setSmartLockPin(String.format("%06d", SECURE_RANDOM.nextInt(100000, 1000000)));
                    }

                    bookingService.changeBookingStatus(
                            booking,
                            BookingStatus.CHECKED_IN,
                            "System-AutoStayLifecycle",
                            "Self Check-in automático ejecutado a la hora oficial de ingreso (" + autoCheckInHour + ":00)"
                    );
                    bookingRepository.save(booking);

                    log.info("Self Check-in automático procesado para reserva {} (Huésped: {}, PIN activado: {})",
                            booking.getId(), booking.getUser(), booking.getSmartLockPin());
                } catch (Exception e) {
                    log.error("Error al procesar auto check-in para reserva {}: {}", booking.getId(), e.getMessage());
                }
            }
        }
    }

    void processAutoCheckOuts(LocalDate today, LocalTime nowTime) {
        // Reservas en estancia activa (checked_in)
        List<Booking> activeBookings = bookingRepository.findByStatusIn(List.of("checked_in"));

        for (Booking booking : activeBookings) {
            if (booking.getCheckOut() == null) continue;

            boolean isPastCheckOutDate = today.isAfter(booking.getCheckOut());
            boolean isCheckOutDayReady = today.isEqual(booking.getCheckOut()) && nowTime.getHour() >= autoCheckOutHour;

            if (isPastCheckOutDate || isCheckOutDayReady) {
                try {
                    // Late check-out automático: aplica si el PIN sigue activo pasadas las 13:00 ANTES de cerrar
                    boolean isPast13 = isPastCheckOutDate || (today.isEqual(booking.getCheckOut()) && (nowTime.getHour() >= 13 || nowTime.isAfter(LocalTime.of(13, 0))));
                    boolean pinStillActive = booking.getSmartLockPin() != null && !booking.getSmartLockPin().isBlank();

                    if (isPast13 && pinStillActive && booking.getPricePerNight() != null) {
                        BigDecimal currentTotal = booking.getTotalPrice() != null ? booking.getTotalPrice() : BigDecimal.ZERO;
                        BigDecimal currentBalance = booking.getBalanceDue() != null ? booking.getBalanceDue() : BigDecimal.ZERO;
                        BigDecimal recargo = booking.getPricePerNight().multiply(new BigDecimal("0.50")).setScale(2, RoundingMode.HALF_UP);
                        booking.setTotalPrice(currentTotal.add(recargo));
                        booking.setBalanceDue(currentBalance.add(recargo));
                        booking.setPaymentStatus("partial");
                        booking.setNotes((booking.getNotes() != null ? booking.getNotes() + " | " : "") + "Recargo automático Late Check-out aplicado: $" + recargo);
                    }

                    // Desactivar el PIN de la cerradura inteligente al cerrar
                    booking.setSmartLockPin(null);

                    // Devolución automática del depósito de garantía
                    BigDecimal deposit = booking.getGuaranteeDeposit() != null ? booking.getGuaranteeDeposit() : BigDecimal.ZERO;
                    if (deposit.signum() > 0 && !Boolean.TRUE.equals(booking.getGuaranteeRefunded())) {
                        BigDecimal paid = booking.getPaidAmount() != null ? booking.getPaidAmount() : BigDecimal.ZERO;
                        BigDecimal refunded = booking.getRefundedAmount() != null ? booking.getRefundedAmount() : BigDecimal.ZERO;
                        BigDecimal refundable = paid.subtract(refunded).min(deposit).max(BigDecimal.ZERO);
                        booking.setRefundedAmount(refunded.add(refundable));
                        booking.setGuaranteeRefunded(true);
                        booking.setNotes((booking.getNotes() != null ? booking.getNotes() + " | " : "") + "Depósito de garantía devuelto automáticamente: $" + refundable);
                    }

                    bookingService.changeBookingStatus(
                            booking,
                            BookingStatus.CHECKED_OUT,
                            "System-AutoStayLifecycle",
                            "Auto Check-out programado ejecutado a la hora límite (" + autoCheckOutHour + ":00)"
                    );
                    bookingRepository.save(booking);

                    log.info("Auto Check-out procesado para reserva {} (Garantía reembolsada, suite liberada)", booking.getId());
                } catch (Exception e) {
                    log.error("Error al procesar auto check-out para reserva {}: {}", booking.getId(), e.getMessage());
                }
            }
        }
    }
}
