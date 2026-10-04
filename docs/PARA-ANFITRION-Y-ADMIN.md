# Lo que el usuario necesita del anfitrión y del administrador

Documento de coordinación. Cada vez que un paso del lado **usuario** dependa de
algo del anfitrión o del admin, se agrega aquí. Nada de esto bloquea al usuario:
hoy funciona con datos de ejemplo.

## Paso 1 · Precios únicos (hecho en `usuario`)

Cómo se cobra ahora (una sola lógica: `backend/utils/pricing.js` + `utils/seasons.js`):
cada noche con el recargo de **su** temporada → promoción de la sucursal en esa noche
→ descuento por estadía larga (5 % desde 5 noches, 10 % desde 7). **No se acumulan**:
se aplica el que más ahorra. El desglose por noche se guarda en `booking.pricing`.

### Pendiente para el ANFITRIÓN
1. **Pantalla de promociones** (crear/editar/pausar): hoy son 4 de ejemplo (`isSample`)
   cargadas por el seed. Modelo `Promotion`: sucursal, título, % (1–60), primera y última
   noche (inclusive), activa. Debe validar que no se solapen de forma confusa (si se
   solapan, el sistema ya usa la de mayor %).
2. **Temporadas y multiplicadores** (bajo ×1.0, medio ×1.15, alto ×1.3, pico ×1.5) están
   fijos en `backend/utils/seasons.js`. Si el anfitrión debe cambiarlos, hay que moverlos
   a una colección editable; el usuario no necesita nada más (ya lee solo de ese módulo).
3. **Festivos**: son las fechas nominales del calendario, **sin** trasladar al lunes como
   hace la ley colombiana (puentes). Decidir si se corrige y quién lo mantiene cada año.
4. **Impuestos**: no se cobra ninguno (campo reservado en 0). Un contador debe confirmar
   qué corresponde antes de activarlo.

### Pendiente para el ADMIN
1. En el listado/detalle de reservas mostrar `discountType` (`promotion` | `long_stay`) y
   `discountReason`, y el desglose `pricing.nights` (precio, temporada y % por noche).
2. Los **ingresos** del panel suman reservas canceladas (`$sum: '$totalPrice'`): debe
   excluir canceladas/expiradas y usar lo realmente cobrado.
3. `AdminPage` al editar una suite puede cambiar `basePrice`: las reservas existentes
   **no** cambian (su precio quedó guardado), solo las nuevas.

## Cambios de comportamiento que el equipo debe conocer
- Antes toda la estadía usaba la temporada de la noche de entrada; ahora es noche a noche.
- Navidad/Año Nuevo (21/12–10/01) **nunca** se aplicaba por un error de rango; ahora sí.
- Semana Santa se calcula cada año (Domingo de Ramos → Domingo de Resurrección).
- Junio y diciembre "medios" (antes la regla de junio no podía cumplirse): 1–14 de junio
  y 1–14 de diciembre.

## Paso 2 · Retención y límites (hecho en `usuario`)

Una reserva `pending` guarda las noches 30 min (`HOLD_MINUTES`). Si no se paga, pasa
sola a `expired` (barrido cada 60 s), libera las noches y avisa por correo. Máximo 3
pendientes vigentes por usuario (`MAX_PENDING_BOOKINGS`); repetir la misma reserva no
crea otra; 10 intentos de reservar por usuario cada 10 min.

### Pendiente para el ADMIN
1. `AdminPage` debe conocer el estado `expired` (filtro "Vencidas" y etiqueta); hoy su
   selector de estados ni siquiera incluye `no_show` correctamente (usa `no-show`).
2. **No** debe poder "confirmar" ni marcar pagada una reserva `expired`: el sistema la
   rechaza (estado terminal). Si el huésped pagó tarde por fuera, hay que crear una
   reserva nueva y reembolsar; decidir el procedimiento.
3. Las métricas de ingresos y ocupación deben excluir `expired` y `cancelled`.

### Pendiente para el ANFITRIÓN
1. Decidir los valores finales: minutos de retención (30), máximo de pendientes (3).
   Se cambian en `docker-compose.yml`; si deben editarse desde un panel, hay que
   guardarlos en base de datos.
2. Reservas con dinero ya recibido (`paymentStatus` pagado/parcial) **nunca** vencen
   solas; si quedan sin confirmar, alguien debe resolverlas a mano.
