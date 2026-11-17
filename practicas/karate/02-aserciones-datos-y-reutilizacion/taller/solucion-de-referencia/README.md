# Solución de referencia: taller Karate 02, aserciones, datos y reutilización

> Intenta el taller antes de leer esto: ver la respuesta primero convierte el taller en lectura.

Proyecto Maven autónomo con la suite de Karate 2.1.4 (53 escenarios en dos ambientes, `local` y `contenedor`), los esquemas compartidos (`esquemas.js`), los datos (`datos/notas-invalidas.json`) y el `compose.yaml` del ambiente contenedor. Tu propia entrega va en `taller/solucion/`.

## Ejecutarla y probarla

Necesita JDK 21, Maven, curl y Docker (Compose) para el ambiente `contenedor`.

```bash
./comprobar.sh
```

Arranca Bitácora en local (puerto 48080) y en un contenedor (puerto 48081), ejecuta la suite en cada ambiente cambiando solo `karate.env`, comprueba que no hay claves en el proyecto, el tiempo de `@humo`, el mensaje de `match each` y qué pasa al renombrar un campo de la API. Termina con código 0 solo si todo pasa; baja el contenedor y borra `target/`.

A mano, contra un ambiente en marcha: `mvn test -Dkarate.env=local -Dbitacora.clave=<clave>`.

Las respuestas a cada paso están en [ANALISIS.md](ANALISIS.md).
