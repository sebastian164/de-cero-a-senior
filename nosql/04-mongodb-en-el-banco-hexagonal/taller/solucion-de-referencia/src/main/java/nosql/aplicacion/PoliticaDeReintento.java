package nosql.aplicacion;

import java.util.concurrent.ThreadLocalRandom;

/** Cuántas veces se repite un caso de uso que chocó con otro, y cuánto se espera entre intentos. */
public interface PoliticaDeReintento {

    int maximoDeIntentos();

    void esperar(int intentoFallido);

    /** Repite enseguida, hasta {@code maximo} intentos en total (lo que hace el servicio original del módulo hexagonal). */
    static PoliticaDeReintento inmediata(int maximo) {
        return new PoliticaDeReintento() {
            public int maximoDeIntentos() {
                return maximo;
            }

            public void esperar(int intentoFallido) {
            }
        };
    }

    /** Espera creciente con azar completo: entre 0 y base * 2^intento milisegundos, con tope. */
    static PoliticaDeReintento esperaCreciente(int maximo, long baseMs, long topeMs) {
        return new PoliticaDeReintento() {
            public int maximoDeIntentos() {
                return maximo;
            }

            public void esperar(int intentoFallido) {
                long techo = Math.min(topeMs, baseMs << Math.min(intentoFallido, 20));
                try {
                    Thread.sleep(ThreadLocalRandom.current().nextLong(techo + 1));
                } catch (InterruptedException interrupcion) {
                    Thread.currentThread().interrupt();
                }
            }
        };
    }
}
