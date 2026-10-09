# AUDITORÍA — Palacio del Mar (rama `proyecto-angular-java`)

> Documento para copiar a `docs/AUDITORIA.md`. Reemplaza a la "Fase 0" del plan anterior: la auditoría ya está hecha sobre tu código real.
> **Fecha del análisis:** 9 de octubre de 2026 · **Fuente:** `palacio-del-mar.zip` (rama `proyecto-angular-java`, 120 commits en el historial).

---

## 0. Alcance y limitaciones (léelo primero)

- **Lectura estática.** Leí el backend completo (controladores, servicios, modelos, repositorios, seguridad, configuración, tests), el frontend (rutas, guards, interceptor, servicios, checkout y paneles) y el historial de git.
- **No ejecuté nada.** No pude compilar, correr tests ni levantar Docker (sin red ni dependencias en mi entorno). Los hallazgos vienen del código, no de pruebas en ejecución. Por eso cada hallazgo crítico incluye un **comando para que lo verifiques tú** (sección 5).
- No revisé a fondo `ReviewController`, `ExperienceService`, `AvailabilityService` (cálculo mensual), `SuiteService` (listados) ni los componentes visuales secundarios (hero, spa, dining, etc.).
- Las referencias `archivo:línea` corresponden a la copia que subiste.

---

## 1. Resumen ejecutivo

**Qué tienes bien:** un prototipo visualmente rico y con buena base: Spring Boot + Angular + MongoDB dockerizados, BCrypt, JWT en cookie `HttpOnly` (no en `localStorage`), rutas con carga diferida, un diseño de disponibilidad ingenioso (índice único sobre un arreglo de fechas) y propiedad de reservas validada en modificar/cancelar/pagar.

**Qué tienes que saber:** el proyecto fue **migrado de Node/React/Express a Java/Angular** y en el camino **se perdieron reglas de negocio** que tu README todavía describe (precios por temporada, promociones, estadía larga, política de cancelación, idempotencia de pagos, verificación de correo, límites de intentos). Además hay **6 hallazgos críticos** de integridad y seguridad que conviene corregir antes de cualquier trabajo de diseño.

| Severidad | Cantidad | Qué significa |
|---|---|---|
| 🔴 Crítico | 6 | Puede producir doble reserva, pérdida de reservas pagadas, toma de cuentas o fraude de precio |
| 🟠 Alto | 9 | Brecha seria de seguridad, correctitud o arquitectura |
| 🟡 Medio | 10 | Calidad, mantenibilidad, experiencia |

**Veredicto honesto:** con la base actual puedes llegar a un proyecto de alto nivel, pero **no antes de resolver los críticos y recuperar las reglas de negocio**. Agregar pantallas bonitas sobre esta base sin corregir eso daría una demo vistosa que falla en cuanto alguien la prueba con seriedad.

---

## 2. Stack real (no el del README)

| Capa | Real | Observación |
|---|---|---|
| Backend | Java 21 · **Spring Boot 4.1.1** | Starters modulares de Boot 4 (`starter-webmvc`). La especificación v2 se escribió pensando en 3.x: adaptar, **no bajar de versión** |
| Seguridad | Spring Security + **jjwt 0.11.5** (HS256) | jjwt está desactualizado (existen versiones 0.12+) |
| BD | **MongoDB 7**, sin autenticación, sin réplica | Puerto ligado a `127.0.0.1` (bien). Sin transacciones |
| Frontend | **Angular 22**, Material 22, Tailwind 3.4, Vitest, TypeScript 6, zone.js | El README dice "Angular 18" y "React + Vite": está obsoleto |
| Servidor web | nginx con proxy a `/api/` | El frontend **no lo usa** (ver A7) |
| Orquestación | `docker-compose.yml` (mongo + backend-java + web-angular) | Sin perfiles dev/prod |
| CI/CD | Ninguno (no existe `.github/`) | |
| Tests | 2 archivos Java (1 humo + 1 JWT), 1 spec en Angular | Cobertura prácticamente nula |

