#!/usr/bin/env bash
# Ejecuta, desde cero, los pasos del README de servicio-pedidos/ tal como están escritos: extrae los comandos del propio
# README.md (no hay copias). Única diferencia con lo que haría una persona nueva: el compose arranca en segundo plano
# (-d --wait) y el puerto del anfitrión se puede cambiar con PUERTO_APP (por defecto 8080, el del README).
# Requiere Docker, JDK 21, curl y jq. Lo baja todo al terminar.
set -euo pipefail
AQUI="$(cd "$(dirname "$0")" && pwd)"
cd "$AQUI/servicio-pedidos"
PUERTO_APP=${PUERTO_APP:-8080}; export PUERTO_APP
export COMPOSE_PROJECT_NAME=${PROYECTO:-n12-05}
paso() { printf '\n== %s\n' "$*"; }
comprobar() { local d=$1; shift; if "$@"; then echo "  OK  $d"; else echo "  FALLA  $d" >&2; exit 1; fi; }
cmd_compose=$(sed -n 's/^2\. `\([^`]*\)`.*/\1/p' README.md)
cmd_curl=$(sed -n 's/^3\. .*prueba: `\(.*\)`\.$/\1/p' README.md)
cmd_pruebas=$(sed -n '/^## Pruebas/{n;p;}' README.md | tr -d '`')
test "$cmd_compose" = "docker compose up --build"
test -n "$cmd_curl"; test "$cmd_pruebas" = "./mvnw verify"
limpiar() { docker compose down -v --rmi local >/dev/null 2>&1 || true; rm -rf target; }
trap limpiar EXIT
docker compose down -v >/dev/null 2>&1 || true

paso "README, paso 2: $cmd_compose (en segundo plano, esperando a que los servicios estén sanos)"
$cmd_compose -d --wait
comprobar "los dos servicios están en marcha y la base healthy" bash -c "[ \$(docker compose ps --format '{{.State}}' | grep -c running) = 2 ] && docker compose ps --format '{{.Service}} {{.Health}}' | grep -q '^db healthy'"

paso "README, paso 3: $cmd_curl"
for i in $(seq 1 90); do curl -sf -o /dev/null "http://localhost:$PUERTO_APP/actuator/health/readiness" && break; sleep 1; done
respuesta=$(eval "${cmd_curl//8080/$PUERTO_APP}")
echo "    $respuesta"
comprobar "responde con un pedido creado (id)" bash -c "echo '$respuesta' | jq -e '.id' >/dev/null"
comprobar "el pedido está en PostgreSQL" bash -c "docker compose exec -T db psql -U postgres -d tienda -tAc 'select count(*) from pedidos' | grep -qx 1"

paso "README, sección Pruebas: $cmd_pruebas"
$cmd_pruebas -B -q 2>&1 | tail -5
comprobar "las pruebas pasaron" bash -c "grep -h 'Tests run' target/surefire-reports/*.txt | grep -q 'Failures: 0, Errors: 0'"

paso "README, sección 'Si algo falla': sin contraseña la aplicación no arranca, con el mensaje que dice el README"
docker compose stop app >/dev/null
imagen=$(docker compose images app --format json | jq -r 'if type=="array" then .[0] else . end | .Repository + ":" + .Tag')
msg=$(docker run --rm --network "${COMPOSE_PROJECT_NAME}_default" -e SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/tienda -e SPRING_DATASOURCE_USERNAME=postgres "$imagen" 2>&1 | grep -m1 -oE 'no password was provided|password authentication failed' || true)
comprobar "el mensaje es el que el README promete ($msg)" test -n "$msg"
echo; echo "README comprobado: todo correcto"
