// Contraste: con instanceof suelto, el tipo nuevo compila y falla en ejecución.
interface Expr { }
record Numero(long valor) implements Expr { }
record Division(Expr izq, Expr der) implements Expr { }
class Operaciones {
    static int contar(Expr e) {
        if (e instanceof Numero) return 1;
        throw new IllegalArgumentException("tipo desconocido: " + e);
    }
}
