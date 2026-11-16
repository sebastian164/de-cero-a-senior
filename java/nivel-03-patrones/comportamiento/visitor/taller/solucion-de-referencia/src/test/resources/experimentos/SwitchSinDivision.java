// Experimento del paso 3 (sealed + switch): se agregó Div a 'permits',
// pero este switch, escrito antes, no tiene su caso.
sealed interface Nodo permits Num, Mas, Div { }
record Num(long valor) implements Nodo { }
record Mas(Nodo izq, Nodo der) implements Nodo { }
record Div(Nodo izq, Nodo der) implements Nodo { }
class Operaciones {
    static int contar(Nodo n) {
        return switch (n) {
            case Num x -> 1;
            case Mas x -> 1 + contar(x.izq()) + contar(x.der());
        };
    }
}
