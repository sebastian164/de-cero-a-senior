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
