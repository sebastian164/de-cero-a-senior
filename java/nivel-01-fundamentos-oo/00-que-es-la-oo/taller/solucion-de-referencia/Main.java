public class Main {

    public static void main(String[] args) {
        Pedido pedido = new Pedido("Carlos", TipoCliente.VIP, 200_000);
        System.out.println("Total a pagar: " + pedido.total());
        pedido.pagar();
        pedido.enviar();
        System.out.println("Estado: " + pedido.estado());

        Pedido mayorista = new Pedido("Ferretería El Tornillo", TipoCliente.MAYORISTA, 1_000_000);
        System.out.println("Total mayorista: " + mayorista.total());

        intentar("Cliente vacío", () -> new Pedido("", TipoCliente.VIP, 10_000));
        intentar("Subtotal negativo", () -> new Pedido("Ana", TipoCliente.REGULAR, -50_000));
        intentar("Enviar sin pagar", () -> new Pedido("Ana", TipoCliente.REGULAR, 10_000).enviar());
        intentar("Pagar dos veces", () -> {
            Pedido p = new Pedido("Ana", TipoCliente.REGULAR, 10_000);
            p.pagar();
            p.pagar();
        });
        // Esto ya no compila: no hay tipo "vip" en minúscula ni campo estado público.
        // new Pedido("Ana", "vip", 10_000);
        // pedido.estado = EstadoPedido.ENVIADO;
    }

    private static void intentar(String caso, Runnable accion) {
        try {
            accion.run();
            System.out.println(caso + ": ACEPTADO (no debería)");
        } catch (IllegalArgumentException | IllegalStateException e) {
            System.out.println(caso + ": rechazado -> " + e.getMessage());
        }
    }
}
