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
