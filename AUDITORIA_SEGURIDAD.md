# 🔒 Auditoría de Seguridad y Tests Intensivos — 2026-10-15

> Auditoría realizada por Vibe (Mistral) sobre la rama `plan/marketplace-webhooks` (código = rama `proyecto-angular-java` al commit 1b0710b).
> Método: revisión estática completa de controladores, servicios, seguridad, modelos y config + 3 archivos de tests intensivos nuevos.

## ✅ Lo que está bien (verificado en código)

- **Anti-overbooking sólido**: índice único compuesto `{suite, unitSlot, dates}` + `@Version` + rollback con `DuplicateKeyException` y restauración de noches.
- **Chequeos anti-IDOR** en modify/cancel/experiences/pre-checkin de `BookingService`.
- **Idempotencia de pagos** valida que el `idempotencyKey` pertenezca al mismo usuario y reserva.
- **BCrypt** para contraseñas; registro SIEMPRE asigna `role=user` (sin auto-escalación a host/admin).
- **Roles bien restringidos** en `SecurityConfig` (`/api/admin/**`, `/api/host/**`).
- **JWT**: cookie `HttpOnly`, expiración 24h, secret desde variable de entorno.
- **Máquina de estados** con transiciones válidas/inválidas explícitas.

## 🐛 Hallazgos y Estado de Resolución (10/10 Cerrados)

| ID | Severidad | Ubicación | Estado | Resolución Aplicada |
|----|-----------|-----------|:------:|---------------------|
| PDM-01 | **ALTA** | `BookingService.calculateVipExtrasTotal` | ✅ **RESUELTO** | Precios de órdenes de comida fijados estrictamente en el backend ($80.000 base + $35.000 combo desayuno), ignorando valores enviados por el cliente (`PriceIntegritySecurityTest`). |
| PDM-02 | MEDIA | `BookingService.parseExperiences` | ✅ **RESUELTO** | Toda experiencia se re-cotiza siempre desde `ExperienceRepository` por ID, incluso si llega como instancia `SelectedExperience`. |
| PDM-03 | MEDIA | `BookingService.validateHostAccessToBooking` & `HostController` | ✅ **RESUELTO** | Validación estricta Anti-IDOR unificada (`property.ownerId == user.id` o `branches.contains(branch)`). Un host sin sedes ni propiedades recibe denegación inmediata (`RoleAndIdorSecurityTest`). |
| PDM-04 | MEDIA | `HostController.getHostBookings` | ✅ **RESUELTO** | Paginación directa en MongoDB con `Pageable` (`findByBranchIn` / `findByPropertyIdIn`) y soporte dual para propiedades del marketplace. |
| PDM-05 | MEDIA | `ChatController` / `ChatService` | ✅ **ELIMINADO** | Componentes de chat removidos en Fase 0 conforme a `AGENTS.md` regla 8. |
| PDM-06 | MEDIA | `AuthController` (cookie jwt) | ✅ **RESUELTO** | `ResponseCookie` configurada explícitamente con `httpOnly(true)` y `sameSite("Lax")`, más soporte `Bearer` token. |
| PDM-07 | BAJA | `HostController.regeneratePin` | ✅ **RESUELTO** | Uso de `SecureRandom.nextInt(100000, 1000000)` garantizando PINs criptográficos de 6 dígitos en el rango `[100000, 999999]` (`RoleAndIdorSecurityTest`). |
| PDM-08 | BAJA | `AdminController.createPromotion` | ✅ **RESUELTO** | Uso de `@Valid @RequestBody PromotionRequest` DTO que previene mass assignment de campos internos (`RoleAndIdorSecurityTest`). |
| PDM-09 | INFO | `GET /api/bookings/code/{code}` | ✅ **RESUELTO** | Se enmascara `smartLockPin = null` cuando la consulta se realiza sin sesión autenticada (`BookingOwnershipSecurityTest`). |
| PDM-10 | INFO | `SimulatedPaymentGateway.refund` | ✅ **RESUELTO** | Validación real de `transactionId` contra `PaymentIntent` persistido en MongoDB y emisión de evento `refund.processed` (`SimulatedPaymentGatewayTest`). |

**Adicional (Seguridad de Webhooks Salientes):** Protección **Anti-SSRF** implementada en `HostWebhookService` para bloquear direcciones loopback, redes privadas RFC1918 y metadatos cloud (`169.254.169.254`) (`HostWebhookServiceTest`).

## 🧪 Suites de Tests de Seguridad y Regresión (175 Tests Activos, 0 `@Disabled`)

| Archivo | Qué cubre | Estado |
|---------|-----------|--------|
| `security/RoleAndIdorSecurityTest.java` | Anti-IDOR en `HostPropertyController`, `HostController` (`propertyId` + `branches`), `SecureRandom` PIN (PDM-07) y validación DTO anti-mass-assignment en `AdminController` (PDM-08) | **✅ 100% En Verde** |
| `service/BookingOwnershipSecurityTest.java` | 13 tests anti-IDOR: modify/cancel/experiences/pre-checkin de otro usuario; ocultamiento de `smartLockPin` en consultas anónimas (PDM-09) y concurrencia multi-slot | **✅ 100% En Verde** |
| `service/CancellationPolicyBoundaryTest.java` | 5 tests de frontera de la política de cancelación (<48h=100%, 2 días=10%, 7 días=gratis, impaga no toca pasarela) | **✅ 100% En Verde** |
| `service/PriceIntegritySecurityTest.java` | 3 tests activos contra manipulación de precios de experiencias y comida desde el cliente (PDM-01 y PDM-02) | **✅ 100% En Verde** |
| `webhook/HostWebhookServiceTest.java` | Protección Anti-SSRF (bloqueo de localhost, IPs privadas y AWS metadata) y aislamiento de suscripciones | **✅ 100% En Verde** |

### Cómo ejecutarlos

```bash
cd backend-java && ./mvnw test
```
Todos los **175 tests** del backend y los **8 tests** del frontend (`npm test -- --watch=false`) pasan al 100%.
