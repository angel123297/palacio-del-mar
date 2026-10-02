# Correcciones aplicadas (24/09/2026)

Verificado: sintaxis (`node --check`) de todos los archivos tocados y 9 pruebas unitarias
(fechas y reembolsos). **No verificado:** nada se ejecutó contra MongoDB/Docker; corre
`TEST_MONGODB_URI=... npm test` (backend) para la prueba de concurrencia antes de publicar.

| ID | Estado | Qué se hizo |
|---|---|---|
| BUG-001 | Corregido | Nuevo modelo `SuiteNight` con índice único `(suite, fecha)`. `createBooking` y `modifyBookingDates` adquieren las noches de forma atómica (todo o nada); cancelar/completar/no_show las libera. Funciona en MongoDB standalone (sin réplica). |
| SEC-002 | Parcial | `.env` fuera del paquete; ejemplos sin valores. **Tú debes rotar** toda credencial real que estuvo en el ZIP. |
| BUG-003 | Corregido | Reembolso = cobrado − ya reembolsado − penalización retenida (nunca > cobrado). Nuevos campos `amountPaid`, `amountRefunded`, `refundStatus`. El reembolso queda *pending* hasta que el admin lo registre. |
| BUG-004 | Corregido | Cancelar valida estado antes de calcular: 409 si está completada/no_show; idempotente si ya estaba cancelada. |
| BUG-005 | Corregido | Una sola constante `BLOCKING_BOOKING_STATUSES`; se eliminó `paid` de los filtros de estado de reserva. |
| BUG-006 | Corregido | Transiciones de pago validadas (409), idempotencia, importes, `transactionId` duplicado → 409; solo *pending* pasa a *confirmed*. También se arregló que las transiciones del modelo **nunca se validaban** (el estado original se guardaba ya con el valor nuevo). |
| BUG-007 | Corregido | Fallos/ausencia de SMTP quedan en el log como alerta (también en producción) y el arranque avisa. Respuesta pública uniforme. |
| BUG-008 | Corregido | Ningún token vuelve por HTTP. En desarrollo sin SMTP el correo se imprime en la consola del servidor. `resend-verification` ya no revela si la cuenta existe. |
| SEC-009 | Corregido | Escape HTML en todas las plantillas; enlaces validados. |
| CONS-014 | Corregido | `utils/dates.js`: noches = fechas de calendario (UTC), "hoy" según `HOTEL_TIMEZONE`. Disponibilidad y calendarios pasados a UTC. |
| CONS-015 | Corregido | `partial` soportado en controlador, validador, admin UI y reembolso. |
| OPS-012 | Corregido | Ejemplos con valores obligatorios vacíos; el backend no arranca con secretos débiles si `FRONTEND_URL` no es localhost; `create-admin` rechaza contraseñas débiles. |
| SEC-010, SEC-011, OPS-013 | **Pendiente** | Requieren decisiones de arquitectura (cookie HttpOnly + CSRF; Redis; cuotas del chat). |

## Pasos al desplegar
1. Rotar credenciales; crear `.env` desde `.env.example` con valores nuevos.
2. `npm run backfill-nights` (simulación) y luego `-- --apply`: crea los bloqueos de las reservas existentes y lista solapes históricos.
3. Configurar SMTP.

## Cambios de comportamiento a tener en cuenta
- Cambiar el pago ahora respeta el diagrama de transiciones (p. ej. `pending → refunded` es 409). Se permitió `failed → paid/partial`.
- `create-admin` exige 12+ caracteres con letras y números.

---

## Sesión 2 (24/09/2026) — Cumplir "solo `docker compose up`"

Foco único de esta sesión: que el proyecto arranque y se pueda ver funcionando con **un solo
comando**, tal como exige el enunciado de la actividad. El resto de hallazgos de
`README_BUGS_CRITICOS.md` (SEC-016 a SEC-020, SEC-010, SEC-011, OPS-013) se dejan para después,
a propósito — no se tocó nada relacionado con ellos en esta sesión.

| ID | Qué se hizo |
|---|---|
| DEPLOY-001 | Se versiona un `.env` en la raíz con credenciales generadas solo para esta entrega (no reutilizar). `.gitignore` ahora permite ese archivo puntual y sigue ignorando cualquier otra variante (`.env.local`, `backend/.env`, etc.). `docker compose up` ya no requiere ningún paso previo. |
| DEPLOY-002 | Nuevo `backend/bootstrap.js`, llamado al arrancar el servidor (`server.js`, justo después de conectar a MongoDB): si el catálogo de suites está vacío lo carga solo, y si no existe ningún usuario `admin` lo crea con `ADMIN_NAME`/`ADMIN_EMAIL`/`ADMIN_PASSWORD` del `.env`. Es idempotente (revisa antes de actuar), así que reiniciar el contenedor no duplica ni borra datos. `seed/seedData.js` y `scripts/createAdmin.js` se refactorizaron para exportar funciones reutilizables (`insertSeedData`, `upsertAdminUser`, `isWeakAdminPassword`) en vez de solo ejecutarse como script; los comandos manuales (`--profile seed`, `--profile create-admin`) se conservan para recargar el catálogo o crear un admin adicional más adelante. |
| `docker-compose.yml` | `ADMIN_NAME`/`ADMIN_EMAIL`/`ADMIN_PASSWORD` pasaron al bloque de entorno compartido (`x-backend-environment`) porque ahora también los usa el servicio `backend`, no solo `create-admin`. |
| `README.md` | Instrucciones de arranque actualizadas: ya no piden `cp .env.example .env` ni rellenar nada; solo `docker compose up -d --build`. |

**Verificado:** sintaxis de todos los archivos tocados (`node --check`), 9/10 pruebas unitarias
(la única que falla necesita una MongoDB real, igual que antes — no se pudo levantar Docker en
este entorno), y las tres validaciones de "secreto débil" del servidor simuladas a mano contra
los valores del nuevo `.env` (todas pasan, el backend arranca sin advertencias).

**No verificado:** no se ejecutó `docker compose up` de verdad contra Docker real. Antes de
entregar, corre tú mismo:
```bash
docker compose up -d --build
docker compose logs -f backend   # deberías ver "[Bootstrap] ✅ ... suites y ... experiencias cargadas"
                                  # y "[Bootstrap] ✅ Administrador creado automáticamente: ..."
```
y confirma que `http://localhost:8080` muestra el catálogo y que puedes entrar a `/admin` con
`admin@palaciomar.co` / `AulaDocker2026Segura`.

**Recordatorio de seguridad:** las credenciales del `.env` versionado son solo para que el
profesor pueda evaluar el proyecto con un comando. No las reutilices en ningún despliegue real;
para eso, sigue las instrucciones de "Desplegar en un servidor propio" del README.
