# 02 — Saga orquestada

## Idea

Una torre de control. Los aviones no se ponen de acuerdo entre sí: cada uno habla con la torre, y la torre sabe en todo momento quién está en qué fase y qué viene después. Y lo anota: si el controlador de turno se desmaya, el que lo releva mira el tablero y continúa. Lo que hace segura a la torre no es que alguien lo tenga todo en la cabeza, sino que el estado está escrito.

## Lo esencial

En una saga **orquestada** hay un componente, el orquestador, que:

1. Envía una **orden** a un participante ("reserva el vuelo").
2. Espera su **respuesta** ("reservado" o "rechazado").
3. Decide el paso siguiente, o empieza a compensar.
4. **Guarda** en qué estado va la saga después de cada cambio.

Los participantes no saben que forman parte de una saga: reciben órdenes y responden.

### El orquestador es una máquina de estados

| Estado | Llega | Pasa a | Envía |
|---|---|---|---|
| Reservando vuelo | Vuelo reservado | Reservando hotel | Reservar hotel |
| Reservando hotel | Hotel reservado | Cobrando | Cobrar |
| Cobrando | Pago aprobado | Completada | — |
| Cobrando | Pago rechazado | Cancelando hotel | Cancelar hotel |
| Cancelando hotel | Hotel cancelado | Cancelando vuelo | Cancelar vuelo |
| Cancelando vuelo | Vuelo cancelado | Cancelada | — |

Escribir esta tabla **antes** del código es la mitad del trabajo: obliga a decidir qué pasa en cada combinación, incluidas las que nadie pensó.

### Lo que tiene que ser duradero

- **El estado de cada saga**, en una base de datos. Sin eso, una caída deja sagas a medias que nadie retomará.
- **Los mensajes**, en un broker. Una orden enviada no se pierde porque el destinatario esté caído.
- Guardar el estado y enviar la orden siguiente deben ocurrir juntos: es exactamente el problema de la doble escritura, y se resuelve con [outbox](../../kafka/07-outbox-transaccional/).

### A favor y en contra

| A favor | En contra |
|---|---|
| El flujo completo está en un solo sitio y se lee | El orquestador conoce a todos: es un punto de acoplamiento |
| "¿En qué paso va el pedido 42?" se responde con una consulta | Riesgo de que acumule lógica de negocio que era de los participantes |
| Añadir, quitar o reordenar pasos toca un lugar | Una pieza más que desplegar y operar |
| Los tiempos límite y los reintentos se manejan de forma central | |

## Ejemplo

Java puro. Ejecuta cada uno con `java Main.java`.

### Antes

<!-- file: ejemplo/antes/Main.java -->
```java
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Set;

/** Un "orquestador" que es un método: el plan y las compensaciones viven en la memoria del proceso. */
public class Main {

    static final Set<String> vuelos = new LinkedHashSet<>();
    static final Set<String> hoteles = new LinkedHashSet<>();
    static final Set<String> cobros = new LinkedHashSet<>();

    public static void main(String[] args) {
        try {
            reservar("viaje-3", 800, true);
        } catch (Error caida) {
            System.out.println("  >> " + caida.getMessage());
        }
        System.out.println("  >> el proceso reinicia. ¿En qué iba viaje-3? Nadie lo sabe.");

        System.out.println();
        System.out.println("Vuelos reservados:  " + vuelos);
        System.out.println("Hoteles reservados: " + hoteles);
        System.out.println("Cobros:             " + cobros);
    }

    static void reservar(String viaje, int valor, boolean caerseAMitad) {
        Deque<Runnable> compensaciones = new ArrayDeque<>();      // solo existe mientras el método corre
        try {
            vuelos.add(viaje);
            compensaciones.push(() -> vuelos.remove(viaje));
            System.out.println("vuelo reservado");

            hoteles.add(viaje);
            compensaciones.push(() -> hoteles.remove(viaje));
            System.out.println("hotel reservado");

            if (caerseAMitad) {
                throw new Error("el proceso se cayó después de reservar el hotel");   // simula un apagón
            }

            if (valor > 1000) {
                throw new IllegalStateException("pago rechazado");
            }
            cobros.add(viaje);
            System.out.println("pago cobrado");
        } catch (IllegalStateException error) {
            while (!compensaciones.isEmpty()) {
                compensaciones.pop().run();
            }
        }
    }
}
```

### Análisis

| Problema | Consecuencia |
|---|---|
| El avance de la saga está en variables locales | Una caída lo borra. Al reiniciar no hay nada que retomar |
| Las compensaciones solo corren si el mismo proceso llega al `catch` | Un apagón, un despliegue o un `OOMKilled` las saltan |
| No hay registro de sagas | Nadie puede listar "las que quedaron a medias" |
| Las llamadas son directas y bloqueantes | Si un servicio está caído o lento, el orquestador se queda esperando |

### Después

