#!/usr/bin/env bash
# =============================================================================
# Palacio del Mar - arranque con registro de logs y reporte de errores
#
#   bash run.sh            Levanta todo (docker compose up -d --build), guarda el
#                          build en logs/ y, cuando termina, genera logs/reporte.txt
#   bash run.sh reporte    Genera logs/reporte.txt con lo ocurrido hasta ahora.
#                          Úsalo justo después de reproducir un error en el sitio.
#   bash run.sh seguir     Guarda los logs EN VIVO en logs/*.live.log mientras
#                          pruebas (Ctrl+C para parar; al parar genera el reporte).
#   bash run.sh parar      Apaga los contenedores (conserva los datos).
#
# Para pedir ayuda con un error, comparte logs/reporte.txt.
# Todo lo que se guarda pasa antes por un filtro que oculta tokens, JWT,
# contraseñas y cadenas largas hexadecimales.
#
# En Windows se ejecuta desde Git Bash o WSL.
# =============================================================================
set -u
cd "$(dirname "${BASH_SOURCE[0]}")" || exit 1

LOG_DIR="logs"
SERVICIOS="backend web mongo"
CONTENEDORES="palacio-mongo palacio-backend palacio-web"
TS="$(date +%Y%m%d-%H%M%S)"
mkdir -p "$LOG_DIR"

# sed sin búfer (para los logs en vivo); no todas las versiones lo soportan
if echo x | sed -u -e 's/x/y/' >/dev/null 2>&1; then SED_U="-u"; else SED_U=""; fi

# --- Oculta secretos antes de guardar o mostrar nada ------------------------
enmascarar() {
  # shellcheck disable=SC2086
  sed -E $SED_U \
    -e 's/eyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]*/[JWT-OCULTO]/g' \
    -e 's/(Bearer )[A-Za-z0-9._~+\/=-]+/\1[OCULTO]/g' \
    -e 's/[0-9a-fA-F]{48,}/[HEX-OCULTO]/g' \
    -e 's/(ADMIN_PASSWORD|JWT_SECRET|SMTP_PASS)=[^ ]+/\1=[OCULTO]/g' \
    -e 's/(ADMIN_PASSWORD|JWT_SECRET|SMTP_PASS): *[^ ]+/\1: [OCULTO]/g' \
    -e 's/("([Pp]assword|[Tt]oken|[Aa]uthorization)" *: *")[^"]*/\1[OCULTO]/g' \
    -e 's#(://[^:/@ ]+:)[^@ /]+@#\1[OCULTO]@#g'
}

# --- Patrones de error por servicio -----------------------------------------
PATRON_BACKEND='error|exception|fatal|unhandled|uncaught|ECONN|EADDRINUSE|ENOTFOUND|EACCES|EPERM|MongoServerError|MongoNetworkError|ValidationError|CastError|TypeError|ReferenceError|SyntaxError|\[5[0-9]{2}\]|failed|denied|timed out|cannot find|❌|⚠'
PATRON_WEB='\[(error|emerg|crit|alert|warn)\]|HTTP/[0-9.]+" 5[0-9]{2} '
PATRON_MONGO='"s":"[EF]"|fatal'
PATRON_BUILD='error|ERR!|failed|cannot find|not found|ERROR:'

patron_de() {
  case "$1" in
    backend) echo "$PATRON_BACKEND" ;;
    web)     echo "$PATRON_WEB" ;;
    mongo)   echo "$PATRON_MONGO" ;;
  esac
}

ultimo_build() { ls -1t "$LOG_DIR"/build-*.log 2>/dev/null | head -n 1; }

# --- Espera a que el backend esté sano --------------------------------------
# Devuelve 0 = sano, 1 = falló/no existe, 2 = tiempo agotado
espera_salud() {
  local limite="${1:-120}" t=0 estado=""
  while [ "$t" -lt "$limite" ]; do
    estado="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' palacio-backend 2>/dev/null)"
    case "$estado" in
      healthy) return 0 ;;
      unhealthy|exited|dead) return 1 ;;
      "") [ "$t" -ge 15 ] && return 1 ;;
    esac
    sleep 3
    t=$((t + 3))
  done
  return 2
}

