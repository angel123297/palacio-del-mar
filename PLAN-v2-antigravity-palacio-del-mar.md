# PLAN v2 — Palacio del Mar (basado en la auditoría real)

> Copia este archivo a `docs/PLAN.md`, junto con `docs/AUDITORIA.md` y `docs/SPEC.md` (la especificación v2 que ya tienes).
> **Reemplaza** al plan anterior: aquel partía de cero; este parte de tu código real y ordena el trabajo por **riesgo**, no por capas.

---

## 1. Orden de trabajo y por qué

| Fase | Objetivo | Por qué en este orden | Tiempo orientativo |
|---|---|---|---|
| **F0 — Emergencia** | Cerrar los fallos que rompen datos o exponen secretos (C1, C2, C3, C6 + CORS/cookie) | Son pocos cambios, de alto impacto, y todo lo demás se apoya en ellos | 1–2 días |
| **F1 — Seguridad y roles** | Roles con alcance por sucursal, sesión robusta, verificación de correo (C4, C5, A1–A4) | Sin esto, anfitrión = administrador y las cuentas se pueden tomar | 4–6 días |
| **F2 — Reservas, precios y pagos** | Recuperar reglas perdidas, máquina de estados, pagos simulados sólidos (A5, A8) | Es el corazón del negocio y lo que el evaluador probará | 5–7 días |
| **F3 — Frontend de alto nivel** | Sistema de diseño, flujos realistas, checkout simulado creíble (A6, A7, A9, M5, M7, M9) | La parte visual rinde más cuando el backend ya es confiable | 6–9 días |
| **F4 — Anfitrión y administrador** | Paneles por rol, reportes, personal, auditoría | Cierra la mitad operativa que hoy está más floja | 4–6 días |
| **F5 — Calidad, DevOps y documentación** | Tests, CI, README real, docs, demo | Es lo que más pesa en la nota y suele dejarse para el final | 4–5 días |
| **F6 — Extras** | 2FA, observabilidad, ZAP, PWA… | Solo si sobra tiempo | opcional |

### Si el tiempo es corto

| Te quedan… | Haz | Sacrifica |
|---|---|---|
| **≥ 6 semanas** | F0–F5 completas + 2 extras | — |
| **3–5 semanas** | F0, F1 (sin 2FA), F2 (sin PDF), F3 (A–D), F5 mínima | F4 reducida a reportes básicos y gestión de reservas |
| **≤ 2 semanas** | **F0 completa**, F1 (C4, C5, A1, A2), F2 (precios + pagos atómicos), F3 (checkout y apiUrl), README real | Todo lo demás |

> **Regla de oro:** F0 no se negocia. Un hotel que cobra una reserva y luego la vuelve a vender, o que deja entrar con un precio negativo, no se salva con un buen diseño.

---

## 2. Decisiones ya tomadas (para que Antigravity no las reabra)

1. **No bajar versiones.** El proyecto usa **Spring Boot 4.1.1** y **Angular 22**. El SPEC se redactó pensando en Boot 3.x: adaptar el código a Boot 4 (starters modulares), no degradar.
2. **Se conserva el dominio actual:** `Suite` (tipo de habitación con `totalUnits`) + `unitSlot` (habitación física) + `SuiteNight` (un documento por reserva con arreglo de fechas e índice único). **No se reescribe** a `rooms`/`room_nights`. Funciona sin réplica ni transacciones **siempre que** el índice exista y las transiciones sean atómicas. Donde el SPEC §7 proponga `availability_locks` y `room_nights`, es alternativa opcional.
3. **Mapeo de roles:** `user` → `GUEST` · `admin` → `SUPER_ADMIN` · `host` → `BRANCH_MANAGER` *(supuesto: confirma qué hace hoy tu anfitrión; si atiende llegadas y salidas, es `RECEPTIONIST`)*. Después se agrega `RECEPTIONIST`.
4. **Reservar exige cuenta con correo verificado.** Se elimina la rama de "reserva de invitado" que hoy crea usuarios fantasma (C5). Es más simple y más seguro; "reservar como invitado con código + correo" queda como mejora futura.
5. **Pagos:** solo simulados, detrás de `PaymentGateway`. Métodos: **Tarjeta, PSE y Nequi** (se quita PayPal: el backend y el mercado objetivo no lo usan).
6. **IVA:** decidir si los precios **incluyen** impuesto o no, y que backend y UI muestren lo mismo. Hoy la UI calcula "IVA 19 %" sobre el total en el cliente y el backend no suma impuesto. El desglose lo calcula **solo el servidor**.
7. **Identidad visual:** se conserva (negro `#0d0d0d` + dorado `#d4af37`, estilo boutique), pero se ordena en tokens y se reducen las tipografías de **3 a 2** (titulares serif + texto sans).

