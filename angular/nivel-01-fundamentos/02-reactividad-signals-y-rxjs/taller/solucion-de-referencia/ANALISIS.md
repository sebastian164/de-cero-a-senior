# Taller n1-02 — Reactividad: señales y RxJS

| # | Solución | Prueba |
|---|---|---|
| 1 | `carrito.store.ts`: `lineas` es la única señal escribible; `subtotal`, `iva`, `total` y `cantidadDeItems` son `computed` | `taller.spec.ts` › carrito |
| 2 | `effect` que escribe en `localStorage` (inyectado con un token para probarlo con un `Storage` en memoria) | `taller.spec.ts` › persiste |
| 3 | `flujos.ts › buscador` con `debounceTime(300)`, `distinctUntilChanged` y `switchMap` | Prueba con `TestScheduler`: 'te' tarda 10 veces más que 'tecl' y el resultado final es 'tecl'. En el navegador, `e2e/n1.spec.ts` pulsa "Simular carrera" contra el servidor simulado (latencia 1200 ms para 'te', 150 ms para 'tecl') |
| 4 | `guardarSinDobleEnvio` con `exhaustMap` | `TestScheduler`: cuatro clics, dos guardados. E2E: tres clics seguidos → una petición |
| 5 | El mismo `buscador` con `mergeMap` | `TestScheduler` reproduce la carrera: llega 'tecl' y después 'te' la pisa. E2E con el selector de operador en `mergeMap`: la pantalla termina mostrando "Resultados para: te" |

## 2. Por qué aquí sí corresponde un efecto

`localStorage` está fuera del grafo de señales de Angular. Escribir en él no produce un valor que otra parte de la aplicación lea como señal: es sincronizar con un sistema externo, que es exactamente el uso que el README asigna a `effect`. Si en lugar de eso se usara un `effect` para calcular `total` y guardarlo en otra señal, habría dos fuentes de verdad y un instante en que `total` está desactualizado.

## 6. Por qué `computed` es preferible a `effect` para derivar

Un `computed` es una función del estado: se calcula cuando alguien lo lee y se guarda en memoria hasta que cambian sus dependencias. No se puede olvidar actualizarlo, no tiene un instante intermedio con un valor viejo, y no escribe en ninguna parte. Un `effect` que copia un valor en otra señal corre después, en el ciclo de efectos: entre el cambio y el efecto la señal copiada miente, puede provocar ciclos (efecto que escribe una señal que otro efecto lee) y además hay que mantener dos señales donde bastaba una. En las pruebas se ve: el `computed` da el valor nuevo de inmediato; el efecto de persistencia necesita `TestBed.tick()` para ejecutarse.
