package co.palaciodelmar.backend.controller;

import co.palaciodelmar.backend.dto.AuthRequest;
import co.palaciodelmar.backend.dto.AuthResponse;
import co.palaciodelmar.backend.dto.UserDTO;
import co.palaciodelmar.backend.exception.GlobalExceptionHandler;
import co.palaciodelmar.backend.security.TokenBlacklistService;
import co.palaciodelmar.backend.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService, tokenBlacklistService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void loginSetsSecureHttpOnlyLaxCookieWhenForwardedOverHttps() throws Exception {
        when(authService.login(any(AuthRequest.class))).thenReturn(AuthResponse.builder()
                .token("jwt-token")
                .user(UserDTO.builder().id("user-1").email("guest@example.com").build())
                .build());

        var result = mockMvc.perform(post("/api/auth/login")
                        .header("X-Forwarded-Proto", "https")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"guest@example.com\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String cookie = result.getResponse().getHeader("Set-Cookie");
        assertTrue(cookie.contains("jwt=jwt-token"));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("Secure"));
        assertTrue(cookie.contains("SameSite=Lax"));
        assertTrue(cookie.contains("Max-Age=604800"));
    }

    @Test
    void registerSetsJwtCookieWhenServiceReturnsToken() throws Exception {
        when(authService.register(any())).thenReturn(AuthResponse.builder()
                .token("registered-token")
                .user(UserDTO.builder().id("user-1").build())
                .build());

        var result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"guest@example.com\",\"password\":\"secret\",\"name\":\"Guest\"}"))
                .andExpect(status().isOk())
                .andReturn();

        assertTrue(result.getResponse().getHeader("Set-Cookie").contains("jwt=registered-token"));
    }

    @Test
    void verifyEmailRejectsBlankTokenWithoutCallingService() throws Exception {
        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"  \"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    @Test
    void verifyEmailRejectsMissingTokenWithoutCallingService() throws Exception {
        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    @Test
    void resendVerificationRejectsBlankEmailWithoutCallingService() throws Exception {
        mockMvc.perform(post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\" \"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    @Test
    void resendVerificationRejectsMissingEmailWithoutCallingService() throws Exception {
        mockMvc.perform(post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    @Test
    void logoutRevokesBearerTokenAndExpiresJwtCookie() throws Exception {
        var result = mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer revoked-token"))
                .andExpect(status().isOk())
                .andReturn();

        verify(tokenBlacklistService).blacklistToken("revoked-token");
        String cookie = result.getResponse().getHeader("Set-Cookie");
        assertTrue(cookie.contains("jwt="));
        assertTrue(cookie.contains("Max-Age=0"));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("SameSite=Lax"));
    }
}
