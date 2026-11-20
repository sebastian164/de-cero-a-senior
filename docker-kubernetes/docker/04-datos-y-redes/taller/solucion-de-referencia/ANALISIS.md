# Docker 04: análisis

Docker 29.9.3. Las órdenes son las que ejecuta `probar.sh`, con los nombres del enunciado (`bitacora-api:1.0.0`, `bitacora-web:0.1.0`) y el puerto 8080; el script usa los nombres `ref-d04-*` y el puerto 50041 para no chocar con lo que ya tengas en marcha. Salidas de una ejecución real.

Parámetros que se repiten: la web corre sin root (la imagen oficial de nginx arranca como root, así que se fuerza el usuario 101 y se dan tres `tmpfs` donde nginx escribe):

```bash
WEB_SIN_ROOT="--user 101:101 --tmpfs /var/cache/nginx:mode=1777 --tmpfs /run:mode=1777 --tmpfs /etc/nginx/conf.d:mode=1777"
```

## 1. El problema: sin volumen se pierde todo

```bash
docker network create red
docker run -d --name api --network red bitacora-api:1.0.0
docker run --rm --network red curlimages/curl:8.17.0 -s -X POST --data 'nota sin volumen' http://api:8080/api/notas
docker rm -f api
docker run -d --name api --network red bitacora-api:1.0.0
docker run --rm --network red curlimages/curl:8.17.0 -s http://api:8080/api/notas
# {"host":"55b546ca2c6f","notas":[]}
```
La nota estaba en la capa escribible del contenedor, y murió con él.

## 2. Volumen con nombre

```bash
docker rm -f api
docker volume create notas
docker run -d --name api --network red -v notas:/datos bitacora-api:1.0.0
docker run --rm --network red curlimages/curl:8.17.0 -s -X POST --data 'nota con volumen' http://api:8080/api/notas
docker rm -f api
docker run -d --name api --network red -v notas:/datos bitacora-api:1.0.0
docker run --rm --network red curlimages/curl:8.17.0 -s http://api:8080/api/notas
# {"host":"c0f431c2c3ee","notas":["nota con volumen"]}      <- otro contenedor (otro host), la misma nota
```

## 3. Permisos

Un volumen nuevo hereda el propietario de la carpeta que la imagen tiene en esa ruta. La imagen de la API (taller 03) la prepara con el usuario sin privilegios:

```dockerfile
RUN addgroup -S -g 10001 bitacora && adduser -S -D -H -u 10001 -G bitacora bitacora \
 && mkdir /datos && chown 10001:10001 /datos
USER 10001:10001
```
Comprobación: `docker exec api stat -c '%u:%g' /datos` da `10001:10001` y `docker exec api id -u` da `10001`.

Contraprueba (imagen sin `/datos`): el volumen nace con dueño `root` y el proceso 10001 no puede escribir:
```
ERROR no puedo escribir en /datos: java.nio.file.AccessDeniedException: /datos/prueba8662782942816719182.tmp. Sigo vivo
```
El arreglo es crear y dar la propiedad de la carpeta **en la imagen**, no ejecutar como root.

## 4. Red por defecto: sin resolución de nombres

```bash
docker run -d --name api-bridge bitacora-api:1.0.0
docker run -d --name web-bridge $WEB_SIN_ROOT -e DESTINO_API=http://api-bridge:8080 bitacora-web:0.1.0
docker inspect -f '{{.State.Status}}' web-bridge       # exited
docker logs web-bridge
# [emerg] 1#1: host not found in upstream "api-bridge" in /etc/nginx/conf.d/default.conf:12
```
La red `bridge` por defecto no tiene DNS interno entre contenedores; nginx resuelve el nombre al arrancar y, si no lo encuentra, no arranca.

## 5 y 6. Red propia y solo la web publicada

```bash
docker rm -f api-bridge web-bridge
docker run -d --name web --network red -p 8080:8080 $WEB_SIN_ROOT -e DESTINO_API=http://api:8080 bitacora-web:0.1.0
curl -s localhost:8080/ | grep '<title>'                      # <title>Bitácora</title>
curl -s localhost:8080/api/info                               # 200, a través de la web
curl -s -X POST --data 'desde la web' localhost:8080/api/notas  # 201
curl -s localhost:8080/api/notas                              # incluye "desde la web"
docker exec web getent hosts api                              # 172.25.0.2  api   (resuelve por nombre, no por IP)
docker inspect -f '{{json .NetworkSettings.Ports}}' api       # {} : la API no publica ningún puerto
docker exec api id -u; docker exec web id -u                  # 10001 y 101: ninguno es root
```
Solo la web lleva `-p`. La API no es alcanzable desde el equipo (no tiene puertos publicados; la prueba comprueba que `curl` contra su puerto falla) y sí a través de la web.

## 7. Segunda API sobre el mismo volumen

```bash
docker run -d --name api2 --network red -v notas:/datos -e MENSAJE='Soy la segunda' bitacora-api:1.0.0
docker run --rm --network red curlimages/curl:8.17.0 -s -X POST --data 'escrita por api'  http://api:8080/api/notas
docker run --rm --network red curlimages/curl:8.17.0 -s -X POST --data 'escrita por api2' http://api2:8080/api/notas
docker run --rm --network red curlimages/curl:8.17.0 -s http://api:8080/api/notas
docker run --rm --network red curlimages/curl:8.17.0 -s http://api2:8080/api/notas
# las dos devuelven las 4 notas: ["nota con volumen","desde la web","escrita por api","escrita por api2"]
```
Las dos copias leen el mismo archivo (`host` distinto, mismas notas). Riesgo: dos procesos escriben el mismo archivo sin coordinarse (no hay bloqueo entre procesos; el orden y la atomicidad dependen del sistema de archivos). Prueba de estrés: 400 escrituras concurrentes desde las dos APIs dieron 404 líneas y 0 mezcladas, o sea, aquí no se corrompió nada, pero eso no es una garantía. Una base de datos existe para esto.

## 8. Montaje de carpeta

```bash
docker rm -f web
docker run -d --name web --network red -p 8080:8080 $WEB_SIN_ROOT -e DESTINO_API=http://api:8080 \
  -v "$PWD/index.html:/usr/share/nginx/html/index.html:ro" bitacora-web:0.1.0
curl -s localhost:8080/        # muestra el contenido del archivo del equipo
# se edita index.html en el equipo
curl -s localhost:8080/        # el cambio se ve al instante, sin reconstruir
```
Cómodo en desarrollo: ciclo de edición inmediato. Mala idea en producción: la imagen deja de ser la unidad que se prueba y se despliega (el contenido depende de un archivo del anfitrión), no hay versión ni trazabilidad, y la misma imagen se comporta distinto en cada máquina.

## 9. Limpieza

```bash
docker rm -fv web api api2
docker network rm red
docker volume rm notas
docker ps -a --filter name=ref-d04-; docker network ls --filter name=ref-d04-red -q; docker volume ls --filter name=ref-d04-notas -q   # los tres vacíos
```
