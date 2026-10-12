# 🏰 Palacio del Mar — Enterprise Luxury Hospitality Platform

Bienvenido al repositorio oficial de **Palacio del Mar**, una plataforma integral de reservas hoteleras de lujo de grado empresarial para Cartagena de Indias, diseñada bajo estándares de alta disponibilidad, concurrencia transaccional y experiencia de usuario premium (estilo Booking.com / Relais & Châteaux).

El proyecto fue completamente migrado y refactorizado a una arquitectura moderna y desacoplada: **Spring Boot 3 + Java 21** en el Backend y **Angular 18 (Standalone Components & Signals)** en el Frontend.

---

## 🚀 Arquitectura y Tecnologías

- **Backend:** Java 21 LTS, Spring Boot 3.4+, Spring Data MongoDB, Spring Security (JWT con algoritmo HMAC-SHA256), JavaMail (integrado con Mailpit).
- **Frontend:** Angular 18+, TypeScript, Tailwind CSS, Angular Signals (programación reactiva sin Zone.js overhead), Componentes Standalone.
- **Base de Datos:** MongoDB 7.0 (Índices únicos compuestos, control de concurrencia optimista `@Version`).
- **Control de Correo / SMTP:** Mailpit para captura y verificación de correos en entornos de desarrollo y pruebas.
- **Pruebas y QA:** JUnit 5, Mockito, AssertJ, GitHub Copilot CLI QA Suite.

---

## 👥 Funcionalidades por Rol

### 👑 1. Administrador (`ADMIN`)
El rol de administración posee control total del sistema y acceso al dashboard gerencial:
- **Métricas Globales en Tiempo Real:** Total de ingresos generados, índice de ocupación global, conteo de reservas activas, completadas y canceladas.
- **Gestión Paginada de Reservas:** Paginación real a nivel de base de datos (`Pageable` en MongoDB) sobre `/api/admin/bookings`, evitando sobrecarga de memoria.
- **Control del Ciclo de Vida de Reservas:** Capacidad de confirmar, marcar check-in, check-out o cancelar reservas con motivo administrativo documentado en el historial de auditoría (`statusHistory`).
- **Gestión de Tarifas y Promociones:** Configuración de promociones estacionales por sucursal con porcentajes de descuento dinámicos y ventanas de validez temporal.
- **Control de Habitaciones y Sucursales:** Supervisión del inventario de suites en Centro Histórico y Getsemaní.

### 🛎️ 2. Anfitrión (`HOST`)
El anfitrión cuenta con una interfaz diseñada específicamente para la operación hotelera en sede:
- **Métricas Aisladas por Sucursal:** Visualización filtrada exclusivamente para la sede asignada al anfitrión (Centro Histórico o Getsemaní).
- **Control de Ocupación y Huéspedes:** Monitoreo de llegadas y salidas de huéspedes con acceso a nombres, teléfonos y estado de pago.
- **Calendario Operativo:** Verificación visual de fechas agotadas y habitaciones disponibles para asignación de llaves y servicio a la habitación.

### 🌴 3. Huésped / Usuario (`USER` / Invitado)
Diseñado para ofrecer una experiencia de usuario fluida, elegante y sin fricciones:
- **Autenticación Segura y Verificación de Correo:**
  - Registro de cuenta con validación de fortaleza de credenciales.
  - Generación de tokens criptográficos SHA-256 de un solo uso con expiración a 24 horas.
  - Bloqueo de inicio de sesión hasta validar la cuenta mediante el enlace enviado al correo (abierto en `/verificar-email/:token`).
- **Explorador y Buscador Dinámico:**
  - Filtrado reactivo por sucursal (Centro Histórico vs. Getsemaní), tipo de suite y número de huéspedes.
  - DateRangePicker personalizado de lujo con selección interactiva y bloqueo de fechas pasadas.
- **Cálculo de Tarifas y Promociones Dinámicas:**
  - Aplicación automática de descuentos activos por sucursal (`findActiveBranchPromotion`).
  - Desglose transparente en el checkout: tarifa base, descuento promocional, impuestos y total a pagar.
- **Experiencias de Lujo y Tour 360°:**
  - Catálogo de experiencias exclusivas (paseos en yate a Islas del Rosario, cenas de alta cocina caribeña, catas de ron).
  - Recorrido inmersivo 360° por Cartagena de Indias.
- **Pasarela de Pagos Concurrente (Simulada):**
  - Métodos disponibles: Tarjeta de Crédito, PSE y transferencia bancaria.
  - Protección de concurrencia optimista (`@Version` en `Booking`) y transacciones compensatorias para evitar overbooking o dobles cobros en fechas concurrentes.
  - Mapeo consistente de base de datos MongoDB: campos `amountPaid` y `amountRefunded` sincronizados.
- **Panel "Mis Reservas":**
  - Historial de reservas activas y pasadas.
  - Vista detallada del voucher de reserva con desglose de costos y código QR.
  - Cancelación de reservas por parte del usuario con políticas de reembolso transparentes.

---

## 🧪 Pruebas de Calidad (QA) y Verificación

La plataforma cuenta con una suite completa de pruebas automatizadas:

