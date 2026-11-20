
# Docker 06: análisis

Docker 29.9.3, Buildx 0.38.1 (builder `docker-container`), Trivy 0.76.2. En lugar de `ghcr.io` (que exige credenciales) se usa un registro local `registry:3` en `localhost:50900`; los nombres, etiquetas, plataformas y resúmenes se comportan igual. Las mediciones salen de `probar.sh`.

## Imagen base

| Imagen | Base | Por qué |
|---|---|---|
| `bitacora-api` | `eclipse-temurin:21-jdk-alpine` para construir; `alpine:3.22` + entorno de ejecución reducido con `jlink` para ejecutar | 74 MB; sin compilador; `sh` y `wget` para el `HEALTHCHECK`; sin `latest` |
| `bitacora-web` | `nginx:1.30-alpine` (argumento `BASE`) | base oficial pequeña; el 1.30 es la serie mantenida |

## Estrategia de etiquetas

Cada imagen se publica con **dos etiquetas**: la versión exacta (`1.0.1`, la que se despliega y nunca se reutiliza) y el **commit corto** (`fd26512`), que apunta al mismo resumen (comprobado). No hay `latest` ni etiquetas flotantes (`1.0`, `1`): en el registro solo existen versiones exactas y commits. La imagen lleva `org.opencontainers.image.revision` con el commit: `docker buildx imagetools inspect --format '{{json .Image}}'` lo muestra y `git cat-file -t` lo encuentra en el repositorio.

## Dos arquitecturas

`docker buildx build --platform linux/amd64,linux/arm64 --push`: `imagetools inspect` lista `linux/amd64` y `linux/arm64` bajo el mismo nombre (más dos entradas de atestación `unknown/unknown`). `probar.sh` borra la imagen local y la descarga con `docker run localhost:50900/bitacora-api:1.0.0`: arranca solo con nombre y versión.

## Vulnerabilidades (Trivy, solo HIGH y CRITICAL, plataforma amd64)

| Imagen | Con arreglo | Sin arreglo | Paquetes principales |
|---|---|---|---|
| api 1.0.0 y 1.0.1 | 0 | 0 | ninguno |
| web 1.0.0 (nginx 1.28-alpine) | 76 (3 críticas, 73 altas) | 0 | curl/libcurl (11 cada una), libexpat (11), libcrypto3 y libssl3 (7 cada una), libuuid (6) |
| web 1.0.1 (nginx 1.30-alpine) | 3 altas | 0 | libxslt (3) |

Clasificación de la web 1.0.0:

- **Se arreglan actualizando la base** (76 de 76 tenían versión corregida): la serie 1.28 de nginx lleva 7 meses sin reconstruirse, y pasar a 1.30 los elimina casi todos.
- **Sin arreglo todavía:** ninguno.
- **No afectan en este uso** (de los 3 restantes en 1.0.1): `libxslt` solo lo usa el módulo de transformación XSLT de nginx, que no está cargado en nuestra configuración; se registra y se revisa en cada reconstrucción. curl/libcurl no se invocan en tiempo de ejecución (el `HEALTHCHECK` usa `wget`).

## Trazabilidad real: de la imagen al commit que la reproduce

El historial del repositorio de la prueba tiene dos commits con cambios reales: el primero (`bitacora 1.0.0`) deja `ARG BASE=nginx:1.28-alpine` en `app/web/Dockerfile` y de él salen las imágenes 1.0.0, sin `--build-arg`; el segundo (`web 1.0.1: la base pasa de nginx 1.28 a 1.30`) cambia esa línea a `nginx:1.30-alpine` y de él sale la 1.0.1. La prueba afirma que `git show <revision>:app/web/Dockerfile` contiene la base de cada imagen, que la etiqueta `revision` de cada imagen apunta a su commit y que el nginx de dentro es 1.28.x en la 1.0.0 y 1.30.x en la 1.0.1. Así, el commit que etiqueta una imagen la reproduce, y el historial contiene el cambio que corrigió la vulnerabilidad. (El `Dockerfile` entregado en `repositorio/` está en el estado final: 1.30.)

## Ejecución de la variante amd64

El equipo es arm64, así que la variante amd64 solo puede ejecutarse **emulada** (la misma emulación con la que se construyó). La prueba descarga `bitacora-api:1.0.1` con `--platform linux/amd64`, la arranca con el entorno de `jlink`, comprueba que `/salud/listo` responde 200, que la imagen es `linux/amd64` y que `uname -m` dentro del contenedor da `x86_64`. Lo que no puede decir es el rendimiento ni el comportamiento en una máquina amd64 real.

## Corrección y versión nueva

Se publicó `web 1.0.1` desde un segundo commit que cambia la base a `nginx:1.30-alpine` (de 76 a 3 hallazgos altos o críticos) y `api 1.0.1` para igualar las versiones; `1.0.0` no se tocó (siguen ambas en el registro).

## Lista de componentes

`trivy image --format cyclonedx` genera `sbom/bitacora-api-1.0.1.cdx.json` (17 componentes) y `sbom/bitacora-web-1.0.1.cdx.json` (72). El día que aparezca una vulnerabilidad nueva basta buscar el paquete en esos archivos.

## Dos ambientes sin reconstruir

Con `repositorio/compose.yaml` y las variables `MENSAJE` y `VERSION`, dos proyectos (`-dev` en el 50901 y `-prod` en el 50902) usan la misma imagen (el ID de imagen coincide) con configuración distinta: `Hola, desarrollo` y `Hola, produccion`.

## Integración continua

`.github/workflows/publicar.yml`: al crear una etiqueta `v*.*.*` construye (una arquitectura, para analizar), **analiza antes de publicar** con Trivy (`severity: CRITICAL`, `ignore-unfixed: true`, `exit-code: 1`), genera la lista de componentes y solo entonces construye las dos arquitecturas y publica con la versión y el commit. Se autentica con el `GITHUB_TOKEN` de la ejecución: sin secretos en el repositorio. Validado con `actionlint`. La misma condición de la puerta, ejecutada con Trivy contra las imágenes reales: web 1.0.0 → código 1 (falla), web 1.0.1 → código 0. **No se ejecutó en GitHub Actions** (necesita ghcr.io y un repositorio de GitHub).

## Tercera imagen

Construir un frontend propio en varias etapas en lugar de `web/` depende de tu propio proyecto (Vite o Angular, Node, nginx sin privilegios): `probar.sh` no lo incluye. Los pasos de esta sesión (arquitecturas, etiquetas, análisis, versión nueva) son los mismos con cualquier frontend.
