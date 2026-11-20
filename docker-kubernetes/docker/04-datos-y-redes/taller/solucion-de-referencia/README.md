# Docker 04: datos y redes (solución de referencia)

> **Intenta el taller antes de leer esto: ver la respuesta primero convierte el taller en lectura.**

Solo `docker run`, sin Compose: volumen con nombre y permisos, red propia con resolución de nombres, solo la web publicada (puerto 50041), dos copias sobre el mismo volumen y montaje de una carpeta sobre `index.html`.

## Qué instalar

Docker, `curl` y `python3`.

## Cómo está organizada

La prueba construye `bitacora-api:1.0.0` y `bitacora-web:0.1.0` con los Dockerfile de las soluciones de las sesiones [03](../../03-imagenes-eficientes-y-seguras/taller/solucion-de-referencia/) y [02](../../02-tu-primera-imagen/taller/solucion-de-referencia/) y el código de [`docker-kubernetes/app`](../../../app/). Esas dos carpetas tienen que estar en el repositorio.

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
