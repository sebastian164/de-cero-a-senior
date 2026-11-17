package bicis;

/** Puerto: el estado de las bicicletas. Apareció porque "la bicicleta queda disponible" es otro efecto hacia fuera. */
public interface Bicicletas {

    void dejarDisponible(String bicicleta, String estacion);
}
