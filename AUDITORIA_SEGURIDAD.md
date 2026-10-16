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

## 🐛 Hallazgos

| ID | Severidad | Ubicación | Problema |
|----|-----------|-----------|----------|
| PDM-01 | **ALTA** | `BookingService.createBooking` / `calculateVipExtrasTotal` (uso de `FoodOrder.price`) | El precio de cada orden de comida viene del **cliente** (`BookingRequest.foodOrders[].price`). Un huésped puede mandar `price=0` (comida gratis) y sin límite de órdenes. **Fix**: eliminar `price` del input; cobrar desde catálogo del backend. |
| PDM-02 | MEDIA | `BookingService.parseExperiences` (rama `instanceof Booking.SelectedExperience`) | Confía en precio/cantidad del objeto entrante. Hoy vía JSON llega como Map (ruta segura que re-cotiza desde BD), pero es defensa rota esperando romperse. **Fix**: eliminar la rama `instanceof`. |
| PDM-03 | MEDIA | `BookingService.getBookingByIdSecure` / `getBookingByCodeSecure` | Un **HOST sin sedes asignadas** (`branches` vacío) puede leer CUALQUIER reserva (PII del huésped + smartLockPin). La condición `!user.getBranches().isEmpty()` vacía salta la validación. **Fix**: host sin sedes → denegar. |
| PDM-04 | MEDIA | `HostController.getHostBookings` | Carga TODAS las reservas de las sedes en memoria y filtra/paginan en Java (escaneo completo con búsqueda). No escala. **Fix**: consulta paginada en Mongo (`@Query` + `Pageable`). |
| PDM-05 | MEDIA | `ChatController` + `ChatService` + `SecurityConfig` | `/api/chat` es público, **sin rate limiting real** (el `clear-cache` es un no-op que menciona rate limiting), y `RestTemplate` sin timeouts. Con `GEMINI_API_KEY` configurada, cualquiera puede drenar tu cuota. **Fix**: rate limit por IP (Bucket4j), timeouts, `clear-cache` solo ADMIN. |
| PDM-06 | MEDIA | `AuthController` (cookie jwt) + `SecurityConfig` (CSRF off) | Autenticación por cookie + CSRF deshabilitado y **sin SameSite explícito**. Los navegadores modernos (Lax por defecto) mitigan, pero debe explicitarse. **Fix**: `cookie.setSameSite("Lax")` (o Strict); considerar exigir Bearer en mutaciones. |
| PDM-07 | BAJA | `HostController.regeneratePin` | Usa `java.util.Random` (no cripto-seguro) y `nextInt(999999)` con `%06d` genera PINs menores a 100000 (inconsistente con `createBooking` que usa 100000-999999). El PIN abre puertas. **Fix**: `SecureRandom` + mismo rango que createBooking. |
| PDM-08 | BAJA | `AdminController.createPromotion(@RequestBody Promotion)` | Recibe la entidad completa (mass assignment): un ADMIN puede fijar campos internos a discreción. Impacto bajo (es admin), pero conviene DTO. |
| PDM-09 | INFO | `GET /api/bookings/code/{code}` público | Para reservas de invitados expone PII (email, teléfono) + smartLockPin sin sesión. Aceptable porque el código tiene ~48 bits de aleatoriedad, pero documéntalo como decisión de diseño. |
| PDM-10 | INFO | `SimulatedPaymentGateway.refund` | Siempre exitoso y no valida `transactionId` (ya conocido; lo corrige la Fase 2 del plan). |

**Higiene** (ya reportadas antes): `backend.log` comprometido, enlace roto a CORRECCIONES.md en el README, cookie sin `Secure` (aceptable en dev).

## 🧪 Tests intensivos agregados (rama `plan/marketplace-webhooks`)

| Archivo | Qué cubre | Estado |
|---------|-----------|--------|
| `service/BookingOwnershipSecurityTest.java` | 12 tests anti-IDOR: modify/cancel/experiences/pre-checkin de otro usuario; ADMIN lee todo; HOST por sede (propia sí, ajena no); lectura anónima de reservas de invitado vs registrado | **Deben pasar hoy** (verifican protecciones existentes) |
| `service/CancellationPolicyBoundaryTest.java` | 5 tests de frontera de la política de cancelación: <48h=100%, exactamente 2 días=10%, exactamente 7 días=gratis, impaga nunca toca el gateway, confirmed→cancelled válido | **Deben pasar hoy** |
| `service/PriceIntegritySecurityTest.java` | Manipulación de precios: precio/cantidad de experiencias siempre desde BD (pasa hoy), y 2 tests que documentan **BUG-PDM-01 y BUG-PDM-02** | **2 @Disabled** hasta corregir los bugs |

### Cómo ejecutarlos

```bash
cd backend-java && ./mvnw test
```

Los 89 tests existentes + los nuevos deben quedar en verde. Los 3 tests `@Disabled` se activan al corregir PDM-01, PDM-02 y PDM-03 (borrar la anotación `@Disabled` y su texto).

## 📋 Orden de corrección sugerido

1. **PDM-01** (precio de comida) — 30 min de fix, es el más explotable.
2. **PDM-03** (host sin sedes) — 10 min.
3. **PDM-02** (rama instanceof) — 10 min.
4. **PDM-05** (chat: rate limit + timeouts) y **PDM-06** (SameSite) — 1-2 h juntos.
5. PDM-04 y PDM-07 en la Fase 5 (calidad).
