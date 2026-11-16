# Taller 06-05 — JVM, memoria y recolección de basura (solución)

`mvn test` ejecuta 4 pruebas; tres de ellas lanzan **otra JVM** con las opciones del enunciado y analizan lo que produce. JDK 21.0.13 (Homebrew), Apple Silicon.

## 1. `OutOfMemoryError` con volcado y su análisis

Programa: el ejemplo del README hecho ejecutable (`RegistroDeSesiones`: un `HashMap` estático que nunca se limpia, 100 KB por sesión). Ejecutado con `-Xmx64m -XX:+HeapDumpOnOutOfMemoryError`:

```
java.lang.OutOfMemoryError: Java heap space
Dumping heap to target/fuga.hprof ...
Heap dump file created [67580876 bytes in 0.013 secs]
```

Análisis del volcado. Eclipse MAT no se pudo instalar en el entorno donde se preparó esta solución (la instalación con `brew install --cask memoryanalyzer` no se autorizó), así que el volcado se analiza con `AnalizadorHprof`, un lector propio del formato HPROF 1.0.2 (≈200 líneas). Construye el grafo de objetos y calcula, para cada campo estático, la memoria alcanzable desde él. Salida real:

```
Clases que más ocupan:
  byte[]                          9.076 instancias     64.242.071 bytes
  java.lang.String                8.371 instancias        251.130 bytes
Campos estáticos con más memoria alcanzable:
  jvm.RegistroDeSesiones.SESIONES  63.998.114 bytes en 3.123 objetos
  java.lang.invoke.MethodType.internTable  413.809 bytes en 7.473 objetos
```

**Raíz que retiene la memoria:** el campo estático `RegistroDeSesiones.SESIONES` (raíz de GC por ser estático de una clase cargada por el cargador de la aplicación), que alcanza unos 61 MB de los 64 MB: el `HashMap` → sus `Node` → `Sesion` → `byte[]`. La prueba exige que esa sea la primera raíz y que retenga más de 55 MB. En MAT se vería igual en *Leak Suspects* o en el árbol de dominadores, con tamaño retenido en lugar de alcanzable (coinciden cuando una sola raíz domina la fuga, como aquí).

## 2. `StackOverflowError`

Recursión sin caso base: en esta máquina, con la pila por defecto, llegó a **37.783** marcos antes de desbordar. Diferencias con `OutOfMemoryError`:

| | `StackOverflowError` | `OutOfMemoryError: Java heap space` |
|---|---|---|
| Qué se agota | La pila de **un** hilo (marcos de llamadas) | El montón **compartido** por todos los hilos |
| Causa típica | Recursión sin fin o demasiado profunda | Objetos alcanzables que no se liberan, o montón chico para la carga |
| Después | Al desenrollar se libera la pila; la JVM sigue sana (la prueba asigna memoria después) | Cualquier hilo puede fallar; el estado de la aplicación queda incierto |
| Se ajusta con | `-Xss` | `-Xmx`, `-XX:MaxRAMPercentage` |

## 3. Registros de GC

`MuchaBasura` crea 3.000.000 de `int[64]` de vida corta y algunos `int[1024]` que sobreviven; se ejecuta con `-XX:+UseG1GC -Xmx256m -Xlog:gc:file=target/gc.log`. Resultado: **6 pausas jóvenes, 2,80 ms en total, la más larga 0,67 ms**, sin recolecciones mixtas ni completas:

```
[0.024s][info][gc] GC(0) Pause Young (Normal) (G1 Evacuation Pause) 24M->1M(256M) 0.672ms
[0.035s][info][gc] GC(1) Pause Young (Normal) (G1 Evacuation Pause) 112M->1M(256M) 0.385ms
```

`112M->1M`: casi todo era basura joven, como predice la hipótesis generacional. Hallazgo del ejercicio: la primera versión del programa (con el arreglo temporal que no salía del bucle) produjo **una sola** pausa, porque el JIT aplicó *escape analysis* y eliminó la asignación. Para medir el GC hay que hacer que los objetos escapen.

## 4. Java Flight Recorder

`Asignaciones` corre 4 segundos con `-XX:StartFlightRecording=filename=target/asignaciones.jfr,settings=profile`. La prueba lee la grabación con `jdk.jfr.consumer.RecordingFile`, suma el peso de los eventos `jdk.ObjectAllocationSample` por el primer marco del código propio y obtiene:

```
jvm.Asignaciones.armarReporte       11.690.781.736 bytes muestreados
jvm.Asignaciones.idsDelLote            626.067.696 bytes muestreados
jvm.Asignaciones.calcularTotales        16.772.832 bytes muestreados
```

El método que más asigna es `armarReporte` (cadenas y `StringBuilder` que crece), que la prueba exige que quede primero. En JDK Mission Control es la vista *Memory → Allocations by method*.

## 5. Memoria que sube durante tres días

1. **Confirmar que es el montón:** métricas de Actuator `jvm.memory.used{area=heap}` tras cada GC (no el uso instantáneo) y `jvm.gc.pause`. Si lo que sube es el RSS y no el montón: metaspace (`jvm.memory.used{area=nonheap}`), hilos, búferes directos (`jvm.buffer.memory.used`) o memoria nativa (`-XX:NativeMemoryTracking=summary` + `jcmd VM.native_memory`).
2. **Ver la tendencia del mínimo:** si el montón después de cada recolección completa sube día a día, hay objetos que se acumulan.
3. **Dos volcados separados en el tiempo** (`jcmd <pid> GC.heap_dump` o el endpoint `heapdump` de Actuator, en una réplica sacada del balanceador) y comparar histogramas: la clase que más crece entre ambos.
4. **Árbol de dominadores** en MAT del segundo volcado: quién retiene esa clase hasta una raíz (un estático, un `ThreadLocal`, una caché, un listener).
5. **Mientras tanto:** `-XX:+HeapDumpOnOutOfMemoryError` con ruta en un volumen persistente, límites de memoria alineados con `MaxRAMPercentage` y alerta por tendencia, no por umbral.
6. **Corregir y verificar** con la misma métrica: el mínimo tras GC debe quedar plano.
