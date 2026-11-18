# Solución de referencia: taller 02 (contenedores y Docker)

> **Intenta el taller antes de leer esto:** ver la respuesta primero convierte el taller en lectura. Esta carpeta es una solución de referencia para comparar con la tuya, no un punto de partida. Tu entrega va en `taller/solucion/`.

## Qué instalar

- Docker (Docker Desktop o Docker Engine con el plugin `compose`), con unos 4 GB de memoria disponibles.
- [Trivy](https://trivy.dev/) (`trivy`): la primera vez descarga sus bases de vulnerabilidades (unos 3 GB) en `~/.cache/curso-trivy`; se cambia con `TRIVY_CACHE`.
- `curl`, `jq`, `python3` y `rsync`.
- No hace falta instalar Java ni Maven: la aplicación se compila dentro de Docker.

## Cómo ejecutarla y probarla

Desde esta carpeta:

```bash
./taller.sh
```

Construye las dos imágenes (una etapa y varias etapas), y levanta con Compose la aplicación, PostgreSQL y Kafka (`compose.yaml`, puerto 8080). Después arranca contenedores sueltos para las pruebas de memoria y de configuración (puertos 8081, 8082 y 8083). **Al terminar, incluso si algo falla, lo baja todo**: `docker compose down -v`, contenedores, red e imágenes que creó.

Termina con código 0 solo si todas las comprobaciones pasan. Si un puerto está ocupado, cámbialo: `PUERTO_COMPOSE=18080 PUERTO_MEM=18081 PUERTO_PRUEBAS=18082 PUERTO_PRODUCCION=18083 ./taller.sh`. El prefijo de los nombres de contenedores es `PROYECTO` (por defecto `n10-02`).

Para levantar y bajar el Compose a mano:

```bash
CLAVE_BD=local docker compose -p n10-02 up -d --build --wait
CLAVE_BD=local docker compose -p n10-02 down -v
```

Las decisiones y las respuestas a cada paso están en [`ANALISIS.md`](ANALISIS.md).
