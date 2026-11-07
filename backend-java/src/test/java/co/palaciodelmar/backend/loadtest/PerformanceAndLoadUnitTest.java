package co.palaciodelmar.backend.loadtest;

import co.palaciodelmar.backend.controller.SuiteController;
import co.palaciodelmar.backend.dto.ApiResponse;
import co.palaciodelmar.backend.dto.CalculatePriceRequest;
import co.palaciodelmar.backend.dto.CalculatePriceResponseDTO;
import co.palaciodelmar.backend.dto.SuiteResponseDTO;
import co.palaciodelmar.backend.service.ICalService;
import co.palaciodelmar.backend.service.SuiteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * ============================================================================
 * PRUEBAS DE CARGA Y RENDIMIENTO (Performance, Concurrency & Stress Benchmark)
 * Tecnologías evaluadas: JUnit 5, Java Concurrency (ExecutorService, CountDownLatch)
 * ============================================================================
 * Simula el comportamiento de los endpoints más transitados bajo alta concurrencia:
 * 1. Catálogo de Suites (50 hilos concurrentes x 10 = 500 solicitudes simultáneas).
 * 2. Cotizador dinámico de tarifas (30 hilos concurrentes x 10 = 300 solicitudes).
 * Evalúa: Throughput (solicitudes/segundo), Latencia P50, P95, P99 y Tasa de Fallos (0%).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas de Rendimiento y Concurrencia - Catálogo y Cotizador")
class PerformanceAndLoadUnitTest {

    @Mock
    private SuiteService suiteService;

    @Mock
    private ICalService iCalService;

    @InjectMocks
    private SuiteController suiteController;

    private SuiteResponseDTO sampleSuite;

    @BeforeEach
    void setUp() {
        sampleSuite = SuiteResponseDTO.builder()
                .id("suite-perf-101")
                .name("Suite Imperial Frente al Mar")
                .type("Presidencial")
                .basePrice(new BigDecimal("1200000"))
                .maxGuests(4)
                .active(true)
                .build();
    }

    @Test
    @DisplayName("Carga Concurrente: 50 hilos concurrentes simulando 500 consultas a getAllSuites()")
    void deberiaSoportarAltaConcurrenciaEnCatalogoSuites() throws InterruptedException {
        int concurrency = 50;
        int totalRequests = 500;

        when(suiteService.getAllActiveSuites(null)).thenReturn(List.of(sampleSuite));

        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch readyLatch = new CountDownLatch(concurrency);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalRequests);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Long> latencies = Collections.synchronizedList(new ArrayList<>(totalRequests));

        long testStartTime = System.nanoTime();

        for (int i = 0; i < totalRequests; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    long start = System.nanoTime();
                    ResponseEntity<ApiResponse<List<SuiteResponseDTO>>> response = suiteController.getAllSuites(null);
                    long durationMs = (System.nanoTime() - start) / 1_000_000;
                    latencies.add(durationMs);

                    if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // Dispara todos los hilos simultáneamente
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        long totalDurationMs = (System.nanoTime() - testStartTime) / 1_000_000;

        assertThat(completed).isTrue();
        assertThat(failureCount.get()).isZero();
        assertThat(successCount.get()).isEqualTo(totalRequests);

        // Métricas de Rendimiento
        Collections.sort(latencies);
        long p50 = latencies.get((int) (latencies.size() * 0.50));
        long p95 = latencies.get((int) (latencies.size() * 0.95));
        long p99 = latencies.get((int) (latencies.size() * 0.99));
        double throughput = (totalRequests / (Math.max(1, totalDurationMs) / 1000.0));

        System.out.printf("[BENCHMARK CATALOGO] Solicitudes: %d | Éxitos: %d | Fallos: %d%n", totalRequests, successCount.get(), failureCount.get());
        System.out.printf("[BENCHMARK CATALOGO] Latencia P50: %d ms | P95: %d ms | P99: %d ms | Throughput: %.2f req/s%n", p50, p95, p99, throughput);

        // Aserciones de SLO (Service Level Objective): P95 < 200ms
        assertThat(p95).isLessThan(200);
    }

    @Test
    @DisplayName("Carga Concurrente: 30 hilos concurrentes calculando precios simultáneamente (POST /calculate-price)")
    void deberiaSoportarCargaConcurrenteEnCalculoDePrecios() throws InterruptedException {
        int concurrency = 30;
        int totalRequests = 300;

        CalculatePriceResponseDTO mockResponse = CalculatePriceResponseDTO.builder()
                .suiteId("suite-perf-101")
                .nightlyPrice(new BigDecimal("1200000"))
                .subtotal(new BigDecimal("3600000"))
                .discount(BigDecimal.ZERO)
                .total(new BigDecimal("3600000"))
                .build();

        when(suiteService.calculatePrice(any(CalculatePriceRequest.class))).thenReturn(mockResponse);

        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalRequests);

        AtomicInteger successCount = new AtomicInteger(0);
        List<Long> latencies = Collections.synchronizedList(new ArrayList<>(totalRequests));

        CalculatePriceRequest request = CalculatePriceRequest.builder()
                .suiteId("suite-perf-101")
                .checkIn(LocalDate.now().plusDays(2))
                .checkOut(LocalDate.now().plusDays(5))
                .build();

        for (int i = 0; i < totalRequests; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    long start = System.nanoTime();
                    ResponseEntity<ApiResponse<CalculatePriceResponseDTO>> response = suiteController.calculatePrice(request);
                    long durationMs = (System.nanoTime() - start) / 1_000_000;
                    latencies.add(durationMs);

                    if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // ignore for metric
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(totalRequests);

        Collections.sort(latencies);
        long p95 = latencies.get((int) (latencies.size() * 0.95));
        System.out.printf("[BENCHMARK COTIZADOR] Solicitudes: %d | Éxitos: %d | P95: %d ms%n", totalRequests, successCount.get(), p95);
        assertThat(p95).isLessThan(200);
    }
}
