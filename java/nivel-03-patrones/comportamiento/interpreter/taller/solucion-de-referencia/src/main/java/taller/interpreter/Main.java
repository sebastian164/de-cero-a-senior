package taller.interpreter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class Main {

    public static void main(String[] args) {
        Map<String, Object> pedido1 = Map.of("vip", true, "total", 350_000L, "categoria", "HOGAR", "nuevo", false, "edad", 30L);
        Map<String, Object> pedido2 = Map.of("vip", false, "total", 40_000L, "categoria", "LIBROS", "nuevo", true, "edad", 17L);

        for (String texto : List.of(
                "vip Y total > 200000 O categoria = LIBROS Y NO nuevo",
                "total < 50000 Y categoria EN LIBROS,MUSICA",
                "edad ENTRE 18 65")) {
            Regla r = Analizador.analizar(texto);
            System.out.println(texto + "  =>  " + r.describir() + "  | p1=" + r.evaluar(pedido1) + " p2=" + r.evaluar(pedido2));
        }

        try {
            Analizador.analizar("total > 100").evaluar(Map.of("total", "mucho"));
        } catch (ErrorDeRegla e) {
            System.out.println("Error: " + e.getMessage());
        }

        Predicate<Map<String, Object>> conPredicados = Predicados.reglaDelEjemplo();
        System.out.println("Con Predicate: p1=" + conPredicados.test(pedido1) + " p2=" + conPredicados.test(pedido2)
                + " describir -> " + conPredicados);
    }
}

// ===================== Gramática (la del ejemplo, más MenorQue, Entre y EnLista) =====================

interface Regla {
    boolean evaluar(Map<String, Object> contexto);
    String describir();
}

final class ErrorDeRegla extends RuntimeException {
    ErrorDeRegla(String mensaje) { super(mensaje); }
}

/** Paso 3: lectura tipada de campos con mensajes claros. */
final class Campos {
    private Campos() { }

    static long numero(Map<String, Object> ctx, String campo, Regla regla) {
        Object v = valor(ctx, campo, regla);
        if (v instanceof Number n) return n.longValue();
        throw new ErrorDeRegla("En la regla '" + regla.describir() + "' el campo '" + campo
                + "' debe ser numérico, pero es " + v.getClass().getSimpleName() + " (\"" + v + "\")");
    }

    static boolean booleano(Map<String, Object> ctx, String campo, Regla regla) {
        Object v = valor(ctx, campo, regla);
        if (v instanceof Boolean b) return b;
        throw new ErrorDeRegla("En la regla '" + regla.describir() + "' el campo '" + campo
                + "' debe ser verdadero/falso, pero es " + v.getClass().getSimpleName() + " (\"" + v + "\")");
    }

    static Object valor(Map<String, Object> ctx, String campo, Regla regla) {
        Object v = ctx.get(campo);
        if (v == null) throw new ErrorDeRegla("En la regla '" + regla.describir() + "' falta el campo '" + campo + "'");
        return v;
    }
}

record EsVerdadero(String campo) implements Regla {
    @Override public boolean evaluar(Map<String, Object> c) { return Campos.booleano(c, campo, this); }
    @Override public String describir() { return campo; }
}

record MayorQue(String campo, long valor) implements Regla {
    @Override public boolean evaluar(Map<String, Object> c) { return Campos.numero(c, campo, this) > valor; }
    @Override public String describir() { return campo + " > " + valor; }
}

record MenorQue(String campo, long valor) implements Regla {
    @Override public boolean evaluar(Map<String, Object> c) { return Campos.numero(c, campo, this) < valor; }
    @Override public String describir() { return campo + " < " + valor; }
}

/** Inclusivo en ambos extremos. */
record Entre(String campo, long desde, long hasta) implements Regla {
    Entre {
        if (desde > hasta) throw new ErrorDeRegla("Rango vacío en '" + campo + "': " + desde + " > " + hasta);
    }
    @Override public boolean evaluar(Map<String, Object> c) {
        long v = Campos.numero(c, campo, this);
        return v >= desde && v <= hasta;
    }
    @Override public String describir() { return campo + " ENTRE " + desde + " " + hasta; }
}

record EnLista(String campo, List<String> valores) implements Regla {
    EnLista { valores = List.copyOf(valores); }
    @Override public boolean evaluar(Map<String, Object> c) { return valores.contains(String.valueOf(Campos.valor(c, campo, this))); }
    @Override public String describir() { return campo + " EN " + String.join(",", valores); }
}

