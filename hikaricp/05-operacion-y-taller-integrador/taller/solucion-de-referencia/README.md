# Solución de referencia: taller 05 (operación y taller integrador)

> **Intenta el taller antes de leer esto:** ver la respuesta primero convierte el taller en lectura. Esta es una solución de referencia, no la única válida.

Una aplicación Spring Boot con la cuenta del pool (`application.yaml`), las alertas (`alertas.yaml`), el panel de Grafana (`panel.json`), el simulacro de siete situaciones (`simulacro.sh`), la prueba de las alertas contra Prometheus y Grafana reales (`prometheus.sh`) y la parte con PgBouncer. `medir.sh` lo ejecuta todo y comprueba los criterios.

## Cómo ejecutarla

Necesitas JDK 21, Maven, Docker, `k6`, `jq`, `curl` y Python 3. Desde esta carpeta:

```bash
./medir.sh              # completo
RAPIDO=1 ./medir.sh     # versión corta
```

`medir.sh` compila, levanta PostgreSQL con `compose.yaml` (puerto 5432; cámbialo con `DB_PUERTO=<puerto>`), arranca hasta seis instancias, lanza contenedores de Prometheus, Grafana y PgBouncer y escribe `resultados/resultados.md`. Termina con código 0 solo si todos los criterios se cumplen. Descarga imágenes de Docker la primera vez.

- **Completo:** entre 15 y 50 minutos según la máquina. Provoca las siete situaciones durante un minuto (la caída de la base, 100 s), repite las dos que más costaron y cierra con PgBouncer.
- **`RAPIDO=1`:** unos 8 a 10 minutos. Provoca solo tres situaciones (fuga, retención y conexiones cortadas) y no repite el paso 7. Las alertas, el panel y PgBouncer se comprueban igual.

`./prometheus.sh` y `./simulacro.sh <situación>...` también se pueden ejecutar por separado una vez compilada la aplicación (`mvn -q -B package -DskipTests`) y con la base levantada. Este taller no tiene pruebas JUnit: sus criterios los comprueba `medir.sh`.

Cuando termines, baja la base: `docker compose -p hikaricp-taller -f compose.yaml down -v`.

## Qué hay

- `src/main/`: la aplicación y su `application.yaml` con la cuenta.
- `alertas.yaml`, `panel.json`: lo que entregas para operar el pool.
- `medir.sh`, `simulacro.sh`, `prometheus.sh` y `herramientas/` (`carga05.js` de k6, `panel.py` y `diagnostico.py`, que graban y leen el panel, `Externo.java`, `Preparadas.java`, `lib.sh`).

El razonamiento, la guía de guardia y las cifras están en [ANALISIS.md](ANALISIS.md).
