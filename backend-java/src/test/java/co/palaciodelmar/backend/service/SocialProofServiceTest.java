package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.dto.SocialProofDTO;
import co.palaciodelmar.backend.model.Suite;
import co.palaciodelmar.backend.repository.SuiteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SocialProofServiceTest {

    @Test
    @DisplayName("Pilar 3: Registra espectadores únicos y actividad de reservas recientes en respaldo L1 y Redis ZSET")
    void testViewerDeduplicationAndBookingUrgencyMetrics() {
        SuiteRepository suiteRepository = mock(SuiteRepository.class);
        when(suiteRepository.findById("penthouse-bocagrande")).thenReturn(Optional.empty());
        when(suiteRepository.findBySlug("penthouse-bocagrande"))
                .thenReturn(Optional.of(Suite.builder().id("suite-101").slug("penthouse-bocagrande").build()));

        SocialProofService service = new SocialProofService(null, suiteRepository);

        // Mismo espectador (misma IP + sesión) hace doble ping -> cuenta como 1 solo
        SocialProofDTO p1 = service.recordViewerPing("penthouse-bocagrande", "190.24.10.5", "sess_abc");
        SocialProofDTO p2 = service.recordViewerPing("penthouse-bocagrande", "190.24.10.5", "sess_abc");
        assertEquals(1L, p1.getActiveViewers());
        assertEquals(1L, p2.getActiveViewers());
        assertEquals("suite-101", p2.getSuiteKey());
        assertFalse(p2.isHighDemand());

        // Dos viajeros adicionales -> 3 espectadores activos -> activa indicador highDemand
        service.recordViewerPing("suite-101", "181.50.20.9", "sess_def");
        SocialProofDTO p3 = service.recordViewerPing("suite-101", "200.11.33.7", "sess_ghi");
        assertEquals(3L, p3.getActiveViewers());
        assertTrue(p3.isHighDemand());

        // Registrar dos reservas confirmadas recientes
        service.recordBookingActivity("suite-101", "penthouse-bocagrande", "bk-1");
        service.recordBookingActivity("suite-101", "penthouse-bocagrande", "bk-2");

        SocialProofDTO snapshot = service.getSocialProof("penthouse-bocagrande");
        assertEquals(3L, snapshot.getActiveViewers());
        assertEquals(2L, snapshot.getBookingsLast24h());
        assertNotNull(snapshot.getLastBookedMinutesAgo());
        assertEquals(0L, snapshot.getLastBookedMinutesAgo());
        assertTrue(snapshot.isHighDemand());
    }

    @Test
    @DisplayName("Pilar 3: Aplica tope máximo de 250 espectadores en Redis ZSET y rechaza claves maliciosas")
    @SuppressWarnings("unchecked")
    void testRedisZSetCapAndInputSanitization() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ZSetOperations<String, String> zsetOps = mock(ZSetOperations.class);
        ValueOperations<String, String> valOps = mock(ValueOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(zsetOps);
        when(redisTemplate.opsForValue()).thenReturn(valOps);
        when(zsetOps.zCard("palacio:social:viewers:suite-vip")).thenReturn(260L);
        when(zsetOps.zCard("palacio:social:bookings_24h:suite-vip")).thenReturn(1L);

        SocialProofService service = new SocialProofService(redisTemplate, null);
        SocialProofDTO dto = service.recordViewerPing("suite-vip", "10.0.0.1", "sess_1");

        assertEquals(250L, dto.getActiveViewers());
        verify(zsetOps).removeRange("palacio:social:viewers:suite-vip", 0, 9);
        verify(redisTemplate).expire("palacio:social:viewers:suite-vip", Duration.ofMinutes(5));

        assertThrows(IllegalArgumentException.class, () ->
                service.recordViewerPing("suite/../injection", "10.0.0.1", "sess_1")
        );
    }

    @Test
    @DisplayName("M-4: GET social-proof no reserva memoria L1, rechaza suites inexistentes con 404 y aplica tope duro <= 500")
    void testGetDoesNotAllocateLocalMapAndRejectsUnknownSuitesAndCapsCardinality() {
        SuiteRepository suiteRepository = mock(SuiteRepository.class);
        when(suiteRepository.findById(anyString())).thenReturn(Optional.empty());
        when(suiteRepository.findBySlug(anyString())).thenReturn(Optional.empty());

        SocialProofService guardedService = new SocialProofService(null, suiteRepository);
        assertThrows(co.palaciodelmar.backend.exception.ResourceNotFoundException.class, () ->
                guardedService.getSocialProof("nonexistent-suite-999")
        );
        assertThrows(co.palaciodelmar.backend.exception.ResourceNotFoundException.class, () ->
                guardedService.recordViewerPing("nonexistent-suite-999", "198.51.100.1", "sess_x")
        );
        assertEquals(0, guardedService.getLocalViewersSuiteCount());

        // Sin repositorio (modo standalone L1): GET no debe poblar localViewers y pings > 500 suites deben acotarse
        SocialProofService localService = new SocialProofService(null, null);
        localService.getSocialProof("suite-cold-read");
        assertEquals(0, localService.getLocalViewersSuiteCount(), "GET nunca debe crear mapas vacíos en localViewers");

        for (int i = 0; i < 560; i++) {
            localService.recordViewerPing("suite-" + i, "198.51.100.5", "sess_" + i);
        }
        assertTrue(localService.getLocalViewersSuiteCount() <= 500,
                "localViewers debe acotarse estrictamente a <= 500 suites");
    }
}
