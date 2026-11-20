# Docker 01: contenedores desde cero (solución de referencia)

> **Intenta el taller antes de leer esto: ver la respuesta primero convierte el taller en lectura.**

Ejecuta los siete pasos del taller con `nginx:1.28-alpine` y `postgres:17` en los puertos 50001 y 50002 del equipo (en el enunciado, 8081 y 8082) y comprueba la salida de cada uno.

## Qué instalar

Docker (Docker Desktop o Docker Engine), `curl` y `python3`.

## Ejecutarla y comprobarla

```bash
./probar.sh
```

Dura menos de un minuto (algo más la primera vez, mientras descarga las imágenes). Termina con código 0 solo si todas las comprobaciones pasan; cada paso imprime `OK` o `FALLA` con lo esperado y lo obtenido.

- `PREFIJO=otro ./probar.sh` cambia el prefijo (por defecto `ref`) de los contenedores, redes, volúmenes e imágenes que crea.
- `MANTENER=1 ./probar.sh` conserva las imágenes `bitacora-*` al terminar (acelera una segunda ejecución).

## Bajarlo todo

`probar.sh` borra lo que crea al terminar (contenedores, volúmenes, redes, imágenes y, en la sesión 06, el registro local y el builder). Si lo interrumpes a la mitad:

```bash
./bajar.sh
```

## Más

[`ANALISIS.md`](ANALISIS.md): las órdenes, las salidas y la explicación de cada paso.
