# Plan aprobado: de "Palacio del Mar" a marketplace multi-anfitrión con Webhooks

> Estado: **APROBADO** — Rama de trabajo: `plan/marketplace-webhooks`
> Verificado contra el código real (rama `proyecto-angular-java`).

## Decisiones tomadas

| # | Decisión | Valor |
|---|----------|-------|
| D1 | Rol de Palacio del Mar | **Anfitrión más (seed)**: sus 2 sedes migran a `Property`; la plataforma sigue mostrándolas como propiedades del marketplace |
| D2 | Alcance de entrega | **Fases 1, 2 y 3 completas + demo de Fase 4 (API key + webhooks)**; Fase 5 en paralelo continuo |
| D3 | Integración externa (Fase 4) | **API key + webhooks** (upsert por `externalId` + bloqueos de disponibilidad) |
| D4 | Plazo | **2 a 4 semanas** hasta la sustentación |
| D5 | Datos existentes | Se pueden **resetear** (`DataInitializer` siembra todo); no se requiere migración |

## Calendario sugerido (4 semanas)

| Semana | Fase | Entregable verificable |
|--------|------|------------------------|
| 1 | Fase 1 | Modelo `Property` + CRUD anfitrión + comisión en `Booking` + frontend "Publica tu alojamiento"; los 89 tests siguen en verde |
| 2 | Fase 2 | Intents de pago persistidos en Mongo + confirmación asíncrona vía `POST /api/webhooks/payments` con firma HMAC + idempotencia |
| 3 | Fase 3 | Webhooks salientes con firma, reintentos y log de entregas + UI de suscripciones + receptor demo |
| 4 | Fase 4 (demo) + Fase 5 | API key + endpoints de integración + sitio externo de ejemplo; CI, tests de seguridad, OpenAPI, README honesto |

---

## Fase 1 — Modelo multi-anfitrión (base de todo)

**Tareas**
- [ ] `[NEW]` Modelo `Property`: `id, ownerId, nombre, tipo (HOTEL|ROOM), ciudad, dirección, descripción, imágenes, commissionRate, status (PENDING|APPROVED|REJECTED), externalSiteUrl, createdAt, updatedAt`
- [ ] `[MODIFY]` `Suite`: agregar `propertyId`, `source (NATIVE|EXTERNAL)`, `externalId`; mantener `branch` como deprecated durante la migración o migrarlo a `propertyId`
- [ ] `[MODIFY]` `DataInitializer`: migrar Centro Histórico y Getsemaní a `Property` con ownerId = usuario host seed (D1)
- [ ] `[NEW]` `PropertyRepository` + `PropertyService` + `HostPropertyController` (CRUD de propiedades y suites propias)
- [ ] Seguridad anti-IDOR: todo endpoint de anfitrión valida `property.ownerId == currentUser.id` (o ADMIN)
- [ ] Registro de anfitrión con estado pendiente + aprobación por ADMIN (`AdminController`)
- [ ] `[MODIFY]` `Booking`: agregar `platformFee` y `hostPayout` (comisión simulada 8% vs ~15% de Airbnb, argumento del proyecto)
- [ ] Frontend: formulario "Publica tu alojamiento", panel anfitrión por propiedad, buscador con filtro por ciudad/propiedad

**Criterios de aceptación**
1. Un HOST puede crear/editar su propiedad y sus suites; otro HOST no puede tocarlas (403/404, no 500).
2. Un huésped puede buscar propiedades por ciudad y reservar en cualquiera.
3. `Booking` guarda `platformFee` y `hostPayout` calculados al confirmar precio.
4. `./mvnw test`: los 89 tests existentes + los nuevos en verde.

---

## Fase 2 — Pagos simulados realistas con webhook (dirección C)

**Tareas**
- [ ] `[MODIFY]` `SimulatedPaymentGateway`: persistir intents en Mongo (colección `payment_intents`); `confirmIntent` pasa a `processing` y tras 2-5 s (`@Async`/`@Scheduled`) el simulador emite el webhook
- [ ] `[NEW]` `PaymentWebhookController` (`POST /api/webhooks/payments`): valida firma HMAC-SHA256, idempotencia por `eventId`, actualiza `Booking`/`Payment`
- [ ] Eventos: `payment.succeeded`, `payment.failed`, `refund.processed`
- [ ] Corregir el stub de `refund()`: debe validar el `transactionId` y poder fallar
- [ ] Frontend: estado "Procesando pago…" con polling/SSE
- [ ] Mantener tarjetas de prueba: `0002` (rechazada), `9995` (fondos), `0069` (vencida)

