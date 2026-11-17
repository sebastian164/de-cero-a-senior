# Solución de referencia: taller 02 (la configuración que importa)

> **Intenta el taller antes de leer esto:** ver la respuesta primero convierte el taller en lectura. Esta es una solución de referencia, no la única válida.

Una aplicación Spring Boot 4.1.1 con HikariCP 7.1.0 y Actuator, y `medir.sh`, que ejecuta los pasos 1 a 10 con k6 (50 usuarios) y comprueba cada criterio de aceptación: valores por defecto, barrido de tamaños del pool, la base limitada a un núcleo, tres instancias que no caben en el límite, la base caída, sesiones ociosas y `minimum-idle`. La configuración final está en `config-final.yaml`.

## Cómo ejecutarla

Necesitas JDK 21, Maven, Docker, `k6`, `jq`, `curl` y Python 3. Desde esta carpeta:

```bash
./medir.sh              # completo
RAPIDO=1 ./medir.sh     # versión corta
```

`medir.sh` compila, levanta PostgreSQL con `compose.yaml` (puerto 5432; cámbialo con `DB_PUERTO=<puerto>`), lanza las instancias, mide y escribe `resultados/resultados.md` y `resultados/grafica.svg`. Termina con código 0 solo si todos los criterios se cumplen.

- **Completo:** entre 12 y 30 minutos según la máquina. Cada punto de medida dura 15 s y los pasos 7 (la base caída 35 s, dos veces) y 8 (75 s de reposo, dos veces) esperan de verdad.
- **`RAPIDO=1`:** unos 5 minutos. Cada punto dura 5 s, el barrido usa los tamaños 2, 5 y 28, y se saltan los pasos 7 y 8.

Cuando termines, baja la base: `docker compose -p hikaricp-taller -f compose.yaml down -v`.

## Qué hay

- `src/main/`: la aplicación y su `application.yaml`.
- `config-final.yaml`: la configuración que sale de la cuenta del paso 6.
- `medir.sh`, `herramientas/` (`carga.js` de k6, `lib.sh`, `resumen.sh`) y `grafica.py`.

El razonamiento y las cifras están en [ANALISIS.md](ANALISIS.md).
