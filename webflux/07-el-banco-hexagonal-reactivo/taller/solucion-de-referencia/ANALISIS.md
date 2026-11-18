# Taller 07: servicio de pedidos hexagonal, opciones A, B y C (sobre el dominio de la tienda)

Spring Boot 4.1.2, PostgreSQL 18.7 (base `pedidos07`, puerto 5433), 4 hilos de bucle, pools de 10 (Hikari y r2dbc-pool). Una sola aplicación, tres configuraciones (`--opcion=a|b|c`). Pruebas: `mvn test` (19).

## El dominio no se copió ni se tocó
El pedido es el de la tienda del módulo hexagonal: `hexagonal/08-pruebas-y-limites/taller/solucion-de-referencia/src/tienda/dominio` (`Pedido`, `LineaDePedido`, `Dinero`, `Existencias`, `Reserva`, `EstadoDePedido`, `ReglaDeNegocioViolada`: 264 líneas) más `ConflictoDeConcurrencia` y `PedidoNoEncontrado` de su capa de aplicación. El `pom.xml` lo añade como carpeta de fuentes con `build-helper-maven-plugin`, igual que el ejemplo `despues` de la sesión, y el compilador solo incluye esos 9 archivos. Este proyecto no tiene ningún archivo `tienda/` propio (lo comprueba `ArquitecturaTest`). La carpeta del dominio de la tienda no se modifica.

## Qué hay de nuevo
- **Puertos y servicio, dos veces**: `aplicacion/sincrona` (puertos `RepositorioDePedidos`, `Inventario`, `UnidadDeTrabajo` y `ServicioDePedidos`) y `aplicacion/reactiva` (los mismos, con `Mono`). Los puertos guardan el agregado (`guardar(pedido, estadoLeido)`, que lanza `ConflictoDeConcurrencia` si en la base el estado ya no es el leído), no hacen `marcarConfirmado`. El servicio **no decide nada**: llama a `pedido.confirmar()` y a `Reserva.reservar(...)` y deja que el dominio diga que no. Por eso confirmar dos veces da 422 con el mensaje del dominio ("solo se confirma un pedido pendiente; m1 está CONFIRMADO") y no un `"YA_CONFIRMADO"` inventado por la aplicación.
- **Adaptadores**: JDBC y R2DBC reconstruyen el agregado con `Pedido.existente(...)` y toman `select ... for update` de la fila de existencias, en orden de producto (sin abrazos mortales).
- **Entrada**: 3 controladores WebFlux (A, B, C), un `Resumen` y las respuestas de error (404, 409 por conflicto, 422 por regla de negocio).

## Paso 1: las tres variantes funcionan igual
El mismo conjunto de `curl` (confirmar m1, repetir, pedido inexistente, consultar, sin existencias de `prod-3`) da en A, B y C: 200 con el resumen, 422 (ya confirmado), 404, 200, 422 (`no hay existencias suficientes de prod-3`) y el pedido queda `PENDIENTE`. Comparadas con `diff`, las tres salidas son idénticas.

## Pasos 2 a 4: la fila de existencias de `prod-5` queda bloqueada 5 s (`select ... for update; pg_sleep(5)` desde otra sesión), 4 confirmaciones esperan y se consulta otro pedido (`GET /pedidos/m300`)
| Opción | Confirmaciones | Consulta ajena | Recursos mientras esperan |
|---|---|---|---|
| A: puertos síncronos + JDBC en el bucle | ~5,0 s | **4,48 s** (esperó al bloqueo) | los 4 hilos del bucle dormidos + 4 conexiones |
| B: el mismo núcleo en `boundedElastic` | ~5,0 s | **6 ms** | 4 hilos de `boundedElastic` dormidos (5 vivos en total) + 4 conexiones |
| C: puertos reactivos + R2DBC | ~5,0 s | **5 ms** | 0 hilos; solo 4 conexiones y 4 suscripciones |
B mejora porque el bloqueo ya no ocurre en el bucle: cada petición en espera consume ahora un hilo de `boundedElastic` (tope por defecto: 10 hilos por núcleo) además de la conexión. Con miles de peticiones esperando, B agota hilos o cola; C solo agota el pool de conexiones, que es un límite que existe en las tres.

## Paso 5
`ArquitecturaTest` (4 pruebas): el dominio de la tienda no nombra `reactor.` ni `org.springframework` en **ningún lugar del texto** (no solo en los `import`, así un nombre totalmente calificado tampoco se cuela); la aplicación reactiva importa `reactor.core.publisher` y no nombra Spring; la síncrona no nombra ninguno; y el proyecto no tiene dominio propio.

## Paso 6: lo que costó la opción C
| | A/B (síncrono) | C (reactivo) | Diferencia |
|---|---|---|---|
| Aplicación: archivos / líneas | 4 / 79 | 4 / 71 | +4 archivos, +71 líneas de un servicio paralelo |
| Adaptador de salida: archivos / líneas | 1 / 38 | 1 / 38 | +1, +38 |
| Dominio de la tienda | 9 archivos, solo los de `tienda/dominio` y 2 excepciones | los mismos 9 | 0 (sin cambios, mismos archivos) |
| Pruebas del servicio con dobles en memoria | `AplicacionSincronaTest`: 6 pruebas, 3 dobles, 67 líneas | `AplicacionReactivaTest`: 6 pruebas, 3 dobles, 66 líneas | **las 6 pruebas y los 3 dobles síncronos no sirven para C**: hubo que reescribirlas (66 líneas) |
| Pruebas que siguen sirviendo | `DominioTest` (3), `ArquitecturaTest` (4), `Memoria` y `Datos` (instantánea y reversión) | las mismas | las del dominio y la arquitectura pasan sin tocarse |
Archivos nuevos para pasar de A a C: 4 de `aplicacion/reactiva` y el adaptador R2DBC (5 archivos, 109 líneas) más 66 líneas de pruebas reescritas, además de lo de A, que sigue sirviendo para A y B. El código es casi del mismo tamaño porque el caso de uso es corto; lo que cambia es **cómo se escribe**: el bucle `for` se vuelve `Flux.concatMap` con `flatMap` anidados, la unidad de trabajo pasa a recibir un `Supplier<Mono<T>>`, y las trazas son más difíciles. Y si A y C conviven durante una migración, cada cambio de negocio se escribe dos veces (en la medición, 79 + 71 líneas de lógica duplicada).

## Decisión (paso 7)
Para este servicio **B** (o A con hilos virtuales, si el equipo está en MVC): A es inaceptable (la consulta ajena esperó 4,48 s). B y C dan la misma latencia a la consulta ajena (6 y 5 ms); C ahorra los hilos de `boundedElastic` en espera, pero eso solo importa con cientos de peticiones bloqueadas a la vez, y se paga con 71 líneas de servicio paralelo, un adaptador reescrito, 66 líneas de pruebas reescritas y código reactivo en toda la aplicación. Elegiríamos C solo si el número de esperas simultáneas pudiera superar el tope de `boundedElastic`, o si el equipo ya trabaja en reactivo.
