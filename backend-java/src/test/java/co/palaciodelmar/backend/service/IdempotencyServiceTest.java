package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.dto.BookingDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IdempotencyServiceTest {

    private Map<String, String> fakeRedisMap;
    private StringRedisTemplate redisTemplate;
    private IdempotencyService idempotencyService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        fakeRedisMap = new ConcurrentHashMap<>();
        redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            String val = inv.getArgument(1);
            return fakeRedisMap.putIfAbsent(key, val) == null;
        });
        when(valueOps.get(anyString())).thenAnswer(inv -> fakeRedisMap.get(inv.getArgument(0)));
        doAnswer(inv -> {
            fakeRedisMap.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(valueOps).set(anyString(), anyString(), any(Duration.class));

        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString())).thenAnswer(inv -> {
            java.util.List<String> keys = inv.getArgument(1);
            String token = inv.getArgument(2);
            String k = keys.get(0);
            String cur = fakeRedisMap.get(k);
            if (cur != null && cur.contains(token)) {
                fakeRedisMap.remove(k);
                return 1L;
            }
            return 0L;
        });

        idempotencyService = new IdempotencyService(redisTemplate, new ObjectMapper());
    }

    @Test
    @DisplayName("Pilar 2: Primera ejecución procesa la acción y segunda petición idéntica reproduce respuesta desde Redis")
    void testFirstCallExecutesAndReplayReturnsCachedResult() {
        AtomicInteger executions = new AtomicInteger(0);
        Map<String, Object> payload = Map.of("suiteId", "s-1", "guests", 2);

        IdempotencyService.IdempotentResult<BookingDTO> first = idempotencyService.execute(
                "bookings:create",
                "user-100",
                "idem_booking_abc_123",
                payload,
                BookingDTO.class,
                () -> {
                    executions.incrementAndGet();
                    return BookingDTO.builder()
                            .id("bk-555")
                            .bookingCode("PDM-555")
                            .totalPrice(new BigDecimal("1200000"))
                            .build();
                }
        );

        assertFalse(first.replayed());
        assertEquals("bk-555", first.body().getId());
        assertEquals(1, executions.get());

        IdempotencyService.IdempotentResult<BookingDTO> second = idempotencyService.execute(
                "bookings:create",
                "user-100",
                "idem_booking_abc_123",
                payload,
                BookingDTO.class,
                () -> {
                    executions.incrementAndGet();
                    return BookingDTO.builder().id("bk-DUPLICATE").build();
                }
        );

        assertTrue(second.replayed());
        assertEquals("bk-555", second.body().getId());
        assertEquals("PDM-555", second.body().getBookingCode());
        assertEquals(1, executions.get(), "La acción subyacente jamás debe ejecutarse dos veces");
    }

    @Test
    @DisplayName("Pilar 2: Petición duplicada mientras otra está IN_PROGRESS lanza HTTP 409 Conflict")
    void testConcurrentInProgressThrows409Conflict() throws Exception {
        String key = "idem_inflight_999";
        Map<String, Object> payload = Map.of("intentId", "pi_1");

        String actorHash = IdempotencyService.sha256("u:user-1");
        String fullKey = IdempotencyService.REDIS_PREFIX + "payments:confirm:" + actorHash + ":" + key;
        String inProgressJson = new ObjectMapper().writeValueAsString(
                new IdempotencyService.StoredRecord("IN_PROGRESS", "tok_other", IdempotencyService.sha256("x"), null)
        );
        fakeRedisMap.put(fullKey, inProgressJson);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                idempotencyService.execute("payments:confirm", "user-1", key, payload, Map.class, () -> Map.of("ok", true))
        );
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    @DisplayName("Pilar 2: Reutilizar la misma Idempotency-Key con parámetros distintos lanza IllegalArgumentException")
    void testPayloadMismatchThrowsIllegalArgumentException() {
        idempotencyService.execute(
                "payments:checkout",
                "user-1",
                "idem_mismatch_1",
                Map.of("bookingId", "bk-1", "method", "card"),
                Map.class,
                () -> Map.of("intentId", "pi_1")
        );

        assertThrows(IllegalArgumentException.class, () ->
                idempotencyService.execute(
                        "payments:checkout",
                        "user-1",
                        "idem_mismatch_1",
                        Map.of("bookingId", "bk-2", "method", "pse"),
                        Map.class,
                        () -> Map.of("intentId", "pi_2")
                )
        );
    }

    @Test
    @DisplayName("Pilar 2: Aislamiento criptográfico por actor impide que Usuario B lea la respuesta idempotente de Usuario A")
    void testActorIsolationPreventsCrossUserLeak() {
        Map<String, String> samePayload = Map.of("suiteId", "s-1");

        IdempotencyService.IdempotentResult<BookingDTO> resA = idempotencyService.execute(
                "bookings:create",
                "user-A",
                "shared-client-key",
                samePayload,
                BookingDTO.class,
                () -> BookingDTO.builder().id("bk-for-A").build()
        );

        IdempotencyService.IdempotentResult<BookingDTO> resB = idempotencyService.execute(
                "bookings:create",
                "user-B",
                "shared-client-key",
                samePayload,
                BookingDTO.class,
                () -> BookingDTO.builder().id("bk-for-B").build()
        );

        assertFalse(resA.replayed());
        assertFalse(resB.replayed());
        assertEquals("bk-for-A", resA.body().getId());
        assertEquals("bk-for-B", resB.body().getId());
    }

    @Test
    @DisplayName("Pilar 2: Si la operación falla, se limpia el estado IN_PROGRESS permitiendo reintentar")
    void testErrorReleasesInProgressAllowingRetry() {
        Map<String, String> payload = Map.of("intentId", "pi_err");

        assertThrows(IllegalStateException.class, () ->
                idempotencyService.execute(
                        "payments:confirm",
                        "user-7",
                        "idem_retry_after_error",
                        payload,
                        Map.class,
                        () -> {
                            throw new IllegalStateException("Falla transitoria de red");
                        }
                )
        );

        IdempotencyService.IdempotentResult<Map> retry = idempotencyService.execute(
                "payments:confirm",
                "user-7",
                "idem_retry_after_error",
                payload,
                Map.class,
                () -> Map.of("status", "completed")
        );

        assertFalse(retry.replayed());
        assertEquals("completed", retry.body().get("status"));
    }

    @Test
    @DisplayName("A-6: localStore aplica tope duro de cardinalidad (<= 2000 entradas) con evicción determinista")
    void testLocalStoreEnforcesHardCardinalityCap() {
        IdempotencyService localOnly = new IdempotencyService(null, new ObjectMapper());
        for (int i = 0; i < 2150; i++) {
            final int idx = i;
            localOnly.execute(
                    "bookings:create",
                    "user-" + idx,
                    "idem-cap-" + idx,
                    Map.of("idx", idx),
                    Map.class,
                    () -> Map.of("created", idx)
            );
        }
        assertTrue(localOnly.getLocalStoreSize() <= 2000,
                "localStore no debe superar MAX_LOCAL_ENTRIES (2000), actual=" + localOnly.getLocalStoreSize());
    }

    @Test
    @DisplayName("Bajo #3: Peticiones anónimas desde IPs distintas quedan aisladas y no colisionan en 'anon'")
    void testAnonymousActorScopeIsolatesByClientIp() {
        try {
            org.springframework.mock.web.MockHttpServletRequest reqA = new org.springframework.mock.web.MockHttpServletRequest("POST", "/api/bookings");
            reqA.setRemoteAddr("203.0.113.10");
            org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                    new org.springframework.web.context.request.ServletRequestAttributes(reqA));

            IdempotencyService.IdempotentResult<BookingDTO> resA = idempotencyService.execute(
                    "bookings:create",
                    null,
                    "guest-shared-key",
                    Map.of("suiteId", "s-1"),
                    BookingDTO.class,
                    () -> BookingDTO.builder().id("bk-anon-A").build()
            );

            org.springframework.mock.web.MockHttpServletRequest reqB = new org.springframework.mock.web.MockHttpServletRequest("POST", "/api/bookings");
            reqB.setRemoteAddr("198.51.100.20");
            org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                    new org.springframework.web.context.request.ServletRequestAttributes(reqB));

            IdempotencyService.IdempotentResult<BookingDTO> resB = idempotencyService.execute(
                    "bookings:create",
                    null,
                    "guest-shared-key",
                    Map.of("suiteId", "s-1"),
                    BookingDTO.class,
                    () -> BookingDTO.builder().id("bk-anon-B").build()
            );

            assertFalse(resA.replayed());
            assertFalse(resB.replayed(), "Un huésped anónimo desde otra IP no debe reproducir la reserva ajena");
            assertEquals("bk-anon-A", resA.body().getId());
            assertEquals("bk-anon-B", resB.body().getId());
        } finally {
            org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
        }
    }
}
