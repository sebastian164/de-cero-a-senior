# 03 — Mensajería y eventos

## Idea

Una llamada telefónica exige que el otro esté disponible ahora. Un correo se deja en el buzón y se lee cuando se pueda; quien lo envía sigue con lo suyo. La mensajería es el buzón entre servicios: desacopla en el tiempo a quien produce de quien consume.

## Lo esencial

### Dos modelos

| | Cola (RabbitMQ, SQS) | Registro de eventos (Kafka) |
|---|---|---|
| Qué es | Mensajes que se entregan y se eliminan | Registro ordenado y persistente al que se añade |
| Consumo | Un consumidor procesa cada mensaje | Cada grupo de consumidores lee a su ritmo; se puede releer |
| Orden | Por cola, con matices | Garantizado dentro de una partición |
| Ideal para | Tareas, trabajos, enrutamiento flexible | Flujos de eventos, alto volumen, reprocesamiento |

**Mensaje como comando** ("genera la factura"): tiene un destinatario. **Mensaje como evento** ("pedido confirmado"): es un hecho; los interesados deciden qué hacer.

### Garantías de entrega

| Garantía | Significa | Implica |
|---|---|---|
| Como máximo una vez | Puede perderse; no se duplica | Aceptable para métricas |
| **Al menos una vez** | No se pierde; puede duplicarse | Lo habitual. Exige consumidores idempotentes |
| Exactamente una vez | Ni se pierde ni se duplica | En la práctica: al menos una vez + idempotencia |

**Diseña asumiendo duplicados y desorden.** Un consumidor idempotente registra el identificador de cada mensaje procesado, o hace operaciones que son idempotentes por naturaleza.

### Conceptos de Kafka

- **Tema (*topic*)** dividido en **particiones**. El orden solo se garantiza dentro de una partición.
- **Clave del mensaje:** determina la partición. Todos los eventos del mismo pedido deben llevar la misma clave para conservar su orden.
- **Grupo de consumidores:** las particiones se reparten entre sus miembros. El paralelismo máximo es el número de particiones.
- **Desplazamiento (*offset*):** la posición de lectura. Confirmarlo **después** de procesar da "al menos una vez".

### Qué hacer con los mensajes que fallan

- Reintentos con retroceso.
- **Cola de mensajes fallidos (DLQ):** tras varios intentos, el mensaje se aparta para no bloquear a los demás, y alguien lo revisa.
- **Mensaje venenoso:** uno que siempre falla. Sin DLQ, detiene la partición.

### Contratos

Un evento publicado es una API. Evoluciona de forma compatible: agregar campos opcionales, nunca quitar ni cambiar el significado. Un registro de esquemas (Avro, Protobuf, JSON Schema) lo verifica.

## Ejemplo

```java
@KafkaListener(topics = "pedidos.confirmados", groupId = "facturacion")
@Transactional
public void alConfirmar(PedidoConfirmado evento) {
    if (procesados.existe(evento.idEvento())) {
        return;                                      // duplicado: ya se facturó
    }
    facturas.emitirPara(evento.pedidoId(), evento.total());
    procesados.registrar(evento.idEvento());         // misma transacción que la factura
}
```

Registrar el identificador en la misma transacción que el efecto es lo que hace seguro al consumidor.

## Errores comunes

- Consumidores no idempotentes.
- Confirmar el mensaje antes de procesarlo (se pierde si el proceso cae).
- Sin clave de partición: eventos del mismo agregado llegan desordenados.
- Sin cola de mensajes fallidos.
- Eventos gigantes con todo el agregado, o tan pequeños que obligan a consultar de vuelta siempre.
- Usar mensajería para lo que necesita respuesta inmediata.
- Cambiar el esquema de un evento sin compatibilidad.
- Ignorar el retraso del consumidor (*lag*): es la métrica principal.
- Publicar el evento fuera de la transacción que guarda el dato (ver Outbox, lección siguiente).

## Taller

1. Levanta Kafka con Docker Compose. Publica eventos de pedidos y consúmelos con dos grupos distintos.
2. Detén un consumidor a mitad de proceso y demuestra la entrega duplicada. Hazlo idempotente.
3. Publica eventos del mismo pedido sin clave y con clave, con tres particiones, y observa el orden.
4. Introduce un mensaje venenoso; configura reintentos y una cola de mensajes fallidos.
5. Agrega un campo a un evento sin romper al consumidor antiguo. Luego intenta quitar uno y describe el fallo.
6. Para cuatro interacciones de un sistema, decide síncrono o mensajería y justifica.

Rama `taller/n9-03-mensajeria`.

Para practicar a fondo, con Kafka real y paso a paso, están los [ocho talleres de Kafka](../../../kafka-y-sagas/kafka/).

## Preguntas senior

1. Cola frente a registro de eventos: ¿cuándo cada uno?
2. ¿Por qué "exactamente una vez" es en la práctica idempotencia?
3. ¿Cómo garantizas el orden de los eventos de un mismo agregado?
4. ¿Qué haces con un mensaje que siempre falla?
5. ¿Cómo evolucionas el esquema de un evento con diez consumidores?
6. ¿Qué métricas vigilas en un sistema basado en mensajes?
