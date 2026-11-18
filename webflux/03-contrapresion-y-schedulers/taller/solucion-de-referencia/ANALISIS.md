# Taller 03: bloqueo en el bucle y contrapresión

Mediciones del programa de la solución (JDK 21, BlockHound 1.0.18, Reactor 3.8; se lanza con `mvn -q compile exec:exec`). Bucle simulado: `Schedulers.newParallel("bucle", 2)`; inventario y antifraude (20 ms) entregan su respuesta en el bucle, como Netty.

## Pasos 1-2: `Mono.just(pagos.cobrar(p))` con `Thread.sleep(150)`
20 confirmaciones y un ping a la vez: total 2213 ms; el ping esperó 913 ms (en tres ejecuciones: 606, 913 y 1985 ms, según el hilo del bucle al que le toque; hacen cola 20 x 150 ms entre dos hilos = 1,5 s por hilo). El `Mono.just(...)` evalúa el cobro al armar la cadena, dentro del hilo del bucle.

## Paso 3: BlockHound
Con `mvn test -Dpagos=apurado` (estado "antes") la prueba `confirmarConLaSuscripcionEnElBucleNoBloquea` FALLA:

    expected: onNext(); actual: onError(reactor.blockhound.BlockingOperationError: Blocking call! java.lang.Thread.sleep0)

(En JDK 21 el método marcado es `Thread.sleep0`.) Tests run: 2, Failures: 1.

## Paso 4: `fromCallable` + `boundedElastic`
Misma prueba, `mvn test` sin la propiedad: 8 pruebas, 0 fallos (BUILD SUCCESS). Mediciones: total 182 ms; espera del ping 0 ms.

## Paso 5: 500 confirmaciones
Total 490 ms, ping 0 ms, 500 confirmadas, y se usaron 180 hilos `boundedElastic-*`: es el tope por defecto, 10 x núcleos (18). Con 5.000 no se crearían más hilos: el tope de hilos es 180; las demás tareas esperan en una cola de hasta 100.000 tareas (`Schedulers.DEFAULT_BOUNDED_ELASTIC_QUEUESIZE`). Al llenarse la cola el scheduler rechaza: `ReactorRejectedExecutionException: Task capacity of bounded elastic scheduler reached` (reproducido con un scheduler pequeño de 2 hilos y 3 tareas: 5 de 5 tareas extra rechazadas, llegan como señal de error). Con 5.000 confirmaciones de 150 ms: 5000/180 x 150 ms = ~4,2 s en total, sin rechazos (5.000 << 100.000).

## Paso 6: contrapresión
Avisos de existencias a ~500/s (`Flux.interval(2 ms)`, 2 s = 1000 avisos), consumidor de 5 ms (200/s):
- Sin estrategia: falla al primer aviso que no puede entregar (`Could not emit tick 1 due to lack of requests`), procesados 1. Inaceptable.
- `onBackpressureBuffer`: procesa los 1000 sin perder ninguno, pero tarda 6084 ms y la cola crece sin límite (memoria) mientras el proveedor sigue.
- `onBackpressureLatest`: procesa 330 de 1001 (descarta 671) y termina a los 2010 ms; el último valor procesado (1000) es el último emitido (1000).

Con un único producto ("el último valor") la conclusión parece obvia, pero el caso del enunciado son avisos de **varios** productos. Se modeló con 20 productos rotando (el aviso `t` es del producto `t % 20` y su valor es `t`; el criterio de negocio: al terminar, el valor procesado de cada producto debe ser el último que se emitió de ese producto), sobre la misma carga:
- `onBackpressureLatest` GLOBAL (un solo "último"): procesa unos 340 de ~1000, y **12 de los 20 productos (en las dos ejecuciones medidas) terminan con un valor viejo**: cuando llegan avisos de otros productos, el último aviso de A se descarta aunque nadie volvió a emitir nada de A. El conteo anterior (último procesado = último emitido) solo probaba que el *último de todos* llegó, no que cada producto quedara al día.
- `groupBy(producto)` + `onBackpressureLatest` por grupo (conflación por clave): procesa unos 360 de ~1000 y **0 de 20 productos con valor viejo**; termina a los ~2200 ms. Se descartan los avisos intermedios *del mismo producto*, que ya estaban obsoletos, y nunca el último de un producto.
Elección de negocio: los avisos de existencias son estado absoluto ("quedan N unidades"), no deltas: solo importa el último valor **de cada producto**, así que la estrategia es la conflación por clave, no `onBackpressureLatest` global. Si fueran deltas (-1, -1...) perder uno corrompería el inventario y habría que usar buffer acotado + reconciliación, o `onBackpressureBuffer(max)` con error explícito.
