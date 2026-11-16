# Taller 06-04 — Concurrencia (solución)

`mvn test` ejecuta 5 pruebas (unos 55 s, casi todo el pool fijo del punto 4). Resultados de una ejecución con JDK 21.0.13 en Apple Silicon:

## 1. Pérdida de incrementos

10 hilos × 1.000.000 de incrementos con `valor++`: **5.096.059 de 10.000.000**; se perdió casi la mitad. Tres correcciones, todas dan 10.000.000 exactos: `synchronized`, `AtomicInteger.incrementAndGet()` y `LongAdder` (la mejor bajo mucha contención: reparte el conteo en celdas y suma al leer).

## 2. Interbloqueo diagnosticado con `jcmd`

`Interbloqueo` arranca como **proceso aparte**; la prueba espera un segundo, ejecuta `jcmd <pid> Thread.print` y verifica el diagnóstico. Salida real (recortada):

```
Found one Java-level deadlock:
"transferencia-1":
  waiting to lock monitor ... (a java.lang.Object), which is held by "transferencia-2"
"transferencia-2":
  waiting to lock monitor ... (a java.lang.Object), which is held by "transferencia-1"
...
	at concurrencia.Interbloqueo.transferir(Interbloqueo.java:15)
	- waiting to lock <0x...ff0>
	- locked <0x...fe0>
Found 1 deadlock.
```

Corrección: los dos hilos toman los recursos en el **mismo orden global** (rompe la condición de espera circular). Con el argumento `corregido` el proceso termina en menos de un segundo.

## 3. `CompletableFuture` con tres servicios

300, 500 y 800 ms en paralelo, combinados con `thenCombine` y `orTimeout(2 s)`: **805 ms** (en serie serían 1.600). La prueba exige entre 795 y 1.000 ms.

## 4. 10.000 tareas de 1 segundo

| Ejecutor | Tiempo |
|---|---|
| `newVirtualThreadPerTaskExecutor()` | **1.045 ms** |
| `newFixedThreadPool(200)` | **50.302 ms** (10.000 / 200 = 50 tandas) |

Mientras duermen, los hilos virtuales liberan su hilo portador; el pool fijo tiene 200 hilos de plataforma ocupados esperando. La ventaja es solo para tareas que esperan; en cómputo intensivo no hay ganancia.

## 5. Error real en un `@Service` con atributo mutable

`ServicioConEstado` simula un bean singleton que guarda `clienteActual` en un atributo para usarlo más abajo en el mismo método. Con 8 hilos y 20.000 peticiones, **1.123 respuestas saludaron al cliente de otra petición**.

En producción se manifestaría así: en pruebas locales (una petición a la vez) nunca falla; bajo carga, un usuario ve de vez en cuando datos de otro (un nombre, un saldo, un PDF ajeno), sin error en los registros y sin poder reproducirlo. Es además una fuga de datos personales. Corrección: el estado de la petición va en variables locales o en parámetros; el bean solo guarda dependencias inmutables.
