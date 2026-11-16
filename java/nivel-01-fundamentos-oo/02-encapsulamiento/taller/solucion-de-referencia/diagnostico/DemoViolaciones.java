/** Se compila con taller/inicio/Main.java del repo: una línea que viola cada regla. */
public class DemoViolaciones {
    public static void main(String[] args) {
        Carrito c = new Carrito();
        c.getLineas().add(new Linea("Pan", -3, 4_000));            // regla 1: cantidad negativa
        c.setTotal(1);                                             // regla 2: total distinto de la suma
        Carrito vacio = new Carrito();
        vacio.setPagado(true);                                     // regla 4: pagar un carrito vacío
        c.setPagado(true);
        c.getLineas().add(new Linea("Queso", 1, 22_000));          // regla 3: cambiar un carrito pagado
        c.getLineas().get(0).cantidad = 1_000;                     // regla 1 otra vez, por el campo público
        System.out.println("líneas=" + c.getLineas().size() + " total=" + c.getTotal()
                + " pagado=" + c.isPagado() + " vacío pagado=" + vacio.isPagado());
    }
}
