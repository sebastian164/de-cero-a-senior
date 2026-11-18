# Taller 02: paso 7, los `Mono.just(...)` que quedan

Búsqueda ejecutada: `grep -rn "Mono.just\|block()\|doOnNext\|subscribe(" src/main`.

- `Mono.just(p)` en `ConfirmarPedido.confirmar`, rama "antifraude aprobó": el argumento es una variable ya calculada (el pedido), no hace trabajo; justificado.
- `Mono.just(...)` en los dobles de `src/test` (`new Reserva(...)`, `aprueba`, `new Cobro(...)`): construir un valor barato; el efecto (el contador) está dentro de `Mono.defer`, así que sucede al suscribirse, no al armar la cadena. La prueba `sinSuscribirNoSeReservaNiSeCobraNada` lo demuestra.
- Ningún `doOnNext` ni `subscribe` en `src/main`; no hay `block()` en `src/main` (solo en la prueba de paralelismo, con tiempo límite).
- Trampa evitada: `Mono.just(pagos.cobrar(p))` ejecutaría el cobro al armar la cadena, aunque nadie se suscriba. Se usa `Mono.defer`.
