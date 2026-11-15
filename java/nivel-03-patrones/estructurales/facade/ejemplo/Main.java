public class Main {

    public static void main(String[] args) {
        ProcesoDeCompra compras = new ProcesoDeCompra(
                new Inventario(), new Pagos(), new Despachos(), new Notificaciones());

        System.out.println(compras.comprar("ana", "Teclado", 1, 120_000));
    }
}

/** La fachada: una operación con sentido de negocio en lugar de cuatro subsistemas. */
class ProcesoDeCompra {

    private final Inventario inventario;
    private final Pagos pagos;
    private final Despachos despachos;
    private final Notificaciones notificaciones;

    ProcesoDeCompra(Inventario inventario, Pagos pagos, Despachos despachos, Notificaciones notificaciones) {
        this.inventario = inventario;
        this.pagos = pagos;
        this.despachos = despachos;
        this.notificaciones = notificaciones;
    }

    String comprar(String cliente, String producto, int cantidad, long total) {
        inventario.reservar(producto, cantidad);
        String autorizacion = pagos.autorizar(cliente, total);
        long guia = despachos.programar(cliente, producto);
        notificaciones.enviar(cliente, "Tu pedido va en camino, guía " + guia);
        return "Compra " + autorizacion + " despachada con guía " + guia;
    }
}

class Inventario {
    void reservar(String producto, int cantidad) { System.out.println("Reservado: " + cantidad + " " + producto); }
}

class Pagos {
    String autorizar(String cliente, long total) { System.out.println("Pago autorizado: $" + total); return "AUT-481"; }
}

class Despachos {
    long programar(String cliente, String producto) { System.out.println("Despacho programado"); return 90_017L; }
}

class Notificaciones {
    void enviar(String cliente, String mensaje) { System.out.println("Aviso a " + cliente + ": " + mensaje); }
}
