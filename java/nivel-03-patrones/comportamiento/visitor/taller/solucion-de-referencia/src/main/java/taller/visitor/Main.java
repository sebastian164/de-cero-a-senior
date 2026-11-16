package taller.visitor;

public class Main {

    public static void main(String[] args) {
        // -(2 + 3) * (4 / 2) * 1 + 0
        Expr e = new Suma(new Multiplicacion(new Multiplicacion(new Negacion(new Suma(new Numero(2), new Numero(3))),
                new Division(new Numero(4), new Numero(2))), new Numero(1)), new Numero(0));

        System.out.println("Visitor clásico");
        System.out.println("  imprimir:    " + e.aceptar(new Imprimir()));
        System.out.println("  evaluar:     " + e.aceptar(new Evaluar()));
        System.out.println("  nodos:       " + e.aceptar(new ContarNodos()));
        Expr s = e.aceptar(new Simplificar());
        System.out.println("  simplificar: " + s.aceptar(new Imprimir()) + " (" + s.aceptar(new ContarNodos()) + " nodos)");

        System.out.println("sealed + switch");
        Arbol.Nodo n = Arbol.desde(e);
        System.out.println("  imprimir:    " + Arbol.imprimir(n));
        System.out.println("  evaluar:     " + Arbol.evaluar(n));
        System.out.println("  nodos:       " + Arbol.contar(n));
        System.out.println("  simplificar: " + Arbol.imprimir(Arbol.simplificar(n)));
    }
}

// ===================== Versión 1: Visitor clásico =====================

interface Expr {
    <R> R aceptar(Visitante<R> v);
}

interface Visitante<R> {
    R visitar(Numero n);
    R visitar(Suma s);
    R visitar(Multiplicacion m);
    R visitar(Negacion n);
    R visitar(Division d);       // paso 3: agregarlo rompió la compilación de TODOS los visitantes
}

record Numero(long valor) implements Expr {
    public <R> R aceptar(Visitante<R> v) { return v.visitar(this); }
}
record Suma(Expr izq, Expr der) implements Expr {
    public <R> R aceptar(Visitante<R> v) { return v.visitar(this); }
}
record Multiplicacion(Expr izq, Expr der) implements Expr {
    public <R> R aceptar(Visitante<R> v) { return v.visitar(this); }
}
record Negacion(Expr expr) implements Expr {
    public <R> R aceptar(Visitante<R> v) { return v.visitar(this); }
}
/** Paso 3: división entera. */
record Division(Expr izq, Expr der) implements Expr {
    public <R> R aceptar(Visitante<R> v) { return v.visitar(this); }
}

final class Evaluar implements Visitante<Long> {
    public Long visitar(Numero n) { return n.valor(); }
    public Long visitar(Suma s) { return s.izq().aceptar(this) + s.der().aceptar(this); }
    public Long visitar(Multiplicacion m) { return m.izq().aceptar(this) * m.der().aceptar(this); }
    public Long visitar(Negacion n) { return -n.expr().aceptar(this); }
    public Long visitar(Division d) {
        long divisor = d.der().aceptar(this);
        if (divisor == 0) throw new ArithmeticException("División por cero en " + d.aceptar(new Imprimir()));
        return d.izq().aceptar(this) / divisor;
    }
}

final class Imprimir implements Visitante<String> {
    public String visitar(Numero n) { return Long.toString(n.valor()); }
    public String visitar(Suma s) { return "(" + s.izq().aceptar(this) + " + " + s.der().aceptar(this) + ")"; }
    public String visitar(Multiplicacion m) { return "(" + m.izq().aceptar(this) + " * " + m.der().aceptar(this) + ")"; }
    public String visitar(Negacion n) { return "-" + n.expr().aceptar(this); }
    public String visitar(Division d) { return "(" + d.izq().aceptar(this) + " / " + d.der().aceptar(this) + ")"; }
}

final class ContarNodos implements Visitante<Integer> {
    public Integer visitar(Numero n) { return 1; }
    public Integer visitar(Suma s) { return 1 + s.izq().aceptar(this) + s.der().aceptar(this); }
    public Integer visitar(Multiplicacion m) { return 1 + m.izq().aceptar(this) + m.der().aceptar(this); }
    public Integer visitar(Negacion n) { return 1 + n.expr().aceptar(this); }
    public Integer visitar(Division d) { return 1 + d.izq().aceptar(this) + d.der().aceptar(this); }
}

