# Solución de referencia: taller 11-04 (sistema de notificaciones)

> **Intenta el taller antes de leer esto:** ver la respuesta primero convierte el taller en lectura. Esta carpeta es una solución de referencia para comparar con la tuya, no un punto de partida. Tu entrega va en `taller/solucion/`.

## Qué instalar

Solo JDK 21. Maven no hace falta: se usa el `mvnw` incluido. No necesita Docker ni base de datos: es una simulación en memoria.

## Cómo ejecutarla y probarla

Desde esta carpeta:

```bash
./mvnw clean test
```

Compila el núcleo (`src/main/java/notificaciones/`) y ejecuta `NotificacionesTest`: una cola y un grupo de trabajadores por canal, límite de tasa por proveedor, prioridades, idempotencia, reintentos con retroceso, cola de fallidos, cortacircuitos, una campaña de 100.000 notificaciones y trabajadores muertos a mitad de envío. Termina con código 0 solo si todas las pruebas pasan. Para limpiar lo generado: `rm -rf target`.

Las decisiones y las respuestas a cada paso están en [`ANALISIS.md`](ANALISIS.md).
