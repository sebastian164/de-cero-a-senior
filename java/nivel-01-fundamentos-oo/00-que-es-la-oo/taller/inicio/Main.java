public class Main {

    public static void main(String[] args) {
        Pedido pedido = new Pedido();
        pedido.cliente = "Carlos";
        pedido.tipoCliente = "VIP";
        pedido.subtotal = 200_000;
        pedido.estado = "CREADO";

        System.out.println("Total a pagar: " + calcularTotal(pedido));

        pagar(pedido);
        enviar(pedido);
        System.out.println("Estado: " + pedido.estado);

        // Todo esto también "funciona".
        Pedido raro = new Pedido();
        raro.cliente = "";
        raro.tipoCliente = "vip";
        raro.subtotal = -50_000;
        raro.estado = "ENVIADO";
        System.out.println("Total a pagar: " + calcularTotal(raro));
    }

    static long calcularTotal(Pedido pedido) {
        long descuento = 0;
        if (pedido.tipoCliente.equals("VIP")) {
            descuento = pedido.subtotal * 10 / 100;
        } else if (pedido.tipoCliente.equals("EMPLEADO")) {
            descuento = pedido.subtotal * 30 / 100;
        }
        return pedido.subtotal - descuento;
    }

    static void pagar(Pedido pedido) {
        if (pedido.estado.equals("CREADO")) {
            pedido.estado = "PAGADO";
        }
    }

    static void enviar(Pedido pedido) {
        if (pedido.estado.equals("PAGADO")) {
            pedido.estado = "ENVIADO";
        } else {
            System.out.println("No se puede enviar un pedido sin pagar");
        }
    }
}
