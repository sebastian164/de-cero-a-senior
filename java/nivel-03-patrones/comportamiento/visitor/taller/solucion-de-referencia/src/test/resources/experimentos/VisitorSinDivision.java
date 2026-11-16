// Experimento del paso 3 (Visitor): se agregó visitar(Division) a la interfaz,
// pero este visitante, escrito antes, no lo implementa.
interface Expr { <R> R aceptar(Visitante<R> v); }
interface Visitante<R> {
    R visitar(Numero n);
    R visitar(Division d);
}
record Numero(long valor) implements Expr { public <R> R aceptar(Visitante<R> v) { return v.visitar(this); } }
record Division(Expr izq, Expr der) implements Expr { public <R> R aceptar(Visitante<R> v) { return v.visitar(this); } }
class ContarNodos implements Visitante<Integer> {
    public Integer visitar(Numero n) { return 1; }
}
