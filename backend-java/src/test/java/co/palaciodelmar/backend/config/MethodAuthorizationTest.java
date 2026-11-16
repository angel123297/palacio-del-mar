package co.palaciodelmar.backend.config;

import co.palaciodelmar.backend.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.mock.web.MockServletContext;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MethodAuthorizationTest {
    @Configuration
    static class Fixture {
        @Bean JwtAuthenticationFilter jwtAuthenticationFilter() { return mock(JwtAuthenticationFilter.class); }
        @Bean UserDetailsService userDetailsService() { return mock(UserDetailsService.class); }
        @Bean RestrictedService restrictedService() { return new RestrictedService(); }
    }
    static class RestrictedService {
        @PreAuthorize("hasRole('ADMIN')")
        public String metrics() { return "metrics"; }
    }
    @Test
    void configuredMethodSecurityDeniesUserAndAllowsAdmin() {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(SecurityConfig.class, Fixture.class);
            context.refresh();
            var service = context.getBean(RestrictedService.class);
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    "guest", null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
            assertThrows(AccessDeniedException.class, service::metrics);
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
            assertEquals("metrics", service.metrics());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
