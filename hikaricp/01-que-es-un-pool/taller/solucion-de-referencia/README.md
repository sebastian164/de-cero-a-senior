# Solución de referencia: taller 01 (qué es un pool de conexiones)

> **Intenta el taller antes de leer esto:** ver la respuesta primero convierte el taller en lectura. Esta es una solución de referencia, no la única válida.

Los nueve pasos del taller como pruebas JUnit contra PostgreSQL 17 con `max_connections=30`: coste de abrir conexiones, HikariCP sin Spring, 50 hilos contra 30 conexiones, la undécima conexión, el registro en DEBUG, dos pools por error y cerrar el pool.

## Cómo ejecutarla

Necesitas JDK 21, Maven y Docker. Desde esta carpeta:

```bash
docker compose -p hikaricp-taller -f compose.yaml up -d --wait db
mvn -q -B test
```

Dura menos de un minuto (el paso 6 espera 30 s a que venza el `connectionTimeout`). Las pruebas escriben sus números en `resultados/resultados.md`. Si tu PostgreSQL escucha en otro puerto: `DB_PUERTO=<puerto>` al levantar la base y `-Ddb.puerto=<puerto>` en `mvn`.

Cuando termines, baja la base: `docker compose -p hikaricp-taller -f compose.yaml down -v`.

## Qué hay

- `src/main/java/taller/Pasos.java` y `SinCerrar.java`: el código de cada paso.
- `src/test/java/taller/Taller01Test.java`: las pruebas, una por paso.
- `compose.yaml`: la base del entorno ([`../../../entorno/`](../../../entorno/)).

El razonamiento y las cifras están en [ANALISIS.md](ANALISIS.md).
