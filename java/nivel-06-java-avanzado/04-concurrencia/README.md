# 04 — Concurrencia

## Idea

Una cocina con un solo cocinero es simple: hace una cosa a la vez. Con cinco cocineros se produce más, pero si dos toman el mismo sartén o los dos creen que el otro ya echó la sal, el plato sale mal. La concurrencia da capacidad a cambio de tener que coordinar el acceso a lo compartido.

## Lo esencial

### Los tres problemas

| Problema | Qué es |
|---|---|
| **Condición de carrera** | El resultado depende del orden en que se intercalan los hilos |
| **Visibilidad** | Un hilo no ve el cambio que hizo otro |
| **Interbloqueo (*deadlock*)** | Dos hilos se esperan mutuamente para siempre |

`contador++` no es atómico: son tres pasos (leer, sumar, escribir). Dos hilos pueden leer el mismo valor y perder un incremento.

### Herramientas, de mejor a peor opción

1. **No compartir estado mutable.** Objetos inmutables, estado local al hilo, cada petición con sus propios objetos. Es la solución que no falla.
2. **Estructuras concurrentes:** `ConcurrentHashMap`, `AtomicLong`, `LongAdder`, `CopyOnWriteArrayList`, `BlockingQueue`.
3. **Abstracciones de alto nivel:** `ExecutorService`, `CompletableFuture`.
4. **Bloqueos explícitos:** `synchronized`, `ReentrantLock`. Correctos pero fáciles de usar mal.
5. **`volatile`:** garantiza visibilidad, no atomicidad. Sirve para banderas.

### Ejecutores

No crees hilos con `new Thread`. Usa un pool:

```java
try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
    Future<Reporte> futuro = pool.submit(() -> generarReporte());
    Reporte reporte = futuro.get(5, TimeUnit.SECONDS);
}
```

### `CompletableFuture`

Componer tareas asíncronas sin bloquear:

```java
CompletableFuture<Cliente> cliente = CompletableFuture.supplyAsync(() -> clientes.buscar(id), pool);
CompletableFuture<List<Pedido>> pedidos = CompletableFuture.supplyAsync(() -> ordenes.de(id), pool);

Resumen resumen = cliente.thenCombine(pedidos, Resumen::new)
        .orTimeout(2, TimeUnit.SECONDS)
        .exceptionally(error -> Resumen.vacio())
        .join();
```

### Hilos virtuales (Java 21)

Hilos muy ligeros gestionados por la JVM. Permiten el estilo "un hilo por petición" con código bloqueante y miles de tareas concurrentes de E/S:

```java
try (var ejecutor = Executors.newVirtualThreadPerTaskExecutor()) {
    ejecutor.submit(() -> llamarServicioExterno());
}
```

Sirven para tareas que esperan E/S. No aceleran el cómputo intensivo, y no se agrupan en pools.

## Ejemplo: la carrera y su corrección

```java
class ContadorInseguro {
    private int valor;
    void incrementar() { valor++; }            // se pierden incrementos
}

class ContadorSeguro {
    private final AtomicInteger valor = new AtomicInteger();
    void incrementar() { valor.incrementAndGet(); }
}

// Operación compuesta: comprobar y luego actuar NO es atómico
if (!mapa.containsKey(clave)) mapa.put(clave, crear());      // carrera
mapa.computeIfAbsent(clave, c -> crear());                   // atómico
```

## Errores comunes

- Estado mutable en beans singleton.
- `SimpleDateFormat`, `HashMap` o `ArrayList` compartidos entre hilos.
- Operaciones "comprobar y actuar" sin atomicidad.
- Adquirir dos bloqueos en órdenes distintos (interbloqueo).
- Pools sin límite o colas infinitas: agotan la memoria.
- Tragar `InterruptedException` sin restaurar el estado de interrupción.
- Bloquear dentro de un pool compartido como el `ForkJoinPool.commonPool`.

## En Spring

- Cada petición HTTP corre en un hilo del servidor; los beans singleton se comparten entre todos.
- `@Async` ejecuta en otro hilo: configura siempre un `TaskExecutor` propio y recuerda que pasa por un proxy (no funciona en autoinvocación).
- `@Transactional` y el contexto de seguridad están ligados al hilo: no se propagan solos a tareas asíncronas.
- `spring.threads.virtual.enabled=true` activa hilos virtuales en Spring Boot 3.2 o superior.
- Bloqueo optimista con `@Version` para concurrencia sobre datos (Nivel 8).

## Taller

1. Demuestra la pérdida de incrementos con 10 hilos y un contador inseguro. Corrígelo de tres formas.
2. Provoca un interbloqueo con dos recursos y dos hilos. Diagnostícalo con un volcado de hilos (`jstack` o `jcmd Thread.print`) y corrígelo.
3. Con `CompletableFuture`, consulta tres servicios simulados (300, 500 y 800 ms) en paralelo y combina resultados; el total debe rondar 800 ms.
4. Lanza 10.000 tareas que duermen un segundo, con pool fijo de 200 hilos y con hilos virtuales. Compara tiempos.
5. Describe un error de concurrencia real en un `@Service` con un atributo mutable y cómo se manifestaría en producción.

Rama `taller/n6-04-concurrencia`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## Preguntas senior

1. Diferencia entre `synchronized`, `volatile` y las clases atómicas.
2. ¿Qué es una condición de carrera y por qué es difícil de reproducir?
3. Cuatro condiciones para un interbloqueo y cómo romper cada una.
4. ¿Qué cambian los hilos virtuales y qué no?
5. ¿Cómo dimensionas un pool de hilos para tareas de CPU y para tareas de E/S?
6. ¿Por qué un bean singleton con estado es un error de concurrencia?
