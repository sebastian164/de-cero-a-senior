# Solución de referencia: taller ODD 02, trazas, métricas y objetivos

> Intenta el taller antes de leer esto: ver la respuesta primero convierte el taller en lectura.

`Servidor.java` (Bitácora instrumentada con OpenTelemetry: tramos manuales, métricas y contador de negocio), el entorno (`compose.yaml` con `grafana/otel-lgtm` y nginx con el módulo OpenTelemetry), el panel (`panel-presupuesto.json`) y la regla de alerta (`alerta-presupuesto.yaml`). Tu propia entrega va en `taller/solucion/`.

## Ejecutarla y probarla

Necesita JDK 21, Docker, curl, Python 3 y red la primera vez: el agente de OpenTelemetry 2.33.1 y la API 1.67.0 se descargan de Maven Central (con verificación SHA-256) a `~/.cache/de-cero-a-senior/opentelemetry` (cámbialo con `CACHE_OTEL`).

```bash
./comprobar.sh
```

Levanta el entorno (puertos 48300 a 48318), arranca la API con el agente, y comprueba con consultas a Grafana: trazas que cruzan nginx y la API, la relación registro-traza, las métricas, la explosión de series por una etiqueta de alta cardinalidad, media frente a percentiles, el SLI, el panel, el defecto de una escritura lenta de cada diez y la regla de alerta (con `promtool`). Tarda unos minutos. Termina con código 0 solo si todo pasa; baja los contenedores.

Las respuestas a cada paso están en [ANALISIS.md](ANALISIS.md).
