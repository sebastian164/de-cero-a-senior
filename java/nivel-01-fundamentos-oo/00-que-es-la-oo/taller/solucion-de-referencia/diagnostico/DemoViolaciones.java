/**
 * Parte 1, punto 2: se compila junto con taller/inicio (Main.java y Pedido.java del repo)
 * y demuestra que el código de inicio viola cada regla sin que nada falle.
 */
public class DemoViolaciones {

    public static void main(String[] args) {
        // Regla 1: pedido sin cliente y con subtotal negativo.
        Pedido p1 = new Pedido();
        p1.cliente = null; p1.tipoCliente = "REGULAR"; p1.subtotal = -50_000; p1.estado = "CREADO";
        System.out.println("Regla 1 violada: cliente=" + p1.cliente + " total=" + Main.calcularTotal(p1));

        // Regla 2: pedido que nace enviado (o sin estado).
        Pedido p2 = new Pedido();
        p2.cliente = "Ana"; p2.tipoCliente = "REGULAR"; p2.subtotal = 1; p2.estado = "ENVIADO";
        System.out.println("Regla 2 violada: nace en " + p2.estado);

        // Regla 3: "vip" en minúscula no recibe descuento y nadie se entera.
        Pedido p3 = new Pedido();
        p3.cliente = "Ana"; p3.tipoCliente = "vip"; p3.subtotal = 100_000; p3.estado = "CREADO";
        System.out.println("Regla 3 violada: vip paga " + Main.calcularTotal(p3) + " (esperado 90000)");

        // Regla 4: pagar un pedido ya enviado no hace nada y no avisa.
        Main.pagar(p2);
        System.out.println("Regla 4 violada: pagar un enviado deja el estado en " + p2.estado + " sin error");
    }
}
