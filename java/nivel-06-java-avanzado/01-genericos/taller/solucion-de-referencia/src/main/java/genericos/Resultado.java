package genericos;

import java.util.function.Function;

/** Éxito con valor o fallo con mensaje. */
public sealed interface Resultado<T> {

    record Exito<T>(T valor) implements Resultado<T> { }
    record Fallo<T>(String mensaje) implements Resultado<T> { }

    static <T> Resultado<T> exito(T valor) { return new Exito<>(valor); }
    static <T> Resultado<T> fallo(String mensaje) { return new Fallo<>(mensaje); }

    /** La función recibe un T o algo más general (? super T) y produce un U o un subtipo (? extends U). */
    default <U> Resultado<U> map(Function<? super T, ? extends U> f) {
        return switch (this) {
            case Exito<T> e -> new Exito<>(f.apply(e.valor()));
            case Fallo<T> fallo -> new Fallo<>(fallo.mensaje());
        };
    }

    default T orElse(T alternativa) {
        return switch (this) {
            case Exito<T> e -> e.valor();
            case Fallo<T> f -> alternativa;
        };
    }
}
