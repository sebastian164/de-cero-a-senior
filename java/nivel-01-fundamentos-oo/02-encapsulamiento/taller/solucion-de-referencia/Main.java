public class Main {

    public static void main(String[] args) {
        Carrito carrito = new Carrito();
        carrito.agregar("Café", 2, 18_000);
        carrito.agregar("Café", 1, 18_000);
        intentar("Cantidad negativa", () -> carrito.agregar("Pan", -3, 4_000));
        carrito.pagar();
        intentar("Agregar a carrito pagado", () -> carrito.agregar("Queso", 1, 22_000));
        intentar("Pagar carrito vacío", () -> new Carrito().pagar());
        // Ya no compilan: carrito.setTotal(1); carrito.getLineas().add(...); linea.cantidad = 5;
        System.out.println("Líneas: " + carrito.lineas().size() + " Total: " + carrito.total()
                + " pagado: " + carrito.estaPagado());
    }

    private static void intentar(String caso, Runnable accion) {
        try {
            accion.run();
            System.out.println(caso + ": ACEPTADO");
        } catch (RuntimeException e) {
            System.out.println(caso + ": rechazado -> " + e.getMessage());
        }
    }
}
