package co.palaciodelmar.backend.webhook;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/**
 * Validador estricto de protección contra SSRF (Server-Side Request Forgery) y DNS Rebinding.
 * Aplica validación sintáctica de protocolo, host y puertos prohibidos, así como resolución DNS
 * de direcciones IP para bloquear rangos privados (RFC 1918), loopback, link-local, CGNAT,
 * metadatos de nube (AWS/GCP/Azure) y servicios internos.
 */
@Slf4j
@Component
public class SsrfProtectionValidator {

    @FunctionalInterface
    public interface DnsResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    public record ValidatedTarget(URI uri, String host, int port, boolean https, InetAddress pinnedAddress) {}

    private DnsResolver dnsResolver = InetAddress::getAllByName;

    public void setDnsResolver(DnsResolver dnsResolver) {
        this.dnsResolver = dnsResolver != null ? dnsResolver : InetAddress::getAllByName;
    }

    private static final Set<String> FORBIDDEN_HOSTS = Set.of(
            "localhost", "127.0.0.1", "0.0.0.0", "::1",
            "169.254.169.254", "metadata.google.internal", "instance-data",
            "mongo", "palacio-mongo",
            "redis", "palacio-redis",
            "backend-java", "palacio-backend-java",
            "web-angular", "palacio-web-angular",
            "mailpit", "palacio-mailpit"
    );

    private static final Set<Integer> FORBIDDEN_PORTS = Set.of(
            22, 23, 25, 110, 143, 1025, 3306, 5000, 5432, 6379, 8025, 27017
    );

    /**
     * Valida una URL contra SSRF. Lanza {@link IllegalArgumentException} si la URL viola las políticas de seguridad.
     *
     * @param rawUrl la URL a validar
     * @return la URL sanitizada y normalizada
     */
    public String validateUrl(String rawUrl) {
        return resolveAndValidate(rawUrl).uri().toString();
    }

    /**
     * Valida la URL y resuelve el host una sola vez devolviendo la IP pública fijada (pinned)
     * para impedir ataques TOCTOU de DNS Rebinding durante la conexión saliente.
     */
    public ValidatedTarget resolveAndValidate(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("La URL del webhook es obligatoria");
        }
        String trimmed = rawUrl.trim();
        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (Exception e) {
            throw new IllegalArgumentException("Formato de URL de webhook inválido: " + e.getMessage());
        }

