# 05 — JVM, memoria y recolección de basura

## Idea

En un restaurante, alguien retira los platos de las mesas que ya se desocuparon. Si un cliente se queda sentado con el plato vacío toda la noche, nadie lo retira: la mesa parece ocupada. La JVM hace lo mismo con la memoria: libera lo que ya nadie usa, pero no puede liberar lo que alguien sigue sosteniendo, aunque no lo necesite.

## Lo esencial

### Dónde vive cada cosa

| Zona | Contiene | Problema típico |
|---|---|---|
| **Pila** (*stack*), una por hilo | Variables locales, llamadas a métodos | `StackOverflowError` (recursión sin fin) |
| **Montón** (*heap*) | Todos los objetos | `OutOfMemoryError: Java heap space` |
| **Metaspace** | Metadatos de clases | `OutOfMemoryError: Metaspace` (cargadores de clases que no se liberan) |

### Recolección de basura

Un objeto es recolectable cuando no es alcanzable desde las raíces (pilas de los hilos, estáticos). La JVM no cuenta referencias: recorre el grafo.

**Hipótesis generacional:** la mayoría de los objetos mueren jóvenes. El montón se divide en generación joven (recolecciones frecuentes y baratas) y vieja (recolecciones menos frecuentes y más costosas).

| Recolector | Prioriza | Uso |
|---|---|---|
| G1 (por defecto) | Equilibrio entre pausas y rendimiento | La mayoría de servicios |
| ZGC | Pausas muy cortas con montones grandes | Baja latencia |
| Parallel | Rendimiento total | Procesos por lotes |
| Serial | Poca memoria | Contenedores pequeños |

### Fugas de memoria en Java

Existen: son objetos alcanzables que ya no se necesitan.

- Colecciones estáticas o cachés que solo crecen.
- Oyentes que nunca se desuscriben.
- `ThreadLocal` sin limpiar en hilos reutilizados de un pool.
- Recursos sin cerrar: conexiones, flujos.
- Claves en mapas con `equals`/`hashCode` defectuosos.

### Herramientas

| Herramienta | Para qué |
|---|---|
| `jcmd`, `jstat` | Estado de la JVM y del GC |
| Volcado de montón (*heap dump*) + Eclipse MAT o VisualVM | Qué ocupa la memoria y quién lo retiene |
| Volcado de hilos (*thread dump*) | Bloqueos e interbloqueos |
| Java Flight Recorder | Perfilado de bajo costo en producción |
| Registros de GC (`-Xlog:gc*`) | Frecuencia y duración de las pausas |

### Parámetros que hay que conocer

`-Xms` y `-Xmx` (montón inicial y máximo), `-XX:MaxRAMPercentage` (en contenedores, mejor que un valor fijo), `-XX:+HeapDumpOnOutOfMemoryError`.

## Ejemplo: una fuga

```java
class RegistroDeSesiones {
    private static final Map<String, Sesion> SESIONES = new HashMap<>();   // nunca se limpia

    static void registrar(Sesion sesion) {
        SESIONES.put(sesion.id(), sesion);     // cada inicio de sesión se queda para siempre
    }
}
// Corrección: expulsión por tiempo y tamaño (Caffeine), o eliminar al cerrar sesión.
```

## Errores comunes

- Aumentar `-Xmx` para "arreglar" una fuga: solo retrasa la caída.
- Cachés caseras sin límite.
- No configurar la memoria en contenedores y dejar que el orquestador mate el proceso.
- Optimizar sin medir.
- Crear objetos enormes de corta vida en bucles calientes.
- Llamar a `System.gc()`.

## En Spring

- Actuator expone métricas de memoria y GC (`/actuator/metrics/jvm.memory.used`) y permite generar volcados.
- Fugas clásicas: `ThreadLocal` de contexto que no se limpia, cachés `@Cacheable` sin expulsión con el proveedor por defecto, listas acumuladas en beans singleton.
- En Kubernetes, alinea el límite de memoria del contenedor con el montón más la memoria fuera del montón (metaspace, hilos, búferes directos).

## Taller

1. Escribe un programa que agote el montón con una colección que crece, ejecútalo con `-Xmx64m -XX:+HeapDumpOnOutOfMemoryError` y analiza el volcado: identifica el objeto raíz que retiene la memoria.
2. Provoca un `StackOverflowError` y explica por qué es distinto de un `OutOfMemoryError`.
3. Activa los registros de GC en un programa que cree muchos objetos y lee cuántas recolecciones hubo y cuánto duraron.
4. Graba una sesión de Java Flight Recorder y encuentra el método que más memoria asigna.
5. Describe cómo diagnosticarías un servicio en producción cuya memoria sube poco a poco durante tres días hasta reiniciarse.

Rama `taller/n6-05-jvm`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## Preguntas senior

1. ¿Cómo decide la JVM que un objeto es basura?
2. ¿Puede haber fugas de memoria en Java? Da tres ejemplos.
3. ¿Cómo eliges un recolector de basura?
4. Pasos para diagnosticar un `OutOfMemoryError` en producción.
5. ¿Qué memoria usa una JVM además del montón y por qué importa en contenedores?
6. Diferencia entre pila y montón, y qué se guarda en cada uno.