**Modelo de datos real:** `users` · `branches` · `suites` (= tipo de habitación con `totalUnits`) · `suitenights` (un documento por reserva con un **arreglo de fechas** y `unitSlot` como "habitación física") · `bookings` · `payments` · `promotions` · `experiences` · `reviews`.
Referencias entre colecciones como `String`. Estados como `String` libres (`"pending"`, `"confirmed"`…). Roles como `String` (`"user"`, `"admin"`, `"host"`).

---

## 3. Lo que se perdió en la migración (README ↔ código Java)

| Regla documentada en el README (versión Node) | Estado en el código Java |
|---|---|
| Recargo por temporada (baja ×1.0, media ×1.15, alta ×1.3, pico ×1.5; Semana Santa y Navidad) | ❌ **No existe.** Precio = `basePrice × noches` (`BookingService.java:142`, `:306`). `SuiteService` devuelve `season("low")` fijo |
| Promociones por sucursal descuentan noches | ❌ Existen CRUD y listado, **pero no se aplican al precio** |
| Estadía larga: 5 % desde 5 noches, 10 % desde 7 | ❌ No existe |
| Desglose por noche congelado en la reserva | ❌ Solo `pricePerNight` único y `totalPrice` |
| Cancelación: reembolso total si faltan ≥ 7 días, 10 % de retención si no | ❌ `cancelBooking` reembolsa 100 % siempre y **no valida el estado**. Además `PaymentService.getPaymentConfig` anuncia "48 horas / 100 %" (contradice al README) |
| Máx. 3 reservas pendientes por usuario; repetir la misma reserva devuelve la existente; 10 intentos / 10 min | ❌ No existe |
| Clave de idempotencia por pago; un solo pago activo por reserva (índice único parcial) | ❌ No existe |
| Confirmar con actualización atómica solo si sigue `pending`, vigente y con el mismo total | ❌ Se hace `save()` directo sin condiciones |
| Verificación de correo; recuperación de contraseña | ❌ Rutas permitidas en `SecurityConfig` **sin controlador** (ver C5) |
| "Nunca vence una reserva con dinero recibido" | ⚠️ Parcial (ver C2: peor, el bloqueo de noches sí se borra) |
| Tests de concurrencia contra MongoDB real | ❌ Solo 1 test de contexto que requiere Mongo levantado |

---

## 4. Hallazgos

### 🔴 CRÍTICOS

#### C1 — Los índices únicos probablemente no existen (doble reserva posible)
- **Evidencia:** `application.properties:7` → `spring.data.mongodb.auto-index-creation=false`, y no hay `IndexOperations`/`ensureIndex` en ningún archivo.
- **Impacto:** el único mecanismo anti doble reserva es el índice único compuesto `{suite, unitSlot, dates}` de `SuiteNight`. También dependen de índices `users.email`, `bookings.bookingCode` y `suites.slug`. En una base **nueva** (otra máquina, `docker compose down -v`, el equipo del profesor) **no se crean**. Hoy puede funcionar solo porque tu volumen de Mongo conserva índices creados por la versión Node/Mongoose.
- **Arreglo:** crear los índices al arrancar de forma explícita (componente `IndexInitializer` con `MongoTemplate.indexOps`, o `auto-index-creation=true` en desarrollo + migraciones con Mongock en producción) y **probarlo** con un test de concurrencia.
- **Verifícalo:** comando en 5.1.

#### C2 — Las reservas pagadas pierden el bloqueo de la habitación
- **Evidencia:** `BookingService.java:190` crea `SuiteNight` con estado `"held"` y `expiresAt` a 30 min. **Nunca** se cambia a `"blocked"` al pagar (ni en `PaymentService`). `BookingExpiryScheduler.java:38` borra todos los `SuiteNight` en `"held"` con `expiresAt` vencido, **sin comprobar que la reserva siga pendiente**.
- **Impacto:** unos 30–35 minutos después de pagar, el scheduler elimina las noches de la reserva **confirmada** y la habitación queda libre para otro huésped: **reservas pagadas con doble ocupación**.
- **Arreglo:** al confirmar el pago, actualizar el `SuiteNight` a `"blocked"` y `expiresAt = null` en la **misma operación atómica** que confirma la reserva; el scheduler solo debe borrar holds cuya reserva siga `pending`; agregar test de regresión.
- **Verifícalo:** comando en 5.2.