---

## 3. Pasos manuales tuyos (antes de usar a Antigravity)

1. **Rota los secretos:** genera un `JWT_SECRET` nuevo (`openssl rand -hex 32`) en tu `.env` y cambia cualquier contraseña de administrador que hayas usado.
2. **Decide sobre el historial:** si el repo en GitHub es **público**, asume que los valores antiguos de `docker-compose.yml` están expuestos (ya los rotaste en el paso 1). Opcional pero recomendable: limpiar el historial con `git filter-repo`.
3. Crea la carpeta `docs/` en la rama de trabajo y copia: `SPEC.md`, `AUDITORIA.md`, `PLAN.md`.
4. Crea una rama por fase: `fix/f0-emergencia`, `feat/f1-seguridad`, etc. **No trabajes sobre `main`.**
5. Haz un respaldo de tu base local: `docker compose exec mongo mongodump --db palacio_db --archive > respaldo.archive`.

---

## 4. Prompt maestro (pégalo al inicio de **cada** conversación con Antigravity)

```
Contexto: repo "Palacio del Mar" (Spring Boot 4.1.1 + Java 21, Angular 22, MongoDB 7). Lee SIEMPRE:
- docs/AUDITORIA.md (hallazgos reales con archivo y línea)
- docs/SPEC.md (especificación objetivo)
- docs/PLAN.md sección 2 (decisiones ya tomadas)

Reglas:
1. Primero entrégame un PLAN (archivos a tocar y orden) y espera mi aprobación. Sin código hasta entonces.
2. Modifica lo mínimo necesario. No renombres ni muevas archivos fuera del alcance de la fase. Conserva lo que funciona.
3. No degrades versiones (Boot 4.1.1, Angular 22). Si el SPEC asume Boot 3, adáptalo.
4. Cada cambio de comportamiento lleva test. No des la fase por terminada si `./mvnw verify` o `npm test` fallan.
5. Los tests de integración usan Testcontainers con MongoDB real, no mocks del repositorio.
6. Nunca registres ni persistas datos de tarjeta, contraseñas ni tokens.
7. El servidor calcula precios y estados: el cliente solo envía identificadores y datos del usuario.
8. Al terminar: actualiza docs/CHANGELOG.md, lista los comandos exactos para que yo verifique y marca en docs/AUDITORIA.md los hallazgos resueltos (con el commit).
9. Commits pequeños con Conventional Commits (feat:, fix:, test:, docs:, refactor:).
```

---

## 5. F0 — Emergencia (C1, C2, C3, C6 + CORS/cookie)

**Rama:** `fix/f0-emergencia` · **Lee:** AUDITORIA §4 (C1, C2, C3, C4 parcial, C6)

### Prompt
```
Aplica el prompt maestro. Misión: corregir los hallazgos C1, C2, C3 y C6 de docs/AUDITORIA.md y las
mitigaciones rápidas de C4. Entrégame un PLAN primero.

1. C1 — Índices: crea un componente IndexInitializer (MongoTemplate.indexOps) que garantice al arrancar:
   suitenights único {suite, unitSlot, dates}; users.email único; bookings.bookingCode único (sparse);
   suites.slug único; branches.name y branches.slug únicos; índices de consulta de bookings (user, branch, status).
   Si un índice único falla por datos duplicados existentes, que el arranque falle con un mensaje claro.
2. C2 — Bloqueo de reservas pagadas: al confirmar el pago, el SuiteNight pasa a "blocked" con expiresAt=null
   en la MISMA operación atómica que confirma la reserva (updateFirst/findAndModify con filtro por estado).
   El scheduler solo borra SuiteNight en "held" cuya reserva siga "pending"; cada reserva se procesa dentro de
   un try/catch con actualización condicional (pending → expired). Reduce el cron a cada minuto.
3. C3 — Precios: elimina la confianza en precios del cliente. foodOrders y airportPickup se calculan desde un
   catálogo en servidor (colección o tabla de configuración de servicios extra); el cliente solo envía ids y cantidades.
   Valida cantidades (>0, máximo razonable) y que el total sea > 0.
4. C6 — Secretos: elimina el administrador con contraseña fija de DataInitializer. El primer admin se crea solo si
   no existe ninguno, desde SEED_ADMIN_EMAIL y SEED_ADMIN_PASSWORD (variables de entorno, mínimo 12 caracteres),
   y debe cambiar la contraseña en el primer ingreso. Elimina el valor por defecto de jwt.secret: si falta o mide
   menos de 32 bytes, la aplicación NO arranca. Actualiza .env.example y docker-compose.yml.
5. C4 (mitigación): CORS con lista blanca desde la propiedad app.cors.allowed-origins (sin comodines); cookie "jwt"
   con ResponseCookie: HttpOnly, Secure configurable por perfil, SameSite=Lax y Path=/.
6. Tests (Testcontainers): (a) los índices existen tras arrancar en una base vacía; (b) 20 hilos reservando la
   misma suite/unidad → exactamente 1 éxito; (c) reserva pagada conserva su bloqueo después de ejecutar el scheduler
   con expiresAt vencido; (d) foodOrders con precio negativo no altera el total.
```

