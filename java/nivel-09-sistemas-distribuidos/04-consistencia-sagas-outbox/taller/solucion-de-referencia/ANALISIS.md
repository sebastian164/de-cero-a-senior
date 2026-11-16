# Solución — n9-04 Consistencia entre servicios: Outbox y sagas

`SagasOutboxTest` (11 pruebas) contra PostgreSQL 18.7 (43432) y Apache Kafka 4.3.1 (43092). Corrida del 2026-11-15. El DDL de `outbox` es el del README, sin cambios.

## 1. La doble escritura, con un proceso que se mata de verdad

`Pedidos.main` (un proceso hijo) guarda el pedido 1001 en PostgreSQL, imprime `GUARDADO` y espera; la prueba le envía **SIGKILL** (`destroyForcibly`) en ese momento. Resultado: el pedido está en la base (1 fila) y el tema de Kafka está **vacío** (se leyó 4 s). El evento nunca saldrá.

Invertir el orden da el error opuesto (prueba `paso1b`): el evento llega a Kafka (1 mensaje) y el pedido no existe (0 filas). No hay orden de las dos líneas que sea correcto, porque falta una transacción común.

## 2. Outbox

`crearConOutbox` inserta el pedido y la fila de `outbox` en **una** transacción. Un proceso hijo crea el pedido 2001 y se mata con SIGKILL: la fila de outbox quedó sin publicar (`publicado_en IS NULL`). Un **publicador** aparte (`publicarPendientes`: `SELECT ... FOR UPDATE SKIP LOCKED`, envía con `acks=all`, marca `publicado_en`) lo recoge: el evento sale aunque el proceso que creó el pedido murió. Una segunda pasada del publicador envía 0 (ya marcado).

**Puede salir dos veces:** el publicador cae tras enviar y antes de marcar (pedido 2002). Al reiniciar reenvía: el tema tiene **2 mensajes** con el **mismo `idEvento`** (cabecera). Un consumidor con Inbox (conjunto/tabla de ids vistos) aplica el efecto 1 sola vez. Es "al menos una vez" con todas sus letras.

## 3. Saga orquestada de tres pasos

`Saga` (orquestador) ejecuta inventario → pagos → envíos contra servicios simulados en memoria, y **persiste en la tabla `sagas`** el estado y el paso antes y después de cada uno (responde dónde iba; `reanudar` la retoma desde esa tabla, ver la sección 5b). Camino feliz: `COMPLETADA`, los tres servicios con el pedido aplicado.

## 4. Falla el tercer paso

Con `envios.fallaAlEjecutar`, la saga compensa **en orden inverso** (pagos, luego inventario): el pedido deja de estar aplicado en los tres servicios, el servicio que falló no se compensa (nunca se aplicó) y `sagas` dice `CANCELADA`. El sistema queda consistente.

## 5. Una compensación falla la primera vez

El reembolso (pagos) falla una vez. El orquestador reintenta (hasta 3): llamadas a pagos `ejecutar, compensar, compensar`; el pedido queda cancelado y el inventario liberado. Como las compensaciones son idempotentes, repetirlas es inofensivo (comprobado llamándolas dos veces más).

Si no se recupera (99 fallos, 2 intentos) la saga **no miente**: queda `COMPENSACION_PENDIENTE / pagos`, y el inventario **no** se libera (el dinero sigue cobrado) hasta que alguien o un reintento posterior lo resuelva. Eso es lo que exige "toda saga necesita un estado para lo que no pudo deshacer".

## 5b. Reanudar una saga desde la tabla (el estado persistido se usa de verdad)

Persistir el estado no basta si nadie lo lee. `Saga.reanudar(pedidoId)` lee `estado`, `paso` y `detalle` y continúa: `EN_CURSO` con paso `hecho` sigue con el siguiente; con `ejecutando` repite ese paso (por eso `ejecutar` debe ser idempotente); `COMPENSANDO` y `COMPENSACION_PENDIENTE` repiten las compensaciones pendientes desde el paso anotado (cada compensación terminada se anota como `compensado`). Tres pruebas lo demuestran:

- **Compensación pendiente:** reembolso que falla hasta agotar los intentos, `COMPENSACION_PENDIENTE`; la dependencia se recupera; un orquestador nuevo (sin memoria) reanuda y deja `CANCELADA`, con pagos e inventario liberados. Reanudar una saga terminada no hace nada.
- **SIGKILL del orquestador tras el paso 2** (proceso hijo `SagaHija`, efectos en la tabla `efectos` de PostgreSQL, el hijo avisa "PASO2-LISTO" justo antes de ejecutar envíos y la prueba lo mata con `kill -9`): la tabla dice `EN_CURSO / envios / ejecutando`, los efectos son inventario y pagos. Un proceso nuevo reanuda y completa: `COMPLETADA`, tres efectos.
- **El mismo SIGKILL, pero al reanudar el tercer paso falla:** `CANCELADA` y la tabla de efectos queda vacía.

## 6. Como coreografía

`Coreografia`: cinco suscripciones (`PedidoCreado → InventarioReservado → PagoAprobado → EnvioFallido → PagoReembolsado`). Llega al mismo resultado (inventario `LIBERADO`, pago `REEMBOLSADO`), pero:

| Pregunta: "¿en qué paso está el pedido 42?" | Orquestada | Coreografiada |
|---|---|---|
| Cómo se responde | `SELECT ... FROM sagas WHERE pedido_id = 42` → `CANCELADA` | Hay que recorrer los eventos de todos los servicios (o consultar a cada uno: inventario dice `LIBERADO`, pagos `REEMBOLSADO`, envíos `FALLIDO`) y **deducir** el paso del último evento |
| Dónde está escrito el flujo | En `Saga`, una lista de pasos | Repartido en cinco suscripciones de tres servicios |
| Qué pasa si dos eventos del mismo pedido se cruzan | No aplica: el orquestador serializa | "El último evento" ya no basta |

Para flujos de tres pasos la coreografía es manejable; para el de seis pasos del README, nadie podría seguirla sin ese recorrido.
