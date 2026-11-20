# Docker 03: imágenes eficientes y seguras (solución de referencia)

> **Intenta el taller antes de leer esto: ver la respuesta primero convierte el taller en lectura.**

Construye la variante intermedia (JRE) y la versión final con `jlink` sobre Alpine, y comprueba usuario sin privilegios, `HEALTHCHECK` con margen de arranque, `--read-only`, 64 MB de memoria y tiempo de parada. Usa los puertos 50031 a 50034.

## Qué instalar

Docker (con BuildKit), `curl` y `python3`.

## Cómo está organizada

Igual que en la sesión 02, el código de la aplicación sale de [`docker-kubernetes/app`](../../../app/). La prueba parte de la imagen 0.1.0, que construye con el Dockerfile de la solución de la [sesión 02](../../02-tu-primera-imagen/taller/solucion-de-referencia/), así que esa carpeta tiene que estar en el repositorio.

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