### Definición de terminado
- [ ] `docker compose down -v && docker compose up -d --build` y el comando 5.1 de la auditoría muestra todos los índices.
- [ ] Los comandos 5.2, 5.3 y 5.4 de la auditoría **ya no reproducen** el problema.
- [ ] No existe ninguna contraseña ni secreto por defecto en el código.
- [ ] `./mvnw verify` en verde con los 4 tests nuevos.

---

## 6. F1 — Seguridad y roles (C4, C5, A1–A4, M1, M3)

**Rama:** `feat/f1-seguridad` · **Lee:** SPEC §3, 4, 5, 8, 9, 13 y 19.2–19.4

### Prompt
```
Aplica el prompt maestro. Misión: implementar seguridad y roles según docs/SPEC.md (§3–§9) adaptado al dominio actual.
Entrégame primero un PLAN con el diagrama de la autenticación (Mermaid) y la migración de datos.

1. Roles: reemplaza User.role (String) por users.assignments[{role, branchId}] y un enum Permission (SPEC §5.5–5.6).
   Script de migración idempotente: user→GUEST, admin→SUPER_ADMIN (branchId null), host→BRANCH_MANAGER para cada
   sucursal de User.branches. Agrega RECEPTIONIST. Roles de Fase 2 solo en el modelo.
2. Autorización: @EnableMethodSecurity; servicio BranchAccess (SPEC §9.2); 404 si no hay acceso a la sucursal y 403
   si falta el permiso. Elimina hasAnyRole("ADMIN","HOST") sobre /api/admin/**: cada endpoint exige su permiso.
   /api/host/** exige permiso y filtra por sucursales asignadas. Todos los listados intersectan con el alcance.
3. Sesión (SPEC §8): access token de 15 min (claims sub, jti, iat, exp, tv, iss, aud) + refresh token opaco rotativo,
   guardado como hash SHA-256 con detección de reutilización (revoca la familia) en cookie HttpOnly/Secure/SameSite=Strict
   y Path=/api/auth. NO devolver el token en el cuerpo. tokenVersion en users; logout y cambio de contraseña revocan.
   Defensa CSRF explícita para los endpoints que usan cookie (refresh/logout). Actualiza jjwt a 0.12+ (o usa
   spring-boot-starter-oauth2-resource-server con Nimbus).
4. Cuentas (C5): elimina la creación de usuarios por reserva de invitado; reservar exige cuenta con correo verificado.
   Implementa verify-email, forgot-password y reset-password (tokens aleatorios hash, un solo uso, vencimiento 24 h y
   30 min), reenvío de verificación, respuestas genéricas anti enumeración. Correo por SMTP a Mailpit (agrégalo al compose).
5. Validación y abuso: Bean Validation en TODOS los DTOs de entrada con @Valid; política de contraseña (≥10, sin triviales);
   bloqueo de cuenta tras 5 intentos / 15 min; Bucket4j en login, registro, forgot-password y reserva.
6. DTOs: reemplaza @RequestBody User (perfil) y @RequestBody Promotion por DTOs con campos permitidos.
7. Errores: ProblemDetail (RFC 7807) con code y traceId; excepciones de dominio (NotFound→404, Conflict→409,
   Forbidden→403, BusinessRule→422) en lugar de IllegalArgumentException para todo; elimina el 403 por texto del mensaje.
8. Cabeceras de seguridad (CSP, X-Content-Type-Options, frame-ancestors, Referrer-Policy, HSTS en prod) y perfiles
   dev/test/prod (en prod: sin Swagger, sin detalles de error).
9. Auditoría: AuditLogWriter de solo inserción (SPEC §13) para login, cambios de rol, reservas, pagos y cancelaciones.

Tests (SPEC §19.2–19.4): aislamiento por sucursal con tokens reales; anfitrión no accede a datos de otra sucursal;
nadie sube su propio rol; último SUPER_ADMIN protegido; token expirado/alterado/tv desfasado → 401; reutilización de
refresh revoca la familia; bloqueo de cuenta; verificación de correo obligatoria antes de reservar.
```

