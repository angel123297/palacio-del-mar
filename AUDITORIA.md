# 📋 Registro de Auditoría y Depuración Web — Palacio del Mar (Noviembre 2026)

Documento vivo de control para registrar todos los hallazgos, errores detectados, análisis de causa raíz y soluciones técnicas implementadas de inmediato en el backend y frontend web, con meta de estabilidad total al **17 de noviembre de 2026**.

---

## 📊 Resumen Ejecutivo de Estado
- **Fecha de inicio**: 02 de noviembre de 2026
- **Alcance**: Entorno Web (Backend Spring Boot / Java 21 + Frontend Angular 22)
- **Regla inquebrantable**: Correcciones inmediatas sin alterar la estructura visual ni el diseño frontend original.
- **Estado de Suites**:
  - **Backend (Spring Boot 3 / Java 21)**: **453 tests ejecutados y pasando al 100% (0 fallos, 0 errores, 0 omitidos)** (+7 tests nuevos de frontera).
  - **Frontend (Angular 22 / TypeScript 6)**: **51 tests ejecutados y pasando al 100% (0 fallos)** (+8 tests nuevos en Checkout y MyBookings), 0 errores TypeScript (`tsc --noEmit`), build de producción limpio (`ng build`).

---

## 🔍 Registro de Hallazgos y Correcciones

