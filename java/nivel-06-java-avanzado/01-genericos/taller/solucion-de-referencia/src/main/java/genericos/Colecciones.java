package genericos;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.IntFunction;

public final class Colecciones {
    private Colecciones() { }

    /**
     * destino: Collection<? super T>   -> consume elementos T: sirve una colección de T o de cualquier supertipo.
     * origen:  Iterable<? extends T>   -> produce elementos T: sirve un iterable de T o de cualquier subtipo.
     * Devuelve cuántos agregó (los que cambiaron la colección).
     */
    public static <T> int agregarTodos(Collection<? super T> destino, Iterable<? extends T> origen) {
        int agregados = 0;
        for (T elemento : origen) {
            if (destino.add(elemento)) agregados++;
        }
        return agregados;
    }

    /** Alternativa 1 a new T[n]: pedir la clase (token de tipo) y usar reflexión. */
    public static <T> T[] arregloDe(Class<T> tipo, int n) {
        @SuppressWarnings("unchecked") T[] arreglo = (T[]) Array.newInstance(tipo, n);
        return arreglo;
    }

    /** Alternativa 2: pedir un generador de arreglos (como Collection.toArray(IntFunction)). */
    public static <T> T[] arregloCon(IntFunction<T[]> generador, int n) {
        return generador.apply(n);
    }

    /** Alternativa 3: no usar arreglos. */
    public static <T> List<T> listaDe(int capacidad) {
        return new ArrayList<>(capacidad);
    }
}
