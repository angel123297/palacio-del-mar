package co.palaciodelmar.backend.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RedisLockServiceTest {

    @Test
    @DisplayName("Pilar 1: Adquiere lock distribuido en Redis con SET NX PX y libera con Lua CAS")
    @SuppressWarnings("unchecked")
    void testAcquireAndReleaseRedisLock() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq("palacio:lock:suite:s-101"), anyString(), eq(Duration.ofSeconds(10))))
                .thenReturn(true);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(1L);

        RedisLockService lockService = new RedisLockService(redisTemplate);

        String result = lockService.executeWithLock(
                "suite:s-101",
                Duration.ofSeconds(10),
                Duration.ofSeconds(2),
                () -> "BOOKING_CREATED"
        );

        assertEquals("BOOKING_CREATED", result);
        verify(valueOps).setIfAbsent(eq("palacio:lock:suite:s-101"), anyString(), eq(Duration.ofSeconds(10)));
        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of("palacio:lock:suite:s-101")), anyString());
    }

    @Test
    @DisplayName("Pilar 1: Contención multi-hilo lanza HTTP 409 Conflict cuando el lock no se libera a tiempo")
    void testConcurrentContentionThrows409OnTimeout() throws Exception {
        RedisLockService lockService = new RedisLockService(null); // Respaldo L1 real con ReentrantLock
        CountDownLatch holderEntered = new CountDownLatch(1);
        CountDownLatch releaseHolder = new CountDownLatch(1);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> firstHolder = pool.submit(() -> lockService.executeWithLock(
                    "suite:penthouse",
                    Duration.ofSeconds(5),
                    Duration.ofMillis(500),
                    () -> {
                        holderEntered.countDown();
                        try {
                            releaseHolder.await(3, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return null;
                    }
            ));

            assertTrue(holderEntered.await(2, TimeUnit.SECONDS));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                    lockService.executeWithLock(
                            "suite:penthouse",
                            Duration.ofSeconds(5),
                            Duration.ofMillis(60),
                            () -> "SHOULD_NOT_RUN"
                    )
            );
            assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());

            releaseHolder.countDown();
            firstHolder.get(2, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("Pilar 1: 10 hilos concurrentes se serializan sin condiciones de carrera cuando el tiempo de espera es suficiente")
    void testTenConcurrentThreadsSerializeCleanly() throws Exception {
        RedisLockService lockService = new RedisLockService(null);
        AtomicInteger activeInsideCriticalSection = new AtomicInteger(0);
        AtomicInteger maxObservedConcurrency = new AtomicInteger(0);
        AtomicInteger completedCount = new AtomicInteger(0);

        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    startGate.await();
                    lockService.executeWithLock("suite:shared-unit", Duration.ofSeconds(5), Duration.ofSeconds(3), () -> {
                        int cur = activeInsideCriticalSection.incrementAndGet();
                        maxObservedConcurrency.accumulateAndGet(cur, Math::max);
                        try {
                            Thread.sleep(8);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            activeInsideCriticalSection.decrementAndGet();
                        }
                        completedCount.incrementAndGet();
                        return null;
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startGate.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
        pool.shutdownNow();

        assertEquals(1, maxObservedConcurrency.get(), "Nunca debe haber más de 1 hilo dentro de la sección crítica");
        assertEquals(threads, completedCount.get());
    }

    @Test
    @DisplayName("Pilar 1: Degrada limpiamente a L1 si Redis lanza excepción de conexión")
    @SuppressWarnings("unchecked")
    void testGracefulFallbackWhenRedisFails() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RedisConnectionFailureException("Redis down"));

        RedisLockService lockService = new RedisLockService(redisTemplate);

        String res = lockService.executeWithLock("suite:fallback", Duration.ofSeconds(5), Duration.ofMillis(200), () -> "OK_L1");
        assertEquals("OK_L1", res);
    }

    @Test
    @DisplayName("Pilar 1: tryLock no bloqueante y retención/liberación de noches (holdSuiteDates / releaseSuiteHold)")
    @SuppressWarnings("unchecked")
    void testTryLockAndSuiteDateHolds() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true)
                .thenReturn(false);

        RedisLockService lockService = new RedisLockService(redisTemplate);

        Optional<String> tok1 = lockService.tryLock("webhook:deliv-1", Duration.ofSeconds(10));
        Optional<String> tok2 = lockService.tryLock("webhook:deliv-1", Duration.ofSeconds(10));
        assertTrue(tok1.isPresent());
        assertTrue(tok2.isEmpty());

        lockService.unlock("webhook:deliv-1", tok1.get());
        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of("palacio:lock:webhook:deliv-1")), eq(tok1.get()));

        LocalDate checkIn = LocalDate.of(2026, 12, 10);
        LocalDate checkOut = LocalDate.of(2026, 12, 12);
        lockService.holdSuiteDates("s-1", 1, checkIn, checkOut, "bk-99", Duration.ofMinutes(15));
        verify(valueOps).set("palacio:hold:suite:s-1:slot:1:2026-12-10", "bk-99", Duration.ofMinutes(15));
        verify(valueOps).set("palacio:hold:suite:s-1:slot:1:2026-12-11", "bk-99", Duration.ofMinutes(15));

        lockService.releaseSuiteHold("s-1", 1, checkIn, checkOut, "bk-99");
        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of("palacio:hold:suite:s-1:slot:1:2026-12-10")), eq("bk-99"));
        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of("palacio:hold:suite:s-1:slot:1:2026-12-11")), eq("bk-99"));
    }

    @Test
    @DisplayName("Pilar 1: executeWithStrictDistributedLock lanza 503 SERVICE_UNAVAILABLE y rehúsa fallback local cuando Redis está caído")
    @SuppressWarnings("unchecked")
    void testStrictDistributedLockRejectsLocalFallbackWhenRedisDown() {
        StringRedisTemplate failingRedis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valOps = mock(ValueOperations.class);
        when(failingRedis.opsForValue()).thenReturn(valOps);
        when(valOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RedisConnectionFailureException("Cluster down"));

        RedisLockService lockService = new RedisLockService(failingRedis);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                lockService.executeWithStrictDistributedLock("suite:critical", Duration.ofSeconds(5), Duration.ofMillis(100), () -> "CRITICAL_ACTION")
        );

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Falla de conectividad con Redis durante bloqueo distribuido estricto"));
    }
}