| ID | Componente | Severidad | Descripción del Hallazgo / Error | Causa Raíz | Solución Aplicada & Archivos Modificados | Estado |
|---|---|---|---|---|---|:---:|
| **AUD-01** | Frontend Test Script | Baja | El comando estándar `npm test` fallaba en Windows con error `NODE_PATH no se reconoce como un comando interno`. | El script de test en `package.json` definía variables de entorno con sintaxis POSIX (`NODE_PATH=... ng test`), incompatible con cmd/powershell. | Se instaló `cross-env` y se actualizó el script en `frontend-angular/package.json` a `"test": "cross-env NODE_PATH=./node_modules/@angular/build/node_modules ng test"`. Verificado con `npm test -- --watch=false` multiplataforma. | ✅ **RESUELTO** |
| **AUD-02** | Backend Concurrencia & Reserva | Media | Riesgo de desincronización de caché ante reservas o modificaciones concurrentes de suites y fechas. | El motor de disponibilidad y tarifas dinámicas almacena respuestas en Redis/SimpleCache (`availability_search`, `availability_monthly`, `dynamic_pricing`). | Verificado que `AvailabilityCacheEvictor` invalida atómicamente todos los cachés en `createBooking`, `modifyBooking`, `cancelBooking` y recepción de webhooks de pago (`PaymentWebhookService`). Cobertura en `BookingOwnershipSecurityTest` y `PriceIntegritySecurityTest`. | ✅ **VERIFICADO Y SÓLIDO** |
| **AUD-03** | Backend Seguridad Webhooks & SSRF | Alta | Riesgo de peticiones salientes a direcciones de loopback o metadatos de nube en webhooks de anfitriones. | Los anfitriones pueden registrar URLs de endpoints para recibir eventos del PMS. | El validador `SsrfProtectionValidator` y `HostWebhookService` aplican resolución de host fijada (pinned target IP) bloqueando loopback, rangos RFC1918 y metadata `169.254.169.254`. Permite explícitamente el receptor demo oficial. Cobertura en `WebhookDispatcherTest` y `HostWebhookServiceTest`. | ✅ **VERIFICADO Y SÓLIDO** |
| **AUD-04** | Backend Tolerancia SMTP / Email | Media | Posible caída o bloqueo de transacciones si el servidor SMTP falla durante registros o reservas. | Caídas de red o demoras en el proveedor de correos externo. | Todos los métodos de `EmailService` (`sendVerificationEmail`, `sendBookingConfirmation`, etc.) están anotados con `@Async` y encapsulados en bloques `try-catch` con degradación suave, permitiendo que la reserva o registro del huésped concluya con éxito sin interrumpirse. | ✅ **VERIFICADO Y SÓLIDO** |
| **AUD-05** | Backend Idempotencia de Pagos | Alta | Riesgo de cobros duplicados por múltiples clics de pago o reintentos de red del cliente. | Peticiones HTTP repetidas bajo alta latencia en `/api/payments/checkout` y `/api/payments/confirm`. | Ambos endpoints implementan `IdempotencyService` con huella digital (`bookingId`, `method`, `intentId`, `paymentToken`) y retorno de cabecera `X-Idempotent-Replay: true` sin re-procesar transacciones. Verificado en tests de pasarela simulada. | ✅ **VERIFICADO Y SÓLIDO** |
| **AUD-06** | Frontend Autenticación & Manejo 401 | Media | Sesión expirada en páginas protegidas podía generar comportamientos inesperados en la UI. | El interceptor `authInterceptor` intercepta códigos HTTP globales. | Manejo explícito: si recibe 401 en endpoints de sesión y el usuario estaba logueado, limpia `localStorage` de inmediato y redirige con `returnUrl` y parámetro `sessionExpired=true`. Se verificó que la estructura visual permanezca inalterada. | ✅ **VERIFICADO Y SÓLIDO** |
| **AUD-07** | Backend HTTP Webhook Coverage | Media | `PaymentWebhookController` no contaba con prueba HTTP unitaria dedicada para validar rechazo de payloads vacíos y excepciones de seguridad (401/400/500). | Cobertura previa concentrada solo en el servicio interno y no en el controlador REST. | Se construyó `PaymentWebhookControllerTest.java` cubriendo rechazo de payload vacío (400), firma inválida (401), validación de parámetros (400), fallas inesperadas (500) y procesamiento exitoso (200). Cobertura total en verde. | ✅ **RESUELTO** |
| **AUD-08** | Backend SSE Stream Security | Media | `BookingStreamController` no tenía prueba unitaria dedicada validando el rechazo de peticiones anónimas y handshake SSE. | El endpoint `/api/bookings/stream` depende de token por parámetro/query para Server-Sent Events. | Se implementó `BookingStreamControllerTest.java` verificando el rechazo forzoso (`SecurityException`) ante clientes anónimos y suscripción exitosa (`SseEmitter`) para usuarios autenticados. | ✅ **RESUELTO** |
| **AUD-09** | Frontend Checkout & MyBookings Coverage | Media | Las vistas críticas de Checkout y Mis Reservas carecían de especificaciones de prueba unitarias automatizadas (`spec.ts`). | Brecha de pruebas en el frontend para el flujo de pago con tarjeta, formateo dinámico y estados de reserva (expirada vs activa). | Se implementaron `checkout.spec.ts` (4 pruebas) y `my-bookings.component.spec.ts` (4 pruebas) validando temporizador de retención (15 min), formateo de tarjeta en bloques de 4 dígitos, validaciones de expiración de reserva e idempotencia. | ✅ **RESUELTO** |
| **AUD-10** | Frontend Checkout Test Routing Stub | Baja | Advertencia `RuntimeError: NG04002: Cannot match any routes. URL Segment: 'reservas/b_test_checkout_1/comprobante'` en salida de pruebas. | `checkout.spec.ts` configuraba `provideRouter([])` sin registrar la ruta de destino tras confirmar el pago. | Se definió el stub `StubReceiptComponent` y se registró la ruta `reservas/:id/comprobante` en `provideRouter`. Pruebas limpias al 100% sin advertencias en consola. | ✅ **RESUELTO** |
| **AUD-11** | CSP Nginx & Booking Detail Map | Media | El mapa en detalle de reserva mostraba mensaje de bloqueo CSP por orígenes no permitidos en iframes. | Nginx solo admitía `maps.google.com` y `google.com`, mientras Angular incrustaba OpenStreetMap sin sanitización. | Se aplicó la doble defensa recomendada por la Guía Técnica (Gatbat & Abigail): se agregaron `openstreetmap.org` y `*.openstreetmap.org` al CSP en `nginx.conf`, y se refactorizó `BookingDetailComponent` con `DomSanitizer`, método dinámico `getSafeEmbedMapUrl()` y Google Maps Embed con fallback por sede. 51 tests pasando y build verificado. | ✅ **RESUELTO** |
| **AUD-12** | Mobile Flutter Architecture Expansion | Media | La aplicación móvil requería los flujos estandarizados de verificación de cuenta, reserva en 4 pasos y pasarela de pago en 2 fases. | Ausencia de pantallas dedicadas para el stepper multipaso y confirmación de pagos en móvil sincronizados con el backend Spring Boot. | Se integraron `email_verification_screen.dart`, `booking_stepper_screen.dart` (cálculo de 8% de comisión y desglose COP) y `payment_screen.dart`. Documentación detallada de la app migrada a `AUDITORIA_APP.md`. | ✅ **RESUELTO** |
| **AUD-13** | Sincronización SSE Paneles Web | Alta | El panel de Administrador y Anfitrión requerían actualización inmediata sin recargar página ante reservas y pagos generados desde móvil o web. | Los eventos de pago en el backend no siempre activaban la reactividad de `BookingEventListener` hacia los streams abiertos. | Se integró y verificó `BookingEventListener` conectado a `/api/bookings/stream`. Al pagar en cualquier cliente, emite evento instantáneo refrescando métricas, ocupación y reservas en vivo en Angular. | ✅ **VERIFICADO Y SÓLIDO** |
| **AUD-14** | Cache Web & PWA Invalidation | Media | Posible desactualización de assets o servido de bundles cacheados tras nuevos despliegues de la web. | Service Workers y caché agresivo en navegadores de clientes. | Se configuraron headers `no-cache, no-store, must-revalidate` en `nginx.conf` para `index.html` y se implementó auto-invalidación de caché en el cliente web. | ✅ **RESUELTO** |
| **AUD-15** | Soporte Reactivo Multidivisa en Paneles | Media | El cambio de moneda (COP, USD, EUR) en el selector del navbar solo afectaba algunas secciones públicas y mantenía valores fijos en pesos en los paneles de administración y anfitrión. | Los paneles `AdminDashboardComponent` y `HostDashboardComponent` utilizaban pipes fijos `number:'1.0-0'` con signo `$` rígido en KPIs y tablas en vez de delegar en `CurrencyService`. | Se inyectó `CurrencyService` y se actualizaron los KPIs de Ingresos Netos/Generados, tablas de reservas, reembolsos, precios por noche, comisiones del 8% y liquidaciones netas con `currency.formatPrice(...)`. Ahora el cambio es instantáneo y reactivo en COP ($), USD (US$) y EUR (€). | ✅ **RESUELTO** |
| **AUD-16** | Backend SSE Syntax & Clean | Alta | Fallo de compilación en `BookingSseService.java` debido a código duplicado fuera de métodos (`return emitter;`). | Merge o edición previa dejó un bloque huérfano rompiendo la compilación con `-source 21`. | Se eliminó la sentencia huérfana en `backend-java/src/main/java/co/palaciodelmar/backend/service/BookingSseService.java`. Verificado con `.\mvnw.cmd test-compile` y suite de 453 pruebas de backend pasando al 100%. | ✅ **RESUELTO** |
| **AUD-17** | Frontend Web Accesibilidad WCAG 2.2 | Media | `src/app/accessibility.spec.ts` fallaba en 3 pruebas de rol y perfil (`Navbar Accessibility` y `Profile Accessibility`) al evaluar sesiones simuladas. | `AuthService.loadCachedUser()` exige `emailVerified: true` y token JWT válido; las pruebas inyectaban usuarios sin estos atributos, impidiendo que el navbar reconociera el estado logueado. | Se actualizaron las pruebas en `accessibility.spec.ts` incluyendo `emailVerified: true` y token de sesión. Suite de 51 pruebas de Angular pasando al 100% con `ng test --watch=false`. | ✅ **RESUELTO** |

---

## 🧪 Pruebas de Verificación y Regresión Ejecutadas

1. **Compilación Backend**:
   - Comando: `.\mvnw.cmd test-compile`
   - Resultado: Exitoso (`BUILD SUCCESS`).
2. **Suite Unitaria / Integración Backend**:
   - Comando: `.\mvnw.cmd test`
   - Resultado: **453 tests ejecutados, 0 fallos, 0 errores, 0 omitidos**.
3. **Chequeo de Tipos Frontend**:
   - Comando: `npx tsc --noEmit`
   - Resultado: 0 errores de tipado.
4. **Compilación Frontend Producción**:
   - Comando: `npm run build`
   - Resultado: Compilación exitosa en 7.9s (bundle optimizado en `dist/palacio-del-mar-web`).
5. **Suite de Pruebas Frontend**:
   - Comando: `npm test -- --watch=false`
   - Resultado: **11 archivos de test pasando, 51 tests exitosos al 100%**.
