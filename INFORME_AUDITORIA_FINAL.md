# Informe de Auditoría Riguroso y Final — Palacio del Mar (Rama `proyecto-angular-java`)

> **Fecha:** 12 de Octubre de 2026 (Mocked)
> **Rama auditada:** `proyecto-angular-java`
> **Consideraciones iniciales:** Los componentes simulados por diseño (pagos con `SimulatedPaymentGateway` y notificaciones con `WhatsAppService`) se conservan sin marcar como defecto por falta de integraciones reales, ya que es intencional para desarrollo.

---

## 1. Resumen Ejecutivo
Tras una revisión técnica profunda del estado actual de la migración a Java 21 y Angular 18 (aunque las dependencias del `package.json` indiquen Angular 22/Node 22 con fallos en `npm build`), se concluye que **se ha avanzado en solucionar varios hallazgos del informe anterior (como C1 y C3)**. Sin embargo, persisten **graves problemas de lógica de negocio, manejo de transacciones, consistencia en la máquina de estados y seguridad** que todavía hacen que la aplicación sea inestable y vulnerable en producción.

---

## 2. Hallazgos por Severidad

### 🔴 CRÍTICOS

#### C1: Doble reserva por concurrencia y limpieza de bloqueos pagados (Regresión Parcial)
- **Estado:** Parcialmente Resuelto / **NUEVO PROBLEMA (C2 Original Modificado).**
- **Descripción:** El problema original de los índices (`C1`) **está resuelto**: `spring.data.mongodb.auto-index-creation=true` está activo y `SuiteNight` declara los índices compuestos únicos.
- **El problema remanente (C2 original):** Cuando se confirma una reserva y se realiza el pago exitoso (`PaymentService:131` llama a `BookingService.changeBookingStatus`), ahora sí se actualiza el `SuiteNight` a `"confirmed"` y se hace `setExpiresAt(null)`. Sin embargo, el scheduler `BookingExpiryScheduler` NO existe en el código de backend. Si el flujo depende de un cron para liberar los "held" (bloqueos temporales) de reservas que no terminan de pagarse a los 30 minutos, las habitaciones se quedarán bloqueadas de por vida. Las reservas abandonadas bloquean inventario indefinidamente.

#### C2: Seguridad de Autenticación, Toma de Cuenta (Account Takeover) y Credenciales Quemadas
- **Estado:** No Resuelto.
- **Descripción (C5 y C6 Original):**
  1. **Cuentas fantasma y robo de sesión:** En `BookingService.createBooking` (línea 60), si `userId` es nulo, el sistema busca un usuario por email o lo crea. Si lo crea, asigna por defecto `emailVerified(true)` de forma insegura. Cualquier atacante puede adjudicarse una reserva si la víctima no está logueada y solo ingresa un email.
  2. **Credenciales quemadas:** En `DataInitializer.java:585` se inyecta siempre una contraseña codificada de forma fija para `admin@palaciodelmar.co`. El secreto JWT sigue expuesto por defecto en el `application.properties` en caso de no proveer la variable de entorno, comprometiendo la generación de tokens.

#### C3: Manejo de Errores y Falsos Positivos en Frontend (El checkout asume éxito siempre)
- **Estado:** No Resuelto.
- **Descripción (A6 Original):** En `frontend-angular/src/app/pages/checkout/checkout.ts:380`, si la solicitud al endpoint paralelo `/payments/process` falla, el cliente ejecuta la lógica: `if (err.status === 404 || err.status === 200 || err.status === 201) { this.router.navigate(...) }`. Un `404 Not Found` en un proceso de pago generará que el usuario reciba un comprobante exitoso sin que el dinero se haya movido ni el backend se entere.

#### C4: Ausencia Total de Protección CSRF con Sesiones Basadas en Cookies
- **Estado:** No Resuelto.
- **Descripción (C4 Original):** La cookie `jwt` se envía y acepta con `Secure(false)`, `SameSite` no restrictivo, y con origen comodín (CORS en `http://localhost:*` permitiendo credenciales) a la vez que se deshabilita la protección CSRF en Spring Security (`SecurityConfig:38`). Esto permite inyectar solicitudes a través de un sitio malicioso forzando al navegador a utilizar la sesión.

