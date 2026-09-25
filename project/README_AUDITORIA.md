# Palacio del Mar — Auditoría del proyecto

Revisión del 20 de septiembre de 2026 sobre el código del zip `palacio-de-mar.zip`.

**Alcance y honestidad del informe**
- Leí el backend (rutas, controladores, modelos, middleware) y analicé el frontend compilado.
- *(verificado)*: lo comprobé leyendo el código exacto y, cuando fue posible, **ejecutándolo**
  (validaciones Joi y Mongoose, controladores de auth, nginx con un proxy de prueba).
- *(según el código)*: lo deduje leyendo; depende de cómo lo ejecute el navegador o la base de datos,
  y no lo ejecuté.
- No pude ejecutar Docker ni MongoDB en mi entorno, así que **no probé los flujos completos
  contra la base de datos**. En tu equipo ya comprobaste registro, login y health.

Leyenda: 🔴 crítico · 🟠 importante · 🟡 menor · 🧩 falta · ✅ bien · 🔧 ya arreglado

---

## ⚠️ Bloqueo previo: no hay código fuente del frontend

El zip trae solo el **build compilado** (`index.html`, `index-*.js`, `index-*.css`).
Varios problemas de abajo (C2, C3, I3) se arreglan en el frontend, y sin su código fuente
no se puede corregir ni recompilar nada. **Lo primero es recuperar ese repositorio.**

---

## 🔴 Críticos

### C1. Cualquier usuario registrado puede crear, editar y borrar suites y experiencias *(verificado)*
- **Dónde:** `routes/suites.js` líneas 24-26 y `routes/experiences.js` líneas 26-28.
  Solo usan `authMiddleware`, y los controladores tampoco revisan el rol.
- **Por qué importa:** registrarse es abierto. Cualquier visitante puede borrar todo el catálogo.
- **Arreglo:** añadir `adminMiddleware` (ya existe en `middleware/auth.js`):
  ```js
  router.post('/', authMiddleware, adminMiddleware, createSuite);
  router.put('/:id', authMiddleware, adminMiddleware, updateSuite);
  router.delete('/:id', authMiddleware, adminMiddleware, deleteSuite);
  ```
  Igual en experiencias. Mismo problema, de menor gravedad, en `DELETE /api/availability/cache`
  y `POST /api/chat/clear-cache`.

### C2. La pantalla «Mis reservas» se rompe *(según el código)*
- **Qué pasa:** el backend responde `{ success, data: { bookings, pagination } }`. El frontend
  guarda `response.data` completo y luego hace `r.length` / `r.map(...)` sobre ese objeto
  → `TypeError: r.map is not a function`.
- **Arreglo:** en el frontend usar `response.data.data.bookings`.

### C3. La web no puede crear reservas *(según el código)*
- En el build no existe ninguna llamada `POST /bookings`. Al buscar disponibilidad, si hay sesión,
  el botón solo desplaza la página hasta la sección de suites (`#rooms`).
- El backend sí tiene la API completa (crear, cancelar, modificar). **Falta el flujo en la interfaz.**

### C4. Sin cobro y sin correos *(verificado)*
- `createBooking` devuelve `paymentLink: /api/payments/initiate/:id`, pero **esa ruta no existe**;
  `paymentStatus` queda siempre en `pending`.
- `sendConfirmationEmail` solo hace `console.log`; `nodemailer` ni siquiera está en las dependencias.
- «Olvidé mi contraseña» genera el token pero **no envía nada**, y además lo imprime en los logs
  en todos los entornos: `console.log('[Password Reset] Token para …')`.
  Quien tenga acceso a los logs puede tomar cuentas. **Elimina ese `console.log` ya.**

---

## 🟠 Importantes

### I1. Crear suites o experiencias por la API falla siempre *(verificado con los modelos reales)*
- `Suite`: el modelo exige `basePrice` y `slug`; `createSuite` envía `price` e `image`.
  El `slug` se genera en `pre('save')`, que corre **después** de la validación, así que no llega a tiempo.
- `Experience`: falla igual por `slug` y `location.name`.
- Solo el seed funciona porque ya usa los campos del modelo.
- **Arreglo:** alinear los nombres de campos y generar el slug en `pre('validate')`.

### I2. No existe forma de crear un administrador *(verificado)*
Ni seed, ni script, ni variable de entorno. Los endpoints `/api/auth/admin/*` y los de gestión de
reservas son inalcanzables. Solución temporal, promoviendo a un usuario ya registrado:
```bash
docker compose exec mongo sh -c 'mongosh -u "$MONGO_INITDB_ROOT_USERNAME" -p "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin palacio_del_mar --eval "db.users.updateOne({email:\"tu@correo.com\"},{\$set:{role:\"admin\"}})"'
```
Ese usuario debe **cerrar sesión y volver a entrar** (ver I5). Lo definitivo es un script
`createAdmin` con `ADMIN_EMAIL` y `ADMIN_PASSWORD`.

