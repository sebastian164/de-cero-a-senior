# Kafka 07 — Bandeja de salida transaccional, con medidas

Código en `src/main/java/outbox/` (base PostgreSQL 17, broker real) y `OutboxTest` (9 pruebas). Mediciones en `salida/evidencia.txt` (la escriben las pruebas al ejecutarse).

| Paso | Resultado |
|---|---|
| 2. Doble escritura (antes) | Kafka detenido al crear el pedido: pedido en la base, 0 eventos. Proceso muerto entre guardar y publicar: dos pedidos sin evento "y nadie lo sabe" |
| 3. Publicar primero | la base falla después: evento de un pedido que **no existe** |
| 4. Bandeja | un pedido bueno y dos transacciones fallidas: 1 pedido y 1 fila de salida; nunca una sin la otra |
| 5. Kafka caído | pedido aceptado, publicador sin éxito, filas pendientes; tras tres caídas con 10 pedidos cada una: 31 pedidos, 31 eventos en el tema, 0 filas pendientes |
| 6. Publicador muerto tras publicar y antes de marcar | el tema tiene 2 mensajes con el mismo id y el consumidor idempotente aplicó 1 efecto: la bandeja da *al menos una vez*, no una |
| 7. Dos publicadores | con `SELECT` sin bloqueo: 2000 mensajes en el tema (1000 duplicados); con `FOR UPDATE SKIP LOCKED`: 1000 mensajes, 1000 distintos |
| 8. Orden por clave | publicador correcto: `[PedidoCreado, PedidoConfirmado, PedidoCancelado]`, y con dos publicadores a la vez 0 pedidos con el orden alterado. Publicador que se salta la fila fallida: `[Creado, Cancelado, Confirmado]` (el cancelado sale antes que el confirmado). Regla: no publicar una fila si hay una anterior de la misma clave sin publicar |
| 9. Intervalo de sondeo | 1000 ms: retraso confirmación a llegada mediana 584 ms, máximo 1017 ms, 1 consulta/s en reposo; 50 ms: mediana 42 ms, máximo 65 ms, ~18 consultas/s. El intervalo compra latencia con carga sobre la base; para una alternativa sin sondeo está la captura de cambios (Debezium) |
| 10. Operación | sin publicador: 30 filas pendientes, la más vieja con 1,6 s; la alerta: pendientes > 100 durante 2 min, o la fila más vieja > 60 s. Limpieza con retención de 1 s: 30 filas borradas |

**Qué garantía queda.** Atomicidad entre la base y la intención de publicar; entrega *al menos una vez* con orden por clave; el consumidor tiene que ser idempotente. Lo que no resuelve es la ausencia de evento por un error de la aplicación antes de insertar la fila.
