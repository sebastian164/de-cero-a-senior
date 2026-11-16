# Solución — n9-03 Mensajería y eventos

Todo corre en `MensajeriaTest` (`kafka-clients` 4.x contra Apache Kafka 4.3.1 en modo KRaft, 1 broker, puerto 43092; PostgreSQL 18.7 en 43432). Cada prueba crea sus propios temas con tres particiones (salvo donde se dice una). Corrida del 2026-11-15.

## 1. Dos grupos de consumidores

12 eventos de pedidos (`pedidos-xxxx`, 3 particiones). El grupo `facturacion` y el grupo `notificaciones` leen **los 12 cada uno**: cada grupo tiene su propio desplazamiento (*offset*) y el registro no se borra al leer. Dentro de un mismo grupo, dos miembros se reparten las particiones: la prueba comprueba que sus asignaciones son disjuntas y suman las tres particiones.

## 2. Caída a mitad de proceso: duplicados e idempotencia

Cinco eventos del mismo pedido en un tema de una partición. El consumidor 1 procesa 3 (cada uno con su `COMMIT` en PostgreSQL) y muere **antes** de confirmar el desplazamiento. El consumidor 2, del mismo grupo, recibe los 5 de nuevo.

| Variante | Facturas del pedido |
|---|---:|
| Ingenua (inserta factura y listo) | **8** (3 duplicadas) |
| Idempotente (`procesados.id_evento` con clave primaria, misma transacción que la factura) | **5** |

Confirmar el desplazamiento **después** de procesar es lo que da "al menos una vez"; la idempotencia es lo que hace que el duplicado sea inofensivo.

## 3. Sin clave y con clave (3 particiones)

9 eventos secuenciales del pedido `pedido-42`:

- **Con clave** (`pedido-42`): las 9 caen en **una sola partición** y se leen en orden 0..8.
- **Sin clave**: con el partidor por defecto de Kafka (pegajoso, *sticky*) los envíos pequeños tienden a juntarse en una partición y el reparto depende del tamaño de lote; para observar el reparto de forma determinista se usó `RoundRobinPartitioner`: los 9 eventos se reparten en **las 3 particiones** (0, 3, 6 en una, 1, 4, 7 en otra, 2, 5, 8 en la tercera). Cada partición conserva su orden interno, pero **entre particiones no hay orden**: tres consumidores en paralelo pueden procesar el 7 antes que el 1. Un consumidor por partición no puede reconstruir el orden del pedido.

Regla: la clave de partición es el identificador del agregado.

## 4. Mensaje venenoso

Tema de una partición con 5 mensajes; el tercero es basura (`{{{VENENO`).

- **Sin DLQ y reintentando para siempre** (límite de 6 s para la prueba): solo se procesaron `k-a` y `k-b`; el venenoso se intentó más de 5 veces y **los dos mensajes siguientes quedaron detrás de él**. La partición está detenida.
- **3 reintentos con espera creciente (100, 200, 300 ms) y tema `veneno-dlq`**: se procesaron `k-a`, `k-b`, `k-d`, `k-e` (4), el venenoso se intentó exactamente 3 veces, se publicó en la cola de fallidos con cabeceras `error` y `origen` (tema-partición@offset) y se leyó de ella. Solo entonces se confirma el desplazamiento y se sigue.

## 5. Evolución del esquema

Consumidor antiguo: `PedidoV1(idEvento, pedidoId, long total)`, leído con Jackson 3.

| Cambio del productor | Resultado en el consumidor antiguo |
|---|---|
| **Agregar** `moneda` | Con lector estricto (`FAIL_ON_UNKNOWN_PROPERTIES` activo) **falla** (`Unrecognized property "moneda"`); con lector tolerante, funciona. El lector del consumidor debe ser tolerante: la compatibilidad hacia atrás depende de ello. |
| **Quitar** `total` | Con la configuración por defecto de Jackson 3 falla con `Cannot map null into type long`. Con `FAIL_ON_NULL_FOR_PRIMITIVES` desactivado (algo habitual) **lee `total = 0` en silencio**: el consumidor facturaría cero. Si el campo se declara obligatorio (`@JsonProperty(required = true)` y tipo `Long`) el fallo es ruidoso siempre. |

Conclusión: agregar campos opcionales es compatible; quitar o cambiar el significado no lo es y debe pasar por un esquema nuevo (versión del tipo de evento, o un tema nuevo) y un periodo en que se publican ambos. Un registro de esquemas lo comprobaría en la publicación, no en producción.

## 6. Síncrono o mensajería

| Interacción | Elección | Justificación |
|---|---|---|
| Consultar las existencias antes de mostrar "agregar al carrito" | **Síncrono** (HTTP con tiempo límite) | El usuario espera la respuesta; sin ella no hay pantalla. Con respaldo (caché) si falla. |
| Confirmar el pedido → avisar a facturación y a notificaciones | **Mensajería** (evento `PedidoConfirmado`, con Outbox) | Es un hecho; hay varios interesados que no deben conocerse entre sí; si facturación está caída, el pedido no debe fallar. |
| Validar la tarjeta en la pasarela de pagos | **Síncrono** con clave de idempotencia | El resultado decide qué ve el usuario ahora; si vence el tiempo se reintenta con la misma clave. |
| Generar el PDF de la factura y enviarlo por correo | **Mensajería** (comando en una cola) | Tarea pesada y diferible; se puede reintentar y apartar a una DLQ sin que el usuario espere. |
