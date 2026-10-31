# 🏰 Palacio del Mar — Marketplace Multi-Anfitrión de Hospedaje

**Palacio del Mar** es una plataforma web de reservas de hospedaje multi-anfitrión orientada a Cartagena de Indias y destinos turísticos de Colombia, con un modelo de baja comisión para anfitriones (8% de tarifa de servicio frente al ~15% de plataformas tradicionales).

La plataforma fue desarrollada con **Spring Boot + Java 21 LTS** en el backend y **Angular (Standalone Components & Signals)** en el frontend, soportada por **MongoDB**, **Redis** y mensajería SMTP mediante **Mailpit**.

---

## 🚀 Arquitectura y Tecnologías

- **Backend:** Java 21 LTS, Spring Boot (spring-boot-starter-parent 4.x), Spring Data MongoDB, Spring Data Redis & In-Memory Cache (`@EnableCaching`), Tareas Asíncronas (`@EnableAsync`), Spring Security (JWT con algoritmo HMAC-SHA256 y persistencia en cabecera Bearer / cookie SameSite=Lax), JavaMail (integrado con Mailpit), Spring Boot Actuator, Springdoc OpenAPI 3.
- **Frontend:** Angular (Standalone Components & Signals), TypeScript, Tailwind CSS, reactividad nativa sin Zone.js overhead.
- **Base de Datos & Concurrencia:** MongoDB 7.0 (índices únicos compuestos, control de concurrencia optimista `@Version`), Redis 7 Alpine (con fallback en memoria para desarrollo ágil).
- **Control de Correo / SMTP:** Mailpit para captura y verificación de correos en entornos de desarrollo y pruebas locales.
- **Pruebas y QA:** JUnit 5, Mockito, AssertJ, Spring MockMvc (256 pruebas unitarias y de integración pasando al 100% con JaCoCo).
- **CI/CD:** Pipeline automatizado de GitHub Actions (`.github/workflows/ci.yml`) con compilación, ejecución de tests y verificación de calidad tanto en backend como frontend.

---

## 👥 Funcionalidades y Roles del Marketplace

### 👑 1. Administrador (`ADMIN`)
- **Panel de Moderación de Alojamientos:** Aprobación o rechazo de nuevas propiedades publicadas por anfitriones (`PENDING` -> `APPROVED` / `REJECTED`).
- **Métricas Globales en Tiempo Real:** Total de ingresos generados, índice de ocupación global, conteo de reservas activas, completadas y canceladas.
- **Gestión Paginada de Reservas:** Paginación real a nivel de base de datos (`Pageable` en MongoDB) sobre `/api/admin/bookings`.
- **Control del Ciclo de Vida de Reservas:** Capacidad de confirmar, marcar check-in, check-out o cancelar reservas con motivo administrativo documentado en el historial de auditoría (`statusHistory`).
- **Gestión de Tarifas y Promociones:** Configuración de promociones estacionales con porcentajes de descuento dinámicos.

### 🛎️ 2. Anfitrión (`HOST`)
- **Gestión Multi-Propiedad y Habitaciones:** Registro de hoteles o villas, administración de inventario de suites y tarifas.
- **Seguridad Anti-IDOR Estricta:** Todo endpoint de anfitrión valida que la propiedad pertenezca al usuario autenticado (`property.ownerId == currentUser.id`); de lo contrario, responde 404 (no revela la existencia de recursos ajenos).
- **Notificaciones por Webhook (Salientes):** Registro de URLs receptoras (PMS del hotel), firma criptográfica HMAC-SHA256 en cabecera `X-Palacio-Signature`, marca de tiempo `X-Palacio-Timestamp` contra ataques de repetición, ID de idempotencia `X-Palacio-Event-Id`, y reintentos automáticos programados (1m, 5m, 30m) ante caídas del receptor.
- **Claves de Conexión Externa (API Keys):** Generación de credenciales seguras (`pdm_...`) con hash criptográfico para conectar motores de reservas externos o sistemas PMS sin exponer contraseñas.
- **Control de Ocupación y Huéspedes:** Monitoreo de llegadas y salidas de huéspedes con acceso a nombres, teléfonos y estado de pago.

