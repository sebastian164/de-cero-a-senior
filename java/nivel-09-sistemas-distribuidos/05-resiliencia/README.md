# 05 — Resiliencia

## Idea

Un barco se divide en compartimientos estancos: si uno se inunda, se cierra y el barco sigue a flote. Y una instalación eléctrica tiene tacos: cuando hay un corto, se disparan y protegen el resto de la casa. Un sistema resiliente no es el que nunca falla, sino el que limita el daño cuando algo falla.

## Lo esencial

En un sistema distribuido, los fallos son la condición normal. La pregunta de diseño no es "¿y si falla?", sino "¿qué pasa **cuando** falle?".

### Patrones

| Patrón | Qué hace | Analogía |
|---|---|---|
| **Tiempo límite** | Deja de esperar | No quedarse eternamente en la línea |
| **Reintento** | Repite ante fallos transitorios | Volver a llamar |
| **Cortacircuitos** | Deja de llamar a un servicio que está fallando | El taco eléctrico |
| **Mamparo (*bulkhead*)** | Aísla recursos por dependencia | Compartimientos del barco |
| **Límite de tasa** | Rechaza el exceso de peticiones | Aforo máximo |
| **Respaldo (*fallback*)** | Respuesta alternativa degradada | Plan B |
| **Descarga de carga** | Rechaza trabajo cuando se está saturado | "No recibimos más pedidos por hoy" |

### Cortacircuitos

| Estado | Comportamiento |
|---|---|
| Cerrado | Las llamadas pasan; se cuentan los fallos |
| Abierto | Las llamadas fallan de inmediato, sin intentar |
| Semiabierto | Deja pasar unas pocas de prueba; si funcionan, cierra |

Protege a quien llama (no agota sus hilos esperando) y a quien es llamado (le da tiempo para recuperarse).

### Mamparo

Cada dependencia externa tiene su propio pool de hilos o su propio límite de llamadas concurrentes. Sin eso, un proveedor lento consume todos los hilos y la aplicación deja de atender incluso lo que no depende de él.

### Degradación elegante

Decidir de antemano qué se puede ofrecer cuando falta una pieza: si falla el servicio de recomendaciones, se muestra la página sin recomendaciones, no un error. Clasifica las dependencias en **críticas** (sin ellas no se puede atender) y **opcionales** (se puede atender peor).

### Contrapresión

Cuando llega más trabajo del que se puede procesar, hay tres opciones: acumularlo sin límite (y caer por memoria), rechazarlo, o hacer que el productor vaya más despacio. Las colas siempre deben tener límite.

## Ejemplo

```java
@CircuitBreaker(name = "recomendaciones", fallbackMethod = "sinRecomendaciones")
@Bulkhead(name = "recomendaciones")
@TimeLimiter(name = "recomendaciones")
public CompletableFuture<List<Producto>> recomendar(Long clienteId) {
    return CompletableFuture.supplyAsync(() -> cliente.recomendaciones(clienteId));
}

public CompletableFuture<List<Producto>> sinRecomendaciones(Long clienteId, Throwable causa) {
    return CompletableFuture.completedFuture(masVendidos.enCache());    // degradado, pero responde
}
```

El método de respaldo es `public` a propósito: con Resilience4j 2.4.0, uno `private` o de paquete falla de forma intermitente bajo concurrencia (`IllegalAccessException`, porque Resilience4j abre y vuelve a cerrar el acceso al método en cada invocación y un hilo puede cerrarlo mientras otro lo usa). Se reprodujo con 200 llamadas simultáneas cuyo destino falla.

```yaml
resilience4j:
  circuitbreaker.instances.recomendaciones:
    failure-rate-threshold: 50
    wait-duration-in-open-state: 20s
    sliding-window-size: 20
  bulkhead.instances.recomendaciones.max-concurrent-calls: 10
  timelimiter.instances.recomendaciones.timeout-duration: 800ms
```

## Errores comunes

- Respaldos que ocultan fallos graves: devolver una lista vacía de saldos no es degradar, es mentir.
- Cortacircuitos con umbrales sin calibrar: o nunca se abren o se abren por cualquier cosa.
- Reintentos detrás de un cortacircuitos abierto, o en varias capas a la vez.
- Sin métricas ni alertas sobre el estado de los cortacircuitos.
- Un único pool de hilos para todas las dependencias.
- Colas internas sin límite.
- No probar nunca los fallos: los caminos de error son el código menos ejecutado y el más importante.
- Comprobaciones de salud que tumban el servicio por una dependencia opcional.

## Taller

1. Crea un servicio que dependa de tres: uno crítico y dos opcionales. Clasifícalos y define el comportamiento ante la caída de cada uno.
2. Configura cortacircuitos, tiempo límite y mamparo. Observa las transiciones de estado en las métricas.
3. Sin mamparo, demuestra cómo una dependencia opcional lenta deja sin servicio una ruta que no la usa. Corrígelo.
4. Implementa un respaldo adecuado y otro engañoso, y explica la diferencia.
5. Agrega un límite de tasa por cliente y devuelve `429` con la cabecera `Retry-After`.
6. Haz un experimento de caos: apaga una dependencia en medio de una prueba de carga y describe qué observó el usuario.

Rama `taller/n9-05-resiliencia`.

## Preguntas senior

1. Explica los tres estados de un cortacircuitos.
2. ¿Qué problema resuelve un mamparo?
3. ¿Cuándo un respaldo es peligroso?
4. ¿Cómo se combinan tiempo límite, reintento y cortacircuitos, y en qué orden?
5. ¿Qué es la contrapresión?
6. ¿Cómo compruebas que tu sistema es resiliente antes de que falle en producción?