### Definición de terminado
- [ ] Un `BRANCH_MANAGER` de la sucursal A recibe **404** al consultar una reserva de la B (con token real).
- [ ] `/api/host/**` y `/api/admin/**` rechazan a un huésped y a un anfitrión sin permiso.
- [ ] El comando 5.5 (toma de cuenta) deja de funcionar.
- [ ] No se devuelve ningún token en el cuerpo de las respuestas; la sesión dura 15 min + refresh.

---

## 7. F2 — Reservas, precios y pagos (A5, A8, M10)

**Rama:** `feat/f2-reservas-pagos` · **Lee:** SPEC §10, 11, 12 y 19.5–19.6; AUDITORIA §3

### Prompt
```
Aplica el prompt maestro. Misión: recuperar las reglas de negocio perdidas (AUDITORIA §3) y blindar reservas y pagos.
Entrégame primero un PLAN.

1. PricingService (lógica pura, con tests unitarios): precio por noche con recargo de temporada (baja ×1.0, media ×1.15,
   alta ×1.3, pico ×1.5; Navidad/Año Nuevo 21/12–10/01 y Semana Santa calculada desde Pascua), promoción de la sucursal
   (descuenta su % en las noches que cubre), estadía larga (5 % desde 5 noches, 10 % desde 7), sin acumular promoción y
   estadía larga (aplica la que más ahorra). Redondeo HALF_UP a 2 decimales. El desglose por noche y el cálculo de
   impuestos se guardan en la reserva (pricing.nightly, subtotal, discount, taxes, total). SuiteService.calculatePrice y
   BookingService usan el MISMO PricingService (hoy el cálculo y el cobro divergen).
2. Máquina de estados: enum BookingStatus (pending, confirmed, checked_in, checked_out, cancelled, expired, no_show) con
   transiciones permitidas (SPEC §10) y statusHistory. Transiciones con actualización condicional (updateFirst/findAndModify
   filtrando por estado esperado). Una reserva en estado final es inmutable.
3. Política de cancelación configurable (settings): reembolso total si faltan ≥ 7 días; retención del 10 % si faltan menos;
   el gerente puede condonar con motivo. Mismos textos en backend y UI (hoy el backend anuncia "48 horas").
4. Retención y límites: hold de 30 min (configurable); máximo 3 reservas pendientes por usuario; repetir la misma reserva
   devuelve la existente; Idempotency-Key en POST /bookings; modifyBooking respeta estados y recalcula con PricingService,
   y si la reserva ya está pagada calcula la diferencia a pagar o a reembolsar.
5. Pagos (SPEC §12): interfaz PaymentGateway + SimulatedPaymentGateway; flujo token → intent → confirm con Idempotency-Key;
   tarjetas de prueba deterministas (4242… aprobada, …0002 rechazada, …9995 fondos insuficientes, …0069 vencida,
   …0119 error reintentable, …3220 requiere autenticación con código 123456); PSE y Nequi simulados (selección de banco;
   aprobación "push" de Nequi). Un solo pago activo por reserva (índice único parcial). Confirmación ATÓMICA: solo si la
   reserva sigue pending, vigente y el total cobrado coincide con el total actual. Unifica /checkout y /process en un
   solo flujo; el monto lo decide el servidor, nunca el cliente. simulateFailure solo en perfil dev.
   Reembolsos con motivo, nunca superiores a lo cobrado. Latencia y fallos simulados configurables (simulation.*).
6. Emails (Mailpit): confirmación, cancelación y recibo, enviados después del commit (evento AFTER_COMMIT).
7. Comprobante en PDF y archivo .ics del check-in.
8. Fechas: toda lógica de "hoy" usa la zona de la sucursal (America/Bogota); evita depender de LocalDate.now() del servidor.

Tests (SPEC §19.5–19.6): precios con cruce de temporadas y estadía larga; transiciones inválidas; cancelación dentro y fuera
de ventana; hold vencido sin esperar al scheduler; pago doble con la misma clave → un solo cobro; dos pestañas con claves
distintas → un solo pago activo; cambio de total mientras se paga → no confirma; ningún PAN/CVC en BD ni logs.
```

