package co.palaciodelmar.backend.webhook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias - Validador de Seguridad Anti-SSRF y DNS Rebinding")
class SsrfProtectionValidatorTest {

    private SsrfProtectionValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SsrfProtectionValidator();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost:8080/hook",
            "http://127.0.0.1/hook",
            "http://127.0.0.2:8000/hook",
            "http://0.0.0.0/hook",
            "http://[::1]:8080/hook",
            "http://169.254.169.254/latest/meta-data/",
            "http://metadata.google.internal/computeMetadata/v1/",
            "http://palacio-mongo:27017/db",
            "http://palacio-redis:6379/data",
            "http://backend-java:5000/api",
            "http://mailpit:8025/api",
            "http://internal.service.local/webhook",
            "http://corp.network.internal/hook"
    })
    @DisplayName("Debe bloquear destinos internos y de metadatos de nube prohibidos")
    void testForbiddenInternalHosts(String url) {
        assertFalse(validator.isSafeUrl(url));
        assertThrows(IllegalArgumentException.class, () -> validator.validateUrl(url));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ftp://example.com/webhook",
            "gopher://example.com/webhook",
            "file:///etc/passwd",
            "javascript:alert(1)"
    })
    @DisplayName("Debe rechazar protocolos que no sean HTTP o HTTPS")
    void testNonHttpProtocols(String url) {
        assertFalse(validator.isSafeUrl(url));
        assertThrows(IllegalArgumentException.class, () -> validator.validateUrl(url));
    }

    @ParameterizedTest
    @ValueSource(ints = { 22, 25, 1025, 3306, 5000, 5432, 6379, 8025, 27017 })
    @DisplayName("Debe rechazar puertos sensibles o de infraestructura interna")
    void testForbiddenPorts(int port) {
        String url = "https://example.com:" + port + "/webhook";
        assertFalse(validator.isSafeUrl(url));
        assertThrows(IllegalArgumentException.class, () -> validator.validateUrl(url));
    }

    @Test
    @DisplayName("Debe bloquear dominios que resuelven a IPs privadas (RFC 1918) mediante DNS")
    void testDnsResolvingToPrivateIp() {
        // Simulamos un atacante con DNS controlado que resuelve su dominio a una IP privada 192.168.1.100
        validator.setDnsResolver(host -> {
            try {
                return new InetAddress[]{ InetAddress.getByAddress(new byte[]{(byte) 192, (byte) 168, 1, 100}) };
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            }
        });

        assertFalse(validator.isSafeUrl("https://attacker-rebind.com/webhook"));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> validator.validateUrl("https://attacker-rebind.com/webhook"));
        assertTrue(ex.getMessage().contains("SSRF"));
    }

    @Test
    @DisplayName("Debe bloquear dominios que resuelven a rango 10.0.0.0/8")
    void testDnsResolvingToClassA() {
        validator.setDnsResolver(host -> {
            try {
                return new InetAddress[]{ InetAddress.getByAddress(new byte[]{10, 0, 0, 5}) };
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            }
        });

        assertFalse(validator.isSafeUrl("https://attacker-10.com/webhook"));
    }

    @Test
    @DisplayName("Debe bloquear dominios que resuelven a rango 172.16.0.0/12 (172.18.x.x docker network)")
    void testDnsResolvingToDockerNetwork() {
        validator.setDnsResolver(host -> {
            try {
                return new InetAddress[]{ InetAddress.getByAddress(new byte[]{(byte) 172, 18, 0, 2}) };
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            }
        });

        assertFalse(validator.isSafeUrl("https://attacker-docker.com/webhook"));
    }

    @Test
    @DisplayName("Debe bloquear dominios que resuelven a CGNAT (100.64.0.0/10)")
    void testDnsResolvingToCgnat() {
        validator.setDnsResolver(host -> {
            try {
                return new InetAddress[]{ InetAddress.getByAddress(new byte[]{100, 64, 1, 1}) };
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            }
        });

        assertFalse(validator.isSafeUrl("https://attacker-cgnat.com/webhook"));
    }

    @Test
    @DisplayName("Debe permitir dominios públicos válidos y resolubles")
    void testValidPublicDomains() {
        validator.setDnsResolver(host -> {
            try {
                // IP pública simulada (ejemplo: 93.184.215.14)
                return new InetAddress[]{ InetAddress.getByAddress(new byte[]{93, (byte) 184, (byte) 215, 14}) };
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            }
        });

        assertTrue(validator.isSafeUrl("https://api.pms-partner.com/webhooks"));
        assertEquals("https://api.pms-partner.com/webhooks",
                validator.validateUrl("https://api.pms-partner.com/webhooks"));
    }
}
