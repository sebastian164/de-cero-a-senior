# 07 — Outbox transaccional

## Idea

Una oficina donde cada documento firmado debe enviarse también por correo. Si el funcionario firma y luego sale a buscar al mensajero, puede que el mensajero no esté: el documento quedó firmado y nadie lo recibió. La solución de toda la vida es la bandeja de salida: al firmar, deja la copia en la bandeja **en el mismo gesto**. El mensajero pasa cada tanto y se lleva lo que haya. Si un día no pasa, las cartas esperan; ninguna se pierde.

## Lo esencial

### La doble escritura

Tu servicio hace dos cosas que deben ocurrir juntas: guardar en su base de datos y publicar un evento. Son dos sistemas distintos y no hay una transacción que los abarque a los dos.

| Orden | Si falla el segundo paso |
|---|---|
| Guardar y luego publicar | El dato existe y el evento nunca salió. Los demás servicios no se enteran |
| Publicar y luego guardar | El evento salió y el dato no existe. Los demás actúan sobre algo que no pasó |

Envolver las dos cosas en un `@Transactional` no lo arregla: la transacción de la base no puede deshacer lo que ya se envió a Kafka, y Kafka no sabe si la base llegó a confirmar.

### La solución

1. En **la misma transacción** de la base de datos se guarda el dato y una fila en una tabla de salida (*outbox*) con el evento que hay que publicar. Como es una sola base, es atómico de verdad: o las dos cosas o ninguna.
2. Un **publicador** aparte lee las filas pendientes, las publica en Kafka y las marca como publicadas.

| Forma de publicar | Cómo | A favor | En contra |
|---|---|---|---|
| Sondeo | Un proceso consulta la tabla cada poco | Simple; solo necesita la base | Algo de retraso; consultas constantes |
| Captura de cambios (CDC) | Una herramienta lee el registro de transacciones de la base y publica (por ejemplo, Debezium) | Sin sondeo; retraso mínimo | Otra pieza de infraestructura que operar |

### Lo que garantiza y lo que no

- **Garantiza** que todo dato guardado tendrá su evento, tarde o temprano.
- **No garantiza** que salga una sola vez: si el publicador cae después de publicar y antes de marcar, lo vuelve a enviar. Es entrega al menos una vez, así que los consumidores deben ser idempotentes. Por eso el evento lleva un `id` que nace con la fila.
- **Introduce retraso:** el evento sale un poco después de la transacción. Es consistencia eventual.
- **Orden:** si importa, el publicador debe enviar en orden por clave, y con varias instancias hay que impedir que dos tomen las mismas filas.

## Ejemplo

Java puro, con una base de datos y un broker simulados. Ejecuta cada uno con `java Main.java`.

### Antes

<!-- file: ejemplo/antes/Main.java -->
```java
import java.util.ArrayList;
import java.util.List;

/** Doble escritura: guardar en la base y publicar en el broker como dos pasos independientes. */
public class Main {

    static final class Broker {
        boolean caido;
        final List<String> eventos = new ArrayList<>();

        void publicar(String evento) {
            if (caido) {
                throw new IllegalStateException("broker no disponible");
            }
            eventos.add(evento);
        }
    }

    static final List<String> pedidosEnLaBase = new ArrayList<>();
    static final Broker broker = new Broker();

    public static void main(String[] args) {
        crearPedido("p-1");

        broker.caido = true;
        try {
            crearPedido("p-2");
        } catch (IllegalStateException error) {
            System.out.println("p-2: " + error.getMessage() + " (pero el pedido ya estaba guardado)");
        }
        broker.caido = false;

        try {
            crearPedidoPublicandoPrimero("p-3", true);
        } catch (IllegalStateException error) {
            System.out.println("p-3: " + error.getMessage() + " (pero el evento ya había salido)");
        }

        System.out.println();
        System.out.println("Pedidos en la base:  " + pedidosEnLaBase);
        System.out.println("Eventos en el broker: " + broker.eventos);
    }

    /** Primero la base, después el broker. */
    static void crearPedido(String pedido) {
        pedidosEnLaBase.add(pedido);                          // 1. confirmado en la base
        broker.publicar("PedidoCreado:" + pedido);            // 2. si esto falla, nadie se entera del pedido
    }

    /** "Entonces publiquemos primero": el problema solo cambia de lado. */
    static void crearPedidoPublicandoPrimero(String pedido, boolean fallaLaBase) {
        broker.publicar("PedidoCreado:" + pedido);            // 1. ya salió
        if (fallaLaBase) {
            throw new IllegalStateException("la base rechazó la transacción");
        }
        pedidosEnLaBase.add(pedido);                          // 2. nunca ocurre
    }
}
```

### Análisis