### Definición de terminado
- [ ] El precio mostrado en el buscador, el detalle, el checkout y el comprobante **es el mismo** (una sola fuente: `PricingService`).
- [ ] Doble clic en "Pagar" no genera dos cobros ni dos reservas.
- [ ] Cancelar una reserva ya cancelada o finalizada devuelve `409`.
- [ ] Cada tarjeta de prueba produce su resultado esperado.

---

## 8. F3 — Frontend de alto nivel (A6, A7, A9, M5, M7, M9)

**Rama:** `feat/f3-frontend` · **Lee:** SPEC §16

### 8.1 Qué significa "se ve realista y de alto nivel" (requisitos)

**Identidad y sistema de diseño**
- Tokens (CSS variables) para color, tipografía, espaciado, radios, sombras y movimiento; **2 tipografías** (titulares serif tipo Cormorant/Playfair + texto sans tipo Jost). Hoy hay 3 y un `styles.css` de 3 321 líneas con estilos en línea en las plantillas.
- Componentes reutilizables: tarjeta de habitación, selector de fechas, selector de huéspedes, chips de estado, *skeleton*, diálogo, *toast*, tabla con filtros/orden/paginación, tarjeta KPI.
- Imágenes **locales** optimizadas (WebP, `NgOptimizedImage`) y fuentes **autoalojadas**: la demo no debe depender de internet (M7). Fotografías con licencia libre o propia, con atribución en el README.

**Páginas públicas**
- *Hero* con buscador (sucursal, rango de fechas, huéspedes) y calendario que muestra días agotados, temporada y descuentos.
- Página de sucursal con galería y *lightbox*, mapa (se conserva), servicios con íconos, reseñas con calificación y políticas visibles.
- Resultados con tarjetas de habitación, **precio por noche y total con desglose**, filtros y orden, y escasez real ("quedan 2") calculada con la disponibilidad verdadera.
- Páginas legales (términos, privacidad, política de cancelación), FAQ y contacto.

**Flujo de reserva (stepper)**
- *Fechas → Habitación → Experiencias → Datos → Pago → Confirmación*, con **resumen fijo**, **contador del hold** y manejo de "ya no hay disponibilidad".

**Checkout simulado creíble**
- Tarjeta con detección de marca, formato, validación Luhn y vista previa animada; modal tipo 3-D Secure (código de prueba); estados *procesando / aprobado / rechazado / requiere autenticación*, con mensajes por código de error.
- **PSE** con selección de banco y pantalla de "redirección" simulada; **Nequi** con aprobación "push" simulada.
- Insignia discreta **"Modo demostración"** (configurable) y ayuda con las tarjetas de prueba.
- **Nunca** enviar número de tarjeta ni CVV al backend: se tokeniza en el paso simulado.

**Post-compra y cuenta**
- Confirmación con código, **QR**, "Agregar al calendario" (.ics) y comprobante PDF.
- "Mis reservas" con chips de estado, línea de tiempo y **vista previa de la penalidad antes de confirmar una cancelación**.
- Pantallas de login, registro, verificación de correo y recuperación de contraseña (además del modal).

**Calidad transversal**
- Responsive (móvil primero en el flujo público), *skeletons*, estados vacíos y de error, accesibilidad WCAG AA, Lighthouse ≥ 90.
- Control flow nuevo (`@if/@for`) en vez de `*ngIf`, tipos estrictos (sin `any`), señales para el estado.

