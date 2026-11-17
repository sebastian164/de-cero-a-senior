# 07 — Taller integrador: la tienda completa

Esta sesión no trae conceptos nuevos. Es el examen de la ruta: entregar el sistema de la tienda funcionando, con todo lo visto, y defenderlo.

## El encargo

Una tienda en línea con cuatro servicios y un orquestador:

| Servicio | Responsabilidad | Base propia |
|---|---|---|
| **Pedidos** | Recibe la compra por HTTP, expone el estado del pedido | Sí |
| **Orquestador** | Coordina la saga de compra | Sí |
| **Pagos** | Cobra y reembolsa contra una pasarela simulada | Sí |
| **Inventario** | Reserva y libera existencias | Sí |
| **Envíos** | Programa y cancela despachos | Sí |

Además, por coreografía y sin tocar la saga: **Notificaciones** (avisa al cliente) y **Fidelidad** (suma puntos).

## Requisitos

### Funcionales

1. `POST /pedidos` responde de inmediato con el pedido en estado `PENDIENTE` y un identificador.
2. `GET /pedidos/{id}` devuelve el estado actual y, si falló, el motivo.
3. Camino feliz: el pedido termina `CONFIRMADO`, con cobro, existencias reservadas y despacho programado.
4. Caminos tristes: pago rechazado, sin existencias y despacho imposible. En los tres, el pedido termina `CANCELADO` y no queda ni cobro ni reserva.
5. El cliente recibe una notificación con el resultado. Los puntos se suman solo a pedidos confirmados.

### De diseño

| Requisito | Sesión |
|---|---|
| Eventos y órdenes con sobre, versión y esquema | Kafka 05 |
| Temas con clave justificada y particiones razonadas | Kafka 01 |
| Productores con `acks=all` e idempotencia | Kafka 02 |
| Consumidores que confirman después de procesar | Kafka 03 |
| Todo consumidor es idempotente | Kafka 04 |
| Mensajes defectuosos a un tema de fallidos, con alerta | Kafka 04 y 06 |
| Nada cruza de la base a Kafka sin tabla de salida | Kafka 07 |
| Orden de los pasos justificado por tipo (compensable, pivote, reintentable) | Sagas 01 |
| Orquestador como máquina de estados con estado duradero | Sagas 02 |
| Reacciones laterales por coreografía | Sagas 03 |
| Lápidas, tiempos límite y compensaciones que insisten | Sagas 04 |
| Contramedida explícita contra la doble venta | Sagas 04 |
| La lógica de la saga se prueba sin infraestructura | Sagas 05 |
| Cada servicio es un hexágono: Kafka y la base de datos aparecen solo en adaptadores | Kafka 08 |
| El orquestador es un servicio de aplicación y cada participante, un puerto de salida | Sagas 06 |

### De operación

- Todo se levanta con una orden (`docker compose up`).
- Un identificador de correlación viaja desde la petición HTTP hasta el último mensaje y aparece en todos los registros.
- Métricas: sagas por estado, sagas vencidas, casos en revisión, retraso por grupo, filas pendientes en cada tabla de salida.
- Una consulta o un extremo que liste las sagas no terminadas.

## El simulacro

Con un generador de carga creando pedidos de forma continua (mezcla de los cuatro caminos), ejecuta cada prueba y completa la tabla:

| Prueba | Qué debe cumplirse |
|---|---|
| Matar el orquestador y reiniciarlo | Ninguna saga queda a medias |
| Matar Pagos durante dos minutos | Las sagas esperan, reintentan y terminan; no hay cobros dobles |
| Matar el broker durante un minuto | `POST /pedidos` sigue respondiendo; todo se procesa al volver |
| Matar el publicador de salida de Pedidos | Los pedidos se aceptan; sus eventos salen al reiniciar |
| Duplicar a mano cien mensajes de un tema | Cero efectos dobles |
| Publicar un mensaje ilegible en cada tema | Va a fallidos; lo demás sigue |
| Dos instancias de cada servicio | Mismo resultado que con una |
| Vender un producto con cinco unidades a veinte compradores a la vez | Exactamente cinco pedidos confirmados |
| Hacer que la compensación de Inventario falle siempre | El caso aparece en revisión humana, con alerta |

Después de **cada** prueba, ejecuta un verificador de invariantes que recorra las bases y compruebe:

- Todo pedido `CONFIRMADO` tiene exactamente un cobro, una reserva y un despacho.
- Todo pedido `CANCELADO` no tiene cobro vigente ni reserva.
- Ningún pedido lleva más de cierto tiempo en `PENDIENTE`.
- Las existencias nunca son negativas.

El verificador es parte de la entrega. Un simulacro sin verificador es una demostración, no una prueba.

## La defensa

Un documento de tres páginas para quien vaya a operar esto:

1. **Diagrama** de servicios, temas y bases, y el recorrido de un pedido confirmado y de uno cancelado.
2. **La tabla de transiciones** de la saga.
3. **Tres decisiones discutibles**, con la alternativa que descartaste y por qué. Como mínimo: el orden de los pasos, qué orquestaste y qué coreografiaste, y la contramedida de aislamiento.
4. **Qué garantiza y qué no.** Qué puede ver un cliente a medias y durante cuánto tiempo. Qué pasa si se pierde una base de datos.
5. **Guía de guardia:** para cada alerta, qué significa y cuál es el primer paso.
6. **Qué cambiarías** con cuarenta sagas en lugar de una.

## Criterios de aceptación

| Criterio | Cómo se comprueba |
|---|---|
| El sistema levanta desde cero con una orden | En un clon limpio |
| Los cuatro caminos funcionan | Pruebas automáticas |
| La tabla del simulacro está completa y el verificador pasa tras cada prueba | Salidas adjuntas |
| La venta de las cinco unidades da exactamente cinco confirmados | Repetida diez veces |
| La defensa nombra límites reales | Lectura |

## Entrega

Rama `taller/sagas-07`, o un repositorio propio enlazado desde el pull request, con el código, el verificador, los resultados del simulacro y la defensa en `taller/solucion/`.

## Preguntas de cierre

Las que te harán en una entrevista senior cuando digas que has trabajado con sagas:

1. Dibuja la saga de compra y explica qué pasa si el orquestador cae después de que Pagos cobró y antes de enterarse.
2. ¿Cómo sabes que no cobraste dos veces?
3. ¿Qué ve el cliente durante los segundos en que la saga está a medias?
4. ¿Por qué no usaste una transacción distribuida?
5. Tu compañero propone quitar la tabla de salida "porque Kafka casi nunca se cae". ¿Qué le respondes?
6. ¿Cuánto cuesta todo esto y cuándo no lo construirías?

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).
