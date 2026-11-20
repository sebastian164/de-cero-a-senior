# Docker 02: tu primera imagen (solución de referencia)

> **Intenta el taller antes de leer esto: ver la respuesta primero convierte el taller en lectura.**

Construye las dos imágenes, mide la caché de capas, el efecto del `.dockerignore` (con un archivo de 200 MB) y el tiempo de parada con la forma shell y la forma exec. Usa los puertos 50021 y 50022.

## Qué instalar

Docker (con BuildKit, incluido en Docker Desktop), `curl` y `python3`.

## Cómo está organizada

Los Dockerfile de `api/` y `web/` van solos en esta carpeta: el código de la aplicación (`Servidor.java`, `index.html`, `default.conf.template`) es el de [`docker-kubernetes/app`](../../../app/), y `probar.sh` lo copia junto a ellos en una carpeta temporal para construir.

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
