package tienda;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Los servicios de la tienda, en memoria. En el servicio hexagonal cada uno es un puerto de salida de Pedidos
 * con un doble como este; aquí van juntos para que el taller quepa en pocos archivos.
 */
public final class Participantes {

    private Participantes() {
    }

    /** Fallo de negocio: el participante dice que no. Dispara compensaciones. */
    public static final class Rechazo extends RuntimeException {
        public Rechazo(String motivo) {
            super(motivo);
        }
    }

    public static final class Pedidos {
        public final Map<String, String> estados = new LinkedHashMap<>();

        public void crear(String pedido) {
            estados.put(pedido, "PENDIENTE");
        }

        public void confirmar(String pedido) {
            estados.put(pedido, "CONFIRMADO");
        }

        public void cancelar(String pedido, String motivo) {
            estados.put(pedido, "CANCELADO (" + motivo + ")");
        }
    }

    public static final class Inventario {
        public final Map<String, Integer> existencias = new LinkedHashMap<>();
        public final Map<String, Integer> reservas = new LinkedHashMap<>();   // pedido -> unidades
        public boolean compensacionRota;                                       // paso 8

        public void reservar(String pedido, String producto, int unidades) {
            int hay = existencias.getOrDefault(producto, 0);
            if (hay < unidades) {
                throw new Rechazo("sin existencias de " + producto);
            }
            existencias.put(producto, hay - unidades);
            reservas.put(pedido, unidades);
        }

        public void liberar(String pedido, String producto) {
            if (compensacionRota) {
                throw new IllegalStateException("inventario no responde al liberar " + pedido);
            }
            Integer unidades = reservas.remove(pedido);
            if (unidades != null) {
                existencias.merge(producto, unidades, Integer::sum);
            }
        }
    }

    public static final class Pagos {
        public final Map<String, Long> cobros = new LinkedHashMap<>();
        public final List<String> reembolsos = new ArrayList<>();           // queda rastro: compensar no es borrar
        public long limiteDeLaTarjeta = 1_000_000;

        public void cobrar(String pedido, long centavos) {
            if (centavos > limiteDeLaTarjeta) {
                throw new Rechazo("pago rechazado");
            }
            cobros.put(pedido, centavos);
        }

        public void reembolsar(String pedido) {
            Long cobrado = cobros.remove(pedido);
            if (cobrado != null) {
                reembolsos.add(pedido + ":" + cobrado);
            }
        }
    }

    public static final class Envios {
        public final Set<String> despachos = new LinkedHashSet<>();
        public final Set<String> ciudadesSinCobertura = new LinkedHashSet<>();

        public void programar(String pedido, String ciudad) {
            if (ciudadesSinCobertura.contains(ciudad)) {
                throw new Rechazo("no hay envíos a " + ciudad);
            }
            despachos.add(pedido);
        }
    }

    public static final class Fidelidad {
        public final Map<String, Long> puntos = new LinkedHashMap<>();

        public void sumar(String cliente, long centavos) {
            puntos.merge(cliente, centavos / 100_000, Long::sum);    // un punto por cada mil pesos
        }
    }

    public static final class Notificaciones {
        public final List<String> correos = new ArrayList<>();

        public void enviar(String texto) {
            correos.add(texto);
        }
    }
}