### 🌴 3. Huésped / Usuario (`USER` / Invitado)
- **Autenticación Segura y Verificación de Correo:** Registro con verificación criptográfica por correo vía enlace seguro.
- **Explorador y Buscador Unificado:** Búsqueda reactiva por ciudad o propiedad, fechas y huéspedes. Integra tanto las propiedades fundadoras como los alojamientos independientes aprobados en el marketplace.
- **Cálculo de Tarifas y Promociones Dinámicas:** Desglose transparente en el checkout (tarifa base, tarifa de servicio de plataforma del 8%, desglose de pago al anfitrión e impuestos).
- **Pasarela de Pagos Asíncrona (Simulada Realista):** Flujo de pago de dos fases (`processing` -> `succeeded`/`failed`), emisión de webhooks asíncronos con firma HMAC y transacciones compensatorias para evitar overbooking o dobles cobros (`@Version` en `Booking`).
- **Panel "Mis Reservas":** Voucher digital de reserva con código QR, políticas de cancelación y reembolso transparente.

---

## 🔌 Webhooks e Integración Externa (PMS / Channel Manager)

### Webhooks Salientes (Palacio del Mar ➔ Sistema Externo)
Cuando ocurre un evento relevante en la plataforma (`booking.created`, `booking.confirmed`, `booking.cancelled`, `payment.succeeded`), el despachador envía una notificación HTTP POST con las siguientes cabeceras:
- `X-Palacio-Signature`: Firma hexadecimal HMAC-SHA256 calculada sobre el cuerpo JSON con el secreto de la suscripción.
- `X-Palacio-Event-Id`: Identificador único del evento para garantizar procesamiento idempotente.
- `X-Palacio-Timestamp`: Marca temporal Unix para mitigar ataques de repetición (replay attacks).
- `X-Palacio-Event-Type`: Tipo de evento notificado.

### Webhooks Entrantes de Pago (Pasarela ➔ Palacio del Mar)
- Endpoint: `POST /api/webhooks/payments`
- Valida la firma `X-Palacio-Signature` contra el secreto configurado en la pasarela.
- Idempotencia estricta: reenvíos del mismo `eventId` son ignorados sin duplicar transacciones.

### Integración por API Key (Sistema Externo ➔ Palacio del Mar)
Los sistemas de hotelería externa pueden interactuar directamente mediante la cabecera `X-API-KEY` (o `X-Palacio-Api-Key`):
- `POST /api/integration/rooms`: Sincronización o actualización de habitaciones (`upsert` por `externalId`).
- `PUT /api/integration/availability`: Bloqueo o desbloqueo de fechas desde el PMS externo, utilizando el modelo transaccional `SuiteNight` para garantizar la protección anti-overbooking frente a reservas del marketplace.

---

## 🧪 Pruebas Automatizadas y Calidad de Código

El repositorio cuenta con **446 pruebas automatizadas en backend + 43 en frontend** que cubren el 100% de los flujos críticos:

### Backend Java (Spring Boot):
```bash
cd backend-java
./mvnw test
```
- **Seguridad y Anti-IDOR (`RoleAndIdorSecurityTest`, `BookingOwnershipSecurityTest`, `TokenBlacklistServiceTest`):** Verificación de aislamiento estricto entre anfitriones (`propertyId` + `branches`), revocación duradera de JWT en MongoDB/Redis al cerrar sesión o cambiar credenciales, protección estricta de Actuator y rechazo de accesos no autorizados (401/403/404).
- **Webhooks y Despacho (`WebhookDispatcherTest`, `HostWebhookServiceTest`, `PaymentWebhookServiceTest`):** Verificación de firmas HMAC, protección Anti-SSRF, políticas de reintento, idempotencia y auditoría de entregas.
- **Sincronización iCal RFC 5545 (`ICalServiceTest`, `ICalServiceAdversarialTest`):** Feeds de calendario exportables compatibles con Airbnb y Google Calendar, con validación de estado aprobado y bloqueo de borradores.
- **Integración Externa (`IntegrationServiceTest`, `ApiKeyServiceTest`):** Alta de habitaciones externas y bloqueos anti-overbooking con `SuiteNight`.
- **Máquina de Estados de Reserva (`BookingStateMachineTest`):** Cobertura exhaustiva de 49 transiciones válidas e inválidas del ciclo de vida de la reserva.
- **Concurrencia y Pagos (`PaymentConcurrencyTest`, `SimulatedPaymentGatewayTest`, `BookingExpirySchedulerTest`):** Manejo de colisiones de bloqueo optimista multi-slot (`@Version`), expiración de reservas pendientes y compensaciones.
- **Notificaciones Transaccionales (`EmailServiceTest`):** Generación y despacho de vouchers adjuntos en PDF y eventos `.ics` vía Mailpit.
- **Reporte JaCoCo:** Generado en `backend-java/target/site/jacoco/index.html` (104 clases cubiertas).

