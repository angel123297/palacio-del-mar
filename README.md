# Palacio del Mar

Reservas de un hotel boutique con **4 sucursales en Cartagena** (Centro Histórico,
Getsemaní, Bocagrande y La Boquilla). Es un monolito para una sola empresa: un
anfitrión, uno o más administradores y varios clientes.

**Stack:** React + Vite (frontend, servido por nginx) · Node 20 + Express (API) ·
MongoDB 7 · todo corre con Docker Compose. Solo local: sin nube ni archivo `.env`.

> **Estado:** en desarrollo. El trabajo del **usuario (huésped)** está en la rama
> `usuario`; el del administrador, en `administrador`. `main` es lo estable.
> Los **pagos son una simulación** (siempre aprueban).

---

## 1. Arrancar

Necesitas Docker. Desde la carpeta del proyecto:

```bash
docker compose up -d --build        # o:  bash run.sh   (además guarda logs)
docker compose exec backend npm run seed     # SOLO la primera vez: carga las sucursales y habitaciones
```

Abre **http://localhost:8080**.

> `AUTO_SEED` está en `false` en el compose: sin el `seed` el catálogo sale vacío.
> El `seed` **borra** sucursales, suites, experiencias, noches ocupadas y promociones
> y las vuelve a crear (no borra reservas ni usuarios).

| Qué | Dónde |
|---|---|
| Sitio | http://localhost:8080 |
| Panel de administración | http://localhost:8080/admin |
| Cuenta de administrador | definida en `docker-compose.yml` (variables `ADMIN_*` y `DEV_ADMIN_*`); también `docker compose exec backend npm run create-admin` |
| MongoDB (Compass) | `mongodb://localhost:27017/palacio_db` (sin usuario; solo desde tu PC) |

> Las credenciales de desarrollo y la clave JWT viven solo en `docker-compose.yml` y son
> **solo para uso local**: no se repiten en este README ni se usan en un servidor real.
> `DEV_ADMIN_*` se ignora con `NODE_ENV=production`.

Los correos no se envían: en local se imprimen en `docker compose logs backend`.
Para enviarlos de verdad define `SMTP_HOST`, `SMTP_USER` y `SMTP_PASS` en el bloque `backend`.

## 2. Qué puede hacer el huésped

1. Entra sin cuenta, elige fechas y huéspedes y ve las **4 sucursales** con "desde $X",
   cupo real y promociones. Si sus fechas no tienen cupo, el sitio sugiere las mismas
   noches en fechas cercanas y otras sucursales. El calendario del buscador muestra
   los días agotados, la temporada y los descuentos de la sucursal elegida.
2. Abre el **detalle de la habitación** (`/habitaciones/:id`): galería, mapa de la
   sucursal (MapLibre + OpenStreetMap, necesita internet), qué incluye, precio exacto por
   noche según las fechas y adultos/niños. Elige habitación, agrega experiencias y pulsa confirmar. Solo en ese momento se le
   pide registrarse o iniciar sesión (su selección se conserva).
3. La reserva queda **pendiente** y la habitación se **retiene 30 minutos**.
4. Paga en `/pagar/:reserva` (tarjeta, PSE o Nequi — *simulado*). Al aprobarse pasa a
   **confirmada**, se emite el comprobante y llega el correo.
5. Si no paga a tiempo, la reserva **vence** y la habitación se libera sola.
6. Desde "Mis reservas" puede ver el detalle, agregar o quitar experiencias, cambiar
   fechas y cancelar (política visible en el pago).

## 3. Reglas de negocio

**Precio** (una sola lógica: `backend/utils/pricing.js` + `utils/seasons.js`)
- Cada **noche** se cobra con el recargo de su temporada: baja ×1.0, media ×1.15,
  alta ×1.3, pico ×1.5. Navidad/Año Nuevo (21/12–10/01) y Semana Santa (calculada
  cada año a partir de Pascua) son temporada pico.
- Una **promoción** de la sucursal descuenta su % de las noches que cubre.
- **Estadía larga:** 5 % desde 5 noches y 10 % desde 7, sobre alojamiento + experiencias.
- Promoción y estadía larga **no se acumulan**: se aplica la que más ahorra.
- El desglose por noche se guarda en la reserva: lo prometido no cambia aunque después
  cambien promociones o temporadas.

**Disponibilidad** (sin doble reserva)
- Cada tipo de habitación tiene N habitaciones físicas. Cada noche ocupada es un
  documento con clave única `(suite, habitación, fecha)`; reservar es insertar esas
  noches y, si otra solicitud las tiene, MongoDB rechaza la segunda. Funciona sin
  réplica ni transacciones.