<!-- file: ejemplo/despues/Main.java -->
```java
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Un orquestador como máquina de estados: el avance se guarda y los pasos son mensajes. */
public class Main {

    enum Estado { RESERVANDO_VUELO, RESERVANDO_HOTEL, COBRANDO, COMPLETADA, CANCELANDO_HOTEL, CANCELANDO_VUELO, CANCELADA }

    record Saga(String id, int valor, Estado estado) {
        Saga en(Estado nuevo) {
            return new Saga(id, valor, nuevo);
        }
    }

    record Mensaje(String destino, String tipo, String saga, int valor) {}

    // Lo que sobrevive a una caída: la tabla de sagas y los mensajes pendientes en el broker
    static final Map<String, Saga> almacen = new LinkedHashMap<>();
    static final Deque<Mensaje> broker = new ArrayDeque<>();

    // Las bases de datos de los participantes
    static final Set<String> vuelos = new LinkedHashSet<>();
    static final Set<String> hoteles = new LinkedHashSet<>();
    static final Set<String> cobros = new LinkedHashSet<>();

    /** No tiene campos: todo lo que sabe lo lee del almacén y lo vuelve a guardar. */
    static final class Orquestador {

        void iniciar(String id, int valor) {
            almacen.put(id, new Saga(id, valor, Estado.RESERVANDO_VUELO));
            System.out.println(id + ": iniciada");
            enviar("vuelos", "reservar", id, valor);
        }

        void alRecibir(Mensaje respuesta) {
            Saga saga = almacen.get(respuesta.saga());
            String tipo = respuesta.tipo();
            switch (saga.estado()) {
                case RESERVANDO_VUELO -> {
                    if (tipo.equals("vuelo-reservado")) avanzar(saga, Estado.RESERVANDO_HOTEL, "hoteles", "reservar");
                }
                case RESERVANDO_HOTEL -> {
                    if (tipo.equals("hotel-reservado")) avanzar(saga, Estado.COBRANDO, "pagos", "cobrar");
                }
                case COBRANDO -> {
                    if (tipo.equals("pago-aprobado")) terminar(saga, Estado.COMPLETADA);
                    if (tipo.equals("pago-rechazado")) avanzar(saga, Estado.CANCELANDO_HOTEL, "hoteles", "cancelar");
                }
                case CANCELANDO_HOTEL -> {
                    if (tipo.equals("hotel-cancelado")) avanzar(saga, Estado.CANCELANDO_VUELO, "vuelos", "cancelar");
                }
                case CANCELANDO_VUELO -> {
                    if (tipo.equals("vuelo-cancelado")) terminar(saga, Estado.CANCELADA);
                }
                case COMPLETADA, CANCELADA -> System.out.println(saga.id() + ": ya terminó; se ignora " + tipo);
            }
        }

        private void avanzar(Saga saga, Estado nuevo, String destino, String orden) {
            almacen.put(saga.id(), saga.en(nuevo));               // en un sistema real: misma transacción
            System.out.println(saga.id() + ": " + saga.estado() + " -> " + nuevo);
            enviar(destino, orden, saga.id(), saga.valor());      // que el envío de la orden (outbox)
        }

        private void terminar(Saga saga, Estado fin) {
            almacen.put(saga.id(), saga.en(fin));
            System.out.println(saga.id() + ": " + saga.estado() + " -> " + fin);
        }
    }

    public static void main(String[] args) {
        Orquestador orquestador = new Orquestador();

        orquestador.iniciar("viaje-1", 800);
        despachar(orquestador, Integer.MAX_VALUE);

        orquestador.iniciar("viaje-2", 5000);                     // el pago se rechaza
        despachar(orquestador, Integer.MAX_VALUE);

        orquestador.iniciar("viaje-3", 800);
        despachar(orquestador, 3);                                // procesa tres mensajes y se cae
        System.out.println("  >> el orquestador se cayó. viaje-3 quedó guardada en " + almacen.get("viaje-3").estado());
        orquestador = new Orquestador();                          // otro proceso, sin memoria de nada
        System.out.println("  >> arranca otro orquestador y sigue con lo que hay en el almacén y en el broker");
        despachar(orquestador, Integer.MAX_VALUE);

        System.out.println();
        almacen.values().forEach(saga -> System.out.println(saga.id() + " terminó en " + saga.estado()));
        System.out.println("Vuelos reservados:  " + vuelos);
        System.out.println("Hoteles reservados: " + hoteles);
        System.out.println("Cobros:             " + cobros);
    }

    static void enviar(String destino, String tipo, String saga, int valor) {
        broker.add(new Mensaje(destino, tipo, saga, valor));
    }

    /** Entrega hasta "maximo" mensajes: al orquestador o al participante que corresponda. */
    static void despachar(Orquestador orquestador, int maximo) {
        for (int entregados = 0; entregados < maximo && !broker.isEmpty(); entregados++) {
            Mensaje mensaje = broker.poll();
            if (mensaje.destino().equals("orquestador")) {
                orquestador.alRecibir(mensaje);
            } else {
                participante(mensaje);
            }
        }
    }

    /** Los tres servicios, resumidos: reciben una orden y responden. No saben nada de la saga. */
    static void participante(Mensaje orden) {
        String respuesta = switch (orden.destino() + ":" + orden.tipo()) {
            case "vuelos:reservar" -> {
                vuelos.add(orden.saga());
                yield "vuelo-reservado";
            }
            case "vuelos:cancelar" -> {
                vuelos.remove(orden.saga());
                yield "vuelo-cancelado";
            }
            case "hoteles:reservar" -> {
                hoteles.add(orden.saga());
                yield "hotel-reservado";
            }
            case "hoteles:cancelar" -> {
                hoteles.remove(orden.saga());
                yield "hotel-cancelado";
            }
            case "pagos:cobrar" -> {
                if (orden.valor() > 1000) {
                    yield "pago-rechazado";
                }
                cobros.add(orden.saga());
                yield "pago-aprobado";
            }
            default -> throw new IllegalArgumentException("orden desconocida: " + orden);
        };
        enviar("orquestador", respuesta, orden.saga(), orden.valor());
    }
}
```

