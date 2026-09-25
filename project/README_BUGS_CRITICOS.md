# Palacio del Mar — README de bugs críticos y fallos (revisión 2)

**Revisión:** 24 de septiembre de 2026
**Base:** el código **ya corregido** en la sesión anterior (doble reserva, reembolsos, transiciones de estado,
fechas, correos/tokens, secretos por defecto). Esta pasada añade una revisión completa del resto del proyecto
(`middleware/`, `models/User.js`, `controllers/chatController.js`, `suiteController.js`,
`experienceController.js`, rutas, Docker) y la contrasta contra el enunciado de la actividad (imagen adjunta):
> *"Realizar despliegue del proyecto de aula en su totalidad con Docker, de tal manera que se pueda visualizar
> ya en funcionamiento. El proyecto solo debe necesitar del comando: `docker compose up`. SOLO SE ACEPTA LA
> ENTREGA POR GITHUB."*

**Alcance:** revisión estática. No se ejecutó `docker compose up` de verdad (sin Docker/red en este entorno),
así que los hallazgos de despliegue son deducidos leyendo `docker-compose.yml`, los `Dockerfile` y `server.js`,
no reproducidos. El resto sí se comprobó leyendo el flujo de código completo.

**Importante:** el `README_BUGS_CRITICOS.md` que subiste (el de la auditoría anterior) describe una versión
**previa**: sus BUG-001 a CONS-015 y OPS-012 ya están corregidos en este código. No los repito aquí salvo que
sigan abiertos.

---

## 🚨 P0 — Incumplen el enunciado (bloquean la entrega tal como está pedida)

### DEPLOY-001 — `docker compose up` no arranca sin un paso manual previo
- **Archivo:** `docker-compose.yml` (variables `MONGO_PASSWORD`, `JWT_SECRET`, `ADMIN_EMAIL`, `ADMIN_PASSWORD` con
  sintaxis `${VAR:?Falta ...}`).
- **Problema:** en la corrección anterior, a propósito, se le quitaron los valores por defecto a los secretos
  (`MONGO_PASSWORD=1234`, `JWT_SECRET=cambia_...`, `ADMIN_PASSWORD=Admin1234`) para que nadie despliegue el
  hotel en producción con credenciales de ejemplo. Eso es correcto para un despliegue real, pero **tu profesor
  va a clonar el repo y ejecutar únicamente `docker compose up`**, sin crear un `.env` antes. Con las variables
  actuales, Docker Compose corta la ejecución de inmediato:
  `error: MONGO_PASSWORD variable is not set. Falta MONGO_PASSWORD en el archivo .env`.
- **Impacto:** el proyecto no arranca con el único comando que el enunciado permite → 0 en el criterio
  "solo necesita `docker compose up`".
- **Ya no puedes resolverlo con "documentar `cp .env.example .env`"**: el enunciado no permite pasos extra.
- **Corrección recomendada (elige una):**
  1. **Recomendada para esta entrega académica:** commitea al repo un `.env` real (sí, versionado) con
     credenciales **generadas para este trabajo y sin usar en ningún otro lado** (no reutilices tu `.env` de
     verdad). Docker Compose lo lee automáticamente porque está en la misma carpeta que `docker-compose.yml`, y
     `docker compose up` funciona solo. Deja un comentario en el propio `.env` aclarando que es solo para la
     evaluación del curso.
  2. Vuelve a poner valores por defecto seguros pero no productivos (`:-valor`) en `docker-compose.yml`, como
     tenía el proyecto originalmente, y acepta que eso es aceptable *solo* porque es un entorno de aula, nunca
     un hotel real.
  - Ambas opciones reintroducen el riesgo que SEC-002 (auditoría anterior) señalaba — es un trade-off consciente
    entre "cumplir el enunciado" y "buena práctica de secretos"; no hay forma de tener ambas cosas con un único
    comando y sin secretos en el repo. Dímelo y lo implemento.

### DEPLOY-002 — Después de `docker compose up` el sitio se ve vacío (sin suites, sin experiencias, sin admin)
- **Archivos:** `docker-compose.yml` (servicios `seed` y `create-admin` usan `profiles`, por lo que **no**
  arrancan con `up`); `backend/seed/seedData.js`; `backend/scripts/createAdmin.js`.