| Lo que queda | Por qué es grave |
|---|---|
| `p-2` está en la base y no tiene evento | Inventario nunca reserva, Pagos nunca cobra. El pedido se queda "creado" para siempre y nadie recibe un error |
| `p-3` tiene evento y no existe | Otros servicios reservan y cobran un pedido fantasma |
| Ocurre solo cuando algo falla en el instante exacto | Pasa una vez cada miles de operaciones: lo bastante raro para no verlo en pruebas y lo bastante frecuente para tener datos rotos en producción |
| Reintentar la publicación en el momento no lo cura | Si el proceso muere entre los dos pasos, no queda nadie para reintentar |

### Después

<!-- file: ejemplo/despues/Main.java -->
```java
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Outbox: el pedido y su evento se guardan en la misma transacción; un publicador los envía después. */
public class Main {

    static final class FilaDeSalida {
        final String id;
        final String evento;
        boolean publicada;

        FilaDeSalida(String id, String evento) {
            this.id = id;
            this.evento = evento;
        }
    }

    static final class BaseDeDatos {
        final List<String> pedidos = new ArrayList<>();
        final List<FilaDeSalida> salida = new ArrayList<>();

        /** Todo o nada: si la acción lanza, se deshace lo que escribió. */
        void transaccion(Runnable accion) {
            int pedidosAntes = pedidos.size();
            int salidaAntes = salida.size();
            try {
                accion.run();
            } catch (RuntimeException error) {
                pedidos.subList(pedidosAntes, pedidos.size()).clear();
                salida.subList(salidaAntes, salida.size()).clear();
                throw error;
            }
        }
    }

    static final class Broker {
        boolean caido;
        final List<String> mensajes = new ArrayList<>();      // "idDelEvento|evento"

        void publicar(String idDelEvento, String evento) {
            if (caido) {
                throw new IllegalStateException("broker no disponible");
            }
            mensajes.add(idDelEvento + "|" + evento);
        }
    }

    static final BaseDeDatos base = new BaseDeDatos();
    static final Broker broker = new Broker();

    public static void main(String[] args) {
        crearPedido("p-1", false);
        publicarPendientes(false);

        broker.caido = true;
        crearPedido("p-2", false);                            // el pedido se crea aunque el broker esté caído
        publicarPendientes(false);
        broker.caido = false;

        try {
            crearPedido("p-3", true);                         // la transacción falla: ni pedido ni evento
        } catch (IllegalStateException error) {
            System.out.println("p-3 rechazado: " + error.getMessage());
        }

        publicarPendientes(true);                             // publica p-2 y cae antes de marcarlo
        publicarPendientes(false);                            // al volver, lo publica otra vez

        // Un consumidor idempotente descarta la repetición por el identificador del evento
        Set<String> identificadoresVistos = new LinkedHashSet<>();
        List<String> efectos = new ArrayList<>();
        for (String mensaje : broker.mensajes) {
            String id = mensaje.substring(0, mensaje.indexOf('|'));
            if (identificadoresVistos.add(id)) {
                efectos.add(mensaje.substring(mensaje.indexOf('|') + 1));
            }
        }

        System.out.println();
        System.out.println("Pedidos en la base:      " + base.pedidos);
        System.out.println("Mensajes en el broker:   " + broker.mensajes);
        System.out.println("Efectos en el consumidor: " + efectos);
    }

    static void crearPedido(String pedido, boolean fallarAlFinal) {
        base.transaccion(() -> {
            base.pedidos.add(pedido);
            base.salida.add(new FilaDeSalida("evt-" + pedido, "PedidoCreado:" + pedido));
            if (fallarAlFinal) {
                throw new IllegalStateException("una regla de negocio no se cumplió");
            }
        });
    }

    /** El publicador: un proceso aparte que vacía la tabla de salida. */
    static void publicarPendientes(boolean caerseAntesDeMarcar) {
        for (FilaDeSalida fila : base.salida) {
            if (fila.publicada) {
                continue;
            }
            try {
                broker.publicar(fila.id, fila.evento);
            } catch (IllegalStateException error) {
                System.out.println("publicador: " + error.getMessage() + "; " + fila.id + " queda pendiente");
                return;
            }
            if (caerseAntesDeMarcar) {
                System.out.println("publicador: caída después de publicar " + fila.id + " y antes de marcarla");
                return;
            }
            fila.publicada = true;
            System.out.println("publicador: " + fila.id + " publicada y marcada");
        }
    }
}
```

- `p-2` se crea con el broker caído y su evento sale cuando vuelve: no se pierde.
- `p-3` falla y no deja ni pedido ni evento: no hay fantasmas.
- El evento de `p-2` sale dos veces, y el consumidor lo descarta por su identificador.

La tabla, en una base real (se ejecutó tal cual en PostgreSQL 17):

```sql
CREATE TABLE salida (
  id            UUID PRIMARY KEY,           -- el id del evento: nace aquí
  tipo          VARCHAR(100) NOT NULL,
  clave         VARCHAR(100) NOT NULL,      -- la clave del mensaje en Kafka
  contenido     JSONB NOT NULL,
  creada_en     TIMESTAMPTZ NOT NULL DEFAULT now(),
  publicada_en  TIMESTAMPTZ
);
CREATE INDEX salida_pendientes ON salida (creada_en) WHERE publicada_en IS NULL;

-- Varias instancias del publicador sin pisarse:
SELECT id, tipo, clave, contenido FROM salida
WHERE publicada_en IS NULL
ORDER BY creada_en
LIMIT 100
FOR UPDATE SKIP LOCKED;
```

