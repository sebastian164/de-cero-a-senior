# Decisiones de diseño: tienda como monolito modular

## Qué es síncrono y qué va por eventos

| Paso | Cómo | Por qué |
|---|---|---|
| Reservar inventario | síncrono, llamada a la API del módulo `inventario` | el pedido necesita saber *ahora* si hay stock para rechazar o seguir; un evento llegaría tarde |
| Cobrar | síncrono, protegido (límite de tiempo, reintento, cortacircuitos) | el resultado decide si el pedido se confirma; es la única llamada que sale del proceso, y por eso la única que necesita resiliencia |
| Confirmar el pedido y escribir el evento | una sola transacción local (Outbox) | el cambio de estado y el aviso al resto del sistema no pueden separarse: o ocurren los dos o ninguno |
| Notificar al cliente | asíncrono, por evento | nada depende de que el aviso salga en ese instante; un fallo del correo no debe tumbar un pedido ya cobrado |

La regla: es síncrono lo que **decide el resultado** del pedido; va por eventos lo que **reacciona** a un resultado ya decidido.

## Otras decisiones

**Monolito modular con saga orquestada, no coreografiada.** `Pedidos.confirmar` dirige el flujo (reservar, cobrar, confirmar) y compensa en orden inverso si algo falla. Con tres pasos, un orquestador es más fácil de leer y de probar que tres módulos reaccionando entre sí; la coreografía compensaría con más flexibilidad a costa de que nadie vea el flujo entero. La prueba `un_fallo_en_cualquier_paso_deja_todo_consistente` inyecta el fallo en cada paso.

**Cada módulo es dueño de sus tablas (prefijo `catalogo_`, `inventario_`, `pedidos_`, `pagos_`, `notificaciones_`).** Ningún módulo nombra las tablas de otro: una prueba lee el código y falla si lo hace. Se descartó una base por módulo: en un monolito, un solo PostgreSQL con esquemas disciplinados da el mismo aislamiento sin coste operativo, y deja abierta la salida de extraer un módulo.

**Outbox con entrega *al menos una vez* y consumidor idempotente.** `RelevoOutbox` lee los eventos pendientes con `FOR UPDATE SKIP LOCKED` y los marca publicados; si falla entre publicar y marcar, el evento se publica dos veces. El consumidor lo tolera: guarda el id del evento en `notificaciones_procesadas` e ignora repetidos. Alternativa descartada: publicar en Kafka dentro de la transacción de negocio, que no es atómico (Kafka y PostgreSQL no comparten transacción).

**La clave de idempotencia del cobro es el id del pedido.** Si el tiempo límite vence, no se sabe si la pasarela cobró: el pedido queda en "resultado desconocido" y se anula **por clave** antes de rechazarlo. La pasarela simulada reproduce una remota: deduplica por clave y respeta una anulación recibida antes de que el cobro termine.

**Tiempo límite de 300 ms, 2 intentos, cortacircuitos de ventana 4.** Valores de una tienda de pruebas, elegidos para que las pruebas sean rápidas; en producción saldrían de la latencia medida de la pasarela real. El reintento no repite una llamada rechazada por el cortacircuitos abierto.

**Índices justificados con `EXPLAIN ANALYZE`.** `pedidos_por_cliente (cliente, creado DESC)` convierte "mis pedidos" de Seq Scan + Sort en Index Scan sin ordenar (la prueba lo mide con 200 000 filas). `outbox_pendientes` es un índice parcial: el relevo solo mira lo no publicado, una fracción mínima de la tabla.

**Spring Modulith solo para verificar.** Se usa `ApplicationModules.verify()` en las pruebas (ciclos y acceso a tipos internos), no el resto de la plataforma Modulith; el Outbox es propio para que se vea cómo funciona.

**Limitaciones conocidas.** La pasarela de pagos es simulada y el modo lento o fallido solo se activa desde las pruebas. El cobro y la reserva son llamadas locales: si el proceso muere entre ellas, la compensación depende de que alguien repase los pedidos `PENDIENTE` (no hay un proceso de recuperación).
