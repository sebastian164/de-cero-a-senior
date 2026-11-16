public class Pruebas {
    private static int fallos = 0, total = 0;

    public static void main(String[] args) {
        Biblioteca a = new Biblioteca("A");
        Biblioteca b = new Biblioteca("B");
        a.agregar(new Libro("X", "Autor", 2));
        b.agregar(new Libro("X", "Autor", 2));
        a.prestar("X");
        igual("préstamo descuenta en A", 1, a.disponibles("X"));
        igual("B es independiente", 2, b.disponibles("X"));
        a.devolver("X");
        igual("devolución suma", 2, a.disponibles("X"));
        lanza("devolver sin préstamo", IllegalStateException.class, () -> a.devolver("X"));
        igual("inventario no se infla", 2, a.disponibles("X"));
        Biblioteca c = new Biblioteca("C");
        c.agregar(new Libro("Y", "Autor", 0));
        lanza("prestar sin ejemplares", IllegalStateException.class, () -> c.prestar("Y"));
        lanza("libro inexistente", IllegalArgumentException.class, () -> c.prestar("Z"));
        lanza("ejemplares negativos", IllegalArgumentException.class, () -> new Libro("Z", "A", -1));
        lanza("duplicado", IllegalArgumentException.class, () -> c.agregar(new Libro("Y", "Otro", 1)));
        lanza("lista inmodificable", UnsupportedOperationException.class, () -> c.libros().clear());
        Libro l = new Libro("R", "A", 1);
        Libro r = l;
        r.prestar();
        igual("dos referencias, un objeto", 0, l.disponibles());
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
