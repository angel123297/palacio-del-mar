package co.palaciodelmar.backend.scheduler;

import co.palaciodelmar.backend.service.HostPayoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class HostPayoutScheduler {

    private final HostPayoutService hostPayoutService;

    /**
     * Ejecuta la liquidación automática de anfitriones cada hora (o a la medianoche en producción).
     */
    @Scheduled(fixedRateString = "${palacio.payouts.check.interval.ms:3600000}")
    public void runAutomatedPayoutSettlement() {
        try {
            log.info("Iniciando tarea programada de liquidación de anfitriones...");
            var payouts = hostPayoutService.processAutomatedPayouts();
            if (!payouts.isEmpty()) {
                log.info("Tarea de liquidación finalizada: {} lotes procesados.", payouts.size());
            }
        } catch (Exception e) {
            log.error("Error al procesar liquidaciones de anfitriones: {}", e.getMessage(), e);
        }
    }
}
