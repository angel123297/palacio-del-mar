package co.palaciodelmar.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Palacio del Mar — Marketplace API")
                        .version("1.0.0")
                        .description("API REST para la plataforma multi-anfitrión de hospedaje Palacio del Mar. " +
                                "Incluye gestión de alojamientos, pasarela asíncrona de pagos, " +
                                "webhooks salientes firmados con HMAC-SHA256 y API de integración para sistemas externos (PMS).")
                        .contact(new Contact()
                                .name("Equipo de Desarrollo")
                                .email("soporte@palaciodelmar.co"))
                        .license(new License().name("Apache 2.0").url("https://springdoc.org")))
                .servers(List.of(
                        new Server().url("http://localhost:5000").description("Entorno Local")
                ))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .name("bearerAuth")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Introduce el token JWT obtenido en /api/auth/login"))
                        .addSecuritySchemes("apiKeyAuth", new SecurityScheme()
                                .name("X-API-KEY")
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .description("Clave de integración externa del anfitrión (prefijo pdm_...)"))
                );
    }
}
