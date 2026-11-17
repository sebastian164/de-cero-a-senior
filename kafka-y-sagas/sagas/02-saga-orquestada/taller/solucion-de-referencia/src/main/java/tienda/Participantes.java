package tienda;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Los participantes. Idempotentes por id de orden: una orden repetida recibe la misma respuesta, sin repetir el efecto. */
public final class Participantes {

    private Participantes() {
    }

    public abstract static class Participante {
        final String nombre;
        final Broker broker;
        private final Map<String, String> respuestas = new HashMap<>();
        public int ordenesQueIgnora;             // simula que está caído: la orden se pierde sin respuesta
        public int efectos;                      // cuántas veces hizo trabajo de verdad

        Participante(String nombre, Broker broker) {
            this.nombre = nombre;
            this.broker = broker;
        }

        public void atender() {
            for (Mensaje orden; (orden = broker.siguiente(nombre)) != null; ) {
                if (ordenesQueIgnora > 0) {
                    ordenesQueIgnora--;
                    broker.confirmar(nombre);
                    continue;
                }
                Mensaje o = orden;
                String respuesta = respuestas.computeIfAbsent(orden.id(), id -> {
                    efectos++;
                    return hacer(o);
                });
                broker.enviar("orquestador", new Mensaje(orden.id() + "/respuesta", orden.sagaId(), respuesta));
                broker.confirmar(nombre);
            }
        }

        abstract String hacer(Mensaje orden);
    }

    public static final class Inventario extends Participante {
        public int existencias;
        public final Map<String, Integer> reservas = new LinkedHashMap<>();
        public final Set<String> lapidas = new LinkedHashSet<>();

        public Inventario(Broker broker, int existencias) {
            super("inventario", broker);
            this.existencias = existencias;
        }

        @Override
        String hacer(Mensaje orden) {
            String saga = orden.sagaId();
            return switch (orden.tipo()) {
                case "reservar-inventario" -> {
                    if (lapidas.contains(saga) || existencias < 1) {
                        yield "sin-existencias";
                    }
                    existencias--;
                    reservas.put(saga, 1);
                    yield "inventario-reservado";
                }
                case "liberar-inventario" -> {
                    if (reservas.remove(saga) != null) {
                        existencias++;
                    } else {
                        lapidas.add(saga);               // liberación antes que reserva: que la reserva tardía no prospere
                    }
                    yield "inventario-liberado";
                }
                default -> throw new IllegalArgumentException(orden.tipo());
            };
        }
    }

    public static final class Pagos extends Participante {
        public long limite = 1_000_000;
        public final Map<String, Long> cobros = new LinkedHashMap<>();
        public final List<String> reembolsos = new ArrayList<>();
        public final Set<String> lapidas = new LinkedHashSet<>();
        public final Map<String, Long> montos = new HashMap<>();   // lo que cobraría por saga (lo pone la tienda)

        public Pagos(Broker broker) {
            super("pagos", broker);
        }

        @Override
        String hacer(Mensaje orden) {
            String saga = orden.sagaId();
            return switch (orden.tipo()) {
                case "cobrar" -> {
                    long centavos = montos.getOrDefault(saga, 0L);
                    if (lapidas.contains(saga) || centavos > limite) {    // la regla del límite vive aquí, no en el orquestador
                        yield "pago-rechazado";
                    }
                    cobros.put(saga, centavos);
                    yield "pago-aprobado";
                }
                case "reembolsar" -> {
                    Long cobrado = cobros.remove(saga);
                    if (cobrado != null) {
                        reembolsos.add(saga);
                    } else {
                        lapidas.add(saga);
                    }
                    yield "pago-reembolsado";
                }
                default -> throw new IllegalArgumentException(orden.tipo());
            };
        }
    }

    public static final class Envios extends Participante {
        public final Set<String> sinCobertura = new LinkedHashSet<>();
        public final Set<String> despachos = new LinkedHashSet<>();

        public Envios(Broker broker) {
            super("envios", broker);
        }

        @Override
        String hacer(Mensaje orden) {
            if (sinCobertura.contains(orden.sagaId())) {
                return "envio-imposible";
            }
            despachos.add(orden.sagaId());
            return "envio-programado";
        }
    }

    public static final class Fidelidad extends Participante {
        public final Set<String> puntos = new LinkedHashSet<>();

        public Fidelidad(Broker broker) {
            super("fidelidad", broker);
        }

        @Override
        String hacer(Mensaje orden) {
            puntos.add(orden.sagaId());
            return "puntos-sumados";
        }
    }
}
