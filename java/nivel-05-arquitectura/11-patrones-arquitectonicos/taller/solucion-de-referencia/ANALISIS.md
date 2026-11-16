# Taller 05-11 — Patrones arquitectónicos (solución)

Se implementaron las dos opciones en Java puro (`mvn test`: 6 pruebas): una saga orquestada de tres pasos con compensación (`patrones.saga`) y una cuenta bancaria con Event Sourcing (`patrones.eventsourcing`).

## Escenarios

| # | Patrón elegido | Alternativa más simple considerada | Por qué |
|---|---|---|---|
| 1 | CQRS de nivel bajo: réplica de lectura de PostgreSQL y consultas del tablero contra ella, con vistas materializadas para lo más pesado | Índices y optimizar las consultas | Se intenta primero; si las consultas son analíticas (agregaciones sobre meses) los índices no bastan y el bloqueo persiste. Bases separadas por eventos se descartan: el retraso de segundos de la réplica es aceptable para un tablero |
| 2 | Outbox: el evento se inserta en una tabla `outbox` en la misma transacción que el pedido; un publicador la lee y envía a bodega. Consumidor idempotente | Publicar después del `commit` con reintentos | Si el proceso muere entre el `commit` y la publicación, el evento se pierde igual. Es exactamente el problema de la doble escritura |
| 3 | Strangler Fig con una fachada delante y capa anticorrupción hacia el sistema viejo | Reescritura total con fecha de corte | La reescritura total congela el negocio y suele fracasar; con la fachada se migra factura por factura (primero un tipo de cliente) y se puede volver atrás |
| 4 | Saga orquestada (vuelo → hotel → carro) con compensaciones | Transacción distribuida (2PC) | Los tres son servicios (y a menudo proveedores externos) que no ofrecen 2PC; la orquestación da un lugar donde ver el estado del viaje. Implementada abajo |
| 5 | Backend for Frontend para la app móvil, que compone las nueve llamadas en una | Agregar un endpoint "inicio" a la API general | El endpoint ad hoc sirve si solo hay una pantalla así; con varias pantallas móviles, la API general se llena de formas que solo usa el móvil |
| 6 | Event Sourcing para los movimientos de la cuenta, con proyecciones de saldo (CQRS) | Tabla de movimientos + saldo, con auditoría por disparadores | La tabla de movimientos ya es casi Event Sourcing y puede bastar; se elige ES completo porque la entidad debe **demostrar** cómo se llegó al saldo y los eventos son inmutables. Implementado abajo |
| 7 | Circuit Breaker (Resilience4j) con tiempo límite y alternativa, más *bulkhead* para aislar el pool de hilos de ese proveedor | Solo tiempos límite | El tiempo límite evita esperar para siempre, pero sigue llamando a un proveedor caído y ocupando hilos; el cortacircuitos deja de llamar y responde de inmediato |

## Saga orquestada (`patrones.saga`)

- `Paso<C>`: transacción local + compensación, ambas idempotentes (reservar dos veces no duplica; liberar dos veces no falla).
- `Orquestador`: ejecuta en orden; si un paso falla, compensa los anteriores **en orden inverso**. Cada compensación se reintenta tres veces; si sigue fallando queda como "PENDIENTE MANUAL" en la bitácora, porque una compensación que no se puede completar no se puede ignorar.
- Pruebas: camino feliz; falla el carro → se liberan hotel y vuelo en ese orden; compensación que se recupera al tercer intento y otra que queda para revisión manual.

## Event Sourcing (`patrones.eventsourcing`)

- Eventos `CuentaAbierta`, `Depositado`, `Retirado` (`sealed` + `record`); `aplicar` con `switch` exhaustivo.
- `desde(historia)` reconstruye; `saldoAl(historia, fecha)` responde el escenario 6: saldo en cualquier fecha pasada.
- Pruebas: el ejemplo del README (100 + 50 − 30 = 120), saldo a tres fechas y que las reglas se evalúan sobre el estado reconstruido.
