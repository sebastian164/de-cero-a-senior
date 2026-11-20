# Docker 05: Docker Compose (solución de referencia)

> **Intenta el taller antes de leer esto: ver la respuesta primero convierte el taller en lectura.**

Levanta el proyecto con una orden, comprueba la clave como secreto, la persistencia de las notas, la espera de la web a la API, el perfil `datos`, los límites de recursos y dos copias a la vez. Usa los puertos 50051 a 50054.

## Qué instalar

Docker con Docker Compose v2 (`docker compose`), `curl`, `openssl` y `python3`.

## Cómo está organizada

Esta carpeta es el proyecto de Compose sin el código de la aplicación: `probar.sh` hace una copia limpia (como un clon nuevo), le añade el código de [`docker-kubernetes/app`](../../../app/) en `app/` y ejecuta las pruebas allí. Para usarla a mano, copia `Servidor.java` a `app/api/` y `index.html` y `default.conf.template` a `app/web/`, luego `cp .env.ejemplo .env && docker compose up -d --build`. Para apagar: `docker compose down -v`.

## Ejecutarla y comprobarla

```bash
./probar.sh
```

La primera vez tarda varios minutos porque descarga las imágenes base; después, alrededor de un minuto. Termina con código 0 solo si todas las comprobaciones pasan; cada paso imprime `OK` o `FALLA` con lo esperado y lo obtenido.

- `PREFIJO=otro ./probar.sh` cambia el prefijo (por defecto `ref`) de los contenedores, redes, volúmenes e imágenes que crea.
- `MANTENER=1 ./probar.sh` conserva las imágenes `bitacora-*` al terminar (acelera una segunda ejecución).

## Bajarlo todo

`probar.sh` borra lo que crea al terminar (contenedores, volúmenes, redes, imágenes y, en la sesión 06, el registro local y el builder). Si lo interrumpes a la mitad:

```bash
./bajar.sh
```

## Más

[`ANALISIS.md`](ANALISIS.md): las órdenes, las salidas y la explicación de cada paso.