- **Problema:** el catálogo de suites/experiencias y el usuario administrador solo se crean con
  `docker compose --profile seed run --rm seed` y `docker compose --profile create-admin run --rm create-admin`,
  comandos que el enunciado no contempla. Con solo `docker compose up`, el frontend carga pero la home, el buscador
  y el panel de admin se ven vacíos, y no hay ninguna cuenta con la que entrar a `/admin`.
- **Impacto:** incumple directamente "de tal manera que se pueda visualizar ya en funcionamiento".
- **Corrección recomendada:** en el arranque del backend (`server.js`, después de `connectDB()`), si
  `Suite.countDocuments() === 0` ejecutar el seed automáticamente, y si no existe ningún usuario con
  `role: 'admin'` crear uno a partir de `ADMIN_EMAIL`/`ADMIN_PASSWORD` (ya definidas como variables del backend
  en el compose). Así un solo `docker compose up` deja el sitio completo y navegable. Los comandos manuales
  (`--profile seed`, `--profile create-admin`) pueden quedar como alternativa para reprocesar datos más
  adelante, no como requisito inicial.

### DEPLOY-003 — `docker compose up` sin `-d --build` puede no reconstruir imágenes tras editar código
- **Archivo:** `docker-compose.yml`, `README.md`.
- **Problema:** el enunciado dice literalmente "el comando `docker compose up`" (sin `--build`). Si ya existen
  imágenes locales `palacio-del-mar-backend`/`web` de una build anterior (por ejemplo, si el profesor ya probó
  el repo antes o reusa una máquina), Compose no las reconstruye solo con `up` y puede levantar código viejo.
- **Impacto:** riesgo bajo pero real en un entorno de corrección compartido; el enunciado no deja margen para
  pedir `--build`.
- **Corrección:** agregar `pull_policy: build` / `build: { ... }` no evita esto por sí solo; lo más simple es
  documentar en el propio `docker-compose.yml` (comentario) que la primera vez se recomienda que el evaluador
  use una máquina limpia, o añadir `x-bake` no es necesario — basta con dejar claro que el repo se probó con
  `docker compose up --build` y sugerirlo entre paréntesis en el README, ya que la mayoría de correctores lo
  hacen por costumbre. No es bloqueante como DEPLOY-001/002, pero puedes evitarlo con un
  `docker-compose.override.yml` que no es necesario tocar ahora mismo.

---

## 🔴 P1 — Seguridad y bugs críticos (nuevos, no estaban en la auditoría anterior)

### SEC-016 — El historial de contraseñas guarda la contraseña **en texto plano**
- **Archivo:** `backend/models/User.js`, hook `pre('save')` que hashea la contraseña (línea ~360).
- **Problema:** al cambiar la contraseña (no en el primer registro), el hook empuja al array
  `passwordHistory` el valor de `this.password` **antes** de reemplazarlo por el hash — es decir, guarda la
  contraseña nueva sin encriptar:
  ```js
  const hashedPassword = await bcrypt.hash(this.password, salt);
  if (this.passwordHistory && this.passwordHistory.length > 0) {
    this.passwordHistory.push({ password: this.password, ... }); // ❌ this.password sigue en texto plano aquí
  }
  ...
  this.password = hashedPassword; // el hash se asigna DESPUÉS
  ```
  Solo la primera vez (cuando `passwordHistory` está vacío) se guarda `hashedPassword` correctamente.
- **Impacto:** cualquiera con acceso de lectura a la base de datos (un backup, un dump, una fuga) obtiene la
  contraseña real y vigente de cualquier usuario que haya cambiado su clave alguna vez — incluidos
  administradores. Es el hallazgo más grave de esta revisión.
- **Corrección:** guardar siempre `hashedPassword` en `passwordHistory`, nunca `this.password`:
  ```js
  this.passwordHistory.push({ password: hashedPassword, changedAt: new Date(), changedBy: this._id });
  ```
  y purgar cuanto antes cualquier `passwordHistory` ya contaminado en datos existentes (o truncarlo).

### SEC-017 — Restablecer la contraseña no invalida los tokens JWT activos
- **Archivos:** `backend/controllers/authController.js` (`resetPassword`, llama a `user.invalidateAllSessions()`);
  `backend/models/User.js` (`invalidateAllSessions`); `backend/middleware/auth.js` (`authMiddleware`,
  `tokenBlacklist`).