# --- Genera logs/reporte.txt y los logs completos por servicio ---------------
reporte() {
  local salida="$LOG_DIR/reporte.txt" svc c build patron
  build="$(ultimo_build)"

  # Logs completos por servicio (ya enmascarados)
  for svc in $SERVICIOS; do
    docker compose logs --no-color --timestamps "$svc" 2>&1 | enmascarar > "$LOG_DIR/$svc.log"
  done

  {
    echo "=== REPORTE PALACIO DEL MAR ==="
    echo "Generado: $(date '+%Y-%m-%d %H:%M:%S %z')"
    echo "Docker:   $(docker --version 2>&1)"
    echo "Compose:  $(docker compose version 2>&1)"
    echo "Sistema:  $(uname -srm 2>&1)"
    echo
    echo "=== ESTADO DE LOS CONTENEDORES ==="
    docker compose ps -a 2>&1
    echo
    for c in $CONTENEDORES; do
      docker inspect --format '{{.Name}}  estado={{.State.Status}}  salud={{if .State.Health}}{{.State.Health.Status}}{{else}}n/a{{end}}  reinicios={{.RestartCount}}  codigo_salida={{.State.ExitCode}}  sin_memoria={{.State.OOMKilled}}' "$c" 2>&1
    done
    echo
    echo "=== ERRORES DETECTADOS (últimas 40 líneas por servicio) ==="
    for svc in $SERVICIOS; do
      echo "--- $svc ---"
      patron="$(patron_de "$svc")"
      # Los avisos [SECURITY] son normales (peticiones sin sesión, etc.): se resumen aparte
      grep -Ei "$patron" "$LOG_DIR/$svc.log" | grep -v '\[SECURITY\]' | tail -n 40
      echo
    done
    echo "=== ERRORES DE CONSTRUCCIÓN (build) ==="
    if [ -n "$build" ]; then
      echo "(archivo: $build)"
      grep -Ei "$PATRON_BUILD" "$build" | tail -n 30
    else
      echo "(todavía no hay un build registrado; usa: bash run.sh)"
    fi
    echo
    echo "=== PETICIONES CON ERROR DEL CLIENTE (4xx) MÁS FRECUENTES ==="
    grep -oE '\[4[0-9]{2}\] [A-Z]+ [^ ?]+' "$LOG_DIR/backend.log" | sort | uniq -c | sort -rn | head -n 10
    echo
    echo "=== EVENTOS DE SEGURIDAD (resumen) ==="
    grep -oE '"event":"[A-Z_]+"' "$LOG_DIR/backend.log" | sort | uniq -c | sort -rn | head -n 10
    echo
    echo "=== ÚLTIMAS 60 LÍNEAS POR SERVICIO ==="
    for svc in $SERVICIOS; do
      echo "--- $svc ---"
      if [ "$svc" = "mongo" ]; then
        # MongoDB registra cada conexión (NETWORK/ACCESS) y tapa lo importante
        grep -vE '"c":"(NETWORK|ACCESS)"' "$LOG_DIR/$svc.log" | tail -n 60
      else
        tail -n 60 "$LOG_DIR/$svc.log"
      fi
      echo
    done
    echo "=== NOTA ==="
    echo "Los errores que ocurren SOLO en el navegador (consola de F12) no aparecen aquí."
    echo "Si el sitio falla visualmente, copia también los mensajes rojos de F12 > Consola."
  } 2>&1 | enmascarar > "$salida"

  echo "Reporte guardado en $salida"
}

# --- Comandos -----------------------------------------------------------------
requiere_docker() {
  command -v docker >/dev/null 2>&1 || { echo "Docker no está instalado o no está en el PATH."; exit 1; }
  docker info >/dev/null 2>&1 || { echo "Docker está instalado pero no está corriendo. Abre Docker Desktop e inténtalo de nuevo."; exit 1; }
}

arrancar() {
  requiere_docker
  local build="$LOG_DIR/build-$TS.log" rc
  echo "Construyendo y levantando (el detalle se guarda en $build)..."
  docker compose up -d --build 2>&1 | enmascarar | tee "$build"
  rc="${PIPESTATUS[0]}"

  # Conserva solo los 5 builds más recientes
  ls -1t "$LOG_DIR"/build-*.log 2>/dev/null | tail -n +6 | xargs rm -f 2>/dev/null

  if [ "$rc" -ne 0 ]; then
    echo "ERROR: docker compose terminó con código $rc."
    reporte
    echo "Comparte $LOG_DIR/reporte.txt para revisar el fallo."
    exit "$rc"
  fi

  echo "Esperando a que el backend esté sano (máx. 120 s)..."
  espera_salud 120
  local salud=$?
  reporte
  case "$salud" in
    0) echo "Listo: http://localhost:8080   (admin: http://localhost:8080/admin)" ;;
    1) echo "El backend NO arrancó bien. Revisa $LOG_DIR/reporte.txt"; exit 1 ;;
    2) echo "El backend tarda demasiado en estar sano. Revisa $LOG_DIR/reporte.txt"; exit 1 ;;
  esac
  echo "Si algo falla, reproduce el error y ejecuta: bash run.sh reporte"
}

seguir() {
  requiere_docker
  local pids=() svc
  echo "Guardando logs en vivo en $LOG_DIR/*.live.log (Ctrl+C para parar)."
  for svc in $SERVICIOS; do
    ( docker compose logs -f --no-color --timestamps --tail 0 "$svc" 2>&1 | enmascarar >> "$LOG_DIR/$svc.live.log" ) &
    pids+=("$!")
  done
  trap 'kill "${pids[@]}" 2>/dev/null; echo; echo "Parado."; reporte; exit 0' INT TERM
  wait
}

case "${1:-arrancar}" in
  arrancar|"") arrancar ;;
  reporte)     requiere_docker; reporte ;;
  seguir)      seguir ;;
  parar)       requiere_docker; docker compose down ;;
  *)
    echo "Uso: bash run.sh [reporte|seguir|parar]"
    exit 1
    ;;
esac
