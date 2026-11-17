# Solución de referencia: taller Karate 03, mocks, paralelo y CI

> Intenta el taller antes de leer esto: ver la respuesta primero convierte el taller en lectura.

Proyecto Maven autónomo: la suite en paralelo, el simulador de Bitácora (`Simulador.java`) con su contrato, la prueba de carga (`CargaTest`), la web de nginx contra el simulador (`ci/`) y el flujo de GitHub Actions (`.github/workflows/karate.yml`). Tu propia entrega va en `taller/solucion/`.

## Ejecutarla y probarla

Necesita JDK 21, Maven, curl, Docker y Node.js. Para la parte de integración continua, además `act` y `actionlint` (si faltan, esa parte se omite; con `CON_ACT=1` falla).

```bash
./comprobar.sh              # todo, incluida la integración continua con act si está instalado
RAPIDO=1 ./comprobar.sh     # menos repeticiones de las corridas en paralelo
```

Ejecuta la suite con 1, 2, 4 y 8 hilos, cada `.feature` solo contra una API vacía, el contrato contra la API real y el simulador, la web de nginx contra el simulador, el flujo de CI (push, pull request y una API defectuosa) y la prueba de carga. Termina con código 0 solo si todo pasa; baja los contenedores y borra `target/`.

Las respuestas a cada paso, con la página para el equipo, están en [ANALISIS.md](ANALISIS.md).