### 8.2 Prompt
```
Aplica el prompt maestro. Misión: llevar el frontend a nivel alto según docs/PLAN.md §8.1 y docs/SPEC.md §16.
Entrégame primero un PLAN con: tokens de diseño, árbol de componentes compartidos, mapa de rutas y orden de entregas.

Entregas pequeñas (un commit/PR cada una):
A. Núcleo técnico (corrige hallazgos A6, A7, A9, M5):
   - environment: apiUrl '/api' con proxy.conf.json en desarrollo y nginx en producción; añade environment.production.
   - AuthService: sin guardar el usuario completo en localStorage; sesión restaurada con provideAppInitializer
     (refresh + /auth/me); estado con signals.
   - Interceptor: adjunta el access token (en memoria); ante 401 por expiración hace UN refresh en vuelo y reintenta una vez;
     NO redirige al inicio cuando el 401 viene de /auth/login; traduce ProblemDetail (code) a mensajes en español.
   - ELIMINA el manejo que trata errores 404/200/201 como éxito en el checkout.
   - Tipos fuertes: reemplaza los 53 `any` por interfaces; contrato único con el backend (genera tipos desde OpenAPI si es posible).
   - Corrige el panel de admin: debe consumir el endpoint real de métricas (no /admin/metrics inexistente).
   - Guards funcionales por PERMISO con PermissionService + directiva *appCan (consume /auth/me).
B. Sistema de diseño: extrae tokens y componentes de styles.css y de los estilos en línea; deja styles.css como capa base pequeña;
   autoaloja fuentes e imágenes (descarga y optimiza; sin hotlinking a Unsplash/Google Fonts).
C. Flujo público: home, sucursal, resultados, detalle de habitación y stepper de reserva con contador del hold.
D. Checkout simulado: tokenización simulada, tarjetas de prueba, 3-D Secure, PSE y Nequi (quita PayPal); consumo del flujo
   token→intent→confirm con Idempotency-Key; confirmación con QR, .ics y PDF.
E. Área del huésped: perfil, mis reservas con vista previa de penalidad, reseñas, login/registro/verificación/recuperación.
F. Pulido: accesibilidad, responsive, rendimiento (presupuestos de bundle, lazy loading), estados vacíos y de error.

Tests: unitarios (Vitest) de guards, interceptor, directiva *appCan y PricingDisplay; E2E (Playwright) de:
(1) registro + verificación + reserva + pago con tarjeta de prueba, (2) tarjeta rechazada y reintento, (3) cancelación con penalidad,
(4) un usuario de la sucursal A no ve datos de la B.
```

### Definición de terminado
- [ ] Un evaluador completa una reserva con pago simulado sin ayuda, también desde el móvil.
- [ ] La aplicación funciona **sin internet** (imágenes y fuentes locales; el mapa puede degradar con un aviso).
- [ ] Cero `any` en servicios y modelos; Lighthouse ≥ 90 en las páginas públicas principales.
- [ ] 4 flujos E2E en verde.

---

## 9. F4 — Anfitrión y administrador (A1, A9)

**Rama:** `feat/f4-staff-admin` · **Lee:** SPEC §4, 5, 9.3, 11.5, 14.4–14.6 y 16.5

### Prompt
```
Aplica el prompt maestro. Misión: completar los paneles de BRANCH_MANAGER (anfitrión), RECEPTIONIST y SUPER_ADMIN.

Backend + frontend:
- Recepción: dashboard del día (llegadas, salidas, en casa, pendientes de pago), tablero calendario por habitación (unitSlot),
  detalle de reserva con acciones (asignar unidad, check-in, check-out, no-show, pagos de mostrador, cancelar con política).
- Anfitrión: sus sucursales únicamente: habitaciones (suites) y tarifas, promociones (con DTOs), bloqueos de unidades,
  reportes de ocupación, ADR, RevPAR, ingresos devengados vs caja, cancelaciones; personal de su sucursal con reglas anti
  escalada (SPEC §9.3); exportación CSV; auditoría de su sucursal; moderación de reseñas; ajustes por sucursal.
- Administrador: sucursales, usuarios y asignaciones, políticas globales, reportes consolidados, auditoría global, exportaciones.
- Reportes con agregaciones de MongoDB (no cargar colecciones completas en memoria); rango máximo y paginación con tope.
- Pestaña de pagos: lista, anular (motivo), reembolsar (límites).

Tests: SPEC §19.2 y 19.3 completos, incluido un gerente con 2 sucursales y un usuario con roles distintos en sucursales distintas;
los reportes cuadran con los datos de demostración (valida un caso a mano y déjalo documentado).
```