### Backend Java (Spring Boot):
- **89 pruebas unitarias y de integración** aprobadas (`BUILD SUCCESS`):
  - `BookingExpirySchedulerTest`: Validación de liberación automática de noches y expiración de reservas pendientes.
  - `PaymentConcurrencyTest`: Verificación de rollback y compensación transaccional ante conflictos de bloqueo optimista (`@Version`).
  - `BookingMappingTest`: Verificación de mapeo `@Field("amountPaid")` y `@Field("amountRefunded")`.
  - `PromotionPricingTest`: Pruebas de promociones activas dinámicas por sucursal y manejo de expiración.
  - `AuthServiceTest`: Pruebas de registro, bloqueo de login sin verificación, expiración de tokens y bypass administrativo.
  - `BookingStateMachineTest`: Verificación exhaustiva de 49 transiciones válidas e inválidas de la máquina de estados de reservas.
  - `BookingCancellationTest`: Pruebas de políticas de cancelación y liberación de noches (`SuiteNight`).
  - `PricingServiceTest`: Cobertura completa de temporadas altas, cargos por huéspedes extra y descuentos.

```bash
cd backend-java
./mvnw test
```

### Frontend Angular:
- Compilación de producción limpia con arquitectura modular SCSS (`npm run build`):
```bash
cd frontend-angular
npm run build
```

---

## 📝 Historial de Correcciones y Auditorías
Para consultar la bitácora completa de correcciones, auditorías de calidad (incluyendo la certificación oficial de Jules) y resolución de hallazgos técnicos, consulta el documento:
👉 **[CORRECCIONES.md](file:///home/angel19/Notas/palacio-del-mar/CORRECCIONES.md)**

---

## 🛠️ Despliegue y Ejecución Local

### Opción 1: Docker Compose (Recomendada)
Para levantar la base de datos, backend, frontend y servidor de correos con un solo comando:
```bash
docker compose up -d --build
```
Servicios disponibles:
- **Frontend Angular:** `http://localhost:4200`
- **Backend Spring Boot:** `http://localhost:5000`
- **Mailpit (Webmail de pruebas):** `http://localhost:8025`
- **MongoDB:** `localhost:27017`

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

## 🔑 Credenciales de Prueba Preconfiguradas

| Rol | Correo Electrónico | Contraseña |
| :--- | :--- | :--- |
| **Administrador** | `admin@palaciodelmar.co` | `Admin123456!` |
| **Anfitrión (Getsemaní)** | `anfitrion@palaciodelmar.co` | `Host123456!` |
| **Huésped (Verificado)** | `huesped@palaciodelmar.co` | `User123456!` |

---

## 💡 Agregados Extras y Mejoras Futuras para la Plataforma

Para elevar aún más el posicionamiento comercial y la propuesta de valor tecnológico de **Palacio del Mar**, se sugieren las siguientes funcionalidades de alto impacto:

### 1. 🤖 Sofía AI Concierge 2.0 (Google Gemini / Vertex AI)
- **Asistente Multimodal de Viaje:** Integrar Gemini 1.5 Pro para brindar asistencia en lenguaje natural sobre el clima en Cartagena, recomendaciones personalizadas de restaurantes según preferencias dietéticas del huésped y reservación de suites conversacional.
- **Audio y Llamada en Tiempo Real:** Capacidad de interacción por voz dentro de la app para huéspedes internacionales en inglés, francés y español.

### 2. 📈 Motor de Precios Dinámicos Predictivo (Yield Management)
- Algoritmo de tarificación algorítmica estilo aerolíneas/hotelería moderna que ajuste el precio por noche automáticamente según:
  - Tasa de ocupación proyectada de la sucursal.
  - Calendario de eventos locales (Festival de Música de Cartagena, Hay Festival, fiestas de la Independencia).
  - Tiempo restante previo al check-in (ofertas de última hora vs. reservas anticipadas).

### 3. 💳 Integración con Pasarelas Reales de Pago (Stripe & Wompi)
- Integración oficial de Wompi (Bancolombia, Nequi, PSE) y Stripe con soporte para Apple Pay y Google Pay.
- Implementación de Webhooks con verificación de firma HMAC para actualización asíncrona segura de reservas.
- Manejo de retención y liberación de depósitos de garantía (security deposit) para incidentes en las suites.

### 4. 📲 Check-in Digital y Llaves Móviles (Smart Lock / QR Dinámico)
- Flujo de auto-check-in 24 horas antes de la llegada con escaneo de documento de identidad (OCR) para cumplimiento con la Policía de Turismo.
- Llave digital accesible desde el portal móvil del usuario mediante Código QR dinámico o integración NFC con cerraduras inteligentes (ej. Yale, Salto, August).

### 5. 🌟 Programa de Lealtad "The Palace Club"
- Niveles de membresía por noches acumuladas (Silver, Gold, Platinum).
- Sistema de puntos canjeables por experiencias gastronómicas o noches gratis.
- Beneficios automáticos: *Early check-in* o *Late check-out* gratuito sujeto a disponibilidad.

### 6. 💬 Notificaciones Transaccionales por WhatsApp (Twilio / Meta API)
- Envío automático del voucher de reserva y ubicación GPS con botón de Google Maps y Waze directo al WhatsApp del huésped tras confirmar el pago.
- Mensaje de bienvenida automatizado a las 9:00 AM del día del check-in con código de acceso y contacto del anfitrión.

### 7. ⭐ Sistema de Reseñas Verificadas y Análisis de Sentimiento
- Módulo de calificación exclusivo para huéspedes que hayan completado su estadía (evita reseñas falsas).
- Análisis de sentimiento automatizado con procesamiento de lenguaje natural (NLP) en el panel de administración para detectar oportunidades de mejora en el servicio.