**Criterios de aceptación**
1. Reiniciar el backend no pierde los intents pendientes.
2. Un webhook con firma inválida → 401/400 y no cambia el estado de la reserva.
3. El mismo `eventId` reenviado no duplica efectos (idempotencia demostrable en test).
4. El pago pasa por `processing` antes de `succeeded` (flujo asíncrono observable).

---

## Fase 3 — Webhooks salientes (dirección A)

**Tareas**
- [ ] `[NEW]` Modelos `WebhookSubscription` (`propertyId, url, secret, eventos[], activo`) y `WebhookDelivery` (`eventId, evento, payload, intentos, estado, códigoRespuesta, timestamps`)
- [ ] `[NEW]` `WebhookDispatcher`: escucha eventos de dominio (`ApplicationEventPublisher`) desde `BookingService`/`PaymentService`; envía con `RestClient` y cabeceras `X-Palacio-Signature` (`HMAC-SHA256` del payload), `X-Palacio-Event-Id`, `X-Palacio-Event-Type`, `X-Palacio-Timestamp`
- [ ] Reintentos con backoff: 1 min, 5 min, 30 min (scheduler sobre `WebhookDelivery` en estado PENDING/FAILED)
- [ ] Eventos: `booking.created`, `booking.confirmed`, `booking.cancelled`, `payment.succeeded`, `payment.failed`
- [ ] UI anfitrión: registrar/editar URL, ver historial de entregas, botones "reenviar" y "enviar evento de prueba"
- [ ] `[NEW]` Receptor demo (`/demo/receiver` o mini servidor Node) que verifica la firma y registra entregas, para la sustentación

**Criterios de aceptación**
1. Al confirmar una reserva, el receptor demo recibe el evento con firma verificable.
2. Si el receptor responde 500/no responde, hay reintentos visibles con backoff y el estado queda FAILED tras agotar intentos.
3. Reenviar una entrega funciona desde la UI.

---

## Fase 4 (demo) — Integración con la web del anfitrión (dirección B, opción API key)

**Tareas**
- [ ] `[NEW]` `ApiKeyService`: generación/revocación de API keys por propiedad (hash almacenado, prefijo `pdm_`)
- [ ] `[NEW]` Endpoints de integración autenticados por API key (no JWT): `POST /api/integration/rooms` (upsert por `externalId` con `source=EXTERNAL`), `PUT /api/integration/availability` (bloqueo de noches)
- [ ] Anti-overbooking: los bloqueos entrantes usan `SuiteNight` igual que las reservas nativas
- [ ] `[NEW]` Sitio externo de ejemplo (HTML+JS simple en `/demo/external-hotel`) que llama a la API y recibe webhooks
- [ ] Fuera de alcance de esta entrega (no hacer): widget embebible y feed iCal

**Criterios de aceptación**
1. El sitio externo crea/actualiza una habitación vía API key.
2. Un bloqueo de disponibilidad desde fuera impide una reserva nativa de esas noches (sin overbooking).
3. Una API key inválida/recotida es rechazada.

---

## Fase 5 — Calidad de software (en paralelo, cerrar en semana 4)

- [ ] Tests de seguridad por rol (`@WebMvcTest`/MockMvc): IDOR, endpoints ADMIN/HOST/USER
- [ ] Tests de webhooks: firma, idempotencia, reintentos; integración con Testcontainers (Mongo)
- [ ] CI: GitHub Actions (backend `./mvnw verify` + frontend `npm ci && npm run build`); JaCoCo
- [ ] OpenAPI (springdoc) incluyendo contrato de webhooks (payloads y cabeceras)
- [ ] README: quitar claims no respaldados ("grado empresarial, alta disponibilidad"), corregir el enlace roto a `CORRECCIONES.md` (apunta a `file:///home/angel19/...`), documentar el flujo de webhooks
- [ ] Higiene: eliminar `backend.log` del repo y agregarlo a `.gitignore`
- [ ] Observabilidad: Actuator health, correlation id en logs, rate limiting en login y endpoints de integración
- [ ] Revisar las 3 issues abiertas del repo antes de cerrar la entrega

---

## Verificación continua (cada fase)

```bash
cd backend-java && ./mvnw test        # 89 existentes + nuevos, SIEMPRE en verde
cd frontend-angular && npm run build  # build limpio
docker compose up -d --build          # humo de integración
```

**Escenario E2E para la sustentación**: crear anfitrión → publicar propiedad → registrar webhook hacia el receptor demo → reservar como huésped → ver el pago pasar de `processing` a `succeeded` → ver el evento llegar firmado al receptor → simular fallo del receptor → observar reintentos → bloquear noches desde el "sitio externo" → intentar reservar esas noches → ver el anti-overbooking actuar.
