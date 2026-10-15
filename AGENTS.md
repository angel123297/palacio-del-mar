# AGENTS.md — Reglas para agentes de IA (Antigravity / Cursor / Claude Code)

## Contexto del proyecto
Marketplace de hospedaje estilo Airbnb con comisión baja. Backend Spring Boot 3 / Java 21, MongoDB, Redis opcional, JWT. Frontend Angular 18 (standalone + signals). Actualmente monohotel ("Palacio del Mar"); se transforma en marketplace multi-anfitrión con webhooks siguiendo `PLAN_MARKETPLACE.md` (aprobado, rama `plan/marketplace-webhooks`).

## Comandos
```bash
# Backend (desde backend-java/)
./mvnw test                 # suite de tests: 89 existentes + nuevos. DEBEN quedar en verde.
./mvnw spring-boot:run      # levanta en :5000 (requiere Mongo; usa docker-compose.yml)

# Frontend (desde frontend-angular/)
npm install
npm run build              # debe compilar limpio
npm start                  # :4200

# Stack completo (raíz)
docker compose up -d --build
```

## Reglas obligatorias
1. **Nunca romper los tests existentes.** Si un cambio requiere modificar un test existente, explica por qué en el commit.
2. **Una fase = una rama = PRs pequeños**: `feat/fase1-property-model`, `feat/fase2-payment-webhooks`, `feat/fase3-outgoing-webhooks`, `feat/fase4-integration-api`, `chore/fase5-ci-quality`. Trabajar SIEMPRE sobre `plan/marketplace-webhooks`.
3. **Anti-IDOR**: todo endpoint de HOST debe validar `property.ownerId == usuario autenticado` antes de leer/escribir. Un HOST nunca accesa recursos de otro HOST: responder 404 (no revelar existencia) o 403.
4. **Concurrencia**: mantener el patrón `SuiteNight` + `@Version` para toda ocupación de noches, incluidos los bloqueos que llegan por webhook/integración.
5. **Convenciones de código**: paquetes bajo `co.palaciodelmar.backend.{model,repository,service,controller,dto,security,config,scheduler}`; Lombok (`@Data @Builder`); respuestas envueltas en `ApiResponse`; DTOs para toda entrada/salida HTTP (nunca exponer entidades).
6. **Secretos**: nunca al repo. Usar `.env` y `application.yml` con `${VAR}`. Las firmas de webhook requieren secrets generados por suscripción.
7. **Webhooks (contrato)**: firma HMAC-SHA256 del body en `X-Palacio-Signature`; `X-Palacio-Event-Id` para idempotencia; `X-Palacio-Timestamp` contra replay; reintentos 1m/5m/30m; registrar toda entrega en `WebhookDelivery`.
8. **No hacer en esta entrega**: widget embebible, feed iCal, pasarela de pago real, chat de IA. Está en `PLAN_MARKETPLACE.md` § Fase 4.
9. **Idioma**: nombres de código en inglés; comentarios y mensajes de usuario en español.

## Guía por fase (archivos clave)
- **Fase 1**: `model/Suite.java` (agregar `propertyId, source, externalId`), `model/User.java` (`branches` → reemplazo gradual por `Property.ownerId`), `config/DataInitializer.java` (migrar sedes a `Property`), `controller/HostController.java` (base del nuevo `HostPropertyController`).
- **Fase 2**: `service/SimulatedPaymentGateway.java` (ConcurrentHashMap → colección Mongo; `confirmIntent` → asíncrono), `service/PaymentGateway.java` (interfaz: mantener).
- **Fase 3**: nuevo paquete `webhook/` o `service/WebhookDispatcher`; eventos desde `service/BookingService.java` vía `ApplicationEventPublisher`.
- **Fase 5**: no existe `.github/workflows` — crear CI desde cero; eliminar `backend.log`; arreglar README (enlace roto a CORRECCIONES.md y claims de "enterprise").

## Definición de terminado (por fase)
1. Tests de la fase en verde + los 89 existentes en verde.
2. `npm run build` limpio.
3. Criterios de aceptación de la fase (en `PLAN_MARKETPLACE.md`) cumplidos y demostrables.
4. PR a `plan/marketplace-webhooks` con descripción de qué cambió y cómo verificarlo.