### I3. El nombre desaparece al recargar la página *(según el código)*
`/auth/me` responde `{ success, user }` y el frontend guarda todo el objeto, así que `user.name`
queda `undefined` («Bienvenido de vuelta, 👋»). Tras un login normal sí funciona. Arreglo en el
frontend: usar `response.data.user`.

### I4. Es posible reservar dos veces la misma suite *(según el código)*
`createBooking` comprueba disponibilidad y luego inserta, sin transacción ni bloqueo. Dos solicitudes
simultáneas pueden pasar el chequeo a la vez. Arreglo: transacciones de Mongo (requieren replica set)
o una restricción atómica por suite y noche.

### I5. Los permisos se leen del token, no de la base de datos *(verificado en el código)*
`middleware/auth.js` detecta que el rol del token no coincide con el de la base, pero **solo lo
registra** (`AUTH_FAILED_ROLE_MISMATCH`) y sigue usando `decoded.role`. Si le quitas el rol de admin
a alguien, su token sigue funcionando como admin hasta 7 días. (La suspensión de cuenta sí se comprueba.)
Arreglo: usar `user.role` de la base, o rechazar cuando no coincidan.

### I6. Limitadores y lista negra de tokens viven en memoria *(verificado)*
Afecta al login (5 intentos por `email+IP`), al chat (10 mensajes/min por IP) y a los tokens revocados
en logout. Todo se pierde al reiniciar: un token «cerrado» vuelve a ser válido. No escala a más de una
instancia, y el límite de login no frena a quien prueba muchos correos distintos.
Arreglo: `express-rate-limit` con Redis, o una colección Mongo con índice TTL.

### I7. El chat es público, gasta dinero y falla con clave configurada *(verificado)*
- `POST /api/chat` no exige autenticación y llama a APIs de pago.
- Usa el modelo `claude-3-sonnet-20241022`, que ya no es válido. Ante ese error HTTP el código
  **relanza la excepción** en lugar de usar el modo offline (solo lo hace ante timeouts o fallos de DNS).
  Con `ANTHROPIC_API_KEY` puesta, el chat falla; sin clave funciona en modo offline.
- Arreglo: cambiar a un modelo vigente (p. ej. `claude-sonnet-5`), usar el fallback ante cualquier
  error y fijar un tope de gasto en el proveedor.

### I8. Dos sistemas de validación que se contradicen *(verificado)*
- Las reglas de `express-validator` en `routes/auth.js` **nunca se evalúan** (no hay `validationResult`).
  Son código muerto.
- El esquema Joi del controlador rechaza campos extra: enviar `confirmPassword` o `phone`
  (que las rutas dicen validar) da `"confirmPassword" is not allowed`.
- Arreglo: quedarse con Joi, borrar la otra capa y permitir los campos opcionales.

### I9. `/api/health` y `/` exponen información interna *(verificado)*
Health público devuelve uso de memoria, host y nombre de la base de datos; `/` lista todos los endpoints.
Arreglo: público mínimo (`{status:"ok"}`) y el detalle solo para administradores o red interna.

### I10. Token de 7 días en `localStorage`, sin CSP
Un XSS puede robarlo. Arreglo: cookie `httpOnly` + `SameSite`, token más corto con refresh,
y cabeceras con `helmet` (CSP, HSTS).

### I11. La regla de contraseña es innecesariamente restrictiva
Solo admite los símbolos `@ $ ! % * ? &` y **rechaza cualquier otro carácter**. Una contraseña fuerte
con `.`, `_`, `-` o `#` se rechaza, y el formulario no lo avisa (ahora el mensaje de error sí lo dice).
Arreglo: aceptar cualquier símbolo y exigir longitud.

---

## 🟡 Menores y deuda técnica

- **Compresión:** `server.js` importa `express-compression` (no existe; el paquete instalado es
  `compression`), así que Express nunca comprime. En Docker ya lo hace nginx.
- **Apagado ordenado duplicado:** `db.js` y `server.js` registran su propio `SIGINT`/`SIGTERM`, y `db.js`
  llama `process.exit(0)` sin esperar a que el servidor HTTP cierre.
- **Avisos «Duplicate schema index»** en varios modelos: índices declarados con `index: true` y con
  `schema.index()`.
