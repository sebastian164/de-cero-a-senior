# Solución de referencia: taller SDD 03, mantener la especificación viva

> Intenta el taller antes de leer esto: ver la respuesta primero convierte el taller en lectura.

Las herramientas (`herramientas/anotar.py` y `herramientas/deriva.sh`), los documentos de cambio (`cambio-005.md`, `cambio-006.md`), la especificación podada (`especificacion-final.md`) y la guía del equipo (`GUIA.md`). Tu propia entrega va en `taller/solucion/`.

## Ejecutarla y probarla

Necesita JDK 21, Maven, git, curl y Python 3. Parte de la solución de referencia de la sesión 02 (ruta relativa dentro del repositorio).

```bash
./comprobar.sh
```

En una carpeta temporal reconstruye el repositorio de las etiquetas y le aplica cada paso con commits reales (enlazar criterios con pruebas, el cambio 005 en orden, dos atajos, la urgencia, la detección de deriva y la poda), levantando el servidor en el puerto 48520. Termina con código 0 solo si todo se cumple, y comprueba que los documentos de esta carpeta coinciden con los que genera el recorrido. No modifica esta carpeta.

Las respuestas a cada paso están en [ANALISIS.md](ANALISIS.md).
