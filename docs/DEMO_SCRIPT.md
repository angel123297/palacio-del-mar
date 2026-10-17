# 🎬 Playbook de Demostración E2E — Sustentación Palacio del Mar

> **Guía oficial paso a paso para la defensa en vivo ante el jurado y el equipo**  
> **Duración estimada de la demo:** 8 a 12 minutos  
> **Stack:** Spring Boot 3 (Java 21) · Angular 18 (Signals) · MongoDB 7 · Redis 7 · Mailpit SMTP

---

## 🔑 1. Credenciales y Recursos de Prueba

### Cuentas Sembradas Automáticamente (`DataInitializer`)

| Rol | Correo Electrónico | Contraseña | Permisos Clave |
|---|---|---|---|
| **👑 Administrador (`ADMIN`)** | `admin@palaciodelmar.co` | `Admin123456!` | Moderar propiedades (`PENDING` ➔ `APPROVED`), métricas globales, promociones, forzar liquidaciones. |
| **🛎️ Anfitrión (`HOST`)** | `anfitrion@palaciodelmar.co` | `Host123456!` | Crear propiedades/habitaciones, suscribir Webhooks HMAC, generar API Keys (`pdm_...`), check-in/out y PINs. |
| **🌴 Huésped (`USER`)** | `huesped@palaciodelmar.co` | `User123456!` | Explorar catálogo, reservar suites, checkout asíncrono, pre-check-in digital y ver voucher con QR/PIN. |

### Tarjetas de Prueba (Algoritmo de Luhn Válido)

| Escenario | Número de Tarjeta | Fecha Exp. | CVV | Resultado Esperado |
|---|---|---|---|---|
| **✅ Pago Exitoso** | `4242 4242 4242 4242` | `12/28` | `123` | Pasa por `processing` ➔ Webhook HMAC `payment.succeeded` ➔ Reserva `confirmed`. |
| **❌ Tarjeta Rechazada** | `4000 0000 0000 0002` | `12/28` | `123` | Webhook `payment.failed` ➔ Reserva se mantiene pendiente / libera bloqueo al expirar. |
| **💸 Fondos Insuficientes** | `4000 0000 0000 9995` | `12/28` | `123` | Rechazo simulado por falta de cupo bancario. |

### URLs del Ecosistema Local

- **Aplicación Web (Angular 18):** `http://localhost:4200`
- **Portal PMS Hotel Aliado (Demo Fase 4):** `http://localhost:4200/demo/external-hotel`
- **API Backend & OpenAPI Swagger:** `http://localhost:5000/swagger-ui.html`
- **Health Check (Actuator):** `http://localhost:5000/actuator/health`
- **Bandeja de Correos (Mailpit):** `http://localhost:8025`
- **Diagrama Interactivo de Arquitectura:** `docs/architecture.html`
- **Diagrama Interactivo de Secuencia Lógica:** `docs/sequence.html`

---

## 🚀 2. Guion Paso a Paso para la Sustentación en Vivo

### Paso 1: Mostrar la Arquitectura y el Ciclo de Vida Lógico (1 min)
1. Abrir en el navegador `docs/architecture.html` y `docs/sequence.html`.
2. **Discurso clave:**
   > *"Transformamos un sistema monolítico de un solo hotel en un **Marketplace Multi-Anfitrión Descentralizado** con una comisión competitiva del **8%** (frente al ~15-20% tradicional), donde el anfitrión recibe el **92%** de cada reserva y puede integrar su propio software hotelero (PMS) mediante API Keys y Webhooks firmados con HMAC-SHA256."*

---

### Paso 2: Publicación de Propiedad por el Anfitrión y Moderación Admin (2 min)
1. Iniciar sesión como **Anfitrión** (`anfitrion@palaciodelmar.co` / `Host123456!`).
2. Ir al **Panel de Anfitrión** (`/host`) ➔ pestaña **Propiedades**.
3. Crear un nuevo alojamiento (ej. *"Villa Coralina Islas del Rosario"*) y agregarle una habitación.
   - Explicar que por seguridad todo nuevo alojamiento nace en estado `PENDING` hasta ser auditado.
   - Mencionar el blindaje **Anti-IDOR**: ningún anfitrión puede leer ni modificar propiedades o reservas de otro anfitrión (`property.ownerId == currentUser.id`).
4. Abrir una ventana de incógnito (o cambiar de cuenta) e iniciar sesión como **Admin** (`admin@palaciodelmar.co` / `Admin123456!`).
5. Ir al **Panel Admin** (`/admin`) ➔ pestaña **Alojamientos** ➔ Aprobar la propiedad (`APPROVED`).
6. Mostrar cómo aparece inmediatamente en el buscador público del marketplace.

---

### Paso 3: Configurar Webhooks Salientes y API Key para Hotel Externo (2 min)
1. De vuelta en el **Panel de Anfitrión** (`/host`):
   - Ir a la pestaña **Webhooks**: crear una suscripción apuntando al receptor de demostración interno:
     ```text
     http://localhost:5000/api/demo/webhook-receiver
     ```
     *(Nota: explicar que `HostWebhookService` cuenta con protección **Anti-SSRF** que bloquea IPs privadas maliciosas, metadatos cloud `169.254.169.254` y esquemas no HTTP, permitiendo solo el receptor demo controlado en entorno local).*
   - Hacer clic en **"Enviar evento de prueba"** y mostrar en el historial de entregas el código `HTTP 200`, el ID de evento (`X-Palacio-Event-Id`) y la firma `X-Palacio-Signature`.
