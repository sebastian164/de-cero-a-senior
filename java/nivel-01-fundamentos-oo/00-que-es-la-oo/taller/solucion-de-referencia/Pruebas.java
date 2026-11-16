/** Pruebas sin librerías: termina con código 1 si alguna falla. */
public class Pruebas {

    private static int fallos = 0;
    private static int total = 0;

    public static void main(String[] args) {
        igual("regular sin descuento", 200_000L, new Pedido("A", TipoCliente.REGULAR, 200_000).total());
        igual("vip 10 %", 180_000L, new Pedido("A", TipoCliente.VIP, 200_000).total());
        igual("empleado 30 %", 140_000L, new Pedido("A", TipoCliente.EMPLEADO, 200_000).total());
        igual("mayorista 15 %", 170_000L, new Pedido("A", TipoCliente.MAYORISTA, 200_000).total());
        igual("nace creado", EstadoPedido.CREADO, new Pedido("A", TipoCliente.VIP, 1).estado());

        Pedido p = new Pedido("A", TipoCliente.VIP, 1);
        p.pagar();
        igual("pagado", EstadoPedido.PAGADO, p.estado());
        p.enviar();
        igual("enviado", EstadoPedido.ENVIADO, p.estado());

        lanza("cliente null", IllegalArgumentException.class, () -> new Pedido(null, TipoCliente.VIP, 1));
        lanza("cliente en blanco", IllegalArgumentException.class, () -> new Pedido("  ", TipoCliente.VIP, 1));
        lanza("tipo null", IllegalArgumentException.class, () -> new Pedido("A", null, 1));
        lanza("subtotal negativo", IllegalArgumentException.class, () -> new Pedido("A", TipoCliente.VIP, -1));
        lanza("enviar sin pagar", IllegalStateException.class, () -> new Pedido("A", TipoCliente.VIP, 1).enviar());
        lanza("pagar enviado", IllegalStateException.class, () -> p.pagar());
        lanza("enviar dos veces", IllegalStateException.class, () -> p.enviar());
        igual("subtotal cero es válido", 0L, new Pedido("A", TipoCliente.VIP, 0).total());

        System.out.println(total + " pruebas, " + fallos + " fallos");
        if (fallos > 0) System.exit(1);
    }

    static void igual(String nombre, Object esperado, Object real) {
        total++;
        if (!esperado.equals(real)) {
            fallos++;
            System.out.println("FALLA " + nombre + ": esperado " + esperado + ", real " + real);
        }
    }

    static void lanza(String nombre, Class<? extends Throwable> tipo, Runnable accion) {
        total++;
        try {
            accion.run();
            fallos++;
            System.out.println("FALLA " + nombre + ": no lanzó " + tipo.getSimpleName());
        } catch (Throwable t) {
            if (!tipo.isInstance(t)) {
                fallos++;
                System.out.println("FALLA " + nombre + ": lanzó " + t);
            }
        }
    }
}
