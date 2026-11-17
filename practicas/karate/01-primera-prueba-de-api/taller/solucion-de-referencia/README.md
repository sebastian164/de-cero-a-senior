# Solución de referencia: taller Karate 01, la API de Bitácora

> Intenta el taller antes de leer esto: ver la respuesta primero convierte el taller en lectura.

Proyecto Maven autónomo con la suite de Karate 2.1.4 para la API de Bitácora (27 escenarios, más `@arranque` y `@falla`). Tu propia entrega va en `taller/solucion/`.

## Ejecutarla y probarla

Necesita JDK 21, Maven y curl. La suite apunta por defecto a `http://localhost:48080` y la clave llega por parámetro (nunca vive en los `.feature`).

```bash
./comprobar.sh
```

Compila y arranca Bitácora (`docker-kubernetes/app/api/Servidor.java`, ruta relativa dentro del curso) en el puerto 48080, ejecuta la suite completa dos veces, un escenario por nombre, solo `@humo`, el fallo provocado y el arranque lento, y termina con código 0 solo si todo pasa. Borra `target/` al terminar.

Contra una Bitácora que ya tengas en marcha:

```bash
mvn test -Dbitacora.clave=<clave> [-Dbitacora.url=http://localhost:8080] [-Detiquetas=@humo] [-Descenario="nombre"]
```

Las respuestas a cada paso están en [ANALISIS.md](ANALISIS.md).
