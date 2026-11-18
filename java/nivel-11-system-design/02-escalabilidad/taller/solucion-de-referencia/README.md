# Solución de referencia: taller 11-02 (escalabilidad)

> **Intenta el taller antes de leer esto:** ver la respuesta primero convierte el taller en lectura. Esta carpeta es una solución de referencia para comparar con la tuya, no un punto de partida. Tu entrega va en `taller/solucion/`.

## Qué instalar

- Docker (Docker Desktop o Docker Engine con el plugin `compose`), con unos 4 GB de memoria disponibles.
- JDK 21 (la aplicación se compila con el `mvnw` incluido; Maven no hace falta).
- [k6](https://k6.io/) (`k6`), `jq`, `curl`, `python3` y `rsync`.

## Cómo ejecutarla y probarla

Desde esta carpeta:

```bash
./taller.sh
```

El script compila la aplicación `pedidos` (carpeta `aplicacion/`), construye su imagen y levanta con `compose.yaml` PostgreSQL (puerto 5432) y Redis (puerto 6379). Después carga 1.000.000 de pedidos y mide con k6: punto de saturación, índice, caché, una instancia frente a dos tras nginx (puertos 8090, 8091 y 8092), el problema de la sesión local y su corrección con Redis, una operación lenta movida a una cola y el particionamiento (`particionamiento.sql`). Tarda varios minutos.

**Al terminar, incluso si algo falla, lo baja todo**: contenedores, red, volúmenes e imagen. Termina con código 0 solo si todas las comprobaciones pasan. Si un puerto está ocupado: `PUERTO_PG=15432 PUERTO_REDIS=16379 PUERTO_APP1=18090 PUERTO_APP2=18091 PUERTO_NGINX=18092 ./taller.sh`. El prefijo de los nombres es `PROYECTO` (por defecto `n11-02`).

Para levantar y bajar PostgreSQL y Redis a mano:

```bash
docker compose -p n11-02 up -d --wait
docker compose -p n11-02 down -v
```

Las cifras exactas cambian de una máquina a otra; lo que debe repetirse son los órdenes de magnitud y las comprobaciones. Las decisiones y las respuestas a cada paso están en [`ANALISIS.md`](ANALISIS.md).
