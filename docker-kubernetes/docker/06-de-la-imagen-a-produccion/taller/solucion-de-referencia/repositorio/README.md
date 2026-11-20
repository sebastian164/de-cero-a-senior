# Bitácora: publicación de imágenes

Dos imágenes (`bitacora-api`, `bitacora-web`) para `linux/amd64` y `linux/arm64`, publicadas con versión y con el commit que las produjo.

## Levantarlo en cinco minutos

Requisitos: Docker con Compose. No hace falta clonar nada más ni construir.

```bash
export REGISTRO=ghcr.io/USUARIO          # el registro donde se publicó
export VERSION_IMAGEN=1.0.1 VERSION_IMAGEN_WEB=1.0.1
MENSAJE="Hola, desarrollo" VERSION=dev-1.0.1 PUERTO_WEB=8081 docker compose -p bitacora-dev up -d --wait
curl http://localhost:8081/api/info
```

Un segundo ambiente con la misma imagen y otra configuración:

```bash
MENSAJE="Hola, producción" VERSION=1.0.1 PUERTO_WEB=8082 docker compose -p bitacora-prod up -d --wait
```

Para apagar: `docker compose -p bitacora-dev down -v`.

## De la imagen al código

```bash
docker buildx imagetools inspect $REGISTRO/bitacora-api:1.0.1       # plataformas y resumen
docker buildx imagetools inspect $REGISTRO/bitacora-api:1.0.1 --format '{{json .Image}}' | grep revision
```

La etiqueta `org.opencontainers.image.revision` es el commit; la etiqueta de la imagen con el mismo nombre que el commit corto apunta a lo mismo.

## Publicar una versión nueva

`git tag v1.0.2 && git push --tags`: `.github/workflows/publicar.yml` construye, analiza (falla con vulnerabilidades críticas que ya tengan arreglo), genera la lista de componentes y publica. Nunca se reutiliza una etiqueta.

## Lista de componentes

`sbom/` guarda la lista de componentes (CycloneDX) de cada versión publicada.
