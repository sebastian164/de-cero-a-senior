# Solución de referencia: taller 11-03 (acortador de URLs)

> **Intenta el taller antes de leer esto:** ver la respuesta primero convierte el taller en lectura. Esta carpeta es una solución de referencia para comparar con la tuya, no un punto de partida. Tu entrega va en `taller/solucion/`.

## Qué instalar

- Docker (Docker Desktop o Docker Engine con el plugin `compose`).
- JDK 21 (Maven no hace falta: se usa el `mvnw` incluido).
- [k6](https://k6.io/) (`k6`), `jq`, `curl` y `python3`.

## Cómo ejecutarla y probarla

Desde esta carpeta:

```bash
./taller.sh
```

Levanta PostgreSQL con `compose.yaml` (puerto 5432), ejecuta las pruebas con `./mvnw clean package` (Base62, comparación de estrategias de generación de códigos, clics asíncronos sin pérdidas, alias, expiración y validaciones, contra PostgreSQL real), y después mide con k6 las redirecciones con y sin caché (aplicación en el puerto 8080). **Al terminar lo baja todo** (`docker compose down -v`, procesos y `target/`). Termina con código 0 solo si todo pasa.

Si un puerto está ocupado: `PUERTO_PG=15432 PUERTO_APP=18080 ./taller.sh`.

Para ejecutar solo las pruebas, con PostgreSQL ya levantado (`docker compose -p n11-03 up -d --wait`):

```bash
./mvnw clean test
```

y al terminar `docker compose -p n11-03 down -v`. Para arrancar el servicio a mano en local, con el perfil de desarrollo (clave pública, nunca en producción): `./mvnw package -DskipTests && java -jar target/acortador-1.0.0.jar --spring.profiles.active=desarrollo`.

Las decisiones y las respuestas a cada paso están en [`ANALISIS.md`](ANALISIS.md).