2. Ir a la pestaña **API Keys**:
   - Generar una nueva clave (`pdm_...`) para la propiedad y copiarla.
   - Mencionar que el backend **nunca guarda la clave en texto plano**, sino su hash `SHA-256`.

---

### Paso 4: Sincronización desde el PMS Externo y Prueba Anti-Overbooking (2 min)
1. Abrir `http://localhost:4200/demo/external-hotel`.
2. Pegar la **API Key (`pdm_...`)** recién generada.
3. **Sincronizar Habitación (`POST /api/integration/rooms`):** Enviar un `externalId` (ej. `PMS-SUITE-101`).
4. **Bloquear Fechas (`PUT /api/integration/availability`):** Bloquear un rango de 2 noches próximas desde el sistema externo.
5. **Demostrar Anti-Overbooking:**
   - Ir al catálogo público como Huésped e intentar reservar esa misma habitación en las fechas bloqueadas.
   - Mostrar cómo el motor transaccional basado en documentos **`SuiteNight` con índice único compuesto `{suite, unitSlot, date}` y bloqueo optimista `@Version`** impide el overbooking en tiempo real.

---

### Paso 5: Reserva de Huésped, Pago Asíncrono con HMAC y Ciclo Completo (3 min)
1. Iniciar sesión como **Huésped** (`huesped@palaciodelmar.co` / `User123456!`).
2. Seleccionar una suite con fechas libres y pasar al **Checkout**:
   - Mostrar el desglose transparente de tarifas (subtotal, impuestos, promociones y comisión interna 8% / 92%).
   - Ingresar la tarjeta `4242 4242 4242 4242`.
3. Confirmar el pago:
   - Explicar lo que ocurre tras bambalinas (pasos 1 a 13 del diagrama de secuencia):
     1. Se bloquean las noches en `SuiteNight` con un TTL de expiración de 15 minutos (`BookingExpiryScheduler`).
     2. Se persiste el `PaymentIntent` en MongoDB en estado `processing`.
     3. La pasarela simulada firma el payload con `HMAC-SHA256` y dispara el webhook `payment.succeeded`.
     4. El backend verifica la firma, garantiza idempotencia por `eventId`, pasa la reserva a `confirmed` y remueve la expiración de las noches.
4. Mostrar en **Mailpit (`http://localhost:8025`)** el correo de confirmación recibido por el huésped.
5. Mostrar en el **Panel del Anfitrión**:
   - La entrega automática del webhook `booking.confirmed` firmada criptográficamente.
   - El botón de **Check-in / Regenerar PIN** (generado con `SecureRandom` de 6 dígitos) y la sección de **Liquidaciones Automáticas (92% Host Payout)**.

---

## 🛡️ 3. Preguntas Frecuentes del Jurado (Respuestas Ganadoras)

| Pregunta del Jurado | Respuesta Técnica Basada en tu Código |
|---|---|
| *¿Qué pasa si dos personas hacen clic en "Reservar" al mismo milisegundo para la última habitación?* | Usamos una colección `suite_nights` con un **índice único compuesto en MongoDB `{suite, unitSlot, date}`** más control de concurrencia optimista (`@Version`). Si una suite tiene múltiples unidades físicas (`totalUnits`), `BookingService` itera automáticamente al siguiente `unitSlot` libre; si todas las unidades están tomadas, captura `DuplicateKeyException`, hace rollback de las noches insertadas y responde `409/400` sin posibilidad de overbooking. |
| *¿Qué pasa si el huésped abre el checkout, bloquea las fechas y cierra la pestaña sin pagar?* | La reserva nace en estado `pending` con un campo `expiresAt = now + 15 min`. El job `BookingExpiryScheduler` corre cada minuto, detecta reservas `pending` vencidas con `paidAmount == 0`, libera todos los documentos `SuiteNight` y transiciona la reserva a `expired`. |
| *¿Cómo evitan que un atacante falsifique el webhook de pago o reenvíe el mismo pago dos veces?* | 1) Verificamos la firma `HMAC-SHA256` en tiempo constante (`MessageDigest.isEqual`) sobre `timestamp + "." + rawBody`.<br>2) Validamos una ventana de tolerancia de 5 minutos en `X-Palacio-Timestamp` contra ataques de replay.<br>3) Guardamos cada `eventId` en la colección `webhook_events` con índice único para garantizar idempotencia estricta. |
| *¿Qué pasa si el servidor del hotel aliado está caído cuando le enviamos un webhook?* | `WebhookDispatcher` registra el intento en `WebhookDelivery` con estado `PENDING` y programa reintentos automáticos con **exponential backoff** (1 min, 5 min y 30 min) mediante `WebhookRetryScheduler`. Tras 3 fallos pasa a `FAILED` y el anfitrión puede reenviarlo manualmente desde su panel. |
| *¿Cómo evitan que un anfitrión ponga `http://localhost:27017` o `http://169.254.169.254` como URL de webhook para escanear su red interna (SSRF)?* | `HostWebhookService.validateUrlNotInternal()` resuelve el DNS del host y rechaza direcciones loopback, site-local (`10.x`, `172.16.x`, `192.168.x`), link-local (`169.254.x.x` de AWS/GCP metadata) y esquemas distintos a `http/https`. |