---

### 🟠 ALTOS

#### A1: Máquina de Estados y Manejo de Cancelaciones Inconsistente
- **Estado:** Parcialmente Resuelto.
- **Descripción:** `changeBookingStatus` (línea 606) de `BookingService` ahora verifica si el estado actual puede transicionar al nuevo usando el enum `BookingStatus`. También se validó la política de penalización del 10% de retención si la cancelación es en menos de 7 días.
- **Fallo Actual:** Sigue existiendo inconsistencia transaccional. En Spring Data MongoDB (no relacional y sin `@Transactional` configurado para réplicas aquí), si la base de datos aborta por `OptimisticLockingFailureException` (lo que se observa en `BookingService` y `PaymentService`), el usuario se queda sin dinero y sin reserva confirmada (Race condition al pagar a la vez que se cancela).

#### A2: Contrato Roto de Frontend y Backend (Peticiones MASIVAS)
- **Estado:** No Resuelto.
- **Descripción (A9 Original):** Al intentar cargar una reserva específica, `checkout.ts` hace un `GET` a `/bookings` (que trae TODAS las reservas) y luego filtra en el cliente usando un `find()`. Esto generará un problema de OOM (Out Of Memory) y latencia enorme si el usuario tiene múltiples interacciones o cuando la tabla de reservas crezca. Debería usar el endpoint `GET /bookings/{id}`.

#### A3: Inconsistencias con Promociones
- **Estado:** Parcialmente Resuelto.
- **Descripción:** `findActiveBranchPromotion` sí recupera promociones para calcular el `CalculatePriceResponseDTO`. Pero si existiesen conflictos entre temporadas (`PricingService` línea 56) y promociones, la arquitectura aplica promociones sin contemplar que no están ligadas permanentemente en el modelo, lo que puede romper reportes contables y el historial si la promoción se elimina en el futuro, debido a que no se congela su valor con precisión (salvo en el total).

---

### 🟡 MEDIOS

#### M1: Interceptor y Sesiones Muertas (M5)
- El `auth.interceptor.ts` sigue eliminando toda la data local (`clearLocalData`) y redirigiendo al inicio ante cualquier error HTTP 401. No se utiliza un flujo de `Refresh Token` y los usuarios son expulsados abruptamente de la plataforma si su token expira en medio de una operación.

#### M2: Calidad y Deuda Técnica del Frontend
- Multiples instancias del tipo `any` en servicios, componentes (particularmente `checkout.ts`).
- `styles.css` contiene más de 3100 líneas, indicando la ausencia de un sistema de diseño modularizado en Angular.
- No hay entorno de producción configurado (`environment.prod.ts` no existe, a pesar de usar `environment.ts` para `/api`).
- Errores de compilación evidentes en NPM en la rama debido a dependencias en `@angular-devkit` que solicitan motores que chocan (Angular 22/Node 22).

#### M3: Falta de Pruebas Unitarias Robustas
- Si bien la ejecución reporta éxito de `86 tests` en Java, la cobertura sigue centrándose mayoritariamente en controladores o validaciones de nulos, omitiendo por completo el testeo real sobre Base de Datos de concurrencia y carrera en `SuiteNight` o rollback en `PaymentService`.

---

## 3. Conclusión y Plan de Acción Sugerido
1. **Reimplantar / Crear `BookingExpiryScheduler`** para limpiar las reservas "held" abandonadas, chequeando primero que la reserva siga en estado `pending`.
2. **Sanear Frontend Checkout:** Borrar urgentemente las redirecciones a página de éxito en caso de errores `404` en `checkout.ts`, y consumir `GET /bookings/{id}` en lugar de `GET /bookings`.
3. **Asegurar Cuentas:** Exigir `emailVerified(false)` al crear usuario por reserva de invitado o crear un modelo de invitado separado (`GuestDetails` que no inserte usuario).
4. **CSRF y CORS:** Configurar `Cookie.setSecure(true)` para HTTPS y limitar orígenes de forma estricta.
