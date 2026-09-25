# Palacio del Mar 🌊

Hotel boutique de lujo en el centro histórico de Cartagena de Indias. Sitio web completo:
landing pública, buscador de disponibilidad, flujo de reserva real, cuenta de usuario con
"Mis reservas", panel de administración y un concierge virtual con IA.

Stack: **React + Vite** (frontend) · **Node.js + Express + MongoDB** (backend) · **Docker Compose**.

---

## 🚀 Arranque rápido (Docker)

Es el modo recomendado: no necesitas instalar Node ni MongoDB en tu máquina.

```bash
docker compose up -d --build
```

Con **un solo comando** ya queda todo funcionando en **http://localhost:8080**: el `.env` de la
raíz viene versionado con credenciales generadas solo para esta entrega (no son secretas, no las
reutilices en otro proyecto), y el backend, al arrancar, **carga automáticamente** el catálogo de
5 suites y 4 experiencias de ejemplo si la base de datos está vacía, y **crea el usuario
administrador** si todavía no existe ninguno (ver `backend/bootstrap.js`). No hace falta copiar
ni rellenar nada a mano, ni correr ningún comando aparte.

Con eso ya puedes:
- Navegar el sitio, buscar disponibilidad y hacer una reserva real (`http://localhost:8080`)
- Iniciar sesión con el `ADMIN_EMAIL` / `ADMIN_PASSWORD` del `.env` (por defecto
  `admin@palaciomar.co` / `AulaDocker2026Segura`) y entrar a `/admin`

Para un despliegue real (no esta entrega de aula): borra el `.env` versionado, copia
`.env.example` como `.env` y genera tus propias credenciales únicas (`openssl rand -hex 32`).
En un servidor con dominio público (`FRONTEND_URL` distinto de localhost) el backend **no
arranca** si detecta que `JWT_SECRET` o la contraseña de MongoDB siguen siendo las de ejemplo.

Si alguna vez quieres recargar el catálogo de ejemplo desde cero, o crear un administrador
adicional, esos comandos manuales se conservan y NO corren solos con `up`:

```bash
# ⚠️ Borra y vuelve a crear las 5 suites y 4 experiencias de ejemplo
docker compose --profile seed run --rm seed

# Crea (o promueve) un administrador adicional
docker compose --profile create-admin run --rm create-admin
```

### Protección contra doble reserva (una sola vez al actualizar)

Las reservas antiguas no tienen bloqueos de noches. Tras desplegar esta versión ejecuta:

```bash
docker compose exec backend npm run backfill-nights            # simulación
docker compose exec backend npm run backfill-nights -- --apply # aplica
```

### Comandos del día a día

```bash
docker compose ps                    # estado y salud de cada servicio
docker compose logs -f backend       # logs del backend (también: web, mongo)
docker compose restart backend       # reiniciar un servicio
docker compose up -d --build         # aplicar cambios de código (reconstruye)
docker compose down                  # apagar (los datos se conservan)
docker compose down -v               # apagar Y BORRAR la base de datos
```

---

## 🧭 Qué incluye el sitio

- **Inicio**: hero, buscador de disponibilidad (fechas + huéspedes contra la API real),
  suites, experiencias, gastronomía, spa, ubicación con mapa y concierge virtual con IA.
- **Reserva real**: elegir suite → agregar experiencias → datos de contacto → confirmación
  con desglose de precio (temporada + descuentos por estadía larga) e instrucciones de pago
  por WhatsApp (no hay pasarela de pago conectada; el pago se confirma manualmente).
- **Cuenta de usuario** (`/dashboard`, "Mis reservas"): ver, filtrar, modificar fechas y
  cancelar reservas.
- **Recuperar contraseña** end-to-end (pedir enlace, restablecer, verificar email).
- **Panel de administración** (`/admin`, solo rol `admin`): CRUD de suites y experiencias,
  listado de reservas con cambio de estado de pago, gestión de usuarios.
- **Concierge con IA** ("Sofía"): funciona sin configurar nada (modo con respuestas
  predefinidas); si defines `ANTHROPIC_API_KEY` u `OPENAI_API_KEY` en `.env`, responde con IA
  de verdad.

---

## 🛠️ Qué se corrigió / se agregó

Este proyecto llegó como un build de frontend ya compilado (sin código fuente) y un backend
con varios problemas que impedían que funcionara de punta a punta. Se hicieron dos cosas:

1. **Se reconstruyó el frontend desde cero** como proyecto React + Vite real (antes solo
   existía el bundle minificado, sin código fuente para editar), conectando de verdad todo lo
   que en el sitio original no tenía funcionalidad: el buscador de disponibilidad, el botón
   "Agregar" de experiencias, el flujo de reserva completo (no existía), "Mis reservas", y se
   sumó lo que faltaba por completo: recuperar contraseña y panel de administración.