        String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : "";
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("La URL del webhook debe usar protocolo http o https");
        }

        String host = uri.getHost() != null ? uri.getHost().toLowerCase(Locale.ROOT) : "";
        if (host.isBlank()) {
            throw new IllegalArgumentException("La URL del webhook debe incluir un host válido");
        }

        // 1. Verificación de lista negra de hosts y sufijos locales
        if (FORBIDDEN_HOSTS.contains(host)
                || host.endsWith(".local")
                || host.endsWith(".internal")
                || host.endsWith(".localhost")) {
            throw new IllegalArgumentException("URL de webhook no permitida: apunta a un servicio interno o reservado (" + host + ")");
        }

        // 2. Verificación de puertos prohibidos
        boolean https = "https".equals(scheme);
        int port = uri.getPort() > 0 ? uri.getPort() : (https ? 443 : 80);
        if (FORBIDDEN_PORTS.contains(port)) {
            throw new IllegalArgumentException("Puerto de destino no permitido para webhooks: " + port);
        }

        // 3. Resolución DNS única y verificación estricta de todas las direcciones IP devueltas
        try {
            InetAddress[] addresses = dnsResolver.resolve(host);
            if (addresses == null || addresses.length == 0) {
                throw new IllegalArgumentException("No se pudo resolver el host del webhook: " + host);
            }

            for (InetAddress address : addresses) {
                assertSafeAddress(address);
            }
            return new ValidatedTarget(uri, host, port, https, addresses[0]);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Host de webhook no reconocible o inalcanzable por DNS: " + host);
        }
    }

    /**
     * Comprueba si una URL es segura sin lanzar excepción.
     *
     * @param rawUrl la URL a comprobar
     * @return true si es segura, false si viola SSRF o no se puede resolver
     */
    public boolean isSafeUrl(String rawUrl) {
        try {
            validateUrl(rawUrl);
            return true;
        } catch (Exception e) {
            log.warn("URL insegura detectada por validador SSRF ({}): {}", rawUrl, e.getMessage());
            return false;
        }
    }

    private void assertSafeAddress(InetAddress address) {
        if (address.isLoopbackAddress()) {
            throw new IllegalArgumentException("Destino bloqueado por SSRF: dirección de loopback local");
        }
        if (address.isAnyLocalAddress()) {
            throw new IllegalArgumentException("Destino bloqueado por SSRF: dirección any-local wildcard");
        }
        if (address.isSiteLocalAddress()) {
            throw new IllegalArgumentException("Destino bloqueado por SSRF: dirección IP de red privada (RFC 1918)");
        }
        if (address.isLinkLocalAddress()) {
            throw new IllegalArgumentException("Destino bloqueado por SSRF: dirección IP link-local o metadatos de nube");
        }
        if (address.isMulticastAddress()) {
            throw new IllegalArgumentException("Destino bloqueado por SSRF: dirección de multicast");
        }

        byte[] ip = address.getAddress();

        // Verificación de IPv4
        if (ip.length == 4) {
            int b0 = ip[0] & 0xFF;
            int b1 = ip[1] & 0xFF;

            // 0.0.0.0/8
            if (b0 == 0) {
                throw new IllegalArgumentException("Destino bloqueado por SSRF: red reservada 0.0.0.0/8");
            }
            // 127.0.0.0/8 (Loopback extendido)
            if (b0 == 127) {
                throw new IllegalArgumentException("Destino bloqueado por SSRF: rango loopback 127.0.0.0/8");
            }
            // 10.0.0.0/8
            if (b0 == 10) {
                throw new IllegalArgumentException("Destino bloqueado por SSRF: red privada 10.0.0.0/8");
            }
            // 172.16.0.0/12 (172.16 - 172.31)
            if (b0 == 172 && (b1 >= 16 && b1 <= 31)) {
                throw new IllegalArgumentException("Destino bloqueado por SSRF: red privada 172.16.0.0/12");
            }
            // 192.168.0.0/16
            if (b0 == 192 && b1 == 168) {
                throw new IllegalArgumentException("Destino bloqueado por SSRF: red privada 192.168.0.0/16");
            }
            // 169.254.0.0/16 (Link local / AWS Metadata)
            if (b0 == 169 && b1 == 254) {
                throw new IllegalArgumentException("Destino bloqueado por SSRF: rango de metadatos o link-local 169.254.0.0/16");
            }
            // 100.64.0.0/10 (Carrier-Grade NAT: 100.64.0.0 - 100.127.255.255)
            if (b0 == 100 && (b1 >= 64 && b1 <= 127)) {
                throw new IllegalArgumentException("Destino bloqueado por SSRF: red CGNAT 100.64.0.0/10");
            }
        }

        // Verificación de IPv6
        if (address instanceof Inet6Address) {
            // IPv6 Unique Local Address fc00::/7 (prefijos fc o fd)
            int firstByte = ip[0] & 0xFF;
            if (firstByte == 0xFC || firstByte == 0xFD) {
                throw new IllegalArgumentException("Destino bloqueado por SSRF: dirección IPv6 local única (fc00::/7)");
            }
            // IPv4-mapped IPv6 (::ffff:x.x.x.x)
            if (isIpv4MappedIpv6(ip)) {
                byte[] mappedIpv4 = Arrays.copyOfRange(ip, 12, 16);
                try {
                    assertSafeAddress(InetAddress.getByAddress(mappedIpv4));
                } catch (UnknownHostException ignored) {}
            }
        }
    }

    private boolean isIpv4MappedIpv6(byte[] ip) {
        if (ip.length != 16) return false;
        for (int i = 0; i < 10; i++) {
            if (ip[i] != 0) return false;
        }
        return (ip[10] & 0xFF) == 0xFF && (ip[11] & 0xFF) == 0xFF;
    }
}