/** Paso 4: operación nueva = una clase nueva. Ningún nodo cambió. */
final class Simplificar implements Visitante<Expr> {
    public Expr visitar(Numero n) { return n; }
    public Expr visitar(Suma s) {
        Expr i = s.izq().aceptar(this), d = s.der().aceptar(this);
        if (esNumero(d, 0)) return i;
        if (esNumero(i, 0)) return d;
        return new Suma(i, d);
    }
    public Expr visitar(Multiplicacion m) {
        Expr i = m.izq().aceptar(this), d = m.der().aceptar(this);
        if (esNumero(d, 1)) return i;
        if (esNumero(i, 1)) return d;
        return new Multiplicacion(i, d);
    }
    public Expr visitar(Negacion n) {
        Expr e = n.expr().aceptar(this);
        return e instanceof Negacion doble ? doble.expr() : new Negacion(e);
    }
    public Expr visitar(Division d) {
        Expr i = d.izq().aceptar(this), der = d.der().aceptar(this);
        return esNumero(der, 1) ? i : new Division(i, der);
    }
    private static boolean esNumero(Expr e, long v) { return e instanceof Numero n && n.valor() == v; }
}

// ===================== Versión 2: sealed + switch =====================

final class Arbol {
    private Arbol() { }

    sealed interface Nodo permits Num, Mas, Por, Menos, Div { }
    record Num(long valor) implements Nodo { }
    record Mas(Nodo izq, Nodo der) implements Nodo { }
    record Por(Nodo izq, Nodo der) implements Nodo { }
    record Menos(Nodo expr) implements Nodo { }
    /** Paso 3: agregarlo a 'permits' rompió la compilación de cada switch. */
    record Div(Nodo izq, Nodo der) implements Nodo { }

    static long evaluar(Nodo n) {
        return switch (n) {
            case Num x -> x.valor();
            case Mas x -> evaluar(x.izq()) + evaluar(x.der());
            case Por x -> evaluar(x.izq()) * evaluar(x.der());
            case Menos x -> -evaluar(x.expr());
            case Div x -> {
                long divisor = evaluar(x.der());
                if (divisor == 0) throw new ArithmeticException("División por cero en " + imprimir(x));
                yield evaluar(x.izq()) / divisor;
            }
        };
    }

    static String imprimir(Nodo n) {
        return switch (n) {
            case Num x -> Long.toString(x.valor());
            case Mas x -> "(" + imprimir(x.izq()) + " + " + imprimir(x.der()) + ")";
            case Por x -> "(" + imprimir(x.izq()) + " * " + imprimir(x.der()) + ")";
            case Menos x -> "-" + imprimir(x.expr());
            case Div x -> "(" + imprimir(x.izq()) + " / " + imprimir(x.der()) + ")";
        };
    }

    static int contar(Nodo n) {
        return switch (n) {
            case Num x -> 1;
            case Mas x -> 1 + contar(x.izq()) + contar(x.der());
            case Por x -> 1 + contar(x.izq()) + contar(x.der());
            case Menos x -> 1 + contar(x.expr());
            case Div x -> 1 + contar(x.izq()) + contar(x.der());
        };
    }

    /** Paso 4: operación nueva = una función nueva; los patrones anidados la acortan. */
    static Nodo simplificar(Nodo n) {
        return switch (n) {
            case Num x -> x;
            case Mas x -> {
                Nodo i = simplificar(x.izq()), d = simplificar(x.der());
                yield d instanceof Num(long v) && v == 0 ? i : i instanceof Num(long v) && v == 0 ? d : new Mas(i, d);
            }
            case Por x -> {
                Nodo i = simplificar(x.izq()), d = simplificar(x.der());
                yield d instanceof Num(long v) && v == 1 ? i : i instanceof Num(long v) && v == 1 ? d : new Por(i, d);
            }
            case Menos x -> simplificar(x.expr()) instanceof Menos(Nodo interno) ? interno : new Menos(simplificar(x.expr()));
            case Div x -> {
                Nodo i = simplificar(x.izq()), d = simplificar(x.der());
                yield d instanceof Num(long v) && v == 1 ? i : new Div(i, d);
            }
        };
    }

    static Nodo desde(Expr e) {
        return switch (e) {
            case Numero x -> new Num(x.valor());
            case Suma x -> new Mas(desde(x.izq()), desde(x.der()));
            case Multiplicacion x -> new Por(desde(x.izq()), desde(x.der()));
            case Negacion x -> new Menos(desde(x.expr()));
            case Division x -> new Div(desde(x.izq()), desde(x.der()));
            default -> throw new IllegalArgumentException(e.toString());
        };
    }
}
