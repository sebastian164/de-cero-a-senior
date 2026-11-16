public class Pruebas {
    private static int fallos = 0, total = 0;

    public static void main(String[] args) {
        Carrito c = new Carrito();
        c.agregar("Café", 2, 18_000);
        c.agregar("Pan", 1, 4_000);
        igual("total es la suma", 40_000L, c.total());
        c.agregar("Café", 1, 18_000);
        igual("mismo producto, una línea", 2, c.lineas().size());
        igual("cantidad sumada", 3, c.lineas().get(0).cantidad());
        igual("total recalculado", 58_000L, c.total());
        lanza("cantidad cero", IllegalArgumentException.class, () -> c.agregar("X", 0, 1));
        lanza("cantidad negativa", IllegalArgumentException.class, () -> c.agregar("X", -3, 1));
        lanza("mismo producto otro precio", IllegalArgumentException.class, () -> c.agregar("Café", 1, 20_000));
        lanza("lista inmodificable", UnsupportedOperationException.class, () -> c.lineas().clear());
        c.quitar("Pan");
        igual("quitar", 54_000L, c.total());
        c.pagar();
        lanza("agregar pagado", IllegalStateException.class, () -> c.agregar("Queso", 1, 22_000));
        lanza("quitar pagado", IllegalStateException.class, () -> c.quitar("Café"));
        lanza("pagar dos veces", IllegalStateException.class, c::pagar);
        lanza("pagar vacío", IllegalStateException.class, () -> new Carrito().pagar());
        igual("pagado", true, c.estaPagado());
        System.out.println(total + " pruebas, " + fallos + " fallos");
        if (fallos > 0) System.exit(1);
    }

    static void igual(String n, Object e, Object r) { total++; if (!e.equals(r)) { fallos++; System.out.println("FALLA " + n + ": " + e + " != " + r); } }

    static void lanza(String n, Class<? extends Throwable> t, Runnable a) {
        total++;
        try { a.run(); fallos++; System.out.println("FALLA " + n + ": no lanzó"); }
        catch (Throwable x) { if (!t.isInstance(x)) { fallos++; System.out.println("FALLA " + n + ": " + x); } }
    }
}
