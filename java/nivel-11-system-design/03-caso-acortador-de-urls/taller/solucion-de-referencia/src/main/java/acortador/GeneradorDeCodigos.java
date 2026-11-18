package acortador;

public interface GeneradorDeCodigos {
    /** Propone un código. Puede repetirse (estrategia aleatoria): quien guarda detecta el conflicto y reintenta. */
    String siguiente();

    String nombre();
}
