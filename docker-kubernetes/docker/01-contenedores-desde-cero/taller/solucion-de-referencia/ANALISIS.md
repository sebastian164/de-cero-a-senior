# Docker 01: análisis

Docker 29.9.3 en macOS (Docker Desktop). Las órdenes son las del enunciado; aquí los puertos son 50001 y 50002 en lugar de 8081 y 8082 para no chocar con lo que ya tengas en marcha (`probar.sh` las ejecuta todas y comprueba cada salida). Salidas copiadas de una ejecución real.

## 1. nginx con versión fija

```bash
docker run -d --name web1 -p 8081:80 nginx:1.28-alpine
curl -sI localhost:8081 | grep -i '^server'     # Server: nginx/1.28.3  (HTTP 200)
```
Versión fija (`1.28-alpine`), nunca `latest`: la imagen de hoy y la de dentro de un año son la misma.

## 2. Un segundo nginx y un cambio dentro del primero

```bash
docker run -d --name web2 -p 8082:80 nginx:1.28-alpine
docker exec web1 sh -c 'echo "<h1>cambiado dentro de web1</h1>" > /usr/share/nginx/html/index.html'
curl localhost:8081     # <h1>cambiado dentro de web1</h1>
curl localhost:8082     # la página de bienvenida de nginx, sin cambios
docker diff web1        # C /usr/share/nginx/html/index.html
```
**No cambia el segundo.** Las dos comparten las capas de solo lectura de la imagen, pero cada contenedor tiene su propia capa escribible encima; `docker diff` muestra que el cambio vive solo en la de `web1`.

## 3. Eliminar y recrear el primero

```bash
docker rm -f web1 && docker run -d --name web1 -p 8081:80 nginx:1.28-alpine
curl localhost:8081     # "Welcome to nginx!" otra vez
```
**El cambio desapareció.** La capa escribible muere con el contenedor. Lo que no está en un volumen no sobrevive.

## 4. PostgreSQL 17 sin instalar nada

```bash
docker run -d --name pg -e POSTGRES_PASSWORD=solo-para-el-taller postgres:17
docker exec pg psql -U postgres -c "create table notas (id serial primary key, texto text not null)" \
  -c "insert into notas (texto) values ('hola desde el taller')"
docker exec pg psql -U postgres -tA -c 'select texto from notas where id = 1'   # hola desde el taller
```
La contraseña va por variable de entorno solo porque es un taller; en serio iría como secreto. Nota: `psql` por el socket Unix puede fallar durante el primer segundo, mientras la imagen arranca un servidor temporal de inicialización y lo reinicia; la prueba espera a que `psql -U postgres -c 'select 1'` responda.

## 5. Procesos dentro y fuera

Dentro del contenedor la imagen no trae `ps`; se lee `/proc`:
```bash
docker exec pg sh -c 'for p in /proc/[0-9]*; do printf "%s %s\n" "${p#/proc/}" "$(tr "\0" " " < $p/cmdline)"; done | sort -n | head -3'
#   1 postgres
#   66 postgres: checkpointer
#   67 postgres: background writer
```
PostgreSQL es el PID 1 del contenedor. Desde el equipo (macOS) no existe ese proceso: `docker inspect -f '{{.State.Pid}}' pg` da un PID (aquí 72870) que **pertenece a la máquina virtual de Linux de Docker Desktop**, no a macOS. Se ve desde ella con un contenedor que comparte su espacio de PID:
```bash
docker run --rm --pid=host busybox:1.37 ps -o pid,args | awk '$1==72870'     # 72870 postgres
```
Un contenedor es un proceso de Linux con espacios de nombres; en macOS y Windows ese Linux es una máquina virtual. En Linux nativo `ps aux | grep postgres` lo mostraría en el propio equipo.

## 6. `docker stop` frente a `docker kill`

```bash
docker stop -t 10 c1; docker kill c2
docker inspect -f '{{.State.ExitCode}}' c1 c2
```
Medido: `docker stop` 177 ms, código **0**; `docker kill` 174 ms, código **137** (128 + 9 = SIGKILL).

Qué señal envía cada una:
- `docker stop` envía la **señal de parada de la imagen** (`STOPSIGNAL`) al proceso 1 y, si no sale en el plazo (10 s por defecto), SIGKILL. No es siempre SIGTERM: la imagen de nginx declara `SIGQUIT` (`docker inspect -f '{{.Config.StopSignal}}' nginx:1.28-alpine` da `SIGQUIT`, apagado ordenado) y la de postgres declara `SIGINT` (apagado rápido); SIGTERM es solo el valor por defecto cuando la imagen no declara otro. nginx la atiende y sale con 0.
- `docker kill` envía **SIGKILL** por defecto: el núcleo termina el proceso sin que pueda hacer nada (por eso 137).

## 7. Limpieza

```bash
docker rm -fv web1 web2 pg stop kill
docker ps -a --filter name=ref-d01-     # vacío
docker network ls --filter name=ref-d01 # vacío: los contenedores usaron la red bridge por defecto y no se creó ninguna
docker volume ls -q --filter name=ref-d01   # vacío
```
La imagen de postgres declara `VOLUME /var/lib/postgresql/data`, así que `docker run` crea un volumen anónimo. `-v` en `docker rm -fv` lo borra con el contenedor (la prueba comprueba que existía antes y que `docker volume inspect` falla después). Sin `-v` habría quedado huérfano.
