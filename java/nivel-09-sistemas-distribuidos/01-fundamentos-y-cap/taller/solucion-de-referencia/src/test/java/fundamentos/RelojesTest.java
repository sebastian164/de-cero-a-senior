package fundamentos;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Paso 4: dos servidores con relojes desfasados y "gana la última escritura" por marca de tiempo. */
class RelojesTest {

    record Escritura(String servidor, String precio, long marcaDeTiempoMs, long lamport) {}

    /** Un servidor con su propio reloj de pared (desfasado) y un reloj lógico de Lamport. */
    static final class Servidor {
        final String nombre;
        final long desfaseMs;
        long lamport;

        Servidor(String nombre, long desfaseMs) {
            this.nombre = nombre;
            this.desfaseMs = desfaseMs;
        }

        Escritura escribir(String precio, long tiempoRealMs) {
            lamport++;
            return new Escritura(nombre, precio, tiempoRealMs + desfaseMs, lamport);
        }

        void recibir(Escritura e) {
            lamport = Math.max(lamport, e.lamport()) + 1;
        }
    }

    @Test
    void con_un_reloj_adelantado_la_escritura_vieja_gana() {
        Servidor a = new Servidor("A", +500);    // reloj de A adelantado medio segundo
        Servidor b = new Servidor("B", 0);
        Escritura primero = a.escribir("100", 1_000);       // a las 10:00:01.000 reales
        b.recibir(primero);                                 // B la ve (replicación) y después…
        Escritura despues = b.escribir("120", 1_200);       // …el usuario la corrige 200 ms después en B

        List<Escritura> ambas = List.of(primero, despues);
        Escritura porReloj = ambas.stream().max(Comparator.comparingLong(Escritura::marcaDeTiempoMs)).orElseThrow();
        Escritura porLamport = ambas.stream().max(Comparator.comparingLong(Escritura::lamport)).orElseThrow();

        assertThat(porReloj.precio()).as("última escritura por marca de tiempo").isEqualTo("100");   // ¡se pierde la corrección!
        assertThat(porLamport.precio()).as("por reloj lógico, que respeta la causalidad").isEqualTo("120");
    }
}