#### C3 — Precios controlados por el cliente
- **Evidencia:** `BookingService.createBooking` (bloque de `foodOrders`, después de la línea ~142): si `food.getPrice() != null` se suma el precio **que envía el navegador**; `BookingRequest.foodOrders` es `List<Booking.FoodOrder>` sin validación. El recogida de aeropuerto (150 000) y el combo de desayuno (35 000) están **fijos en el código**.
- **Impacto:** un precio negativo o cero abarata la reserva; el total puede quedar incluso negativo.
- **Arreglo:** el servidor calcula **todo** el precio a partir de catálogos propios (tarifas, experiencias, servicios VIP en base de datos); el cliente solo envía identificadores y cantidades. Validar `> 0`, cantidades máximas y tope de totales.
- **Verifícalo:** comando en 5.3.

#### C4 — Cookie de sesión + CSRF desactivado + CORS comodín con credenciales
- **Evidencia:** `SecurityConfig.java:38` (`csrf` deshabilitado), `:81` (`allowedOriginPatterns("*")`) y `:84` (`allowCredentials(true)`); `AuthController.java:30-32` y `:52-54` (cookie `jwt` con `Secure=false` y sin `SameSite`); `JwtAuthenticationFilter` acepta el JWT desde esa cookie.
- **Impacto:** Spring refleja cualquier `Origin` con credenciales habilitadas. Si la sesión viaja en cookie, cualquier sitio puede intentar peticiones autenticadas. Hoy lo atenúa el comportamiento por defecto de algunos navegadores con cookies sin `SameSite`, pero **no todos lo aplican** y se rompe en cuanto despliegues bajo HTTPS con `SameSite=None`.
- **Arreglo:** lista blanca de orígenes por perfil; cookie `HttpOnly; Secure; SameSite=Strict` (o Lax) con `ResponseCookie`; defensa CSRF explícita para endpoints con cookie (cabecera personalizada u `Origin` verificado) o pasar a access token en memoria + refresh en cookie (ver SPEC §8).
- **Verifícalo:** comando en 5.4.

#### C5 — Cuentas sin verificación y usuarios "fantasma": toma de cuenta
- **Evidencia:** `AuthService.java:43` y `BookingService.java:67` crean usuarios con `emailVerified(true)` sin verificar nada. Una reserva de invitado **busca un usuario por correo y le adjunta la reserva**; si no existe, crea una cuenta sin contraseña. `SecurityConfig` permite `/api/auth/verify-email`, `/reset-password` y `/forgot-password`, pero **no hay controlador** que los atienda.
- **Impacto:** (a) un atacante registra primero el correo de la víctima; cuando la víctima reserve como invitada, la reserva se adjunta a la cuenta del atacante, que luego ve los datos de la víctima. (b) Una reserva de invitado con el correo de alguien bloquea que esa persona se registre ("El email ya está registrado") y no puede recuperar nada porque no existe el flujo.
- **Arreglo:** verificación de correo obligatoria antes de adjuntar reservas a una cuenta; reservas de invitado que **no** crean ni reutilizan usuarios (se identifican por código + correo); flujo completo de recuperación/verificación con tokens hash, de un solo uso y con vencimiento; respuestas genéricas anti enumeración.

#### C6 — Credenciales y secretos fijos (y en el historial)
- **Evidencia:** `DataInitializer.java:577-582` crea **siempre** un administrador `admin@palaciodelmar.co` con una contraseña fija escrita en el código, en cualquier entorno. `application.properties:10` tiene un **secreto JWT por defecto** (`${JWT_SECRET:local_development_secret_key_...}`): si falta la variable, el backend arranca con un secreto público. El historial de git contiene versiones anteriores de `docker-compose.yml` con `JWT_SECRET`, `ADMIN_PASSWORD` y `DEV_ADMIN_PASSWORD` (valores no reproducidos aquí).
- **Lo bueno:** `.env` **nunca fue confirmado** en git y el `docker-compose.yml` actual exige `JWT_SECRET`.
- **Impacto:** si el repositorio es público (o lo fue), esos valores deben considerarse **comprometidos**.
- **Arreglo:** (1) rotar JWT secret y contraseñas; (2) quitar el admin fijo (crear el primero desde variables de entorno solo si no existe, forzando cambio de contraseña) y el valor por defecto del secreto (fallar al arrancar si no hay secreto ≥ 32 bytes); (3) si el repo es público, limpiar el historial (`git filter-repo` o BFG) o asumir que el secreto está expuesto y rotarlo.

