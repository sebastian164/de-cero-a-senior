#!/usr/bin/env bash
# Solución ejecutable del taller Docker 01. Cada paso imprime la evidencia que pide el enunciado.
# Usa los puertos 50001 y 50002 del equipo (en el enunciado, 8081 y 8082) para no chocar con lo que ya tengas.
. "$(cd "$(dirname "$0")" && pwd)/lib.sh"
P="$PREFIJO-d01"
NGINX=nginx:1.28-alpine
PG=postgres:17

limpiar() { docker rm -fv $P-web1 $P-web2 $P-pg $P-stop $P-kill >/dev/null 2>&1; }
limpiar

titulo "Docker 01, paso 1: nginx con versión fija en el 50001"
docker run -d --name $P-web1 -p 50001:80 $NGINX >/dev/null
esperar_hasta 20 curl -sf localhost:50001
igual "nginx responde en el 50001" 200 "$(codigo_http localhost:50001)"
nota "$(curl -sI localhost:50001 | grep -i '^server')"

titulo "Paso 2: segundo nginx y cambio dentro del primero"
docker run -d --name $P-web2 -p 50002:80 $NGINX >/dev/null
esperar_hasta 20 curl -sf localhost:50002
docker exec $P-web1 sh -c 'echo "<h1>cambiado dentro de web1</h1>" > /usr/share/nginx/html/index.html'
afirmar "web1 sirve el cambio" sh -c 'curl -s localhost:50001 | grep -q "cambiado dentro de web1"'
negar "web2 NO ve el cambio (cada contenedor tiene su propia capa escribible)" sh -c 'curl -s localhost:50002 | grep -q "cambiado"'
nota "docker diff web1: $(docker diff $P-web1 | grep index.html | tr '\n' ' ')"

titulo "Paso 3: eliminar y recrear el primero"
docker rm -f $P-web1 >/dev/null
docker run -d --name $P-web1 -p 50001:80 $NGINX >/dev/null
esperar_hasta 20 curl -sf localhost:50001
negar "el cambio desapareció con el contenedor" sh -c 'curl -s localhost:50001 | grep -q "cambiado"'
afirmar "vuelve la página original de la imagen" sh -c 'curl -s localhost:50001 | grep -q "Welcome to nginx"'

titulo "Paso 4: PostgreSQL 17 y psql dentro del contenedor"
docker run -d --name $P-pg -e POSTGRES_PASSWORD=solo-para-el-taller $PG >/dev/null
esperar_hasta 60 docker exec $P-pg pg_isready -U postgres
sleep 2
esperar_hasta 30 docker exec $P-pg psql -U postgres -c 'select 1'
docker exec $P-pg psql -U postgres -q -c "create table notas (id serial primary key, texto text not null)" \
  -c "insert into notas (texto) values ('hola desde el taller')"
fila="$(docker exec $P-pg psql -U postgres -tA -c 'select texto from notas where id = 1')"
igual "la consulta devuelve la fila" "hola desde el taller" "$fila"

titulo "Paso 5: procesos dentro y fuera"
nota "Dentro del contenedor (ps no viene en la imagen; se lee /proc):"
docker exec $P-pg sh -c 'for p in /proc/[0-9]*; do printf "%s %s\n" "${p#/proc/}" "$(tr "\0" " " < $p/cmdline)"; done | sort -n | head -3' | sed 's/^/     /'
pid1="$(docker exec $P-pg sh -c 'tr "\0" " " < /proc/1/cmdline')"
afirmar "dentro, el PID 1 es postgres" sh -c "echo '$pid1' | grep -q '^postgres'"
pid_anfitrion="$(docker inspect -f '{{.State.Pid}}' $P-pg)"
nota "docker inspect .State.Pid = $pid_anfitrion (PID en la máquina virtual de Linux de Docker Desktop)"
[ "$(uname)" = Darwin ] && afirmar "en macOS no existe ese proceso postgres en el equipo" sh -c "! pgrep -x postgres >/dev/null || ! ps -p $pid_anfitrion -o comm= | grep -q postgres"
[ "$(uname)" = Darwin ] || nota "(en Linux el proceso sí aparece en el equipo: no hay máquina virtual de por medio)"
nota "Visto desde el espacio de PID de la máquina virtual (contenedor con --pid=host):"
visto="$(docker run --rm --pid=host busybox:1.37 ps -o pid,args | awk -v p="$pid_anfitrion" '$1==p')"
nota "  $visto"
afirmar "la máquina virtual de Docker sí lo ve con el PID del anfitrión" sh -c "echo '$visto' | grep -q postgres"

titulo "Paso 6: docker stop frente a docker kill"
docker run -d --name $P-stop $NGINX >/dev/null
docker run -d --name $P-kill $NGINX >/dev/null
sleep 2
i=$(ahora_ms); docker stop -t 10 $P-stop >/dev/null; t_stop=$(( $(ahora_ms) - i ))
i=$(ahora_ms); docker kill $P-kill >/dev/null; t_kill=$(( $(ahora_ms) - i ))
c_stop=$(docker inspect -f '{{.State.ExitCode}}' $P-stop)
c_kill=$(docker inspect -f '{{.State.ExitCode}}' $P-kill)
nota "docker stop: ${t_stop} ms, código $c_stop | docker kill: ${t_kill} ms, código $c_kill"
igual "docker stop: nginx atiende la señal de parada y sale con 0" 0 "$c_stop"
igual "kill envía SIGKILL: 128 + 9" 137 "$c_kill"
igual "docker stop envía la STOPSIGNAL de la imagen: nginx declara SIGQUIT (no SIGTERM)" SIGQUIT "$(docker inspect -f '{{.Config.StopSignal}}' $NGINX)"
igual "postgres (para comparar) usa SIGINT para el apagado rápido" SIGINT "$(docker inspect -f '{{.Config.StopSignal}}' $PG)"
echo "stop_ms=$t_stop stop_codigo=$c_stop kill_ms=$t_kill kill_codigo=$c_kill" > "$SALIDAS/docker-01.txt"

titulo "Paso 7: limpieza"
vol_pg="$(docker inspect -f '{{range .Mounts}}{{.Name}}{{end}}' $P-pg)"
afirmar "antes de limpiar existe el volumen anónimo de postgres ($vol_pg)" docker volume inspect "$vol_pg"
limpiar
quedan="$(docker ps -a --filter name=$P- --format '{{.Names}}')"
igual "docker ps -a no muestra contenedores del taller" "" "$quedan"
negar "el volumen anónimo de postgres ya no existe (docker rm -v)" docker volume inspect "$vol_pg"
igual "no hay redes del taller (los contenedores usaron la red bridge por defecto)" "" "$(docker network ls --filter name=$P --format '{{.Name}}')"
igual "docker volume ls no muestra volúmenes del taller" "" "$(docker volume ls -q --filter name=$P)"
nota "docker rm -v borró el volumen anónimo que crea la imagen de postgres (VOLUME /var/lib/postgresql/data)"

terminar "Docker 01"
