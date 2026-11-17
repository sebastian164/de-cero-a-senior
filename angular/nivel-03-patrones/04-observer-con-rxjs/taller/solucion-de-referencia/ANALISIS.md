# Taller n3-04 — Observer con RxJS

| # | Solución | Prueba |
|---|---|---|
| 1 | `flujos.ts › autoguardado`: `debounceTime` + `distinctUntilChanged` + `concatMap`; reintentos y pausa como parámetros; `catchError` dentro del `concatMap` | `flujos.spec.ts` › 1 (azar determinista con `TestScheduler`: la primera escritura se recupera, la segunda agota los reintentos y emite `error` sin morir); E2E con la API que falla al azar |
| 2 | Formulario real (correo y mensaje obligatorios) cuyo envío pasa por `envioProtegido` (`exhaustMap`). El estado `enviando / enviado / error` se muestra en la página | `flujos.spec.ts` › 2 (tres clics seguidos, un solo envío) y › 2b (tras un envío fallido, el siguiente clic vuelve a enviar); E2E: tres clics, un solo POST en el servidor |
| 3 | `cargarTablero` (`forkJoin`) con `recomendaciones` opcional; `estadoTablero` añade `cargando / listo / error` | `flujos.spec.ts` › 3 (opcional que falla: se muestra sin ella) y › 3b (una obligatoria que falla: estado `error`, no una espera eterna); E2E con el 500 real de recomendaciones |
| 4 | `busquedaConCatchErrorAfuera` frente a `busquedaConCatchErrorAdentro` | `flujos.spec.ts` › 4: afuera, el flujo termina tras el primer fallo; adentro, sigue |
| 5 | `sondeo` + `visibilidadDe`: `timer` cada 10 s solo con la pestaña visible; `takeUntilDestroyed()` al salir de la pantalla; `catchError` dentro del `concatMap` | `flujos.spec.ts` › 5 (visible, oculta, visible) y › 5b (una consulta fallida no detiene el sondeo); E2E con `/api/n3-04/estado` en 500 |
| 6 | `conEspera` (`debounceTime` + `filter`) | `flujos.spec.ts` › 6 con tiempo simulado |

## Lo que el taller enseña y la primera versión de esta solución no cumplía

La lección dice "`catchError` va dentro del flujo interno". Una primera versión de esta solución ponía `envioProtegido` y `sondeo` sin ese manejo: un POST fallido terminaba el flujo y el botón quedaba muerto; una consulta fallida detenía el sondeo para siempre. Ahora:

- `envioProtegido` y `sondeo` convierten el error en un valor (`'error'` / `{ estado: 'error' }`) dentro del flujo interno y el exterior sigue vivo.
- La página muestra ese valor (`role="alert"` en el envío y en el tablero; "Última consulta: error" en el sondeo).
- Las pruebas fijan el comportamiento: `2b`, `3b` y `5b` con `TestScheduler`, y una E2E que pone el servidor en 500 (`POST /api/n3-04/_fallar`), comprueba el mensaje, restablece el servidor y comprueba que el mismo botón envía y que el sondeo cuenta su primera consulta buena 10 s después.

Se quitó además un `tap()` vacío que había en `conEspera`.

## Decisión sobre el estado de error

Un estado de error como valor del flujo (en vez de dejar propagar la excepción) obliga a la pantalla a tratarlo: el tipo `EstadoEnvio | EstadoTablero | ResultadoSondeo` no permite olvidarse del caso `error`.