- **Problema:** `invalidateAllSessions()` vacía el array `activeSessions` del documento del usuario en MongoDB,
  pero **nada en `authMiddleware` consulta jamás ese array**. La única lista que `authMiddleware` revisa es
  `tokenBlacklist`, un `Map` en memoria que solo se llena en `/api/auth/logout`. Resultado: si alguien roba el
  JWT de un usuario (ver SEC-010 más abajo) y la víctima "recupera" su cuenta cambiando la contraseña, el token
  robado **sigue siendo válido** hasta que expira solo (hasta 7 días, `JWT_EXPIRES_IN`), pese a que el código da
  la impresión de invalidar todas las sesiones.
- **Impacto:** el flujo de recuperación de cuenta no cierra el hueco que se supone que cierra.
- **Corrección:** o bien el middleware consulta `activeSessions`/una lista de tokens revocados por usuario (más
  trabajo, requiere guardar el JWT o su `jti` en la sesión), o más simple: guardar `passwordChangedAt` en el
  usuario y, en `authMiddleware`, rechazar cualquier token cuyo `iat` sea anterior a `passwordChangedAt`.

### SEC-018 — `/api/availability/stats` expone ingresos y ocupación sin autenticación
- **Archivos:** `backend/routes/availability.js` (sin `authMiddleware`); `backend/controllers/availabilityController.js`
  (`getAvailabilityStats`).
- **Problema:** el endpoint es público y devuelve `occupancyByMonth` con `revenue` (ingresos reales agregados
  por mes vía `$sum: '$totalPrice'`) y la tasa de ocupación general. Cualquier visitante, sin loguearse, puede
  pedir `GET /api/availability/stats?startDate=...&endDate=...` y obtener las cifras de negocio del hotel.
- **Impacto:** fuga de información de negocio (ingresos, ocupación) a competidores o a cualquiera.
- **Corrección:** añadir `authMiddleware, adminMiddleware` a esa ruta, igual que ya tienen las estadísticas
  equivalentes en `bookingController.js`.

### SEC-019 — El chat con IA acepta un `history` sin validar (inyección de prompt / abuso de costo)
- **Archivo:** `backend/controllers/chatController.js` (`chatWithAI`, `callAIAPI`).
- **Problema:** `chatWithAI` valida `message` (máx. 500 caracteres) pero **no valida el contenido de `history`**,
  que llega tal cual en `req.body` y se manda directo a la API de Claude/OpenAI:
  ```js
  let validHistory = Array.isArray(history) ? history : [];
  validHistory = validHistory.slice(-MAX_HISTORY_LENGTH); // solo limita CANTIDAD, no forma ni tamaño
  ...
  messages: [...history.slice(-MAX_HISTORY_LENGTH), { role: 'user', content: message }]
  ```
  Un cliente puede enviar hasta 10 elementos de `history` con `role: 'system'` (para intentar sobrescribir las
  instrucciones de "Sofía") y con `content` de varios megabytes cada uno (el límite de `express.json` es 10 MB).
  Como el `ANTHROPIC_API_KEY`/`OPENAI_API_KEY` son opcionales pero, si se configuran, cada request se factura,
  esto es además una vía de abuso de costo (mandar historiales enormes repetidamente).
- **Impacto:** inyección de prompt (bajo, porque el system prompt real se sigue pasando aparte en `system:`)
  y abuso de costo/cuota de la API de IA si se configuran claves reales.
- **Corrección:** filtrar `history` a objetos `{ role: 'user' | 'assistant', content: string }`, aplicar el mismo
  límite `MAX_MESSAGE_LENGTH` a cada `content` del historial, y descartar cualquier otro campo.

### SEC-020 — Búsqueda de suites/experiencias vulnerable a ReDoS (regex sin escapar)
- **Archivos:** `backend/controllers/suiteController.js` y `experienceController.js` (`sanitizeString`,
  uso en `$regex: filters.search`).
