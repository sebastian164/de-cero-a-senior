import java.util.Map;

public class Main {

    public static void main(String[] args) {
        // (vip Y total > 200000) O (categoria = LIBROS Y NO nuevo)
        Regla promo = new O(
                new Y(new EsVerdadero("vip"), new MayorQue("total", 200_000)),
                new Y(new Igual("categoria", "LIBROS"), new No(new EsVerdadero("nuevo"))));

        Map<String, Object> pedido1 = Map.of("vip", true, "total", 350_000L, "categoria", "HOGAR", "nuevo", false);
        Map<String, Object> pedido2 = Map.of("vip", false, "total", 40_000L, "categoria", "LIBROS", "nuevo", true);

        System.out.println(promo.describir());
        System.out.println("Pedido 1: " + promo.evaluar(pedido1));
        System.out.println("Pedido 2: " + promo.evaluar(pedido2));
    }
}

/** Cada elemento de la gramática es una clase que sabe evaluarse en un contexto. */
interface Regla {
    boolean evaluar(Map<String, Object> contexto);
    String describir();
}

// ----- Expresiones terminales -----

record EsVerdadero(String campo) implements Regla {
    @Override public boolean evaluar(Map<String, Object> contexto) { return Boolean.TRUE.equals(contexto.get(campo)); }
    @Override public String describir() { return campo; }
}

record MayorQue(String campo, long valor) implements Regla {
    @Override public boolean evaluar(Map<String, Object> contexto) { return ((Long) contexto.get(campo)) > valor; }
    @Override public String describir() { return campo + " > " + valor; }
}

record Igual(String campo, String valor) implements Regla {
    @Override public boolean evaluar(Map<String, Object> contexto) { return valor.equals(contexto.get(campo)); }
    @Override public String describir() { return campo + " = " + valor; }
}

// ----- Expresiones compuestas -----

record Y(Regla izquierda, Regla derecha) implements Regla {
    @Override public boolean evaluar(Map<String, Object> contexto) { return izquierda.evaluar(contexto) && derecha.evaluar(contexto); }
    @Override public String describir() { return "(" + izquierda.describir() + " Y " + derecha.describir() + ")"; }
}

record O(Regla izquierda, Regla derecha) implements Regla {
    @Override public boolean evaluar(Map<String, Object> contexto) { return izquierda.evaluar(contexto) || derecha.evaluar(contexto); }
    @Override public String describir() { return "(" + izquierda.describir() + " O " + derecha.describir() + ")"; }
}

record No(Regla regla) implements Regla {
    @Override public boolean evaluar(Map<String, Object> contexto) { return !regla.evaluar(contexto); }
    @Override public String describir() { return "NO " + regla.describir(); }
}