---

### 🟠 ALTOS

| ID | Hallazgo | Evidencia | Arreglo resumido |
|---|---|---|---|
| **A1** | **Anfitrión = administrador.** `/api/admin/**` permite `ADMIN` **y** `HOST` sin alcance por sucursal: un anfitrión ve todas las reservas, cambia estados y borra promociones de cualquier sucursal | `SecurityConfig.java:52`, `AdminController` | RBAC por permiso + alcance por sucursal (SPEC §4, §5, §9) |
| **A2** | **`/api/host/**` no exige rol**: cae en `anyRequest().authenticated()`; solo lo limita que `user.branches` esté vacío | `SecurityConfig.java:53`, `HostController` | Exigir permiso en la ruta y en el servicio |
| **A3** | **Sesión sin control:** token de 7 días en la práctica (la variable `JWT_EXPIRATION=604800000` del compose sobrescribe el valor del properties), sin refresh, sin revocación; `logout` solo borra la cookie y el token **también viaja en el cuerpo** de la respuesta | `docker-compose.yml`, `AuthController.java:30-52` | Access 15 min + refresh rotativo con detección de reutilización (SPEC §8) |
| **A4** | **Sin validación ni límites en autenticación:** `RegisterRequest` no tiene anotaciones y el controlador no usa `@Valid` (la contraseña de 6 caracteres solo se exige al **cambiar**); sin bloqueo de cuenta ni rate limiting; el registro revela si el correo/teléfono ya existen; un correo nulo produce `NullPointerException` (500) | `RegisterRequest`, `AuthController`, `AuthService` | Bean Validation, política de contraseña, Bucket4j, mensajes genéricos |
| **A5** | **Pagos frágiles:** dos endpoints paralelos (`/checkout` y `/process`), monto aceptado desde el cliente, sin idempotencia, sin transición condicional, `simulateFailure` controlado por el cliente | `PaymentService`, `PaymentController` | `PaymentGateway` simulado + intents + confirmación atómica (SPEC §12) |
| **A6** | **El frontend trata errores como éxito:** si `/payments/process` responde 404, 200 o 201 **como error**, navega al comprobante igual | `checkout.ts:373` | Eliminar; manejar errores por código de ProblemDetail |
| **A7** | **URL de API fija** `http://localhost:5000/api` en el frontend: se salta el proxy de nginx, obliga a CORS y no existe `environment.prod.ts` | `environment.ts:3`, `nginx.conf` | `apiUrl: '/api'` + proxy en desarrollo y producción |
| **A8** | **Cancelación y modificación sin máquina de estados:** `cancelBooking` acepta cualquier estado (incluso `checked_out` o ya `cancelled`) y reembolsa 100 % del pagado sin política; `modifyBooking` no restringe estados ni recalcula con tarifas reales | `BookingService.java:408-427`, `:234-359` | Máquina de estados y política (SPEC §10, §11.3) |
| **A9** | **Contratos frontend ↔ backend rotos:** el panel de admin llama a `/admin/metrics`, que **no existe** (el backend expone `/admin/stats`); la UI ofrece Tarjeta/PSE/PayPal y el backend anuncia tarjeta/PSE/Nequi; el checkout descarga **todas** las reservas del usuario y filtra en el cliente | `admin-dashboard.component.ts:144`, `AdminController`, `checkout.ts` | Contrato OpenAPI + tipos compartidos + endpoint `GET /bookings/{id}` |

### 🟡 MEDIOS

