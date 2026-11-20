# Docker 06: de la imagen a producción (solución de referencia)

> **Intenta el taller antes de leer esto: ver la respuesta primero convierte el taller en lectura.**

Crea un repositorio con dos commits, construye y publica las imágenes para `linux/amd64` y `linux/arm64`, las analiza con Trivy, publica la versión 1.0.1 corregida, genera las listas de componentes, levanta dos ambientes con Compose y valida el flujo de integración continua. Usa los puertos 50900 a 50905.

## Qué instalar

Docker con Buildx y emulación de arquitecturas (Docker Desktop la trae; en Linux, `docker run --privileged --rm tonistiigi/binfmt --install all`), Git, [Trivy](https://trivy.dev), [actionlint](https://github.com/rhysd/actionlint), `curl` y `python3`. Necesita acceso a internet (bases de datos de Trivy e imágenes).

## Cómo está organizada

La entrega del taller (el pequeño repositorio con `app/`, `compose.yaml`, `sbom/`, `README.md` y `.github/workflows/publicar.yml`) está en [`repositorio/`](repositorio/). El código de la aplicación sale de [`docker-kubernetes/app`](../../../app/): `probar.sh` hace una copia de `repositorio/`, le añade ese código y trabaja ahí, de modo que no modifica nada de esta carpeta. En lugar de `ghcr.io` usa un registro local `registry:3` en el puerto 50900 (no necesita credenciales); no ejecuta el flujo de GitHub Actions, solo lo valida con `actionlint`. El paso 9 (frontend propio en varias etapas) depende de tu proyecto y no se incluye.

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