- `viaje-2` se compensa paso a paso y termina `CANCELADA`.
- `viaje-3` sobrevive a la caída del orquestador: su estado estaba en el almacén y la respuesta pendiente, en el broker. El orquestador nuevo no "recuerda" nada y aun así la termina.
- La pregunta "¿en qué va viaje-3?" se responde mirando el almacén.

## Errores comunes

- Estado de la saga en memoria.
- Guardar el estado y enviar la orden como dos pasos separados, sin outbox.
- Un orquestador que hace el trabajo de los participantes: valida existencias, calcula precios. Debe coordinar, no decidir negocio ajeno.
- Llamadas síncronas bloqueantes desde el orquestador.
- No definir qué pasa cuando llega una respuesta que no corresponde al estado actual.
- Sin tiempo límite por paso: una respuesta que nunca llega deja la saga esperando para siempre.
- Un único orquestador gigante para todos los flujos de la empresa.

## Taller

**Objetivo:** construir el orquestador de la compra de la tienda como máquina de estados con estado duradero.

**Sobre tu servicio hexagonal:** si hiciste el [módulo de arquitectura hexagonal](../../../hexagonal/), el orquestador es un **servicio de aplicación** y su estado, una entidad con repositorio que se confirma en la misma unidad de trabajo que los pedidos. El ejemplo completo con esa forma está en la [sesión 06](../06-una-saga-en-un-servicio-hexagonal/).

En Java puro y en memoria, como el ejemplo. El salto a Kafka es la sesión 05.

1. Escribe la tabla de transiciones de tu saga de la sesión 01, con todos los caminos: éxito, pago rechazado, sin existencias, fallo de envío.
2. Implementa el orquestador a partir de la tabla. El almacén y el broker son las únicas estructuras que sobreviven entre "procesos".
3. Demuestra los cuatro caminos imprimiendo las transiciones y el estado final de cada servicio.
4. Simula la caída del orquestador en tres puntos distintos de una misma saga. En los tres, uno nuevo debe terminarla.
5. Ahora simula la caída **entre** guardar el estado y enviar la orden. ¿Qué pasa? Explica cómo lo evita outbox y simúlalo: las órdenes salen de una tabla de salida que se escribe junto con el estado.
6. Añade tiempo límite: si un participante no responde en cierto número de "ciclos", el orquestador reintenta la orden y, tras tres intentos, compensa. ¿Qué exige eso de los participantes?
7. Entrega una respuesta dos veces y otra fuera de lugar (un `pago-aprobado` cuando la saga ya está cancelando). Tu orquestador no debe corromperse. Decide y documenta qué hace en cada caso.
8. Añade una consulta: listar las sagas que llevan más de cierto tiempo sin terminar. ¿Para qué sirve en operación?
9. Añade el paso de puntos de fidelidad. ¿Cuántos sitios tocaste? Compáralo con la sesión 01.
10. Revisa tu orquestador: ¿hay alguna regla de negocio que debería estar en un participante? Muévela.

**Criterios de aceptación**

| Criterio | Cómo se comprueba |
|---|---|
| La tabla de transiciones existe y el código la sigue | Revisión cruzada |
| El orquestador no tiene estado en memoria entre mensajes | Paso 4, en tres puntos |
| Respuestas repetidas o tardías no lo corrompen | Paso 7 |
| Ninguna saga queda a medias sin que se sepa | Paso 8 |

**Entrega:** rama `taller/sagas-02`, con el código en `taller/solucion/` y `ANALISIS.md`.

## En el trabajo real

Escribir el orquestador a mano enseña el patrón; en producción, muchos equipos usan un motor de flujos duraderos (Temporal, Camunda y similares), que guarda el estado, reintenta y maneja tiempos límite por ti. La decisión entre uno y otro depende de cuántas sagas tengas y de quién va a operar el motor. En ambos casos, la tabla de transiciones la escribes tú.

## Preguntas senior

1. ¿Qué responsabilidades tiene el orquestador y cuáles no debe tener?
2. ¿Qué debe ser duradero para que una saga sobreviva a una caída?
3. ¿Por qué guardar el estado y enviar la orden es un problema de doble escritura?
4. ¿Cómo manejas un participante que no responde?
5. ¿Cuándo usarías un motor de flujos en lugar de un orquestador propio?
