# Docker 05: análisis

Se levanta con `cp .env.ejemplo .env && docker compose up -d --build` (`probar.sh` usa `PUERTO_WEB=50051` y `-p ref-d05a` para no chocar con el 8080 de tu equipo). Sin ningún guion previo: `.env.ejemplo` trae valores de ejemplo para las dos claves.

## Qué hace cada pieza

- **api**: se construye de `app/api`; sin `ports`, así que solo se llega a ella por la red del proyecto, desde la web. Comprobación de salud con `start_period` de 30 s; 128 MB y 0,5 CPU de límite; volumen `notas` en `/datos`.
- **web**: se construye de `app/web`, publica `${PUERTO_WEB:-8080}`, corre como usuario 101 con `tmpfs` donde nginx necesita escribir y espera con `depends_on: condition: service_healthy`. Con `RETARDO_ARRANQUE_MS=15000` se observa: a los 3, 8 y 13 s la API está `starting` y la web aún `created`, sin levantar.
- **db** (perfil `datos`): PostgreSQL 17 con volumen `datos-db` y comprobación `pg_isready -h 127.0.0.1` (por TCP a propósito: el servidor temporal de la primera inicialización solo escucha en el socket y daría «sano» antes de tiempo). No publica puertos y la API no lo usa. Sin `--profile datos` no existe.

## Paso 6: la opción de secretos y sus límites

**Opción elegida:** secretos de Compose con origen `environment`. `CLAVE_API` y `CLAVE_DB` se definen en `.env` (en `.gitignore`); Compose monta cada una como archivo en `/run/secrets/<nombre>` dentro del contenedor, y `compose.yaml` solo nombra la variable. `app/api/entrada.sh` lee `/run/secrets/clave_api` y la exporta como `CLAVE_API` para el proceso de Java; la base usa `POSTGRES_PASSWORD_FILE`. Las opciones de la JVM están solo en ese guion de entrada, no repetidas en `compose.yaml`.

**Limitaciones (medidas en la prueba):**

- No aparece en `docker inspect` (`Config.Env`) ni en ningún archivo versionable: la prueba busca el valor aleatorio en `compose.yaml`, `.env.ejemplo`, `.gitignore` y `app/`.
- **Sí es visible** para quien pueda hacer `docker exec`: está en `/run/secrets/clave_api` y, como la imagen la exporta a Java, en `/proc/1/environ`. Un secreto de Compose no es un almacén seguro: evita que el valor viaje en el repositorio y en los metadatos de la imagen, nada más.
- Está en claro en `.env`, en el equipo de cada persona. En producción lo trae un gestor externo (Kubernetes Secret con cifrado en reposo, External Secrets, Vault).
- Los valores de `.env.ejemplo` son públicos: sirven para que un clon levante, nunca para un ambiente real.
- Alternativa descartada: secretos con `file:` (`clave_api.txt`). Evita que la variable pase por el entorno de `docker compose`, pero obliga a un guion previo para crear los archivos: un clon limpio no levantaba con una sola orden.

## Paso 9: por qué chocan dos copias

El archivo de inicio publica puertos fijos del anfitrión (`8080:8080` y `8081:8080`): dos proyectos distintos (`-p`) piden el mismo puerto y la segunda copia falla con `port is already allocated` (reproducido con 50053/50054). La solución quita el puerto de la API, parametriza el de la web (`${PUERTO_WEB:-8080}`) y no usa `container_name`, que también impediría las copias. Con `PUERTO_WEB=50052 docker compose -p ref-d05b up -d` conviven: los volúmenes llevan el nombre del proyecto (`ref-d05a_notas`, `ref-d05b_notas`), así que no comparten notas.

## Paso 11: `docker compose config` (clon limpio, sin perfil, `PUERTO_WEB` sin definir)

```yaml
name: ref-d05a
services:
  api:
    deploy:
      resources:
        limits:
          cpus: 0.5
          memory: "134217728"
    restart: unless-stopped
    environment:
      MENSAJE: Hola desde mi equipo
      RETARDO_ARRANQUE_MS: "0"
      VERSION: 1.0.0
    networks:
      default: null
    secrets:
      - source: clave_api
        target: /run/secrets/clave_api
    volumes:
      - type: volume
        source: notas
        target: /datos
        volume: {}
    build:
      context: <carpeta del proyecto>/app/api
      dockerfile: Dockerfile
    healthcheck:
      test:
        - CMD
        - wget
        - -q
        - -O
        - /dev/null
        - http://127.0.0.1:8080/salud/listo
      timeout: 3s
      interval: 5s
      retries: 3
      start_period: 30s
      start_interval: 1s
  web:
    restart: unless-stopped
    environment:
      DESTINO_API: http://api:8080
    networks:
      default: null
    tmpfs:
      - /var/cache/nginx:mode=1777
      - /run:mode=1777
      - /etc/nginx/conf.d:mode=1777
    user: 101:101
    build:
      context: <carpeta del proyecto>/app/web
      dockerfile: Dockerfile
    depends_on:
      api:
        condition: service_healthy
        required: true
    healthcheck:
      test:
        - CMD
        - wget
        - -q
        - -O
        - /dev/null
        - http://127.0.0.1:8080/
      timeout: 3s
      interval: 5s
      retries: 3
    ports:
      - mode: ingress
        target: 8080
        published: "8080"
        protocol: tcp
networks:
  default:
    name: ref-d05a_default
volumes:
  notas:
    name: ref-d05a_notas
secrets:
  clave_api:
    name: ref-d05a_clave_api
    environment: CLAVE_API
```