2. **Se corrigieron bugs reales del backend**, entre ellos varios que impedían que la app
   funcionara en absoluto:
   - Toda reserva calculaba el precio mal (`suite.price`, un campo que no existe en el
     modelo — es `basePrice`) y terminaba dando `NaN`, por lo que **crear una reserva siempre
     fallaba**.
   - Varias rutas usaban `mongoose.model('X')` sin importar `mongoose`, lo que las hacía
     fallar con un error de servidor.
   - Todo usuario nuevo quedaba con estado `pending_verification` sin ninguna forma real de
     verificarse (no había envío de emails), así que el middleware de autenticación lo
     bloqueaba en la primera petición después de registrarse. **Nadie podía usar la cuenta
     que acababa de crear.**
   - Crear una suite o experiencia desde el backend **siempre fallaba**: el slug (obligatorio)
     se generaba en un punto del ciclo de vida de Mongoose que corre después de la validación.
   - Cualquier usuario registrado (el registro es abierto) podía crear, editar o borrar
     cualquier suite o experiencia del catálogo: la ruta solo exigía estar logueado, no ser
     administrador.
   - El limitador de intentos de login estaba conectado pero nunca se usaba: nunca bloqueaba
     a nadie.
   - Ningún archivo de rutas llamaba a `validationResult()`, así que ninguna regla de
     `express-validator` de todo el proyecto se aplicaba de verdad.
   - El endpoint `/auth/me` se leía mal en el frontend: el nombre, email y demás datos del
     perfil desaparecían en cuanto se recargaba la página.
   - "Mis reservas" leía la respuesta del backend con una forma equivocada: la pantalla
     quedaba en blanco con un error de JavaScript apenas cargaba.
   - El token de recuperación de contraseña se escribía en los logs de producción.
   - Faltaba cualquier forma de crear un usuario administrador.

La lista completa y detallada del primer diagnóstico está en
[`README_AUDITORIA.md`](./README_AUDITORIA.md). El trabajo de esta vuelta corrigió todo lo
listado ahí, más varios bugs adicionales que aparecieron al revisar el código a fondo (arriba
solo se resumen los más importantes).

---

## ⚙️ Variables de entorno

Todas viven en el `.env` de la raíz (usado por Docker Compose). Copia `.env.example` y rellena
los obligatorios (`HOTEL_TIMEZONE` define qué es "hoy" y los límites de cada noche):

| Variable | Para qué | Obligatoria |
|---|---|---|
| `MONGO_USER`, `MONGO_PASSWORD`, `MONGO_DB` | Credenciales de MongoDB | Sí |
| `JWT_SECRET`, `JWT_EXPIRES_IN` | Firma de los tokens de sesión | Sí |
| `WEB_PORT` | Puerto en tu máquina para abrir el sitio | No (por defecto `8080`) |
| `FRONTEND_URL` | URL pública del sitio (CORS) | No |
| `ANTHROPIC_API_KEY` / `OPENAI_API_KEY` | Chat con IA real (Sofía) | No — sin esto, modo offline |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASS`, `SMTP_FROM` | Envío real de correos (verificación, recuperar contraseña, confirmación de reserva) | No — sin esto, simplemente no se envían |
| `HOTEL_WHATSAPP` | Número que se muestra al huésped para confirmar el pago | No |
| `ADMIN_NAME`, `ADMIN_EMAIL`, `ADMIN_PASSWORD` | El backend crea esta cuenta como admin al arrancar si aún no existe ninguna (también las usa `create-admin`) | Sí |

---

## 💻 Desarrollo local sin Docker

Necesitas Node 20+ y una instancia de MongoDB corriendo en `localhost:27017`.

**Backend:**
```bash
cd backend
cp .env.example .env      # y edita JWT_SECRET al menos
npm install
npm run dev                # nodemon, puerto 5000
npm run seed                # opcional: carga datos de ejemplo
npm run create-admin        # opcional: crea el administrador (lee ADMIN_* del .env)
```

**Frontend** (en otra terminal):
```bash
cd frontend
npm install
npm run dev                 # http://localhost:5173, con proxy de /api al backend
```

---

## 📂 Estructura del proyecto

```
palacio-del-mar/
├── docker-compose.yml
├── .env / .env.example
├── backend/
│   ├── server.js
│   ├── controllers/        (auth, suites, experiences, bookings, availability, chat)
│   ├── models/              (User, Suite, Experience, Booking)
│   ├── routes/
│   ├── middleware/          (auth, validate, errorHandler)
│   ├── utils/email.js       (envío de correos, opcional)
│   ├── scripts/createAdmin.js
│   └── seed/seedData.js
└── frontend/
    ├── src/
    │   ├── components/      (Navbar, BookingBar, RoomsSection, BookingModal, ...)
    │   ├── pages/            (HomePage, DashboardPage, AdminPage, ...)
    │   ├── context/          (Auth, BookingCart, Toast)
    │   ├── api/client.js
    │   └── styles/
    ├── Dockerfile            (build con Vite -> sirve con nginx)
    └── nginx.conf