record Igual(String campo, String valor) implements Regla {
    @Override public boolean evaluar(Map<String, Object> c) { return valor.equals(String.valueOf(Campos.valor(c, campo, this))); }
    @Override public String describir() { return campo + " = " + valor; }
}

record Y(Regla izquierda, Regla derecha) implements Regla {
    @Override public boolean evaluar(Map<String, Object> c) { return izquierda.evaluar(c) && derecha.evaluar(c); }
    @Override public String describir() { return "(" + izquierda.describir() + " Y " + derecha.describir() + ")"; }
}

record O(Regla izquierda, Regla derecha) implements Regla {
    @Override public boolean evaluar(Map<String, Object> c) { return izquierda.evaluar(c) || derecha.evaluar(c); }
    @Override public String describir() { return "(" + izquierda.describir() + " O " + derecha.describir() + ")"; }
}

record No(Regla regla) implements Regla {
    @Override public boolean evaluar(Map<String, Object> c) { return !regla.evaluar(c); }
    @Override public String describir() { return "NO " + regla.describir(); }
}

// ===================== Paso 2: analizador sin paréntesis =====================

/**
 * Gramática (Y tiene más precedencia que O, como en la lógica habitual):
 *   expresion   := termino ( "O" termino )*
 *   termino     := factor ( "Y" factor )*
 *   factor      := "NO" factor | comparacion
 *   comparacion := CAMPO [ (">"|"<") NUMERO | "=" VALOR | "ENTRE" NUMERO NUMERO | "EN" V1,V2,... ]
 */
final class Analizador {
    private final List<String> tokens;
    private int pos;

    private Analizador(String texto) {
        this.tokens = new ArrayList<>(List.of(texto.trim().split("\\s+")));
    }

    static Regla analizar(String texto) {
        if (texto.isBlank()) throw new ErrorDeRegla("La regla está vacía");
        Analizador a = new Analizador(texto);
        Regla r = a.expresion();
        if (a.pos < a.tokens.size()) throw new ErrorDeRegla("Sobra '" + a.tokens.get(a.pos) + "' en la posición " + (a.pos + 1));
        return r;
    }

    private Regla expresion() {
        Regla r = termino();
        while (acepta("O")) r = new O(r, termino());
        return r;
    }

    private Regla termino() {
        Regla r = factor();
        while (acepta("Y")) r = new Y(r, factor());
        return r;
    }

    private Regla factor() {
        if (acepta("NO")) return new No(factor());
        return comparacion();
    }

    private Regla comparacion() {
        String campo = siguiente("un campo");
        if (List.of("Y", "O", ">", "<", "=", "EN", "ENTRE").contains(campo)) {
            throw new ErrorDeRegla("Se esperaba un campo y llegó '" + campo + "'");
        }
        if (acepta(">")) return new MayorQue(campo, numero());
        if (acepta("<")) return new MenorQue(campo, numero());
        if (acepta("=")) return new Igual(campo, siguiente("un valor"));
        if (acepta("ENTRE")) return new Entre(campo, numero(), numero());
        if (acepta("EN")) return new EnLista(campo, List.of(siguiente("una lista").split(",")));
        return new EsVerdadero(campo);
    }

    private long numero() {
        String t = siguiente("un número");
        try {
            return Long.parseLong(t);
        } catch (NumberFormatException e) {
            throw new ErrorDeRegla("Se esperaba un número y llegó '" + t + "'");
        }
    }

    private boolean acepta(String esperado) {
        if (pos < tokens.size() && tokens.get(pos).equals(esperado)) { pos++; return true; }
        return false;
    }

    private String siguiente(String que) {
        if (pos >= tokens.size()) throw new ErrorDeRegla("Se esperaba " + que + " al final de la regla");
        return tokens.get(pos++);
    }
}

// ===================== Paso 4: la misma regla con Predicate =====================

final class Predicados {
    private Predicados() { }

    static Predicate<Map<String, Object>> reglaDelEjemplo() {
        Predicate<Map<String, Object>> vip = c -> Boolean.TRUE.equals(c.get("vip"));
        Predicate<Map<String, Object>> totalAlto = c -> ((Long) c.get("total")) > 200_000;
        Predicate<Map<String, Object>> libros = c -> "LIBROS".equals(c.get("categoria"));
        Predicate<Map<String, Object>> nuevo = c -> Boolean.TRUE.equals(c.get("nuevo"));
        return vip.and(totalAlto).or(libros.and(nuevo.negate()));
    }
}