### Definición de terminado
- [ ] El anfitrión de una sucursal no ve ni modifica nada de otra.
- [ ] El administrador ve el consolidado de las 4 sucursales.
- [ ] Ninguna pantalla de administración depende de rutas inexistentes.

---

## 10. F5 — Calidad, DevOps y documentación (M6, M8)

**Rama:** `feat/f5-calidad-docs`

### Prompt
```
Aplica el prompt maestro. Misión: dejar el proyecto entregable y reproducible.

1. README.md NUEVO y veraz (el actual describe Node/React/Express y está obsoleto): stack real, cómo arrancar con un comando,
   usuarios de demostración por rol, estructura, comandos de pruebas, ramas. Quita referencias a `run.sh`, `backend/` y `frontend/` que ya no existen.
2. docker-compose: mongo (autenticación activada, usuario de aplicación con mínimo privilegio), mailpit, backend, frontend (nginx),
   con perfiles dev/prod, volúmenes y healthchecks. Variables en .env.example.
3. GitHub Actions: backend (verify + Testcontainers + OWASP dependency-check), frontend (lint + test + build + npm audit), CodeQL;
   el pipeline falla con vulnerabilidades críticas.
4. Cobertura: JaCoCo (≥ 70 % en servicios de dominio) y reporte del frontend.
5. OpenAPI/Swagger (solo en dev) con seguridad Bearer documentada.
6. docs/ARQUITECTURA.md (C4 simple en Mermaid, ER de colecciones, secuencias de login/refresh y reserva+pago, ADRs numerados),
   docs/SEGURIDAD.md (STRIDE resumido, checklist con evidencia y mapeo a OWASP Top 10), docs/DEMO.md (guion de 10 minutos), docs/CHANGELOG.md.
7. Datos demo realistas en español (4 sucursales, fotos locales, reseñas, reservas en todos los estados) con un comando de carga y otro de reinicio.
8. Limpieza: elimina replace.py, fix_images.py, fix_components.js, fix_loaders.js, fix_script.sh, generate*.sh, HELP.md y código muerto;
   archiva o borra las ramas obsoletas (feature/admin-hollow-knight, mejoras-red-estabilidad, paso-6-wip…) tras fusionarlas o descartarlas.
9. Observabilidad básica: Actuator protegido (health/metrics), logs con traceId.
```

### Definición de terminado
- [ ] `git clone` + `docker compose up` + README = proyecto funcionando en una máquina limpia.
- [ ] CI en verde; cobertura publicada.
- [ ] Los documentos coinciden con el código (no describen cosas que no existen).

---

## 11. F6 — Extras (solo si sobra tiempo)

1. **2FA TOTP** para `SUPER_ADMIN`.
2. **Micrometer + Prometheus + Grafana** con un panel (reservas/min, errores, latencia).
3. **OWASP ZAP** (baseline) contra el entorno local, con el reporte adjunto.
4. **Pruebas de carga con k6** de disponibilidad y de reservas concurrentes.
5. **Modo oscuro/claro**, PWA básica y recordatorios de llegada por correo.
6. **Migraciones con Mongock** y **ShedLock** para tareas programadas.

---

## 12. Checklist final para la entrega

- [ ] Los 6 críticos de `AUDITORIA.md` marcados como resueltos, con el commit.
- [ ] Tests de concurrencia, aislamiento por sucursal, JWT y pagos en verde.
- [ ] Un recorrido de 10 minutos ensayado: registro y verificación → búsqueda → reserva con temporada y promoción → pago con tarjeta rechazada y luego aprobada → correo en Mailpit → check-in en recepción → intento de acceso cruzado entre sucursales rechazado → reporte del anfitrión → auditoría.
- [ ] README veraz, `docs/` completo y repositorio limpio.
- [ ] Sin secretos en el repositorio ni valores por defecto inseguros.

### Lo que más suele puntuar
Aislamiento por sucursal demostrable con tests · prevención de doble reserva con prueba de concurrencia · sesión con refresh rotativo · precios calculados solo en el servidor · pagos simulados bien abstraídos · demo fluida y sin internet · documentación con diagramas.

### Lo que más suele fallar
Secretos en el repositorio · roles validados solo en la interfaz · índices que "funcionan en mi máquina" · reservas sin control de concurrencia · fechas con zona horaria mal manejada · dinero con `double` · README que no levanta el proyecto · tests que pasan porque no verifican nada.