| ID | Hallazgo | Evidencia |
|---|---|---|
| **M1** | Entidades como cuerpo de petición (*mass assignment*): `@RequestBody User` en `PUT /auth/profile` y `@RequestBody Promotion` en crear promoción | `AuthController`, `AdminController` |
| **M2** | Paginación sin tope (`limit` libre) y métricas del anfitrión que cargan todas las reservas en memoria | `AdminController:29`, `HostController` |
| **M3** | `GlobalExceptionHandler`: detecta el 403 buscando "permiso"/"acceder" en el texto del mensaje; todo `IllegalArgumentException` es 400 (nunca 404/409); `printStackTrace`; sin `ProblemDetail` ni `traceId` | `GlobalExceptionHandler.java` |
| **M4** | Chat "concierge" público, no es IA (respuestas fijas) con **precios y promesas hardcodeados** ("desde COP 860.000", "upgrade gratuito") y un endpoint público `clear-cache` que no hace nada | `ChatService`, `ChatController` |
| **M5** | Interceptor: **cualquier 401** (incluso una contraseña incorrecta) limpia la sesión y redirige al inicio; el objeto de usuario (con teléfono/documento) se guarda en `localStorage` y los guards deciden por ese dato (solo UX; el backend protege) | `auth.interceptor.ts`, `auth.service.ts` |
| **M6** | Pruebas casi inexistentes y sin CI; el único test de contexto exige MongoDB levantado | `src/test`, `app.spec.ts` |
| **M7** | Dependencias externas en tiempo de ejecución: imágenes de Unsplash (hotlinking), Google Fonts, iframe de Google Maps; la demo falla sin internet | `DataInitializer`, `index.html`, `suite-detail.component.ts:93` |
| **M8** | Higiene del repositorio: scripts residuales (`replace.py`, `fix_images.py`, `fix_*.js`, `generate*.sh`), `HELP.md`, README obsoleto, 9 ramas y carpeta `.agents/` versionada | raíz del repo |
| **M9** | Calidad del frontend: `styles.css` de 3 321 líneas + estilos en línea en las plantillas, 53 usos de `any`, `*ngIf` heredado, sin sistema de diseño ni componentes reutilizables, un solo spec | `src/styles.css`, `checkout.ts` |
| **M10** | Fechas: `LocalDate.now()` con la zona del servidor (`getUpcomingBookings`) y `LocalDate` en Mongo sujeto a conversión por zona; los *holds* vencidos siguen bloqueando hasta 5 minutos (cron cada 5 min) y el scheduler no es atómico (una `OptimisticLockingFailure` aborta el ciclo) | `BookingService`, `BookingExpiryScheduler` |

---

## 5. Cómo verificar tú mismo los críticos

> Ejecútalos en tu entorno local. Ninguno modifica datos de forma peligrosa.

**5.1 — C1: ¿existen los índices?**
```bash
docker compose exec mongo mongosh palacio_db --quiet --eval '
  printjson(db.suitenights.getIndexes().map(i => i.name));
  printjson(db.users.getIndexes().map(i => i.name));
  printjson(db.bookings.getIndexes().map(i => i.name));'
```
Debe aparecer `suite_unit_dates_idx` y un índice único de `email`. Si no aparecen, C1 está confirmado. Para probar desde cero: `docker compose down -v && docker compose up -d --build` y repite el comando.

**5.2 — C2: ¿se borra el bloqueo de una reserva pagada?**
1. Crea una reserva y págala desde la interfaz.
2. Obtén su id y comprueba el bloqueo: `db.suitenights.find({booking: "<ID_DE_LA_RESERVA>"})` (verás `status: "held"`).
3. Simula el paso del tiempo: `db.suitenights.updateOne({booking: "<ID>"}, {$set: {expiresAt: new Date(Date.now() - 60000)}})`.
4. Espera al siguiente ciclo de 5 minutos y vuelve a consultar: el documento habrá desaparecido aunque la reserva siga `confirmed`.

**5.3 — C3: precio manipulado** (sustituye `TOKEN`, `SUITE_ID` y las fechas)
```bash
curl -s -X POST http://localhost:5000/api/bookings \
  -H "Authorization: Bearer TOKEN" -H "Content-Type: application/json" \
  -d '{"suiteId":"SUITE_ID","checkIn":"2026-12-01","checkOut":"2026-12-03","guests":1,
       "foodOrders":[{"dishName":"x","type":"custom","price":-500000}]}'
```
Si `totalPrice` baja respecto a la reserva sin `foodOrders`, C3 está confirmado.

