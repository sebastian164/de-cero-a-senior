# 04 — Consistencia entre servicios: Outbox y sagas

## Idea

Reservar un viaje implica vuelo, hotel y carro, con tres empresas distintas. No existe un "confirmar todo" único. Se reserva paso a paso, y si el hotel no tiene cupo, hay que cancelar el vuelo que ya se reservó. Eso es una saga: avanzar por pasos y saber deshacer.

## Lo esencial

Dentro de un servicio, una transacción de base de datos garantiza todo o nada. Entre servicios, no. Las transacciones distribuidas clásicas (confirmación en dos fases) bloquean recursos, reducen la disponibilidad y muchos sistemas modernos no las soportan. La alternativa es aceptar la **consistencia eventual** y gestionarla de forma explícita.

### Problema 1: la doble escritura

```java
repositorio.guardar(pedido);          // base de datos
kafka.send("pedidos", evento);        // broker
```

Son dos sistemas sin transacción común. Si el proceso cae entre las dos líneas, el pedido existe y el evento nunca salió. Invertir el orden produce el error opuesto.

### Solución: Outbox transaccional

1. En **la misma transacción** que guarda el pedido, se inserta el evento en una tabla `outbox`.
2. Un proceso aparte lee la tabla y publica en el broker, marcando lo enviado.

```sql
CREATE TABLE outbox (
    id            UUID PRIMARY KEY,
    tipo          VARCHAR(100) NOT NULL,
    agregado_id   VARCHAR(100) NOT NULL,
    contenido     JSONB        NOT NULL,
    creado_en     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    publicado_en  TIMESTAMPTZ
);
```

El evento se publica **al menos una vez** (si el publicador cae tras enviar y antes de marcar, reenvía): los consumidores deben ser idempotentes. La publicación puede hacerse con un proceso que consulta la tabla o con captura de cambios de la base (Debezium).

El espejo en el consumidor es la tabla de mensajes procesados (**Inbox**), vista en la lección anterior.

### Problema 2: operaciones de varios pasos entre servicios

### Solución: saga

Una secuencia de transacciones locales. Cada paso tiene una **compensación** que deshace su efecto en términos de negocio.

| Paso | Acción | Compensación |
|---|---|---|
| 1 | Reservar inventario | Liberar inventario |
| 2 | Cobrar | Reembolsar |
| 3 | Programar envío | Cancelar envío |

| | Coreografía | Orquestación |
|---|---|---|
| Cómo | Cada servicio reacciona al evento del anterior | Un orquestador ordena cada paso y escucha las respuestas |
| A favor | Sin punto central, desacoplada | El flujo se ve en un solo lugar; fácil manejar condiciones y tiempos |
| En contra | El flujo no está escrito en ningún sitio; ciclos de eventos | El orquestador concentra lógica |
| Conviene | Dos o tres pasos | Flujos largos o con ramificaciones |

### Lo que una saga no da

- **No hay aislamiento.** Otros procesos ven estados intermedios: un pedido "pendiente de pago" es visible. Se modela con estados explícitos.
- **Las compensaciones no son reversiones.** Un cobro se reembolsa, no se borra; un correo enviado no se retira. Algunas acciones se dejan para el final porque no tienen compensación.
- **Las compensaciones también pueden fallar:** deben ser idempotentes y reintentarse.

## Ejemplo: orquestador

```java
class SagaDePedido {

    void manejar(InventarioReservado evento) { enviar(new CobrarPedido(evento.pedidoId())); }

    void manejar(PagoAprobado evento)        { enviar(new ProgramarEnvio(evento.pedidoId())); }

    void manejar(PagoRechazado evento) {
        enviar(new LiberarInventario(evento.pedidoId()));     // compensación
        marcar(evento.pedidoId(), EstadoSaga.CANCELADA);
    }

    void manejar(EnvioFallido evento) {
        enviar(new ReembolsarPago(evento.pedidoId()));        // compensaciones en orden inverso
        enviar(new LiberarInventario(evento.pedidoId()));
        marcar(evento.pedidoId(), EstadoSaga.CANCELADA);
    }
}
```

El estado de la saga se persiste: debe sobrevivir a un reinicio y saber en qué paso iba.

## Errores comunes

- Guardar y publicar sin Outbox.
- Sagas sin compensaciones definidas para cada paso.
- Compensaciones no idempotentes.
- Coreografías de seis pasos que nadie puede seguir.
- No contemplar el paso que nunca responde: toda saga necesita tiempos límite.
- Usar una saga donde bastaba mantener las dos operaciones en el mismo servicio. **La mejor saga es la que no hace falta:** si dos datos deben ser consistentes de inmediato, probablemente pertenecen al mismo servicio.

## Taller

1. Reproduce la doble escritura: mata el proceso entre guardar y publicar.
2. Implementa Outbox con un publicador que consulte la tabla. Demuestra que el evento sale aunque el proceso caiga, y que puede salir dos veces.
3. Implementa una saga orquestada de tres pasos con sus compensaciones (servicios simulados en memoria o reales).
4. Haz fallar el tercer paso y verifica que el sistema queda consistente.
5. Haz que una compensación falle la primera vez y se recupere.
6. Reescribe la saga como coreografía y compara qué tan fácil es responder "¿en qué paso está el pedido 42?".

Rama `taller/n9-04-sagas-outbox`.

Para practicar a fondo están el taller de [outbox](../../../kafka-y-sagas/kafka/07-outbox-transaccional/) y los [siete talleres de sagas](../../../kafka-y-sagas/sagas/), con ejemplos ejecutables.

## Preguntas senior

1. Explica el problema de la doble escritura y por qué no se arregla cambiando el orden.
2. ¿Cómo funciona Outbox y qué garantía de entrega da?
3. Coreografía frente a orquestación.
4. ¿Qué propiedad de ACID sacrifica una saga y cómo se mitiga?
5. ¿Qué haces con un paso que no tiene compensación posible?
6. ¿Cuándo es mejor rediseñar los límites de los servicios que implementar una saga?
