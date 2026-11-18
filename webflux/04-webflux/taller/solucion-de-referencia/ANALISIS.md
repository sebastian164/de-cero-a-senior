# Taller 04: servicio de pedidos con WebFlux

Spring Boot 4.1.2 (WebFlux, Reactor Netty), JDK 21. Puertos: app 8080, inventario simulado 8081, pagos simulado 8082 (`pedidos.ServiciosSimulados` los levanta con `java -cp target/classes pedidos.ServiciosSimulados inventario 8081 300`). Pruebas: `mvn test` (9 pruebas con `WebTestClient` y los servicios simulados dentro de la JVM).

## Tiempos límite (paso 2)
- Inventario, 1 s: responde en unos 300 ms; el triple absorbe un mal momento y una petición nunca espera más.
- Pagos, 2 s: un cobro es más lento y la reserva ya está hecha; cortar demasiado pronto deja reservas huérfanas. Pasado el límite es mejor un 503 que un cliente colgado.

## Errores con `ProblemDetail` (paso 3), con `curl`
| Caso | Respuesta |
|---|---|
| pedido inexistente | 404 `{"detail":"no existe el pedido no-existe","status":404,"title":"Not Found"}` |
| sin existencias | 409 `{"detail":"sin existencias para el pedido sin-stock","status":409,"title":"Conflict"}` |
| pago rechazado | 422 `{"detail":"pago rechazado para el pedido rechazado","status":422,"title":"Unprocessable Content"}` |
| pagos caído o lento | 503 `{"detail":"el servicio de pagos no está disponible o tardó demasiado","status":503,...}` |
Todas con `Content-Type: application/problem+json`. Excepciones propias: `PedidoNoEncontrado`, `SinExistencias`, `PagoRechazado`, `ServicioExternoNoDisponible`.

## SSE (paso 4)
`curl -N /pedidos/p7/eventos` abierto antes de confirmar recibe `RESERVADO`, `COBRADO`, `CONFIRMADO` como eventos `estado`, en ese orden y mientras ocurren (hay 300 ms entre cada uno). Un `Sinks.many().multicast().directBestEffort()` por pedido; al terminar (confirmado o fallido) se completa y se retira del mapa. La prueba `sseMuestraLosEstadosEnOrden` lo comprueba.

## Mediciones (pasos 5, 6 y 7): 4 hilos de bucle, 40 confirmaciones simultáneas, servicios en 300 ms
| Variante | Total 40 confirmaciones | Resultado | `GET /pedidos/p1` durante la carga |
|---|---|---|---|
| 5. WebClient | 622 ms (inventario 300 + pagos 300 + cola) | 40 x 200 | media 2,1 ms, máx 8 ms |
| 6. RestClient en pagos | 1248 ms | 12 x 200, 28 x 503 | media 63,4 ms, máx 916 ms |
| 7. WebClient con pagos detenido a mitad | 347 ms | 40 x 503 | media 2,1 ms, máx 6 ms |

- Paso 5 contra 6: con `RestClient` cada cobro duerme uno de los 4 hilos del bucle 300 ms; 40 cobros en 4 hilos son 3 s de bucle ocupado, y mientras tanto no se atiende nada (el GET llegó a esperar 916 ms) ni se leen las respuestas del inventario: expiran sus límites y 28 de 40 terminan en 503. Se volvió a dejar `WebClient` (`pagos.cliente=webclient`, el valor por omisión).
- Paso 7: con el servicio de pagos detenido (conexión rechazada) la respuesta es 503 en ~0,3 s (el inventario sí respondió). Con el servicio **congelado** (`kill -STOP`: acepta la conexión y no contesta) la respuesta es 503 en 2,32 s: 0,3 s de inventario más el límite de 2 s de pagos. Ninguna petición cuelga más que la suma de sus límites. Las consultas `GET` siguieron en 2 ms.
