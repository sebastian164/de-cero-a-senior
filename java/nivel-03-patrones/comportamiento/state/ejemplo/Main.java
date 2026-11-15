public class Main {

    public static void main(String[] args) {
        Pedido pedido = new Pedido();
        pedido.pagar();
        pedido.enviar();
        System.out.println("Estado: " + pedido.estado());
        try {
            pedido.cancelar();
        } catch (IllegalStateException e) {
            System.out.println("Rechazado: " + e.getMessage());
        }
    }
}

/** Cada estado decide qué se puede hacer y a qué estado se pasa. */
interface EstadoPedido {
    String nombre();

    default EstadoPedido pagar() { throw invalida("pagar"); }
    default EstadoPedido enviar() { throw invalida("enviar"); }
    default EstadoPedido cancelar() { throw invalida("cancelar"); }

    private IllegalStateException invalida(String accion) {
        return new IllegalStateException("No se puede " + accion + " un pedido " + nombre());
    }
}

class Creado implements EstadoPedido {
    @Override public String nombre() { return "creado"; }
    @Override public EstadoPedido pagar() { return new Pagado(); }
    @Override public EstadoPedido cancelar() { return new Cancelado(); }
}

class Pagado implements EstadoPedido {
    @Override public String nombre() { return "pagado"; }
    @Override public EstadoPedido enviar() { return new Enviado(); }
    @Override public EstadoPedido cancelar() { return new Cancelado(); }
}

class Enviado implements EstadoPedido {
    @Override public String nombre() { return "enviado"; }
}

class Cancelado implements EstadoPedido {
    @Override public String nombre() { return "cancelado"; }
}

/** El contexto delega en su estado actual. */
class Pedido {

    private EstadoPedido estado = new Creado();

    void pagar() { estado = estado.pagar(); }
    void enviar() { estado = estado.enviar(); }
    void cancelar() { estado = estado.cancelar(); }
    String estado() { return estado.nombre(); }
}
