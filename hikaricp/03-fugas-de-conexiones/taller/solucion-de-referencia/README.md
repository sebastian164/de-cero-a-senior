# Solución de referencia: taller 03 (fugas de conexiones)

> **Intenta el taller antes de leer esto:** ver la respuesta primero convierte el taller en lectura. Esta es una solución de referencia, no la única válida.

Una aplicación Spring Boot con un camino que fuga una conexión (`conFuga`) y sus versiones corregidas (`try-with-resources`, transacción a medias, `JdbcTemplate`). `medir.sh` ejecuta los pasos 2 a 11 y las pruebas automáticas comprueban que la fuga se detecta y que no hay conexiones fuera.

## Cómo ejecutarla

Necesitas JDK 21, Maven, Docker, `k6`, `jq`, `curl` y Python 3. Desde esta carpeta:

```bash
./medir.sh
```

`medir.sh` compila, levanta PostgreSQL con `compose.yaml` (puerto 5432; cámbialo con `DB_PUERTO=<puerto>`), carga la aplicación con k6 (25 peticiones/s) y escribe `resultados/resultados.md`. Dura unos 4 minutos. Termina con código 0 solo si todos los criterios se cumplen.

Solo las pruebas automáticas (paso 11), con la base ya levantada por `medir.sh` o con `docker compose -p hikaricp-taller -f compose.yaml up -d --wait db`:

```bash
mvn -q -B test
```

Cuando termines, baja la base: `docker compose -p hikaricp-taller -f compose.yaml down -v`.

## Qué hay

- `src/main/java/taller/Clientes.java`: los caminos con fuga y corregidos.
- `src/test/java/taller/`: `SinFugasTest` (los extremos corregidos no dejan conexiones) y `DetectaFugaTest` (la misma comprobación falla con la fuga).
- `medir.sh` y `herramientas/` (`fuga.js` de k6, `serie.py`, `lib.sh`, `resumen.sh`).

El razonamiento y las cifras están en [ANALISIS.md](ANALISIS.md).
