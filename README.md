# Palacio del Mar

Sitio web de un hotel boutique en Cartagena: catálogo de suites y experiencias,
reservas con calendario de disponibilidad, cuentas de cliente y panel de
administración.

**Stack:** React + Vite (frontend), Node.js + Express (API), MongoDB, todo
dentro de Docker Compose.

## Arrancar (un solo comando)

Necesitas [Docker](https://www.docker.com/products/docker-desktop/) instalado.

```bash
docker compose up -d --build
```

Cuando termine, abre **http://localhost:8080**.

No hace falta ningún archivo `.env`: todos los valores para correr en local
están en `docker-compose.yml`. En el primer arranque se cargan solas las suites
y experiencias de ejemplo y se crea el administrador.

| Qué | Valor |
|---|---|
| Sitio | http://localhost:8080 |
| Panel de administración | http://localhost:8080/admin |
| Correo del administrador | `admin@palaciomar.co` |
| Contraseña del administrador | `AulaDocker2026Segura` |

> Estas credenciales y la clave JWT de `docker-compose.yml` son **solo para uso
> local**. No las uses en un servidor real.

## Si algo falla: registro de errores

`run.sh` hace lo mismo que `docker compose up -d --build`, pero además guarda los
logs en la carpeta `logs/` y genera un reporte con los errores ya filtrados:

```bash
bash run.sh            # levanta todo; guarda el build y genera logs/reporte.txt
bash run.sh reporte    # genera logs/reporte.txt tras reproducir un error en el sitio
bash run.sh seguir     # guarda los logs en vivo mientras pruebas (Ctrl+C para parar)
bash run.sh parar      # apaga los contenedores (conserva los datos)
```

Comparte `logs/reporte.txt` para pedir ayuda: oculta tokens, JWT y contraseñas.
Los errores que solo salen en el navegador (consola de F12) no se incluyen.
En Windows usa Git Bash o WSL. La carpeta `logs/` no se sube a Git.

## Comandos útiles

```bash
docker compose logs -f backend          # ver la API (aquí salen los correos de
                                        # verificación y de recuperar contraseña)
docker compose down                     # apagar (conserva los datos)
docker compose down -v                  # apagar y BORRAR la base de datos
docker compose exec backend npm run create-admin   # crear/promover otro administrador
docker compose exec backend npm run seed           # recargar el catálogo de ejemplo
                                                   # (borra y vuelve a crear suites y experiencias)
```

Los correos no se envían de verdad: en local se imprimen en el log del backend.
Para enviarlos hay que definir `SMTP_HOST`, `SMTP_USER` y `SMTP_PASS` en el
bloque `backend` de `docker-compose.yml`.

## Estructura

```
docker-compose.yml     arranque completo (mongo + backend + web)
run.sh                 arranque con registro de logs y reporte de errores
backend/               API Express
  controllers/ routes/ models/ middleware/ utils/
  seed/ scripts/ bootstrap.js   datos de ejemplo y administrador inicial
  tests/
frontend/              React (Vite) servido por nginx; /api se reenvía al backend
```

## Pruebas

```bash
cd backend
npm ci
npm test                                  # pruebas de fechas y precios
TEST_MONGODB_URI=mongodb://localhost:27017/test npm test   # incluye la prueba de concurrencia
```

## Desarrollo sin Docker (opcional)

Necesitas Node 20+ y una MongoDB local. Copia `backend/.env.example` a
`backend/.env` y completa `JWT_SECRET`; luego `npm run dev` en `backend/` y en
`frontend/` (el frontend corre en http://localhost:5173 y reenvía `/api` al
puerto 5000).