### Frontend Angular:
```bash
cd frontend-angular
npm run build
npm test -- --watch=false
```
- Compilación de producción limpia sin errores TypeScript ni advertencias bloqueantes (presupuesto inicial < 700KB), y 43 pruebas unitarias pasando con Vitest en 9 archivos (incluyendo suite de accesibilidad WCAG 2.2 AA, componentes de Voucher QR, PIN inteligente y estado reactivo con Signals).

---

## 📊 Diagramas Interactivos, Sustentación y Auditorías
- 📚 **Índice Completo de Documentación:** **[docs/README.md](docs/README.md)**
- 🏛️ **Diagrama Interactivo de Arquitectura:** **[docs/architecture.html](docs/architecture.html)**
- 🔄 **Diagrama Interactivo de Secuencia Lógica:** **[docs/sequence.html](docs/sequence.html)**
- 🎬 **Playbook de Demostración E2E (Sustentación):** **[docs/DEMO_SCRIPT.md](docs/DEMO_SCRIPT.md)**
- 📋 **Plan de Marketplace & Webhooks:** **[docs/PLAN_MARKETPLACE.md](docs/PLAN_MARKETPLACE.md)**
- 🧪 **Reporte de Calidad y QA:** **[docs/QA_REPORTE.md](docs/QA_REPORTE.md)**
- 🔒 **Auditoría de Seguridad:** **[docs/AUDITORIA_SEGURIDAD.md](docs/AUDITORIA_SEGURIDAD.md)**
- 📝 **Historial de Correcciones:** **[docs/CORRECCIONES.md](docs/CORRECCIONES.md)**

---

## 🛠️ Despliegue y Ejecución Local

### Opción 1: Docker Compose (Recomendada)
Para levantar el stack completo (MongoDB, Redis, Backend y Frontend):
```bash
docker compose up -d --build
```
Servicios locales disponibles:
- **Frontend Angular:** `http://localhost:4200`
- **Backend Spring Boot:** `http://localhost:5000`
- **Documentación Swagger / OpenAPI:** `http://localhost:5000/swagger-ui.html`
- **Actuator Health:** `http://localhost:5000/actuator/health`
- **Correo Transaccional Real (SMTP):** Configurable en `.env` (`SPRING_MAIL_*`, por defecto `smtp.gmail.com:587`) + enlaces externos opcionales vía `cloudflared tunnel --url http://localhost:4200` (`APP_BASE_URL`)
- **Simulador PMS de Prueba:** `http://localhost:4200/demo/external-hotel`
- **Receptor de Webhooks de Prueba:** `http://localhost:5000/api/demo/webhook-receiver`

### Opción 2: Ejecución Nativa
1. **Backend:**
   ```bash
   cd backend-java
   ./mvnw spring-boot:run
   ```
2. **Frontend:**
   ```bash
   cd frontend-angular
   npm install
   npm start
   ```

---

## 🔑 Credenciales de Prueba Preconfiguradas (Modo Demo / Desarrollo)

> [!WARNING]
> Las credenciales a continuación solo se siembran si `SEED_DEMO_DATA=true`. En entornos de producción (`SEED_DEMO_DATA=false`), el sembrado de cuentas demo está deshabilitado por seguridad y la cuenta de administrador requiere configurar una contraseña fuerte en `ADMIN_DEFAULT_PASSWORD`.

