// Se ejecuta con un JDK posterior al 21: java Novedades.java  (archivo fuente único, sin compilar aparte)
import java.util.List;
import java.util.stream.Gatherers;

public class Novedades {

    record Punto(int x, int y) { }

    static class Cuenta {
        final long saldo;
        Cuenta(long saldo) {
            if (saldo < 0) throw new IllegalArgumentException("saldo negativo");   // validar ANTES de super() (JEP 513, Java 25)
            super();
            this.saldo = saldo;
        }
    }

    public static void main(String[] args) {
        // 1. Variables y patrones sin nombre con _ (JEP 456, Java 22)
        Object o = new Punto(3, 4);
        if (o instanceof Punto(int x, _)) System.out.println("x=" + x);
        int total = 0;
        for (var _ : List.of("a", "b", "c")) total++;
        System.out.println("elementos=" + total);

        // 2. Stream gatherers (JEP 485, Java 24): ventanas deslizantes sin colectores caseros
        System.out.println("ventanas=" + List.of(1, 2, 3, 4).stream().gather(Gatherers.windowSliding(2)).toList());

        // 3. Cuerpos de constructor flexibles (JEP 513, Java 25)
        try { new Cuenta(-1); } catch (IllegalArgumentException e) { System.out.println("validado antes de super: " + e.getMessage()); }
        System.out.println("saldo=" + new Cuenta(10).saldo);
    }
}