## Errores comunes

- `@Transactional` alrededor de guardar y publicar, creyendo que eso lo hace atómico.
- Publicar dentro de la transacción "y si falla, que se deshaga": si lo que falla después es la confirmación de la base, el evento ya salió.
- Consumidores no idempotentes con un publicador que entrega al menos una vez.
- Generar el identificador del evento al publicar en vez de al guardar: cada reintento es un evento distinto.
- No limpiar la tabla y verla crecer sin fin.
- Varias instancias del publicador tomando las mismas filas.
- No vigilar las filas pendientes: si el publicador se detiene, el negocio se detiene sin un solo error.
- Usar outbox donde no hace falta: si perder un evento de vez en cuando es tolerable, es complejidad gratis.

## Taller

**Objetivo:** reproducir la doble escritura con infraestructura real, eliminarla con outbox y dejar el publicador listo para producción.

**Sobre tu servicio hexagonal:** si hiciste el [módulo de arquitectura hexagonal](../../../hexagonal/), tu servicio de pedidos ya tiene una bandeja de salida en memoria desde la sesión de [transacciones](../../../hexagonal/04-transacciones/). Aquí la llevas a PostgreSQL y a Kafka real: la tabla y el publicador son adaptadores de salida del mismo puerto de eventos. El caso de uso no cambia.

Sobre el servicio de Pedidos de la tienda, con PostgreSQL y Kafka.

1. Implementa "crear pedido" con doble escritura: guardar y luego publicar `PedidoCreado`.
2. Reprodúcela de las dos formas: (a) detén Kafka y crea un pedido; (b) mata el proceso entre el guardado y la publicación. Muestra el pedido sin evento.
3. Invierte el orden, haz fallar la base y muestra el evento fantasma.
4. Implementa la tabla de salida y cambia "crear pedido" para escribir pedido y evento en una transacción. Prueba que si la transacción falla no queda ninguna de las dos cosas.
5. Escribe el publicador por sondeo. Repite el paso 2: el evento debe salir cuando Kafka vuelve o el proceso se reinicia.
6. Mata el publicador justo después de publicar y antes de marcar. Demuestra el duplicado en el tema y que tu consumidor de la sesión 06 lo descarta.
7. Arranca dos instancias del publicador a la vez con mil filas pendientes. ¿Hay duplicados por competencia entre ellas? Arréglalo.
8. Crea tres eventos seguidos del mismo pedido (creado, confirmado, cancelado). ¿Salen en orden? ¿Y con dos instancias del publicador? ¿Y si la publicación del segundo falla y la del tercero no? Explica qué garantiza tu implementación.
9. Mide el retraso entre la confirmación de la transacción y la llegada del evento. ¿De qué depende? ¿Cómo lo reducirías y a qué costo?
10. Añade limpieza de filas publicadas y una métrica de filas pendientes con su alerta.
11. Opcional: sustituye el sondeo por captura de cambios con Debezium, siguiendo su documentación. Compara retraso, complejidad y qué pasa cuando esa pieza se cae.

**Criterios de aceptación**

| Criterio | Cómo se comprueba |
|---|---|
| Ningún pedido guardado se queda sin evento | Pasos 2 y 5, con conteos tras varias caídas |
| Ningún evento sin pedido | Paso 4 |
| Los duplicados no producen efectos dobles | Paso 6 |
| Dos publicadores no se pisan | Paso 7 |
| Se sabe qué orden se garantiza | Paso 8, por escrito |

**Entrega:** rama `taller/kafka-07`, con el código en `taller/solucion/` y `ANALISIS.md`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## En el trabajo real

Outbox es el patrón que sostiene casi todo lo que viene después: sin él, las sagas de la segunda parte de esta ruta se quedan a medias cada vez que un proceso cae en mal momento. En Spring hay bibliotecas que lo implementan; conviene haberlo escrito una vez a mano para saber qué garantizan y qué no.

## Preguntas senior

1. ¿Por qué una transacción no puede abarcar la base de datos y Kafka?
2. Explica outbox y qué garantía de entrega da.
3. Sondeo frente a captura de cambios.
4. ¿Cómo mantienes el orden de los eventos con outbox?
5. ¿Qué pasa si el publicador se detiene y cómo te enteras?
6. ¿Cuándo no usarías outbox?

Con esto termina la parte de Kafka. Ya puedes mover hechos entre servicios sin perderlos. Falta lo difícil: qué hacer cuando una operación de negocio necesita que **varios** servicios cambien su estado, y uno de ellos dice que no. Eso son las [sagas](../../sagas/).