- **Sin `package-lock.json`:** las instalaciones no son reproducibles. Genera uno y usa `npm ci`.
- **Archivos de raíz obsoletos:** el script `build` de `package.json` invoca un `frontend/package.json`
  que no existe; `start.js` duplica `npm start`; `README_DEPLOY.md` describe una estructura que no
  coincide con el zip; `frontend/.htaccess` no se usa con nginx.
- **`backend/.env.production` dentro del proyecto** con un JWT de ejemplo débil. No lo uses ni lo
  subas a un repositorio.
- **Archivos con BOM y saltos de línea CRLF mezclados.** Conviene un `.editorconfig`.
- **Sin tests, linter ni CI.**
- **Cabeceras de seguridad:** `X-XSS-Protection` está obsoleta; faltan HSTS, CSP y `Referrer-Policy`.
- **Dependencias externas en el frontend:** imágenes de Unsplash y pravatar, fuentes de Google,
  teléfono y correo escritos a mano en el código.
- **SEO:** es una SPA sin prerender; el HTML inicial solo contiene `<div id="root">`. Faltan sitemap,
  Open Graph y datos estructurados (`schema.org/Hotel`), importantes para un hotel.
- **Infraestructura Docker:** sin HTTPS, sin copias de seguridad de MongoDB y con una sola instancia.

---

## 🧩 Funcionalidades que faltan

| Funcionalidad | Estado actual |
|---|---|
| Flujo de reserva completo en la web | El backend lo tiene; la interfaz no lo usa |
| Pagos (en Colombia: Wompi, PayU, Mercado Pago…) y webhooks | No existe ruta ni integración |
| Correos: confirmación, verificación de email, recuperar contraseña | Solo simulados con `console.log` |
| Pantallas de «olvidé mi contraseña» y «verificar email» | Endpoints listos; el frontend no los llama |
| Panel de administración (suites, experiencias, reservas, usuarios) | No existe; ni siquiera hay primer admin |
| Cancelar o modificar reservas desde la web | Existe en la API; sin pantalla |
| Precio por temporada y `calculate-price` | Modelo y endpoint listos; la UI no los usa |
| Reseñas de suites | El modelo las tiene; no hay ruta ni pantalla |
| Subida de imágenes | Hoy las imágenes son URLs externas |
| Aviso al hotel (correo o WhatsApp) cuando entra una reserva | No existe |

---

## ✅ Lo que está bien

- Contraseñas con **bcrypt de 12 rondas**.
- JWT con **algoritmo fijado (HS256)**, y en cada petición se comprueba que el usuario exista y que
  su cuenta no esté suspendida.
- **El precio de la reserva lo calcula el servidor**, no el cliente.
- Validación de variables de entorno al arrancar y cierre ordenado de conexiones.
- Modelos bien pensados (temporadas, mantenimiento, reseñas, capacidad).
- Chat con modo offline y sitio bilingüe (es/en).

---

## 🔧 Ya corregido en este proyecto

1. **`backend/database/db.js`:** TLS configurable con `MONGODB_TLS` (antes forzado en producción, lo
   que impedía usar MongoDB local en Docker).
2. **`backend/controllers/authController.js`:** registro y login devuelven el motivo real del error
   en `message` (antes «Error de validación» sin detalle); el login ahora está en español.
3. **`backend/server.js` + `docker-compose.yml`:** nuevo `TRUST_PROXY=1`. Sin él, detrás de nginx
   todos los visitantes aparecían con la misma IP, y el limitador de login (por `email+IP`) permitía
   **bloquear la cuenta de otra persona** con 5 intentos fallidos, mientras que el del chat
   (10 mensajes/min por IP) habría dejado sin chat a todos a la vez.
   Verificado con nginx real: cada visitante conserva su IP y falsificar `X-Forwarded-For` no funciona.

---

## 📋 Orden de trabajo sugerido

1. **Ya:** quitar el `console.log` del token de reset (C4) y cerrar el acceso a suites/experiencias
   con `adminMiddleware` (C1). Son cambios de pocas líneas.
2. Recuperar el **código fuente del frontend**.
3. Arreglar los formatos de respuesta (C2, I3) y construir el **flujo de reserva** (C3).
4. Integrar **correos y pagos** (C4).
5. Crear el primer **administrador** y un panel; alinear `createSuite`/`createExperience` (I1, I2).
6. Corregir la lectura de roles desde la base (I5) y mover los limitadores a un almacén persistente (I6).
7. Endurecer: `helmet`, cookies `httpOnly`, HTTPS, copias de seguridad, tests y CI.
