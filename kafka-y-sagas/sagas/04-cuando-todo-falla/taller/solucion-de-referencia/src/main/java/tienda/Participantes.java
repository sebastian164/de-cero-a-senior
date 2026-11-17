package tienda;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Participantes con dos interruptores: idempotencia (paso 3) y lápidas (paso 4). */
public final class Participantes {

    private Participantes() {
    }

    /** Respuesta "no pude, inténtalo después": no se guarda como resultado de la orden. */
    static final String FALLO_TECNICO = "-fallo";

    public abstract static class Participante {
        final String nombre;
        final Broker broker;
        private final Map<String, String> respuestas = new HashMap<>();
        public boolean idempotente = true;
        public boolean conLapidas = true;
        public int ordenesQueIgnora;
        private int fallosTecnicos;

        Participante(String nombre, Broker broker) {
            this.nombre = nombre;
            this.broker = broker;
        }

        public void atender() {
            for (Mensaje orden; (orden = broker.siguiente(nombre)) != null; ) {
                broker.confirmar(nombre);
                if (ordenesQueIgnora > 0) {
                    ordenesQueIgnora--;
                    continue;
                }
                String respuesta = idempotente ? respuestas.get(orden.id()) : null;
                if (respuesta == null) {
                    respuesta = hacer(orden);
                    if (idempotente && !respuesta.endsWith(FALLO_TECNICO)) {
                        respuestas.put(orden.id(), respuesta);
                    }
                }
                // Un fallo técnico es un hecho nuevo en cada intento: lleva su propio id, o el orquestador lo tomaría por repetido
                String id = respuesta.endsWith(FALLO_TECNICO) ? orden.id() + "/fallo-" + ++fallosTecnicos : orden.id() + "/respuesta";
                broker.enviar("orquestador", new Mensaje(id, orden.sagaId(), respuesta));
            }
        }

        abstract String hacer(Mensaje orden);
    }

    public static final class Inventario extends Participante {
        public int existencias;
        public final Map<String, Integer> reservas = new LinkedHashMap<>();   // unidades por saga
        public final Set<String> lapidas = new LinkedHashSet<>();
        public int fallosAlLiberar;                                         // paso 6

        public Inventario(Broker broker, int existencias) {
            super("inventario", broker);
            this.existencias = existencias;
        }

        @Override
        String hacer(Mensaje orden) {
            String saga = orden.sagaId();
            switch (orden.tipo()) {
                case "reservar-inventario":
                    if ((conLapidas && lapidas.contains(saga)) || existencias < 1) {
                        return "sin-existencias";
                    }
                    existencias--;
                    reservas.merge(saga, 1, Integer::sum);
                    return "inventario-reservado";
                case "liberar-inventario":
                    if (fallosAlLiberar > 0) {
                        fallosAlLiberar--;
                        return "liberar" + FALLO_TECNICO;
                    }
                    Integer unidades = reservas.remove(saga);
                    if (unidades != null) {
                        existencias += unidades;
                    } else if (conLapidas) {
                        lapidas.add(saga);
                    }
                    return "inventario-liberado";
                default:
                    throw new IllegalArgumentException(orden.tipo());
            }
        }
    }

    public static final class Pagos extends Participante {
        public long limite = 1_000_000;
        public final Map<String, Long> montos = new HashMap<>();
        public final Map<String, Integer> cobros = new LinkedHashMap<>();     // cobros vigentes por saga
        public final List<String> reembolsos = new ArrayList<>();
        public final Set<String> lapidas = new LinkedHashSet<>();

        public Pagos(Broker broker) {
            super("pagos", broker);
        }

        @Override
        String hacer(Mensaje orden) {
            String saga = orden.sagaId();
            switch (orden.tipo()) {
                case "cobrar":
                    if ((conLapidas && lapidas.contains(saga)) || montos.getOrDefault(saga, 0L) > limite) {
                        return "pago-rechazado";
                    }
                    cobros.merge(saga, 1, Integer::sum);
                    return "pago-aprobado";
                case "reembolsar":
                    if (cobros.remove(saga) != null) {
                        reembolsos.add(saga);
                    } else if (conLapidas) {
                        lapidas.add(saga);
                    }
                    return "pago-reembolsado";
                default:
                    throw new IllegalArgumentException(orden.tipo());
            }
        }
    }

    public static final class Envios extends Participante {
        public final Set<String> sinCobertura = new LinkedHashSet<>();
        public final Map<String, Integer> despachos = new LinkedHashMap<>();

        public Envios(Broker broker) {
            super("envios", broker);
        }

        @Override
        String hacer(Mensaje orden) {
            if (sinCobertura.contains(orden.sagaId())) {
                return "envio-imposible";
            }
            despachos.merge(orden.sagaId(), 1, Integer::sum);
            return "envio-programado";
        }
    }
}
