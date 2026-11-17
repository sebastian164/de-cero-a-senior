# Solución de referencia: taller SDD 02, del plan a las tareas

> Intenta el taller antes de leer esto: ver la respuesta primero convierte el taller en lectura.

Los documentos de las puertas (`principios.md`, `aclaraciones.md`, `especificacion.md`, `plan.md`, `tareas.md`), el código de las etiquetas para Bitácora (`codigo/`), las pruebas de aceptación con Karate (`pruebas/`) y la versión de control sin especificación (`control/`). Tu propia entrega va en `taller/solucion/`.

## Ejecutarla y probarla

Necesita JDK 21, Maven, git y curl. Parte de `docker-kubernetes/app/api/Servidor.java` del propio curso (ruta relativa dentro del repositorio).

```bash
./comprobar.sh
```

En una carpeta temporal reconstruye el historial (puertas, pruebas en rojo y una tarea por commit), levanta el servidor en el puerto 48502, ejecuta las pruebas de aceptación tras cada tarea (de 4 de 29 a 29 de 29), una por criterio, y compara la versión de control con la suite. Termina con código 0 solo si todo se cumple; no deja archivos en esta carpeta.

Solo las pruebas de aceptación, contra un servidor que ya tengas en marcha: `cd pruebas && mvn test -Dbitacora.url=http://localhost:48502 -Dbitacora.clave=<clave>` (después borra `pruebas/target`).

Las respuestas a cada paso están en [ANALISIS.md](ANALISIS.md).
