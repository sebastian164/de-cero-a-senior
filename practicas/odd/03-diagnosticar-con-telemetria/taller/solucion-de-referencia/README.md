# Solución de referencia: taller ODD 03, diagnosticar con telemetría

> Intenta el taller antes de leer esto: ver la respuesta primero convierte el taller en lectura.

El simulacro de cinco fallos: `Servidor.java` (la API con fallos activables por configuración), `trafico.sh` (tráfico continuo por nginx), `diagnosticar.py` (quien diagnostica: solo habla con Prometheus y Tempo a través de Grafana), `alertas.yaml`, `otelcol.yaml` (muestreo por cola) y el entorno (`compose.yaml`, `nginx.conf`). Tu propia entrega va en `taller/solucion/`.

## Ejecutarla y probarla

Necesita JDK 21, Docker, curl, Python 3 y red la primera vez: el agente de OpenTelemetry 2.33.1 y la API 1.67.0 se descargan de Maven Central (con verificación SHA-256) a `~/.cache/de-cero-a-senior/opentelemetry` (cámbialo con `CACHE_OTEL`).

```bash
./comprobar.sh
```

Levanta el entorno (puertos 48400 a 48420) con tres copias de la API detrás de nginx, sortea el orden de los cinco fallos (semilla 7, `SEMILLA=n` la cambia), diagnostica cada uno solo con la interfaz de observabilidad, cierra los huecos de instrumentación, dispara una alerta de verdad en Grafana, reproduce la alerta mala de CPU y repite el simulacro con muestreo por cola. Tarda varios minutos. Termina con código 0 solo si todo pasa; baja los contenedores y los procesos.

Las respuestas a cada paso están en [ANALISIS.md](ANALISIS.md).
