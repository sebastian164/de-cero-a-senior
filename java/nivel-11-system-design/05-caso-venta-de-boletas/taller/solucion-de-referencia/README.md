# Solución de referencia: taller 11-05 (venta de boletas)

> **Intenta el taller antes de leer esto:** ver la respuesta primero convierte el taller en lectura. Esta carpeta es una solución de referencia para comparar con la tuya, no un punto de partida. Tu entrega va en `taller/solucion/`.

## Qué instalar

- Docker (Docker Desktop o Docker Engine con el plugin `compose`).
- JDK 21 (Maven no hace falta: se usa el `mvnw` incluido).

## Cómo ejecutarla y probarla

Desde esta carpeta:

```bash
./taller.sh
```

Levanta PostgreSQL con `compose.yaml` (puerto 5432), ejecuta `./mvnw clean test` (1.000 hilos compitiendo por 100 asientos con tres estrategias, expiración de reservas, pago tardío con reembolso, sala de espera y la transacción SQL del README) y **lo baja todo al terminar** (`docker compose down -v` y `target/`). Termina con código 0 solo si todas las pruebas pasan. La prueba `elSqlDeLaTransaccionEsElDelReadme` lee el README de la sesión, así que esta carpeta debe seguir dentro del repositorio del curso.

Si el puerto 5432 está ocupado: `PUERTO_PG=15432 ./taller.sh`.

Para levantar y bajar PostgreSQL a mano y ejecutar solo las pruebas:

```bash
docker compose -p n11-05 up -d --wait
./mvnw clean test
docker compose -p n11-05 down -v
```

Las decisiones y las respuestas a cada paso están en [`ANALISIS.md`](ANALISIS.md).