**5.4 — C4: CORS reflejado con credenciales**
```bash
curl -s -i -H "Origin: https://sitio-malicioso.example" http://localhost:5000/api/branches | grep -i "access-control"
```
Si responde `Access-Control-Allow-Origin: https://sitio-malicioso.example` y `Access-Control-Allow-Credentials: true`, C4 está confirmado.

**5.5 — C5: reserva de invitado que se adjunta a una cuenta ajena**
1. Registra un usuario con el correo `victima@ejemplo.com` (no se verifica nada).
2. Cierra sesión y crea una reserva de invitado con ese mismo correo.
3. Inicia sesión como `victima@ejemplo.com`: la reserva aparece en "Mis reservas".

**5.6 — C6:** busca la contraseña fija en `DataInitializer.java:577-582` e intenta ingresar con ella en tu instancia local; revisa también `git log --all -p -- docker-compose.yml` buscando `JWT_SECRET`/`PASSWORD`.

---

## 6. Brecha frente a la especificación (`docs/SPEC.md`)

| Requisito de la spec | Estado actual | Esfuerzo |
|---|---|---|
| Roles por permiso con alcance por sucursal (`assignments[]`) | ❌ Rol único en texto; `branches` solo se usa en `HostController` | L |
| Guard de sucursal en servicios (`BranchAccess`) y tests de aislamiento | ❌ | M |
| JWT corto + refresh rotativo + revocación (`tokenVersion`) | ❌ (token largo, sin refresh) | M |
| Verificación de correo, recuperación, rate limiting, bloqueo | ❌ | M |
| Cabeceras de seguridad, CORS estricto, perfiles dev/prod | ❌ | S |
| Disponibilidad sin doble reserva | ⚠️ Buen diseño, **sin garantía de índice** (C1) y con bug de bloqueo (C2) | S–M |
| Máquina de estados de reserva con historial | ❌ (strings libres) | M |
| Precio por noche congelado, temporadas, promociones, estadía larga | ❌ | M |
| Política de cancelación configurable | ❌ | S–M |
| Pagos simulados con `PaymentGateway`, intents, idempotencia, tarjetas de prueba | ⚠️ Simulación básica; faltan idempotencia, atomicidad y abstracción | M |
| Auditoría inmutable y *settings* | ❌ | M |
| Reportes (ocupación, ADR, RevPAR) | ⚠️ Solo totales básicos | M |
| Tests (concurrencia, aislamiento, JWT, pagos), CI | ❌ | M–L |
| Notificaciones por correo (Mailpit) | ⚠️ Dependencia presente, sin uso verificado | S |
| Frontend: sistema de diseño, stepper, checkout simulado realista, paneles | ⚠️ Visualmente rico pero con estilos en línea y contratos rotos | L |

**Decisión de diseño recomendada:** **conserva** tu modelo `Suite` + `unitSlot` + `SuiteNight` (arreglo de fechas con índice único). Funciona **sin réplica ni transacciones** y evita reescribir el dominio. Donde la sección 7 del SPEC proponga `availability_locks` y `room_nights`, trátala como alternativa, no como requisito; lo que sí es obligatorio es: índice garantizado, transiciones atómicas y tests de concurrencia.

---

## 7. Lo que está bien y se debe conservar

- BCrypt para contraseñas y sesión por cookie `HttpOnly` (el JWT no vive en `localStorage`).
- Backend sin estado (`STATELESS`) y usuario cargado en cada petición (desactivar/suspender surte efecto inmediato).
- Propiedad validada al **modificar, cancelar y pagar** una reserva propia.
- `@Version` (bloqueo optimista) en `Booking` y manejo de `DuplicateKeyException`/`OptimisticLockingFailureException`.
- Diseño anti-solape con índice único multiclave sobre fechas.
- Rutas cargadas de forma diferida, componentes standalone, `nginx` con gzip y *fallback* de SPA.
- `.env` fuera de git, `JWT_SECRET` obligatorio en el compose, Mongo enlazado a `127.0.0.1`, *healthcheck* de Mongo.
- Respuesta de checkout sin enviar datos de tarjeta (solo nombre y últimos 4 dígitos).