- **Problema:** `sanitizeString` solo quita `< > { } [ ] $ ? ;`, pero dej intactos los metacaracteres de regex
  `( ) + * . |`. El valor de `search` se usa directo como patrón de `$regex` de MongoDB. Un valor como
  `?search=(a+)+b` (parcialmente filtrado, pero `(a+)+` sobrevive) puede provocar backtracking catastrófico en
  el motor de regex durante la búsqueda, colgando esa consulta.
- **Impacto:** denegación de servicio localizada (una query lenta bloqueando el hilo/consulta) vía un endpoint
  público sin autenticación.
- **Corrección:** escapar el string con una función tipo
  `search.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')` antes de usarlo en `$regex`, en vez de solo quitar unos
  pocos caracteres.

---

## 🟡 P1 — Seguidos abiertos de la auditoría anterior (no resueltos aún, siguen vigentes)

Estos ya estaban documentados como pendientes en el README que subiste; los confirmo tras leer el código
corregido — nada de la sesión anterior los tocó:

- **SEC-010 — JWT en `localStorage` del frontend.** El token vive en `localStorage` (revisar
  `frontend/src/context/AuthContext.jsx` / `src/api/client.js`), expuesto a robo por XSS. Migrar a cookie
  `HttpOnly` + `SameSite` requiere CSRF token y tocar CORS/`credentials`; es un cambio de arquitectura, no un
  parche pequeño.
- **SEC-011 — Blacklist de tokens y rate limiting en memoria (`Map`).** `middleware/auth.js` y
  `chatController.js` guardan blacklist/rate-limit en `Map()` del proceso Node. Con más de una réplica del
  backend (o al reiniciar el contenedor) cada instancia tiene su propio estado: un token "cerrado" en una
  réplica sigue válido en otra, y el rate limit se resetea en cada redeploy. Migrar a Redis (`docker-compose.yml`
  no tiene ese servicio hoy).
- **OPS-013 — `/api/chat` sin límite duro de costo.** El rate limit (10 msj/min por IP) es fácil de eludir
  rotando IP/cliente, y no hay un tope de gasto diario/mensual si `ANTHROPIC_API_KEY`/`OPENAI_API_KEY` son
  reales. Bajo impacto mientras el proyecto se evalúe sin esas claves configuradas (usa el modo *offline* con
  respuestas predefinidas), pero crítico si se despliega con claves reales.

---

## 🟢 P2 — Menor prioridad / a vigilar

- **`resetPassword` responde 500 en vez de 400 con una contraseña débil.** (`authController.js`) No valida
  `newPassword` con Joi antes de guardar como sí hace `changePassword`; el validador del modelo Mongoose sí la
  rechaza, pero el `catch` genérico del controlador devuelve "Error al restablecer la contraseña" (500) en vez
  de un mensaje claro de validación (400). Cosmético, pero mala experiencia de usuario.
- **CORS en producción incluye `http://localhost:3000` fijo** (`server.js`, `corsOptions.origin`), residuo de
  desarrollo que no debería estar en la lista de orígenes permitidos en producción.

---

## Resumen para la entrega

| # | ID | Severidad | ¿Bloquea el enunciado del profesor? |
|---|----|-----------|--------------------------------------|
| 1 | DEPLOY-001 | Crítico | **Sí** — `docker compose up` no arranca |
| 2 | DEPLOY-002 | Crítico | **Sí** — sitio vacío, sin admin |
| 3 | DEPLOY-003 | Bajo | Riesgo si no se reconstruyen imágenes |
| 4 | SEC-016 | Crítico | No, pero es el bug de seguridad más grave del proyecto |
| 5 | SEC-017 | Alto | No |
| 6 | SEC-018 | Alto | No |
| 7 | SEC-019 | Medio | No |
| 8 | SEC-020 | Medio | No |
| 9 | SEC-010 / SEC-011 / OPS-013 | Medio (abiertos) | No |
| 10 | P2 (2 hallazgos) | Bajo | No |

**Antes de subir a GitHub para la entrega, lo mínimo indispensable es resolver DEPLOY-001 y DEPLOY-002** — sin
eso, `docker compose up` no cumple lo que pide la actividad. Puedo implementar ambos (y SEC-016/017/018, que son
cambios acotados) ahora mismo si me confirmas cuál opción prefieres para DEPLOY-001 (commitear un `.env` de
curso, o volver a poner valores por defecto en `docker-compose.yml`).
