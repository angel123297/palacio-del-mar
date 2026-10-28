package co.palaciodelmar.backend.controller;

import co.palaciodelmar.backend.exception.GlobalExceptionHandler;
import co.palaciodelmar.backend.service.IdempotencyService;
import co.palaciodelmar.backend.service.PaymentGateway;
import co.palaciodelmar.backend.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock
    private PaymentService paymentService;

    @Mock
    private IdempotencyService idempotencyService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        PaymentController controller = new PaymentController(paymentService);
        ReflectionTestUtils.setField(controller, "idempotencyService", idempotencyService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getPaymentConfigReturnsServiceConfiguration() throws Exception {
        when(paymentService.getPaymentConfig()).thenReturn(Map.of("provider", "simulated"));

        mockMvc.perform(get("/api/payments/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.provider").value("simulated"));
    }

    @Test
    void checkoutDefaultsPaymentMethodToCard() throws Exception {
        PaymentGateway.PaymentIntentResponse intent = new PaymentGateway.PaymentIntentResponse(
                "intent-1", "client-secret", new BigDecimal("125000"), "COP", "requires_payment_method");
        when(paymentService.createPaymentIntent(null, "booking-1", "card", null)).thenReturn(intent);

        mockMvc.perform(post("/api/payments/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\":\"booking-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.intentId").value("intent-1"))
                .andExpect(jsonPath("$.data.currency").value("COP"));
    }

    @Test
    void confirmPaymentPassesIntentAndTokenToService() throws Exception {
        when(paymentService.confirmPayment(null, "intent-2", "token-2"))
                .thenReturn(Map.of("paymentStatus", "succeeded"));

        mockMvc.perform(post("/api/payments/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"intentId\":\"intent-2\",\"paymentToken\":\"token-2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("succeeded"));
    }

    @Test
    void paymentStatusIsReturnedForRequestedIntent() throws Exception {
        when(paymentService.getPaymentStatus("intent-3", null))
                .thenReturn(Map.of("paymentStatus", "succeeded"));

        mockMvc.perform(get("/api/payments/status/intent-3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("succeeded"));
    }

    @Test
    void checkoutReplaySetsReplayHeaderWithoutCreatingAnotherIntent() throws Exception {
        PaymentGateway.PaymentIntentResponse cached = new PaymentGateway.PaymentIntentResponse(
                "intent-cached", "client-secret", new BigDecimal("125000"), "COP", "requires_payment_method");
        when(idempotencyService.execute(eq("payments:checkout"), isNull(), eq("payment-key"), anyMap(),
                eq(PaymentGateway.PaymentIntentResponse.class), any()))
                .thenReturn(new IdempotencyService.IdempotentResult<>(cached, true));

        mockMvc.perform(post("/api/payments/checkout")
                        .header("Idempotency-Key", "payment-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\":\"booking-1\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Idempotent-Replay", "true"))
                .andExpect(jsonPath("$.data.intentId").value("intent-cached"));

        verifyNoInteractions(paymentService);
    }
}
