# Taller 01: ¿reactivo o hilos virtuales para "confirmar pedido"?

Medido con `mvn -q compile exec:java` (JDK 21, Reactor 3.8 de Spring Boot 4.1.2, 18 núcleos), **después de calentar** (dos vueltas de 300 confirmaciones por versión antes de medir: sin calentar, la primera medida de Reactor daba 242 ms para 500 confirmaciones, 80 ms más que una sola, por carga de clases y compilación JIT, no por Reactor).
Confirmar = inventario 80 ms + pagos 150 ms + guardar 10 ms. Las tres versiones se miden en las dos formas: con inventario y pagos **en secuencia** (lo que hace un programa imperativo ingenuo) y **en paralelo** (Reactor: `Mono.zip`; hilos: la consulta de inventario en un segundo hilo virtual mientras el hilo de la confirmación consulta pagos). Así la comparación es entre cosas iguales.

| Escenario | Pool 50, secuencia | Pool 50, paralelo | Virtuales, secuencia | Virtuales, paralelo | Reactor (paralelo) |
|---|---|---|---|---|---|
| Una confirmación aislada (mediana de 15) | 243 ms | 164 ms | (no medido por separado) | (no medido por separado) | 161 ms |
| 500 confirmaciones | 2463 ms | 1635 ms | 243 ms | 162 ms | 163 ms |
| 5.000 confirmaciones | 24612 ms | 16472 ms | 251 ms | 183 ms | 204 ms |
| 500 con 30 ms de CPU (firma) | 3350 ms | 2227 ms | 1479 ms | 1399 ms | 1415 ms |
| 500 con solo 20 a la vez en "guardar" | 2504 ms | 1669 ms | 530 ms | 456 ms | 459 ms |

La confirmación aislada se midió de verdad con hilos (el programa mide una sola variante con hilos: una confirmación no distingue el pool de los hilos virtuales, por eso las dos columnas dicen "no medido por separado") y con Reactor (antes se decía "240 ms" para hilos sin haberlo medido): secuencia 243 ms, y en paralelo 164 ms con hilos y 161 ms con Reactor. La diferencia de 240 a 160 ms no es de Reactor: es de **paralelizar dos consultas independientes**, y los hilos también pueden.

## Qué cambia con 5.000
El pool de 50 escala linealmente (10 veces más peticiones, 10 veces más tiempo: 5000 x 240 ms / 50 = 24 s; con paralelo, 5000 x 160 ms / 50 = 16 s). Hilos virtuales y Reactor casi no se mueven (180-250 ms): esperar no ocupa un hilo de plataforma. Cambia más el pool, por el tope de 50 hilos dormidos.

## Con CPU
Con 30 ms de CPU por confirmación, 500 confirmaciones son 15 s de CPU repartidos en 18 núcleos (~0,85 s como mínimo). Reactor y virtuales se igualan (~1,4 s): los dos acaban limitados por los núcleos. La ventaja de "no bloquear" desaparece cuando el cuello es la CPU; el pool de 50 queda peor (3,3 s en secuencia, 2,2 s en paralelo) porque suma la espera.

## Con el límite de 20 en "guardar"
El límite se convierte en el cuello (500 x 10 ms / 20 = 250 ms de guardado en cola). Virtuales con `Semaphore` (456 ms en paralelo, 530 en secuencia) y Reactor con `flatMap` de concurrencia 20 (459 ms) quedan idénticos; el semáforo bloquea hilos virtuales (baratos), el `flatMap` encola sin hilos. El pool de 50 sigue limitado por sus 50 hilos. Ganan los dos modelos ligeros; con las dos consultas en paralelo en ambos, la diferencia entre ellos es de 3 ms.

## Recomendación (una página)
Para este servicio (E/S dominante: 240 ms de espera, 0-30 ms de CPU) recomendamos **MVC con hilos virtuales**:
- Con 500 y 5.000 confirmaciones, virtuales en paralelo (162/183 ms) igualan a Reactor (163/204 ms): no hay ganancia de rendimiento medida que justifique reescribir. Cuidado con el sobrecosto de arranque: sin calentar, Reactor parecía 80 ms peor; con calentamiento desaparece. Lo que se mide en frío es la JVM, no el modelo.
- El código es el imperativo de siempre (sin `Mono`, depuración y trazas normales). Poner dos consultas en paralelo con hilos cuesta una línea (`submit` y `get`), no un cambio de modelo.
- Con CPU (30 ms) o con límites (20 en guardar) las dos opciones siguen empatadas (1399 contra 1415 ms; 456 contra 459 ms).
- Lo único que Reactor ganó contra el programa ingenuo fue la latencia de una confirmación aislada (161 ms frente a 243 ms), por correr inventario y pagos en paralelo; los hilos también lo consiguen (164 ms).
- Elegiríamos WebFlux si necesitáramos contrapresión de extremo a extremo, flujos (SSE/streaming) o ya viviéramos en un ecosistema reactivo; no es el caso medido aquí.