| Rol | Correo Electrónico | Contraseña Demo | Descripción |
| :--- | :--- | :--- | :--- |
| **Administrador** | `admin@palaciodelmar.co` | `Admin123456!` (solo demo) | Acceso completo y moderación de alojamientos |
| **Anfitrión Fundador** | `anfitrion@palaciodelmar.co` | `Host123456!` (solo demo) | Gestión de sedes iniciales (Centro Histórico y Getsemaní) |
| **Huésped (Verificado)** | `huesped@palaciodelmar.co` | `User123456!` (solo demo) | Usuario con email pre-verificado para reservas |

---

## 💳 Tarjetas de Prueba (Pasarela Simulada)

| Número de Tarjeta | Comportamiento Simulado |
| :--- | :--- |
| `**** **** **** 4242` | Pago aprobado exitosamente (`payment.succeeded`) |
| `**** **** **** 0002` | Tarjeta rechazada por la entidad bancaria (`payment.failed`) |
| `**** **** **** 9995` | Fondos insuficientes (`payment.failed`) |
| `**** **** **** 0069` | Tarjeta expirada / vencida (`payment.failed`) |

---

## 📱 Guía de Instalación Móvil (Directo desde tu Celular)

Instala la aplicación de **Palacio del Mar** en tu dispositivo móvil en menos de 1 minuto, sin necesidad de cables ni computadores, sincronizada automáticamente y en tiempo real con la versión web del hotel.

---

### 🤖 1. En Dispositivos Android (Instalación por APK Autónomo)

La aplicación para Android se instala directamente descargando el paquete instalador desde tu celular y se comunica de forma automática con la misma base de datos y reservas de la página web:

1. **Descarga el instalador desde tu celular:**
   - Descarga directa: [Descargar APK Oficial de Palacio del Mar](https://unbroke-unsized-zandra.ngrok-free.dev/palacio-del-mar-release.apk)
   - O desde GitHub: [Release v1.0.0 Oficial](https://github.com/angel123297/palacio-del-mar/releases/tag/v1.0.0)
2. **Instala la aplicación:**
   - Abre la notificación de descarga finalizada en tu celular.
   - Si tu dispositivo solicita autorización, presiona **Ajustes** y activa la opción **"Permitir desde esta fuente"** (solo toma 2 segundos).
   - Toca el botón **Instalar**.
3. **Apertura y sincronización automática:**
   - Abre **Palacio del Mar** desde la pantalla de inicio o cajón de aplicaciones de tu celular.
   - ¡Listo! Cualquier reserva, suite o actualización que ocurra en la página web se sincronizará automáticamente en tu celular en tiempo real mediante el motor de eventos SSE.

---

### 🍏 2. En Dispositivos iOS (iPhone & iPad vía Enlace Activo Permanente)

En iPhone y iPad la aplicación se instala permanentemente como **Web App Nativa (PWA)** a través del enlace activo de la plataforma, sin certificados que expiren ni necesidad de supervisión continua:

1. **Abre el enlace en Safari:**
   - En tu iPhone, abre el navegador **Safari** e ingresa a la dirección web oficial de la plataforma:
     `https://unbroke-unsized-zandra.ngrok-free.dev/app/` (o el dominio público activo del hotel).
2. **Agrégala a tu pantalla de inicio:**
   - Toca el botón **Compartir** en la barra inferior de Safari (icono del cuadrado con la flecha hacia arriba: ⎋).
   - En el menú que aparece, desplázate hacia abajo y presiona **"Agregar al inicio"** (o *"Add to Home Screen"*).
   - Deja el nombre sugerido **Palacio del Mar** y toca **Agregar** arriba a la derecha.
3. **Disfruta de la experiencia de aplicación nativa:**
   - Verás el icono dorado oficial de Palacio del Mar directamente junto a tus demás aplicaciones de iOS.
   - Al tocarlo, abrirá a pantalla completa sin barra de navegación de Safari, manteniéndose activa y sincronizada las 24 horas sin requerir que nadie la reactive.
