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
