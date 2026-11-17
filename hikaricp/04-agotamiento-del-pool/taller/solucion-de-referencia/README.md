# Solución de referencia: taller 04 (agotamiento del pool)

> **Intenta el taller antes de leer esto:** ver la respuesta primero convierte el taller en lectura. Esta es una solución de referencia, no la única válida.

Una aplicación Spring Boot con JPA y un pool de 5 que se agota de cuatro maneras (llamada externa dentro de la transacción, vista abierta, consulta lenta, bloqueos) y cómo se corrige cada una. `medir.sh` ejecuta los pasos 2 a 10 con k6 y un servicio de pagos de pega (`herramientas/Externo.java`).

## Cómo ejecutarla

Necesitas JDK 21, Maven, Docker, `k6`, `jq`, `curl` y Python 3. Desde esta carpeta:

```bash
./medir.sh
```

`medir.sh` compila, levanta PostgreSQL con `compose.yaml` (puerto 5432; cámbialo con `DB_PUERTO=<puerto>`), crea una tabla de 6 millones de filas para el paso 8 y escribe `resultados/resultados.md`. Dura unos 5 minutos (algo más la primera vez, por la tabla grande). Con `DURACION=10s ./medir.sh` cada escenario dura la mitad. Termina con código 0 solo si todos los criterios se cumplen. El taller no tiene pruebas JUnit: sus criterios los comprueba `medir.sh`.

Cuando termines, baja la base: `docker compose -p hikaricp-taller -f compose.yaml down -v`.

## Qué hay

- `src/main/java/taller/`: `Extremos` (`/v1/...` con todo en una transacción, `/v2/...` con la llamada fuera, la vista, el historial y el bloqueo), `ConfirmarPedido`, `VistaDePedido` y `ServicioDePagos`.
- `medir.sh` y `herramientas/` (`carga.js` de k6, `Externo.java`, `lib.sh`, `resumen.sh`).

El razonamiento y las cifras están en [ANALISIS.md](ANALISIS.md).
