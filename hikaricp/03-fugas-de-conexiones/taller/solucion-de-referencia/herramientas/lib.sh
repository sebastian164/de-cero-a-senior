# Utilidades compartidas por los scripts de esta solución. Se cargan con: source herramientas/lib.sh
# Variables que puedes cambiar: DB_PUERTO (puerto de PostgreSQL en tu máquina, 5432 por defecto)
# y PROYECTO (nombre del proyecto de Docker Compose).
RAIZ_VER="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export DB_PUERTO="${DB_PUERTO:-5432}"
export PROYECTO="${PROYECTO:-hikaricp-taller}"
export DB_URL="jdbc:postgresql://localhost:${DB_PUERTO}/tienda"
export DB_USUARIO=postgres
export DB_PASSWORD=local
CONTENEDOR_DB="${PROYECTO}-db-1"
dc() { docker compose -p "$PROYECTO" -f "$RAIZ_VER/compose.yaml" "$@"; }
psqlv() { dc exec -T db psql -U postgres -d tienda -v ON_ERROR_STOP=1 "$@"; }
# El usuario de aplicación de los talleres (no es superusuario: le aplica la reserva de superuser_reserved_connections).
preparar_tienda() {
  psqlv -q -c "do \$\$ begin if not exists (select from pg_roles where rolname = 'tienda') then create role tienda login password 'Tienda2026!'; end if; end \$\$;" \
    -c "grant all on schema public to tienda"
}
# Espera a que una aplicación responda en su puerto (cualquier código HTTP).
esperar_http() { for _ in $(seq 1 120); do [ "$(curl -s -o /dev/null -w '%{http_code}' "localhost:$1/actuator/health")" != "000" ] && return 0; sleep 0.5; done; return 1; }
metrica() { curl -s "localhost:$1/actuator/metrics/$2${3:+?tag=$3}" | jq -r '.measurements[0].value'; }
# muestrear puerto metrica archivo: cada segundo anota "segundo valor" hasta que se mate el proceso. Deja el PID en MUESTREO.
muestrear() {
  ( local t=0; : > "$3"; while true; do echo "$t $(metrica "$1" "$2" | cut -d. -f1)" >> "$3"; sleep 1; t=$((t + 1)); done ) &
  MUESTREO=$!
}
