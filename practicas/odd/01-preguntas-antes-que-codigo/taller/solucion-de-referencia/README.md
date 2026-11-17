# Solución de referencia: taller ODD 01, preguntas antes que código

> Intenta el taller antes de leer esto: ver la respuesta primero convierte el taller en lectura.

`PREGUNTAS.md` (las diez preguntas, escritas antes del código), `Servidor.java` (Bitácora con eventos JSON, `X-Correlacion`, `motivo` y `nota.guardada`) y la plantilla de nginx (`default.conf.template`, `compose-web.yaml`) que genera el identificador de correlación. Tu propia entrega va en `taller/solucion/`.

## Ejecutarla y probarla

Necesita JDK 21, curl, jq y Docker (solo para nginx).

```bash
./comprobar.sh
```

Compila y arranca `Servidor.java` en el puerto 48100, genera tráfico variado, comprueba que cada línea es JSON válido, que no aparece ningún contenido de nota ni clave, responde las diez preguntas con `jq` y comprueba la correlación por nginx (puerto 48101) y el 503 por no estar listo. Termina con código 0 solo si todo pasa; baja el contenedor.

Las respuestas a cada paso están en [ANALISIS.md](ANALISIS.md).