```

---

## 🌐 Desplegar en un servidor propio (VPS)

1. En `.env`: `WEB_PORT=80` y `FRONTEND_URL=https://tudominio.com`. Cambia `MONGO_PASSWORD`
   y `JWT_SECRET` por valores propios.
2. `docker compose up -d --build`
3. Para HTTPS, pon delante un proxy con certificados (Caddy, Traefik o nginx + Certbot)
   apuntando al puerto de `web`. Eso no viene incluido en este `docker-compose.yml`.
4. El catálogo de ejemplo y el usuario administrador se crean solos al primer arranque (ver
   arriba). Los comandos `--profile seed` / `--profile create-admin` solo hacen falta si más
   adelante quieres recargar el catálogo o crear un admin adicional.

MongoDB no publica ningún puerto: solo lo ven los otros contenedores. Para conectarte con
MongoDB Compass desde tu PC, descomenta el bloque `ports` del servicio `mongo` en
`docker-compose.yml` (queda limitado a `127.0.0.1`).

### Usar MongoDB Atlas en vez del contenedor

En `docker-compose.yml`: pon tu URI de Atlas en `MONGODB_URI`, cambia `MONGODB_TLS` a
`"true"`, y elimina el servicio `mongo` junto con los `depends_on` que lo mencionan.

### Hosting compartido (sin Docker)

Si tu proveedor no soporta Docker (por ejemplo, un plan compartido tipo Hostinger):
- Backend: sube la carpeta `backend/`, configura Node.js desde el panel del proveedor y las
  variables de entorno de `backend/.env.example`.
- Frontend: en tu máquina corre `cd frontend && npm install && npm run build`, y sube el
  contenido de la carpeta `frontend/dist/` generada a la raíz pública del sitio. Ajusta el
  proxy de `/api` en el panel del proveedor (o edita `src/api/client.js` para apuntar a la URL
  completa de tu backend si no puedes configurar un proxy).

---

## 🩺 Solución de problemas

- **`Falta MONGO_PASSWORD en el archivo .env`**: falta el archivo `.env` en la raíz, o le
  falta esa variable. El proyecto ya trae uno listo; si lo borraste, copia `.env.example` de
  nuevo.
- **Cambiaste `MONGO_PASSWORD` y ya no conecta**: las credenciales de Mongo solo se crean una
  vez, al inicializar el volumen. Para aplicar una contraseña nueva: `docker compose down -v`
  (esto borra los datos) y vuelve a levantar.
- **El chat con IA responde siempre lo mismo**: es el modo offline (sin `ANTHROPIC_API_KEY` ni
  `OPENAI_API_KEY` configuradas). Sigue funcionando, solo que con respuestas predefinidas en
  vez de generadas.
- **No llegan los correos** (verificación, recuperar contraseña, confirmación de reserva): no
  hay `SMTP_*` configurado. El proyecto funciona igual sin esto — simplemente no se envía el
  correo. En desarrollo (`NODE_ENV=development`), el enlace de recuperación de contraseña se
  devuelve también en la respuesta de la API para poder probar el flujo sin correo real.

---

## ⚠️ Limitaciones conocidas

- **No hay pasarela de pago conectada.** Las reservas quedan en estado "pendiente de pago" y
  se confirman manualmente por WhatsApp; un administrador marca la reserva como pagada desde
  el panel. Conectar Wompi/PayU/Mercado Pago requeriría credenciales propias que este proyecto
  no tiene.
- **El limitador de intentos de login y la lista negra de tokens viven en memoria del
  proceso**, no en una base de datos: se reinician si el contenedor del backend se reinicia.
  Para producción de alto tráfico, lo ideal es moverlos a Redis o a una colección de Mongo con
  índice TTL.
- **El idioma del sitio es español únicamente.** El proyecto original traía un selector
  es/en a medio implementar (con la mayoría del contenido solo en español); se optó por un
  sitio 100% en español, consistente, en vez de un bilingüe a medias.