**Retención y límites**
- Reserva pendiente: 30 min (`HOLD_MINUTES`); máximo 3 pendientes por usuario
  (`MAX_PENDING_BOOKINGS`); repetir la misma reserva devuelve la existente; 10
  intentos de reservar cada 10 min por usuario.
- Nunca vence una reserva con dinero recibido.

**Cancelación:** reembolso total si faltan 7 días o más; si faltan menos, se retiene el 10 %.

**Pagos (simulados)**
- `PAYMENTS_MODE=simulated`: siempre aprueba y **no se permite con `NODE_ENV=production`**
  (el backend no arranca). Los datos de tarjeta **nunca** se piden ni se envían.
- Cada intento lleva clave de idempotencia (un doble clic no cobra dos veces); además
  solo puede haber **un pago activo por reserva** (índice único parcial), así que dos
  pestañas con claves distintas tampoco cobran dos veces.
- La reserva se confirma con una actualización atómica solo si sigue pendiente, vigente
  y con el **mismo total** que se cobró; si el total cambió mientras se pagaba, no se
  confirma y se avisa.
- Al cancelar una reserva pagada el reembolso simulado se completa al instante y el
  pago queda `refunded`.
- Para una pasarela real se agrega un proveedor con la misma forma `{ name, charge }`
  en `backend/services/payments/`.

## 4. Si algo falla: logs y reporte

```bash
bash run.sh            # levanta todo; guarda el build y genera logs/reporte.txt
bash run.sh reporte    # genera logs/reporte.txt tras reproducir un error
bash run.sh seguir     # guarda los logs en vivo (Ctrl+C para parar)
bash run.sh parar      # apaga los contenedores (conserva los datos)
```

`logs/reporte.txt` oculta tokens, JWT y contraseñas; compártelo para pedir ayuda. Los
errores que salen solo en el navegador (consola F12) no se incluyen. En Windows usa
Git Bash o WSL. `logs/` no se sube a Git.

## 5. Pruebas

Todo dentro de Docker (el segundo comando incluye las pruebas contra MongoDB real:
concurrencia, vencimiento y pagos):

```bash
docker compose exec -e TEST_MONGODB_URI=mongodb://mongo:27017/palacio_test backend npm test
cd frontend && npm ci && npm test        # pruebas del frontend
```

Sin `TEST_MONGODB_URI` las pruebas que necesitan base de datos se saltan.

## 6. Comandos útiles

```bash
docker compose logs -f backend                       # ver la API y los correos
docker compose down                                  # apagar (conserva los datos)
docker compose down -v                               # apagar y BORRAR la base de datos
docker compose exec backend npm run create-admin     # crear o promover otro administrador
```

## 7. Estructura

```
docker-compose.yml          mongo + backend + web (toda la configuración local)
run.sh                      arranque con logs y reporte de errores
docs/PARA-ANFITRION-Y-ADMIN.md   lo que el lado del huésped necesita del anfitrión y del admin
backend/
  models/        Branch, Suite, SuiteNight, Booking, Payment, Promotion, Experience, User
  controllers/ routes/ middleware/
  services/      pricingService, bookingExpiry, paymentService, payments/ (proveedor simulado)
  utils/         pricing, seasons, dates, validators, bookingRules
  migrations/    sucursales y retención (idempotentes, corren al arrancar)
  seed/ scripts/ tests/
frontend/src/    pages/ components/ (CheckoutPage, HoldNotice, admin/...) utils/ styles/
```

## 8. Ramas y cómo trabajar

- `main`: estable. `usuario`: huésped (pasos 1 a 5 hechos; el 6 está en curso en `paso-6-wip`). `administrador`: panel de admin.
- Antes de empezar: `git fetch && git pull` de tu rama; otros suben cambios a diario.
- Antes de subir: tests del backend y `npx vite build` en el frontend, sin errores ni advertencias.

## 9. Lo que falta (resumen; el detalle está en el feedback del equipo)

- **Huésped:** coherencia por sucursal (textos "18 suites", banner de promociones con
  datos reales), filtros de habitaciones, reservar **varias habitaciones** en una sola
  reserva, reseñas, favoritos, comprobante en PDF y `.ics`,
  verificación de email obligatoria, términos y datos personales (Ley 1581).
- **Pagos:** pagar la diferencia al cambiar fechas o agregar experiencias a una reserva
  ya pagada.
- **Anfitrión y admin:** rol de anfitrión, métricas de ocupación reales, pestaña de pagos.
- **Seguridad (rama aparte):** secretos fuera del repositorio, CORS y modo producción,
  límite general de peticiones, chat de IA público, autenticación de MongoDB, volumen para imágenes.
